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
    Driver driver =
        DriverDao.lookupByPhone(jdbi, userPhone)
            .orElseGet(() -> Driver.builder().phone(userPhone).active(true).build());

    List<Delivery> deliveries = DeliveryDao.fetchDeliveriesByDriverPhoneNumber(jdbi, userPhone);

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

    // Server-side "selected" flags for the pallet-capacity dropdown (5 represents "5+").
    int selectedPallet = Math.min(Math.max(driver.getPallet_capacity(), 0), 5);
    for (int i = 0; i <= 5; i++) {
      params.put("palletSelected" + i, i == selectedPallet);
    }

    return new ModelAndView("driver/portal", params);
  }

  @PostMapping("/driver/update")
  ResponseEntity<String> updateDriver(
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone,
      @RequestParam Map<String, String> update) {
    // Build straight from the submitted form -- no need to load an existing row first, since the
    // upsert creates one when absent. active/black_listed are intentionally omitted: the upsert
    // leaves them untouched on an existing row and defaults them on insert.
    var updatedDriverData =
        Driver.builder()
            .phone(userPhone)
            .location(update.get(PageParams.location.name()).trim())
            .licensePlates(update.get(PageParams.licensePlates.name()).trim())
            .availability(update.get(PageParams.availability.name()).trim())
            .comments(update.get(PageParams.comments.name()).trim())
            .can_lift_50lbs(Boolean.parseBoolean(update.get(PageParams.canLift50lbs.name())))
            .pallet_capacity(Integer.parseInt(update.get(PageParams.palletCapacity.name().trim())))
            .build();

    DriverDao.upsert(jdbi, updatedDriverData);

    return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body("<span class=\"green-check\">&#10003;</span> Updated!");
  }

  @GetMapping("/driver/toggle-active")
  ModelAndView changeDriverActiveStatus(
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone) {
    DriverDao.toggleActiveStatus(jdbi, userPhone);
    return new ModelAndView("redirect:/driver/portal");
  }
}
