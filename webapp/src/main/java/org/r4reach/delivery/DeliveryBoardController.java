package org.r4reach.delivery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Dispatcher kanban board for deliveries. Columns are the forward {@link DeliveryStatus} values;
 * dragging a card to another column changes that delivery's status. Reachable only by {@link
 * UserRole#DISPATCHER} (read/write). Lives under {@code /dispatch}, so {@code AuthInterceptor}
 * login-gates it; the dispatch landing page links here.
 */
@Controller
@Slf4j
@AllArgsConstructor
public class DeliveryBoardController {

  public static final String PATH_BOARD = "/dispatch/deliveries";
  public static final String PATH_SET_STATUS = "/dispatch/deliveries/set-status";
  public static final String PATH_NEW = "/dispatch/deliveries/new";
  public static final String PATH_CREATE = "/dispatch/deliveries/create";

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
      @RequestParam(required = false) String status) {
    if (!UserRole.canManageDeliveries(roles)) {
      return new ModelAndView("redirect:/");
    }
    DeliveryStatus startStatus = parseBoardStatus(status).orElse(DeliveryStatus.DRIVER_VOLUNTEERED);
    return createForm(startStatus, new HashMap<>(), null);
  }

  @PostMapping(PATH_CREATE)
  ModelAndView create(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam(required = false) Long fromSiteId,
      @RequestParam(required = false) Long toSiteId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String targetDeliveryDate,
      @RequestParam(required = false) String dispatcherName,
      @RequestParam(required = false) String dispatcherNumber,
      @RequestParam(required = false) String driverName,
      @RequestParam(required = false) String driverNumber,
      @RequestParam(required = false) String dispatcherNotes,
      @RequestParam(required = false) String items) {
    if (!UserRole.canManageDeliveries(roles)) {
      return new ModelAndView("redirect:/");
    }
    DeliveryStatus startStatus = parseBoardStatus(status).orElse(DeliveryStatus.DRIVER_VOLUNTEERED);

    if (fromSiteId == null || toSiteId == null) {
      Map<String, Object> entered = new HashMap<>();
      entered.put("targetDeliveryDate", targetDeliveryDate);
      entered.put("dispatcherName", dispatcherName);
      entered.put("dispatcherNumber", dispatcherNumber);
      entered.put("driverName", driverName);
      entered.put("driverNumber", driverNumber);
      entered.put("dispatcherNotes", dispatcherNotes);
      entered.put("items", items);
      return createForm(startStatus, entered, "Pickup and drop-off sites are both required.");
    }

    DeliveryDao.createDelivery(
        jdbi,
        DeliveryDao.CreateDeliveryRequest.builder()
            .fromSiteId(fromSiteId)
            .toSiteId(toSiteId)
            .deliveryStatus(startStatus)
            .targetDeliveryDate(targetDeliveryDate)
            .dispatcherName(dispatcherName)
            .dispatcherNumber(dispatcherNumber)
            .driverName(driverName)
            .driverNumber(driverNumber)
            .dispatcherNotes(dispatcherNotes)
            .items(splitItems(items))
            .build());
    return new ModelAndView("redirect:" + PATH_BOARD);
  }

  /**
   * The create form's text fields, whose values it echoes back via {@code {{field}}}. The template
   * references every one unconditionally, and Mustache is strict (a missing variable throws), so
   * the form must always supply them — blank on the initial GET, prior input on a rejected
   * submission.
   */
  private static final List<String> FORM_TEXT_FIELDS =
      List.of(
          "targetDeliveryDate",
          "driverName",
          "driverNumber",
          "dispatcherName",
          "dispatcherNumber",
          "dispatcherNotes",
          "items");

  /** Builds the create-delivery form, re-populating text fields from a rejected submission. */
  private ModelAndView createForm(
      DeliveryStatus startStatus, Map<String, Object> enteredValues, String errorMessage) {
    Map<String, Object> params = new HashMap<>(enteredValues);
    FORM_TEXT_FIELDS.forEach(field -> params.putIfAbsent(field, ""));
    params.put("sites", DeliveryDao.fetchSiteOptions(jdbi));
    params.put("statusName", startStatus.name());
    params.put("statusLabel", startStatus.getAirtableName());
    if (errorMessage != null) {
      params.put("errorMessage", errorMessage);
    }
    return new ModelAndView("delivery/delivery-create", params);
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

  private static List<String> splitItems(String items) {
    if (items == null || items.isBlank()) {
      return List.of();
    }
    return Arrays.stream(items.split("\\R"))
        .map(String::strip)
        .filter(item -> !item.isEmpty())
        .toList();
  }
}
