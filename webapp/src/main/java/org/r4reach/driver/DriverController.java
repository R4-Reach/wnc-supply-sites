package org.r4reach.driver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.delivery.Delivery;
import org.r4reach.delivery.DeliveryDao;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@Slf4j
@AllArgsConstructor
public class DriverController {

  private final Jdbi jdbi;

  enum PageParams {
    location,
    licensePlates,
    availability,
    comments,
    active,
    canLift50lbs,
    palletCapacity,
    driverDeliveries,
    ;
  }

  @GetMapping("/driver/portal")
  ModelAndView showDriverPortal(@ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone) {
    if (userPhone == null) {
      log.warn("Driver portal reached without an authenticated phone number");
      return new ModelAndView("redirect:/");
    }

    // A user may hold the DRIVER role before any driver row exists (e.g. a freshly registered
    // driver who has not saved their profile). Render an empty, active-by-default form in that
    // case; the row is created on the first save (see DriverDao.upsert). active defaults to true to
    // match the driver-table column default the row will get, so the pre-save view matches the
    // saved state.
    Optional<Driver> existingDriver = DriverDao.lookupByPhone(jdbi, userPhone);
    boolean isNewDriver = existingDriver.isEmpty();
    Driver driver =
        existingDriver.orElseGet(() -> Driver.builder().phone(userPhone).active(true).build());

    List<Delivery> deliveries = List.of();
    boolean routesLoadError = false;
    try {
      deliveries = DeliveryDao.fetchDeliveriesByDriverPhoneNumber(jdbi, userPhone);
    } catch (RuntimeException e) {
      log.error("Failed to load driver deliveries for portal (phone={})", userPhone, e);
      routesLoadError = true;
    }

    Map<String, Object> params = new HashMap<>();
    params.put(PageParams.location.name(), Optional.ofNullable(driver.getLocation()).orElse(""));
    params.put(
        PageParams.licensePlates.name(), Optional.ofNullable(driver.getLicensePlates()).orElse(""));
    params.put(
        PageParams.availability.name(), Optional.ofNullable(driver.getAvailability()).orElse(""));
    params.put(PageParams.comments.name(), Optional.ofNullable(driver.getComments()).orElse(""));
    params.put(PageParams.active.name(), driver.isActive());
    params.put(PageParams.canLift50lbs.name(), driver.isCan_lift_50lbs());
    params.put(PageParams.palletCapacity.name(), driver.getPallet_capacity());
    params.put(PageParams.driverDeliveries.name(), deliveries);
    params.put("routesLoadError", routesLoadError);

    // Capability fields (can-lift / pallet-capacity) are non-nullable primitives in the schema, so
    // a brand-new driver can't be told apart from one who truthfully answered "no" / "0" once a row
    // exists. Rather than add a null sentinel to the schema (a separate migration), a new driver
    // renders with neither option pre-selected -- a blank, required placeholder -- so the first
    // save
    // always records a deliberate answer. See driver-portal UX review F4/T3.
    params.put("isNewDriver", isNewDriver);
    params.put("canLiftBlankSelected", isNewDriver);
    params.put("canLiftYesSelected", !isNewDriver && driver.isCan_lift_50lbs());
    params.put("canLiftNoSelected", !isNewDriver && !driver.isCan_lift_50lbs());
    params.put("palletSelectedBlank", isNewDriver);

    // Server-side "selected" flags for the pallet-capacity dropdown (5 represents "5+").
    int selectedPallet = Math.min(Math.max(driver.getPallet_capacity(), 0), 5);
    for (int i = 0; i <= 5; i++) {
      params.put("palletSelected" + i, !isNewDriver && i == selectedPallet);
    }

    // Route-status counts drive both the "All (active)" default filter's empty-state text and the
    // per-tab counts ("Confirmed (2)") -- see F3/F9. "Active" excludes Completed, matching the
    // default view.
    long volunteeredCount = countInBucket(deliveries, "volunteered");
    long confirmedCount = countInBucket(deliveries, "confirmed");
    long pendingCount = countInBucket(deliveries, "pending");
    long completedCount = countInBucket(deliveries, "completed");
    long activeCount = volunteeredCount + confirmedCount + pendingCount;
    params.put("volunteeredCount", volunteeredCount);
    params.put("confirmedCount", confirmedCount);
    params.put("pendingCount", pendingCount);
    params.put("completedCount", completedCount);
    params.put("activeCount", activeCount);
    params.put("activeIsEmpty", !routesLoadError && activeCount == 0);
    params.put("volunteeredIsEmpty", !routesLoadError && volunteeredCount == 0);
    params.put("confirmedIsEmpty", !routesLoadError && confirmedCount == 0);
    params.put("pendingIsEmpty", !routesLoadError && pendingCount == 0);
    params.put("completedIsEmpty", !routesLoadError && completedCount == 0);

    return new ModelAndView("driver/portal", params);
  }

  private static long countInBucket(List<Delivery> deliveries, String bucketId) {
    return deliveries.stream()
        .filter(d -> bucketId.equals(d.getDriverFacingStatusBucketId()))
        .count();
  }

  @PostMapping("/driver/update")
  ResponseEntity<String> updateDriver(
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone,
      @RequestParam Map<String, String> update) {
    String location = trimOrEmpty(update.get(PageParams.location.name()));
    String licensePlates = trimOrEmpty(update.get(PageParams.licensePlates.name()));
    String availability = trimOrEmpty(update.get(PageParams.availability.name()));
    String comments = trimOrEmpty(update.get(PageParams.comments.name()));
    String canLiftRaw = update.get(PageParams.canLift50lbs.name());

    // Required fields are enforced client-side (native `required`) as a fast path, but the server
    // re-checks since the client constraint can't be trusted (e.g. htmx submits are still POSTs a
    // curl/bot can send directly). See driver-portal UX review F5.
    if (location.isEmpty()) {
      return validationError("location", "Please enter your city and state.");
    }
    if (licensePlates.isEmpty()) {
      return validationError(
          "licensePlates", "Please list at least one license plate for your vehicle.");
    }
    if (!"true".equals(canLiftRaw) && !"false".equals(canLiftRaw)) {
      return validationError("can-lift", "Please answer whether you can lift 50 lbs.");
    }
    Integer palletCapacity = parsePalletCapacity(update.get(PageParams.palletCapacity.name()));
    if (palletCapacity == null) {
      return validationError(
          "pallet-capacity", "Please select how many pallets you're able to transport.");
    }

    // Build straight from the submitted form -- no need to load an existing row first, since the
    // upsert creates one when absent. active/black_listed are intentionally omitted: the upsert
    // leaves them untouched on an existing row and defaults them on insert.
    var updatedDriverData =
        Driver.builder()
            .phone(userPhone)
            .location(location)
            .licensePlates(licensePlates)
            .availability(availability)
            .comments(comments)
            .can_lift_50lbs(Boolean.parseBoolean(canLiftRaw))
            .pallet_capacity(palletCapacity)
            .build();

    DriverDao.upsert(jdbi, updatedDriverData);

    return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body(
            "<span role=\"status\" aria-live=\"polite\" class=\"confirm-success\">"
                + "<span class=\"green-check\" aria-hidden=\"true\">&#10003;</span> Saved!"
                + "</span>");
  }

  /**
   * A driver's own state toggle. POST, not GET: a GET link is followable by prefetch, a scanner, or
   * an accidental re-navigation, any of which would silently pull the driver out of dispatch
   * consideration (driver-portal UX review F1). No confirmation dialog -- see the portal template
   * for the inline, persistent post-toggle messaging that serves as the undo affordance instead.
   */
  @PostMapping("/driver/toggle-active")
  ModelAndView changeDriverActiveStatus(
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone) {
    DriverDao.toggleActiveStatus(jdbi, userPhone);
    return new ModelAndView("redirect:/driver/portal");
  }

  private static String trimOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  /**
   * Returns null (rather than throwing) for a missing, blank, non-numeric, or out-of-range value.
   */
  private static Integer parsePalletCapacity(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      int value = Integer.parseInt(raw.trim());
      return (value >= 0 && value <= 5) ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static ResponseEntity<String> validationError(String fieldId, String message) {
    return ResponseEntity.badRequest()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body(
            "<span role=\"alert\" class=\"confirm-error\" data-error-field=\""
                + fieldId
                + "\">"
                + message
                + "</span>");
  }
}
