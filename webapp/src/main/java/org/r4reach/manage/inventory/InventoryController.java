package org.r4reach.manage.inventory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.data.ItemStatus;
import org.r4reach.manage.ManageSiteDao;
import org.r4reach.manage.ManageSiteDao.ItemTagData;
import org.r4reach.manage.SelectSiteController;
import org.r4reach.manage.UserSiteAuthorization;
import org.r4reach.supplies.site.details.SiteDetailDao;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for operations involving item updates at sites (item added to site, item removed from
 * site, item status changed).
 */
@Controller
@Slf4j
public class InventoryController {
  public static final String PATH_INVENTORY = "/manage/inventory/inventory";

  public static String buildInventoryPath(long siteId) {
    return PATH_INVENTORY + "?siteId=" + siteId;
  }

  private final Jdbi jdbi;

  public InventoryController(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /** Returns null if ID is not valid or DNE. */
  String fetchSiteName(String siteId) {
    if (siteId == null || siteId.isBlank()) {
      return null;
    }

    try {
      long id = Long.parseLong(siteId);
      return ManageSiteDao.fetchSiteName(jdbi, id);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Display inventory listing for a site. */
  @GetMapping(PATH_INVENTORY)
  ModelAndView fetchSiteInventoryListing(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> sites, @RequestParam String siteId) {
    SiteDetailDao.SiteDetailData data =
        UserSiteAuthorization.isAuthorizedForSite(jdbi, sites, siteId).orElse(null);
    if (data == null) {
      return new ModelAndView("redirect:" + SelectSiteController.PATH_SELECT_SITE);
    }
    Map<String, Object> pageParams = new HashMap<>();
    pageParams.put("siteName", data.getSiteName());
    pageParams.put("siteId", siteId);

    List<ItemInventoryDisplay> inventoryList =
        ManageSiteDao.fetchSiteInventory(jdbi, Long.parseLong(siteId)).stream()
            .map(ItemInventoryDisplay::new)
            .sorted(
                Comparator.comparing(
                    d -> d.getItemName().toUpperCase())) // ItemInventoryDisplay::getItemName))
            .toList();

    pageParams.put("inventoryList", inventoryList);
    pageParams.put(
        "tagList",
        ItemTagDao.fetchAllDescriptionTags(jdbi).stream()
            .map(tag -> new TagData(tag, "#7fffd4"))
            .toList());
    return new ModelAndView("manage/inventory/inventory", pageParams);
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  static class TagData {
    private String tagName;
    private String tagColor;
  }

  @Data
  @Builder
  @AllArgsConstructor
  static class ItemInventoryDisplay {
    String itemName;
    List<ItemTagData> tags;

    /** Should either be blank or "checked" */
    @Builder.Default String itemChecked = "";

    @Builder.Default String urgentChecked = "";
    @Builder.Default String neededChecked = "";
    @Builder.Default String availableChecked = "";
    @Builder.Default String oversupplyChecked = "";

    ItemInventoryDisplay(ManageSiteDao.SiteInventory siteInventory) {
      itemName = siteInventory.getItemName();
      tags = siteInventory.getTags();
      itemChecked = siteInventory.isActive() ? "checked" : "";

      urgentChecked =
          ItemStatus.URGENTLY_NEEDED.getText().equalsIgnoreCase(siteInventory.getItemStatus())
              ? "checked"
              : "";
      neededChecked =
          ItemStatus.NEEDED.getText().equalsIgnoreCase(siteInventory.getItemStatus())
              ? "checked"
              : "";
      oversupplyChecked =
          ItemStatus.OVERSUPPLY.getText().equalsIgnoreCase(siteInventory.getItemStatus())
              ? "checked"
              : "";

      // if none of the statuses are checked, then check 'available' by default.
      availableChecked =
          (urgentChecked.isEmpty() && neededChecked.isEmpty() && oversupplyChecked.isEmpty())
              ? "checked"
              : "";
    }

    @SuppressWarnings("unused")
    public String getItemLabelClass() {
      if (urgentChecked != null && !urgentChecked.isEmpty()) {
        return ItemStatus.URGENTLY_NEEDED.getCssClass();
      } else if (neededChecked != null && !neededChecked.isEmpty()) {
        return ItemStatus.NEEDED.getCssClass();
      } else if (availableChecked != null && !availableChecked.isEmpty()) {
        return ItemStatus.AVAILABLE.getCssClass();
      } else if (oversupplyChecked != null && !oversupplyChecked.isEmpty()) {
        return ItemStatus.OVERSUPPLY.getCssClass();
      } else {
        return ItemStatus.AVAILABLE.getCssClass();
      }
    }

    @SuppressWarnings("unused")
    public String getItemStatusDisabled() {
      if (itemChecked == null || itemChecked.isEmpty()) {
        return "disabled";
      } else {
        return "";
      }
    }
  }

  /** Re-renders a single inventory row fragment for the given item after a mutation. */
  private ModelAndView renderRow(long siteId, String itemName) {
    return renderRow(siteId, itemName, "manage/inventory/inventory-row-single", Map.of());
  }

  private ModelAndView renderRow(
      long siteId, String itemName, String view, Map<String, Object> extraModel) {
    ItemInventoryDisplay row =
        ManageSiteDao.fetchSiteInventory(jdbi, siteId).stream()
            .map(ItemInventoryDisplay::new)
            .filter(d -> d.getItemName().equals(itemName))
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Item not found after update: " + itemName));
    Map<String, Object> model = new HashMap<>(extraModel);
    model.put("row", row);
    model.put("siteId", String.valueOf(siteId));
    return new ModelAndView(view, model);
  }

  /**
   * Activates/deactivates an item at a site and/or changes its status. Posted by the inventory
   * row's htmx checkbox and status radios; returns the re-rendered row fragment that htmx swaps in
   * place. An absent {@code active} param (unchecked checkbox) deactivates the item.
   */
  @PostMapping("/manage/update-site-item")
  ModelAndView updateSiteItem(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> sites,
      @RequestParam Map<String, String> params) {
    String siteId = params.get("siteId");
    SiteDetailDao.SiteDetailData siteData =
        UserSiteAuthorization.isAuthorizedForSite(jdbi, sites, siteId).orElse(null);
    if (siteData == null) {
      return new ModelAndView("redirect:" + SelectSiteController.PATH_SELECT_SITE);
    }
    String itemName =
        Optional.ofNullable(params.get("itemName"))
            .map(String::trim)
            .orElseThrow(
                () -> new IllegalArgumentException("Missing item name in params: " + params));
    long id = Long.parseLong(siteId);

    if (params.get("active") != null) {
      String itemStatus = params.get("itemStatus");
      if (itemStatus == null || !ItemStatus.allItemStatus().contains(itemStatus)) {
        itemStatus = ItemStatus.AVAILABLE.getText();
      }
      log.info(
          "Activating item: {}, site: {}, status: {}",
          itemName,
          siteData.getSiteName(),
          itemStatus);
      InventoryDao.updateSiteItemActive(jdbi, id, itemName, itemStatus);
    } else {
      log.info("Deactivating item: {}, site: {}", itemName, siteData.getSiteName());
      InventoryDao.updateSiteItemInactive(jdbi, id, itemName);
    }
    return renderRow(id, itemName);
  }

  /**
   * Creates a brand new item, adds it to the site, and returns the new row (swapped into the table
   * out-of-band) plus a confirmation. On a duplicate name returns an error fragment instead.
   */
  @PostMapping("/manage/add-site-item")
  ModelAndView addNewSiteItem(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> sites,
      @RequestParam Map<String, String> params) {
    String siteId = params.get("siteId");
    SiteDetailDao.SiteDetailData siteData =
        UserSiteAuthorization.isAuthorizedForSite(jdbi, sites, siteId).orElse(null);
    if (siteData == null) {
      return new ModelAndView("redirect:" + SelectSiteController.PATH_SELECT_SITE);
    }
    String itemName =
        Optional.ofNullable(params.get("itemName"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .orElseThrow(
                () ->
                    new IllegalArgumentException("addNewSiteItem:: missing item name: " + params));
    String itemStatus = params.get("itemStatus");
    if (itemStatus == null || !ItemStatus.allItemStatus().contains(itemStatus)) {
      itemStatus = ItemStatus.AVAILABLE.getText();
    }

    log.info("Creating brand new item: {}", params);
    boolean itemAdded = InventoryDao.addNewItem(jdbi, itemName);
    if (!itemAdded) {
      log.warn("Failed to add item, already exists. Params: {}", params);
      return new ModelAndView("manage/inventory/inventory-add-error");
    }
    long id = Long.parseLong(siteId);
    InventoryDao.updateSiteItemActive(jdbi, id, itemName, itemStatus);
    return renderRow(
        id,
        itemName,
        "manage/inventory/inventory-added",
        Map.of("oob", true, "addedName", itemName));
  }
}
