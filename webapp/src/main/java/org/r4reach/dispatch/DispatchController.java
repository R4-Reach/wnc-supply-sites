package org.r4reach.dispatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.r4reach.vehicletype.VehicleType;
import org.r4reach.vehicletype.VehicleTypeDao;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Dispatch drivers grid. Reachable by {@link UserRole#DISPATCHER} (read-only except the notes
 * field) and {@link UserRole#DRIVER_ADMIN} (full read/write). Field-level access is governed by
 * {@link DriverFieldPolicy}, which gates both what the grid renders and which edits each endpoint
 * accepts. The home-page "Dispatch" button leads here.
 */
@Controller
@Slf4j
@AllArgsConstructor
public class DispatchController {

  public static final String PATH_DISPATCH = "/dispatch";
  public static final String PATH_DRIVERS = "/dispatch/drivers";

  private final Jdbi jdbi;

  @GetMapping(PATH_DISPATCH)
  ModelAndView dispatchHome(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    if (!UserRole.canViewDrivers(roles)) {
      return new ModelAndView("redirect:/");
    }
    return new ModelAndView("dispatch/dispatch");
  }

  @GetMapping(PATH_DRIVERS)
  ModelAndView driversPage(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    if (!UserRole.canViewDrivers(roles)) {
      return new ModelAndView("redirect:/");
    }
    Map<DriverField, FieldAccess> policy = DriverFieldPolicy.forRoles(roles);
    List<VehicleType> vehicleTypes = VehicleTypeDao.fetchAll(jdbi);

    List<Map<String, Object>> rows =
        DispatchDao.fetchAll(jdbi).stream().map(row -> toRow(row, vehicleTypes, policy)).toList();

    Map<String, Object> params = new HashMap<>();
    params.put("drivers", rows);
    params.put("showStatus", policy.get(DriverField.ACTIVE).isVisible());
    params.put("showBlacklist", policy.get(DriverField.BLACK_LISTED).isVisible());
    params.put("canAddDriver", UserRole.canManageDrivers(roles));
    return new ModelAndView("dispatch/drivers", params);
  }

  @PostMapping(PATH_DRIVERS + "/add")
  ResponseEntity<String> addDriver(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam String phone,
      @RequestParam(required = false) String name) {
    // Creating a driver mints their identity, so it needs write access to that identity.
    if (!DriverFieldPolicy.writable(roles, DriverField.PHONE)) {
      return htmlBadRequest("Not authorized");
    }
    if (!DispatchDao.createDriver(jdbi, phone, name)) {
      return htmlBadRequest("Invalid phone number");
    }
    // Re-render the whole page so the new driver appears in the grid.
    return ResponseEntity.ok().header("HX-Refresh", "true").build();
  }

  @PostMapping(PATH_DRIVERS + "/set-name")
  ModelAndView setName(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam long wssUserId,
      @RequestParam(required = false) String name) {
    if (!DriverFieldPolicy.writable(roles, DriverField.FULL_NAME)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setFullName(jdbi, wssUserId, name);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-phone")
  ModelAndView setPhone(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam long wssUserId,
      @RequestParam(required = false) String phone) {
    if (!DriverFieldPolicy.writable(roles, DriverField.PHONE)) {
      return new ModelAndView("redirect:/");
    }
    // On an invalid or already-taken number the row re-renders with the stored value, discarding
    // the
    // bad edit.
    DispatchDao.setPhone(jdbi, wssUserId, phone);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-location")
  ModelAndView setLocation(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam(required = false) String location) {
    if (!DriverFieldPolicy.writable(roles, DriverField.LOCATION)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setLocation(jdbi, wssId, location);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-availability")
  ModelAndView setAvailability(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam(required = false) String availability) {
    if (!DriverFieldPolicy.writable(roles, DriverField.AVAILABILITY)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setAvailability(jdbi, wssId, availability);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-vehicle-type")
  ModelAndView setVehicleType(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam(required = false) Integer vehicleTypeId) {
    if (!DriverFieldPolicy.writable(roles, DriverField.VEHICLE_TYPE)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setVehicleType(jdbi, wssId, vehicleTypeId);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-active")
  ModelAndView setActive(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam boolean active) {
    if (!DriverFieldPolicy.writable(roles, DriverField.ACTIVE)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setActive(jdbi, wssId, active);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-blacklisted")
  ModelAndView setBlacklisted(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam boolean blacklisted) {
    if (!DriverFieldPolicy.writable(roles, DriverField.BLACK_LISTED)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setBlackListed(jdbi, wssId, blacklisted);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-notes")
  ModelAndView setNotes(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam(required = false) String notes) {
    if (!DriverFieldPolicy.writable(roles, DriverField.NOTES)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setNotes(jdbi, wssId, notes);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-license-plates")
  ModelAndView setLicensePlates(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam(required = false) String licensePlates) {
    if (!DriverFieldPolicy.writable(roles, DriverField.LICENSE_PLATES)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setLicensePlates(jdbi, wssId, licensePlates);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-can-lift")
  ModelAndView setCanLift(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam boolean canLift) {
    if (!DriverFieldPolicy.writable(roles, DriverField.CAN_LIFT_50LBS)) {
      return new ModelAndView("redirect:/");
    }
    DispatchDao.setCanLift50lbs(jdbi, wssId, canLift);
    return driverRowView(wssId, roles);
  }

  @PostMapping(PATH_DRIVERS + "/set-pallet-capacity")
  ModelAndView setPalletCapacity(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long wssId,
      @RequestParam(required = false) Integer palletCapacity) {
    if (!DriverFieldPolicy.writable(roles, DriverField.PALLET_CAPACITY)) {
      return new ModelAndView("redirect:/");
    }
    // A blank or negative entry resets to zero (the column's default).
    int capacity = palletCapacity == null || palletCapacity < 0 ? 0 : palletCapacity;
    DispatchDao.setPalletCapacity(jdbi, wssId, capacity);
    return driverRowView(wssId, roles);
  }

  private ModelAndView driverRowView(long wssId, List<UserRole> roles) {
    DispatchDao.DriverRow row =
        DispatchDao.fetch(jdbi, wssId)
            .orElseThrow(() -> new IllegalStateException("Driver not found: " + wssId));
    return new ModelAndView(
        "dispatch/driver-row",
        toRow(row, VehicleTypeDao.fetchAll(jdbi), DriverFieldPolicy.forRoles(roles)));
  }

  /**
   * Builds the template model for one grid row (used for the full page and single-row swaps). Each
   * field renders as a nested cell object carrying its value and an {@code rw} flag; a hidden field
   * is simply absent, so the template omits that cell entirely.
   */
  private static Map<String, Object> toRow(
      DispatchDao.DriverRow row,
      List<VehicleType> vehicleTypes,
      Map<DriverField, FieldAccess> policy) {
    Map<String, Object> model = new HashMap<>();
    model.put("wssId", row.getWssId());
    model.put("wssUserId", row.getWssUserId());
    // The row's active state styles the <tr> even when the status cell itself is hidden.
    model.put("active", row.isActive());

    putTextCell(model, "name", policy.get(DriverField.FULL_NAME), row.getFullName());
    putTextCell(model, "location", policy.get(DriverField.LOCATION), row.getLocation());
    putTextCell(model, "phone", policy.get(DriverField.PHONE), row.getPhone());
    putTextCell(model, "availability", policy.get(DriverField.AVAILABILITY), row.getAvailability());
    putTextCell(model, "notes", policy.get(DriverField.NOTES), row.getComments());
    putTextCell(
        model, "licensePlates", policy.get(DriverField.LICENSE_PLATES), row.getLicensePlates());
    putTextCell(
        model,
        "palletCapacity",
        policy.get(DriverField.PALLET_CAPACITY),
        row.getPalletCapacity() == null ? "0" : String.valueOf(row.getPalletCapacity()));

    FieldAccess vehicleTypeAccess = policy.get(DriverField.VEHICLE_TYPE);
    if (vehicleTypeAccess.isVisible()) {
      Map<String, Object> cell = new HashMap<>();
      cell.put("rw", vehicleTypeAccess.isWritable());
      cell.put("value", row.getVehicleTypeName() == null ? "" : row.getVehicleTypeName());
      cell.put("options", vehicleOptions(vehicleTypes, row.getVehicleTypeId()));
      model.put("vehicleType", cell);
    }

    FieldAccess activeAccess = policy.get(DriverField.ACTIVE);
    if (activeAccess.isVisible()) {
      Map<String, Object> cell = new HashMap<>();
      cell.put("rw", activeAccess.isWritable());
      cell.put("active", row.isActive());
      model.put("status", cell);
    }

    FieldAccess blacklistAccess = policy.get(DriverField.BLACK_LISTED);
    if (blacklistAccess.isVisible()) {
      Map<String, Object> cell = new HashMap<>();
      cell.put("rw", blacklistAccess.isWritable());
      cell.put("blacklisted", row.isBlackListed());
      model.put("blacklist", cell);
    }

    FieldAccess canLiftAccess = policy.get(DriverField.CAN_LIFT_50LBS);
    if (canLiftAccess.isVisible()) {
      Map<String, Object> cell = new HashMap<>();
      cell.put("rw", canLiftAccess.isWritable());
      cell.put("on", row.isCanLift50lbs());
      model.put("canLift", cell);
    }
    return model;
  }

  private static void putTextCell(
      Map<String, Object> model, String key, FieldAccess access, String value) {
    if (!access.isVisible()) {
      return;
    }
    Map<String, Object> cell = new HashMap<>();
    cell.put("rw", access.isWritable());
    cell.put("value", value == null ? "" : value);
    model.put(key, cell);
  }

  private static List<Map<String, Object>> vehicleOptions(
      List<VehicleType> vehicleTypes, Integer selectedId) {
    List<Map<String, Object>> options = new ArrayList<>();
    for (VehicleType type : vehicleTypes) {
      options.add(
          Map.of(
              "id", type.getId(),
              "name", type.getName(),
              "selected", selectedId != null && selectedId == type.getId()));
    }
    return options;
  }

  private static ResponseEntity<String> htmlBadRequest(String message) {
    // 200 so htmx swaps the message into place (it ignores non-2xx bodies by default).
    return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body("<span class=\"errorMessage\">" + message + "</span>");
  }
}
