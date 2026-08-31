package org.r4reach.admin.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.admin.item.MergeItemsController.InventoryItem;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.HtmlUtils;

/**
 * Item-tagging admin UI, gated to {@link UserRole#DATA_ADMIN}. Lets a data admin manage the tag
 * registry (create / rename / delete) and toggle which catalog items each tag is assigned to, one
 * at a time or in bulk across the filtered set. This replaces the old Airtable-driven tag import,
 * which had no in-app UI.
 */
@Controller
@AllArgsConstructor
@Slf4j
public class ItemTagAdminController {

  public static final String PATH_TAG_ITEMS = "/admin/tag-items";

  private final Jdbi jdbi;

  @GetMapping(PATH_TAG_ITEMS)
  ModelAndView tagItemsPage(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    if (!UserRole.isDataAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    return new ModelAndView("admin/tag-items", buildPageParams());
  }

  private Map<String, Object> buildPageParams() {
    List<TagAdminDao.TagRow> tags = TagAdminDao.fetchAllTags(jdbi);
    List<InventoryItem> items = MergeItemsController.fetchAllItems(jdbi);
    Map<Long, Set<Long>> tagsByItem = tagsByItem();

    List<Map<String, Object>> itemRows =
        items.stream().map(item -> toItemRow(item, tags, tagsByItem)).toList();
    long untaggedCount =
        items.stream()
            .filter(item -> tagsByItem.getOrDefault(item.getId(), Set.of()).isEmpty())
            .count();

    Map<String, Object> params = new HashMap<>();
    params.put("tags", tags);
    params.put("items", itemRows);
    params.put("hasTags", !tags.isEmpty());
    params.put("itemTotal", items.size());
    params.put("untaggedCount", untaggedCount);
    params.put("allTagged", untaggedCount == 0);
    return params;
  }

  private Map<Long, Set<Long>> tagsByItem() {
    return TagAdminDao.fetchAssignments(jdbi).stream()
        .collect(
            Collectors.groupingBy(
                TagAdminDao.Assignment::getItemId,
                Collectors.mapping(TagAdminDao.Assignment::getTagId, Collectors.toSet())));
  }

  /** Builds one item's row model: the item plus every tag flagged assigned / not for that item. */
  private static Map<String, Object> toItemRow(
      InventoryItem item, List<TagAdminDao.TagRow> tags, Map<Long, Set<Long>> tagsByItem) {
    Set<Long> assigned = tagsByItem.getOrDefault(item.getId(), Set.of());
    List<Map<String, Object>> cells =
        tags.stream()
            .map(
                tag ->
                    Map.<String, Object>of(
                        "itemId", item.getId(),
                        "tagId", tag.getId(),
                        "tagName", tag.getName(),
                        "assigned", assigned.contains(tag.getId())))
            .toList();
    Map<String, Object> row = new HashMap<>();
    row.put("itemId", item.getId());
    row.put("itemName", item.getItemName());
    row.put("tags", cells);
    row.put("untagged", assigned.isEmpty());
    return row;
  }

  /**
   * The refreshed row after a toggle, plus an out-of-band update of the toggled tag's item count in
   * the registry list, so Section A's count doesn't silently go stale.
   */
  private ModelAndView toggledRowView(long itemId, long tagId) {
    InventoryItem item =
        MergeItemsController.fetchAllItems(jdbi).stream()
            .filter(i -> i.getId() == itemId)
            .findFirst()
            .orElseThrow();
    Map<String, Object> model = toItemRow(item, TagAdminDao.fetchAllTags(jdbi), tagsByItem());
    TagAdminDao.fetchTag(jdbi, tagId)
        .ifPresent(
            tag -> {
              model.put("oobTagId", tag.getId());
              model.put("oobCountLabel", tag.getItemCountLabel());
            });
    return new ModelAndView("admin/tag-items-item-row", model);
  }

  @PostMapping(PATH_TAG_ITEMS + "/create")
  ResponseEntity<String> createTag(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles, @RequestParam String name) {
    if (!UserRole.isDataAdmin(roles)) {
      return htmlBadRequest("Not authorized");
    }
    String formatError = TagAdminDao.nameError(name).orElse(null);
    if (formatError != null) {
      return htmlBadRequest(formatError);
    }
    if (TagAdminDao.createTag(jdbi, name).isEmpty()) {
      return htmlBadRequest(
          "A tag named “" + HtmlUtils.htmlEscape(name.trim()) + "” already exists.");
    }
    return refresh();
  }

  @PostMapping(PATH_TAG_ITEMS + "/rename")
  ResponseEntity<String> renameTag(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long tagId,
      @RequestParam String name) {
    if (!UserRole.isDataAdmin(roles)) {
      return htmlBadRequest("Not authorized");
    }
    String formatError = TagAdminDao.nameError(name).orElse(null);
    if (formatError != null) {
      return htmlBadRequest(formatError);
    }
    if (!TagAdminDao.renameTag(jdbi, tagId, name)) {
      return htmlBadRequest(
          "Another tag is already named “" + HtmlUtils.htmlEscape(name.trim()) + "”.");
    }
    return refresh();
  }

  @PostMapping(PATH_TAG_ITEMS + "/delete")
  ResponseEntity<String> deleteTag(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles, @RequestParam long tagId) {
    if (!UserRole.isDataAdmin(roles)) {
      return htmlBadRequest("Not authorized");
    }
    TagAdminDao.deleteTag(jdbi, tagId);
    return refresh();
  }

  @PostMapping(PATH_TAG_ITEMS + "/toggle")
  ModelAndView toggleAssignment(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long itemId,
      @RequestParam long tagId,
      @RequestParam boolean assigned) {
    if (!UserRole.isDataAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    TagAdminDao.setAssignment(jdbi, itemId, tagId, assigned);
    return toggledRowView(itemId, tagId);
  }

  @PostMapping(PATH_TAG_ITEMS + "/bulk")
  ResponseEntity<String> bulkAssign(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam(name = "itemIds", required = false) List<Long> itemIds,
      @RequestParam long tagId,
      @RequestParam boolean assigned) {
    if (!UserRole.isDataAdmin(roles)) {
      return htmlBadRequest("Not authorized");
    }
    TagAdminDao.setAssignmentBulk(jdbi, itemIds == null ? List.of() : itemIds, tagId, assigned);
    return refresh();
  }

  /** Tells htmx to reload the page so a new/renamed/removed tag propagates to every item row. */
  private static ResponseEntity<String> refresh() {
    return ResponseEntity.ok().header("HX-Refresh", "true").build();
  }

  private static ResponseEntity<String> htmlBadRequest(String message) {
    // 200 so htmx swaps the message into place (it ignores non-2xx bodies by default).
    return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body("<span class=\"errorMessage\">" + message + "</span>");
  }
}
