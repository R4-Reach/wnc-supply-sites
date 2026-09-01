package org.r4reach.delivery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.r4reach.incoming.webhook.NeedsMatchingController;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/**
 * Dispatcher kanban board for deliveries. Columns are the forward {@link DeliveryStatus} values;
 * dragging a card to another column changes that delivery's status. Reachable only by {@link
 * UserRole#DISPATCHER} (read/write). Lives under {@code /dispatch}, which the security filter chain
 * login-gates; the dispatch landing page links here.
 */
@Controller
@Slf4j
@AllArgsConstructor
public class DeliveryBoardController {

  public static final String PATH_BOARD = "/dispatch/deliveries";
  public static final String PATH_SET_STATUS = "/dispatch/deliveries/set-status";
  public static final String PATH_NEW = "/dispatch/deliveries/new";
  public static final String PATH_CREATE = "/dispatch/deliveries/create";
  public static final String PATH_AVAILABLE_ITEMS = "/dispatch/deliveries/available-items";
  public static final String PATH_DETAIL = "/dispatch/deliveries/{publicUrlKey}";
  public static final String PATH_MATCH_GOODS = "/dispatch/deliveries/{publicUrlKey}/match-goods";

  /** The dispatcher detail (read/write) page for one delivery. */
  public static String buildDetailLink(String publicUrlKey) {
    return "/dispatch/deliveries/" + publicUrlKey;
  }

  /**
   * The forward pipeline columns, in order. {@link DeliveryStatus#DELIVERY_CANCELLED} is a terminal
   * state reached through the cancel flow, so it is neither a column nor a drop target here.
   */
  private static final List<DeliveryStatus> BOARD_STATUSES =
      Arrays.stream(DeliveryStatus.values())
          .filter(status -> status != DeliveryStatus.DELIVERY_CANCELLED)
          .toList();

  private final Jdbi jdbi;

  @GetMapping(PATH_BOARD)
  ModelAndView board(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    if (!UserRole.canManageDeliveries(roles)) {
      return new ModelAndView("redirect:/");
    }

    Map<String, List<Delivery>> byStatus = new HashMap<>();
    for (Delivery delivery : DeliveryDao.fetchAllDeliveries(jdbi)) {
      byStatus
          .computeIfAbsent(delivery.getDeliveryStatus(), key -> new ArrayList<>())
          .add(delivery);
    }

    List<Map<String, Object>> columns = new ArrayList<>();
    for (DeliveryStatus status : BOARD_STATUSES) {
      List<Delivery> deliveries = byStatus.getOrDefault(status.getAirtableName(), List.of());
      Map<String, Object> column = new HashMap<>();
      column.put("label", status.getAirtableName());
      column.put("statusName", status.name());
      column.put("count", deliveries.size());
      column.put("deliveries", deliveries);
      columns.add(column);
    }

    Map<String, Object> params = new HashMap<>();
    params.put("columns", columns);
    return new ModelAndView("delivery/deliveries-board", params);
  }

  /** Moves a delivery to a new column. Called by the board's drag-and-drop handler. */
  @PostMapping(PATH_SET_STATUS)
  ResponseEntity<Void> setStatus(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam String publicUrlKey,
      @RequestParam String status) {
    if (!UserRole.canManageDeliveries(roles)) {
      return ResponseEntity.status(403).build();
    }
    Optional<DeliveryStatus> target = parseBoardStatus(status);
    if (target.isEmpty()) {
      return ResponseEntity.badRequest().build();
    }
    DeliveryDao.updateDeliveryStatus(jdbi, publicUrlKey, target.get());
    return ResponseEntity.ok().build();
  }

  @GetMapping(PATH_NEW)
  ModelAndView newDelivery(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone,
      @RequestParam(required = false) String status) {
    if (!UserRole.canManageDeliveries(roles)) {
      return new ModelAndView("redirect:/");
    }
    DeliveryStatus startStatus = parseBoardStatus(status).orElse(DeliveryStatus.DRIVER_VOLUNTEERED);
    // The create page is dispatcher-only, so the current user is always a dispatcher: preselect
    // them in the dispatcher dropdown.
    Long currentDispatcherId = DeliveryDao.fetchUserIdByPhone(jdbi, userPhone).orElse(null);
    return createForm(
        startStatus, null, null, null, null, List.of(), currentDispatcherId, null, null);
  }

  @PostMapping(PATH_CREATE)
  ModelAndView create(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam(required = false) Long fromSiteId,
      @RequestParam(required = false) Long toSiteId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String targetDeliveryDate,
      @RequestParam(required = false) Long dispatcherUserId,
      @RequestParam(required = false) Long driverUserId,
      @RequestParam(required = false) String dispatcherNotes,
      @RequestParam(required = false) List<String> items) {
    if (!UserRole.canManageDeliveries(roles)) {
      return new ModelAndView("redirect:/");
    }
    DeliveryStatus startStatus = parseBoardStatus(status).orElse(DeliveryStatus.DRIVER_VOLUNTEERED);
    List<String> cleanedItems = cleanItems(items);

    if (fromSiteId == null || toSiteId == null) {
      return createForm(
          startStatus,
          fromSiteId,
          toSiteId,
          targetDeliveryDate,
          dispatcherNotes,
          cleanedItems,
          dispatcherUserId,
          driverUserId,
          "Pickup and drop-off sites are both required.");
    }

    DeliveryDao.createDelivery(
        jdbi,
        DeliveryDao.CreateDeliveryRequest.builder()
            .fromSiteId(fromSiteId)
            .toSiteId(toSiteId)
            .deliveryStatus(startStatus)
            .targetDeliveryDate(targetDeliveryDate)
            .dispatcherWssUserId(dispatcherUserId)
            .driverWssUserId(driverUserId)
            .dispatcherNotes(dispatcherNotes)
            .items(cleanedItems)
            .build());
    return new ModelAndView("redirect:" + PATH_BOARD);
  }

  /**
   * Builds the create-delivery form, re-populating fields from a rejected submission. The template
   * references every text field unconditionally and Mustache is strict (a missing variable throws),
   * so blank values are always supplied. The dispatcher, driver, and both sites are dropdowns whose
   * selected option is marked from the (nullable) selected ids.
   */
  private ModelAndView createForm(
      DeliveryStatus startStatus,
      Long fromSiteId,
      Long toSiteId,
      String targetDeliveryDate,
      String dispatcherNotes,
      List<String> selectedItemNames,
      Long selectedDispatcherUserId,
      Long selectedDriverUserId,
      String errorMessage) {
    Map<String, Object> params = new HashMap<>();
    params.put("targetDeliveryDate", targetDeliveryDate == null ? "" : targetDeliveryDate);
    params.put("dispatcherNotes", dispatcherNotes == null ? "" : dispatcherNotes);
    params.put("sitesFrom", DeliveryDao.fetchSiteOptions(jdbi, fromSiteId));
    params.put("sitesTo", DeliveryDao.fetchSiteOptions(jdbi, toSiteId));
    params.put("dispatchers", DeliveryDao.fetchDispatcherOptions(jdbi, selectedDispatcherUserId));
    params.put("drivers", DeliveryDao.fetchDriverOptions(jdbi, selectedDriverUserId));
    params.put("statusName", startStatus.name());
    params.put("statusLabel", startStatus.getAirtableName());
    putItemPickerParams(params, selectedItemNames, fromSiteId);
    if (errorMessage != null) {
      params.put("errorMessage", errorMessage);
    }
    return new ModelAndView("delivery/delivery-create", params);
  }

  /**
   * JSON list of the item names a pickup site has to give — drives the picker's live refresh when
   * the dispatcher changes the pickup site on the form.
   */
  @GetMapping(PATH_AVAILABLE_ITEMS)
  @ResponseBody
  ResponseEntity<List<String>> availableItems(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam(required = false) Long fromSiteId) {
    if (!UserRole.canManageDeliveries(roles)) {
      return ResponseEntity.status(403).build();
    }
    if (fromSiteId == null) {
      return ResponseEntity.ok(List.of());
    }
    return ResponseEntity.ok(DeliveryDao.fetchAvailableItemNamesForSite(jdbi, fromSiteId));
  }

  /** The dispatcher read/write detail page for one delivery (reached from a kanban card). */
  @GetMapping(PATH_DETAIL)
  ModelAndView detail(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @PathVariable("publicUrlKey") String publicUrlKey,
      @RequestParam(required = false) Integer matchAdded,
      @RequestParam(required = false) Integer matchCandidates) {
    if (!UserRole.canManageDeliveries(roles)) {
      return new ModelAndView("redirect:/");
    }
    Optional<Delivery> found = DeliveryDao.fetchDeliveryByPublicKey(jdbi, publicUrlKey);
    if (found.isEmpty()) {
      return new ModelAndView("redirect:" + PATH_BOARD);
    }
    Delivery delivery = found.get();
    DeliveryStatus status =
        DeliveryStatus.fromAirtableName(delivery.getDeliveryStatus())
            .orElse(DeliveryStatus.CREATING_DISPATCH);

    String matchMessage = null;
    Boolean matchIsError = null;
    if (matchAdded != null) {
      matchIsError = false;
      if (matchAdded > 0) {
        matchMessage = "Added " + matchAdded + (matchAdded == 1 ? " item" : " items");
      } else if (matchCandidates != null && matchCandidates > 0) {
        matchMessage = "No new items to add — all matched goods are already on this delivery";
      } else {
        matchMessage = "No matching goods found between these sites";
        matchIsError = true;
      }
    }

    return detailForm(
        delivery,
        delivery.getFromSiteId(),
        delivery.getToSiteId(),
        status,
        delivery.getDeliveryDate(),
        delivery.getDispatcherNotes(),
        delivery.getDispatcherWssUserId(),
        delivery.getDriverWssUserId(),
        delivery.getItemList(),
        null,
        matchMessage,
        matchIsError);
  }

  /** Persists dispatcher edits to a delivery and its item list, then reloads the detail page. */
  @PostMapping(PATH_DETAIL)
  ModelAndView update(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @PathVariable("publicUrlKey") String publicUrlKey,
      @RequestParam(required = false) Long fromSiteId,
      @RequestParam(required = false) Long toSiteId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String targetDeliveryDate,
      @RequestParam(required = false) Long dispatcherUserId,
      @RequestParam(required = false) Long driverUserId,
      @RequestParam(required = false) String dispatcherNotes,
      @RequestParam(required = false) List<String> items) {
    if (!UserRole.canManageDeliveries(roles)) {
      return new ModelAndView("redirect:/");
    }
    Optional<Delivery> found = DeliveryDao.fetchDeliveryByPublicKey(jdbi, publicUrlKey);
    if (found.isEmpty()) {
      return new ModelAndView("redirect:" + PATH_BOARD);
    }
    Delivery delivery = found.get();
    DeliveryStatus current =
        DeliveryStatus.fromAirtableName(delivery.getDeliveryStatus())
            .orElse(DeliveryStatus.CREATING_DISPATCH);
    DeliveryStatus newStatus = parseBoardStatus(status).orElse(current);
    List<String> cleanedItems = cleanItems(items);

    if (fromSiteId == null || toSiteId == null) {
      return detailForm(
          delivery,
          fromSiteId,
          toSiteId,
          newStatus,
          targetDeliveryDate,
          dispatcherNotes,
          dispatcherUserId,
          driverUserId,
          cleanedItems,
          "Pickup and drop-off sites are both required.",
          null,
          null);
    }

    DeliveryDao.updateDelivery(
        jdbi,
        publicUrlKey,
        DeliveryDao.UpdateDeliveryRequest.builder()
            .fromSiteId(fromSiteId)
            .toSiteId(toSiteId)
            .deliveryStatus(newStatus)
            .targetDeliveryDate(targetDeliveryDate)
            .dispatcherWssUserId(dispatcherUserId)
            .driverWssUserId(driverUserId)
            .dispatcherNotes(dispatcherNotes)
            .build());
    DeliveryDao.setDeliveryItems(jdbi, publicUrlKey, cleanedItems);
    return new ModelAndView("redirect:" + buildDetailLink(publicUrlKey));
  }

  /**
   * Dispatcher action: add the goods the drop-off needs that the pickup has available (the app's
   * needs match, reused from {@link NeedsMatchingController}) to this delivery's items, skipping
   * any already present. Role-gated like the rest of the board; redirects back to the detail page
   * with the outcome so the picker can report what was added.
   */
  @PostMapping(PATH_MATCH_GOODS)
  ModelAndView matchGoods(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @PathVariable("publicUrlKey") String publicUrlKey) {
    if (!UserRole.canManageDeliveries(roles)) {
      return new ModelAndView("redirect:/");
    }
    Optional<Delivery> found = DeliveryDao.fetchDeliveryByPublicKey(jdbi, publicUrlKey);
    if (found.isEmpty()) {
      return new ModelAndView("redirect:" + PATH_BOARD);
    }
    Delivery delivery = found.get();

    Long fromWssId =
        delivery.getFromSiteId() == null
            ? null
            : DeliveryDao.fetchSiteWssId(jdbi, delivery.getFromSiteId()).orElse(null);
    Long toWssId =
        delivery.getToSiteId() == null
            ? null
            : DeliveryDao.fetchSiteWssId(jdbi, delivery.getToSiteId()).orElse(null);

    // Both endpoints must be WSS-registered sites for a match to be possible; deliveries to/from
    // external sites simply add nothing.
    int candidates = 0;
    int added = 0;
    if (fromWssId != null && toWssId != null) {
      List<String> matchedItems =
          NeedsMatchingController.computeNeedsMatch(jdbi, fromWssId, toWssId);
      candidates = matchedItems.size();
      added = DeliveryDao.addItemsToDelivery(jdbi, publicUrlKey, matchedItems);
      log.info(
          "Matched goods for delivery {}: {} candidate(s), {} newly added",
          publicUrlKey,
          candidates,
          added);
    }

    return new ModelAndView(
        "redirect:"
            + buildDetailLink(publicUrlKey)
            + "?matchAdded="
            + added
            + "&matchCandidates="
            + candidates);
  }

  /**
   * Builds the dispatcher detail (read/write) page for a delivery, populating the edit form from
   * the supplied field values (the delivery's stored values on a GET, or a rejected submission's
   * values on a failed save).
   */
  private ModelAndView detailForm(
      Delivery delivery,
      Long fromSiteId,
      Long toSiteId,
      DeliveryStatus status,
      String targetDeliveryDate,
      String dispatcherNotes,
      Long dispatcherUserId,
      Long driverUserId,
      List<String> selectedItemNames,
      String errorMessage,
      String matchMessage,
      Boolean matchIsError) {
    Map<String, Object> params = new HashMap<>();
    params.put("publicKey", delivery.getPublicKey());
    params.put("deliveryId", delivery.getDeliveryNumber());
    params.put("deliveryStatusLabel", delivery.getDeliveryStatus());
    params.put("targetDeliveryDate", targetDeliveryDate == null ? "" : targetDeliveryDate);
    params.put("dispatcherNotes", dispatcherNotes == null ? "" : dispatcherNotes);
    params.put("sitesFrom", DeliveryDao.fetchSiteOptions(jdbi, fromSiteId));
    params.put("sitesTo", DeliveryDao.fetchSiteOptions(jdbi, toSiteId));
    params.put("dispatchers", DeliveryDao.fetchDispatcherOptions(jdbi, dispatcherUserId));
    params.put("drivers", DeliveryDao.fetchDriverOptions(jdbi, driverUserId));
    params.put("statuses", statusOptions(status));
    params.put(
        "publicManifestUrl", DeliveryController.buildDeliveryPageLink(delivery.getPublicKey()));
    params.put(
        "dispatcherManifestUrl",
        delivery.getDispatchCode() == null
            ? DeliveryController.buildDeliveryPageLink(delivery.getPublicKey())
            : DeliveryController.buildDeliveryPageLinkWithCode(
                delivery.getPublicKey(), delivery.getDispatchCode()));
    params.put("missingData", delivery.missingData());
    putItemPickerParams(params, selectedItemNames, fromSiteId);
    if (errorMessage != null) {
      params.put("errorMessage", errorMessage);
    }
    if (matchMessage != null) {
      params.put("matchMessage", matchMessage);
      params.put("matchMessageIsError", Boolean.TRUE.equals(matchIsError));
    }
    return new ModelAndView("delivery/delivery-detail", params);
  }

  /** Board-status dropdown options, marking {@code current} selected. */
  private static List<Map<String, Object>> statusOptions(DeliveryStatus current) {
    List<Map<String, Object>> options = new ArrayList<>();
    for (DeliveryStatus status : BOARD_STATUSES) {
      Map<String, Object> option = new HashMap<>();
      option.put("name", status.name());
      option.put("label", status.getAirtableName());
      option.put("selected", status == current);
      options.add(option);
    }
    return options;
  }

  /**
   * Populates the shared item-picker partial: the current selection as chips (flagging any that no
   * longer resolve to an inventory item as legacy), the pickup site's available items as
   * checkboxes, and the full catalog for the search datalist.
   */
  private void putItemPickerParams(
      Map<String, Object> params, List<String> selectedItemNames, Long fromSiteId) {
    List<String> catalog = DeliveryDao.fetchAllItemNames(jdbi);
    Set<String> catalogLower =
        catalog.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    List<String> available =
        fromSiteId == null
            ? List.of()
            : DeliveryDao.fetchAvailableItemNamesForSite(jdbi, fromSiteId);
    Set<String> selectedLower =
        selectedItemNames.stream()
            .map(name -> name.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());

    List<Map<String, Object>> selectedChips = new ArrayList<>();
    for (String name : selectedItemNames) {
      Map<String, Object> chip = new HashMap<>();
      chip.put("name", name);
      chip.put("legacy", !catalogLower.contains(name.toLowerCase(Locale.ROOT)));
      selectedChips.add(chip);
    }

    List<Map<String, Object>> availableOptions = new ArrayList<>();
    for (String name : available) {
      Map<String, Object> option = new HashMap<>();
      option.put("name", name);
      option.put("checked", selectedLower.contains(name.toLowerCase(Locale.ROOT)));
      availableOptions.add(option);
    }

    List<Map<String, Object>> catalogOptions = new ArrayList<>();
    for (String name : catalog) {
      Map<String, Object> option = new HashMap<>();
      option.put("name", name);
      catalogOptions.add(option);
    }

    params.put("selectedItems", selectedChips);
    params.put("availableItems", availableOptions);
    params.put("catalogItems", catalogOptions);
    params.put("pickupSiteChosen", fromSiteId != null);
    params.put("pickupHasItems", !available.isEmpty());
  }

  /** Parses an enum name, accepting it only if it is a board column (never cancelled/unknown). */
  private static Optional<DeliveryStatus> parseBoardStatus(String status) {
    if (status == null) {
      return Optional.empty();
    }
    try {
      DeliveryStatus parsed = DeliveryStatus.valueOf(status);
      return BOARD_STATUSES.contains(parsed) ? Optional.of(parsed) : Optional.empty();
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** Strips and drops blank item names from a submitted picker selection. */
  private static List<String> cleanItems(List<String> items) {
    if (items == null) {
      return List.of();
    }
    return items.stream()
        .filter(item -> item != null)
        .map(String::strip)
        .filter(item -> !item.isEmpty())
        .toList();
  }
}
