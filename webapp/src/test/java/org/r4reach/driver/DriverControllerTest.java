package org.r4reach.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.util.PhoneNumberUtil;

class DriverControllerTest {

  DriverController driverController = new DriverController(jdbiTest);
  Driver driver = TestConfiguration.buildDriver(-103L, "123-123-4444");

  @BeforeEach
  void setup() {
    TestConfiguration.setupDatabase();
    TestConfiguration.insertDriver(driver);
  }

  @Test
  void renderPage() {
    var modelAndView = driverController.showDriverPortal("123-123-4444");
    Arrays.stream(DriverController.PageParams.values())
        .forEach(
            param -> assertThat(modelAndView.getModelMap().getAttribute(param.name())).isNotNull());
    assertThat(
            modelAndView
                .getModelMap()
                .getAttribute(DriverController.PageParams.availability.name()))
        .isEqualTo(driver.getAvailability());
    assertThat(modelAndView.getModelMap().getAttribute(DriverController.PageParams.comments.name()))
        .isEqualTo(driver.getComments());
    assertThat(modelAndView.getModelMap().getAttribute(DriverController.PageParams.location.name()))
        .isEqualTo(driver.getLocation());
    assertThat(
            modelAndView
                .getModelMap()
                .getAttribute(DriverController.PageParams.licensePlates.name()))
        .isEqualTo(driver.getLicensePlates());
    assertThat(modelAndView.getModelMap().getAttribute(DriverController.PageParams.active.name()))
        .isEqualTo(driver.isActive());
  }

  @Test
  void driverLookupWorksWithAnyFormatting() {
    TestConfiguration.insertDriver(
        driver.toBuilder().wssId(-10_000L).phone("(111) 111-1111").build());
    var modelAndView = driverController.showDriverPortal("(111) 111-1111");
    assertThat(modelAndView.getViewName()).isEqualTo("driver/portal");

    modelAndView = driverController.showDriverPortal("111.111.1111");
    assertThat(modelAndView.getViewName()).isEqualTo("driver/portal");

    modelAndView = driverController.showDriverPortal("1111111111");
    assertThat(modelAndView.getViewName()).isEqualTo("driver/portal");
  }

  @Test
  void updateDriver() {
    Map<String, String> params = new HashMap<>();
    params.put(DriverController.PageParams.comments.name(), "comments demo");
    params.put(DriverController.PageParams.location.name(), "location demo");
    params.put(DriverController.PageParams.licensePlates.name(), "plates demo");
    params.put(DriverController.PageParams.availability.name(), "availability demo");
    params.put(DriverController.PageParams.canLift50lbs.name(), "true");
    params.put(DriverController.PageParams.palletCapacity.name(), "1");

    var response = driverController.updateDriver("123-123-4444", params);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    var dataResult = DriverDao.lookupByPhone(jdbiTest, driver.getPhone()).orElseThrow();
    assertThat(dataResult.getComments()).isEqualTo("comments demo");
    assertThat(dataResult.getLocation()).isEqualTo("location demo");
    assertThat(dataResult.getLicensePlates()).isEqualTo("plates demo");
    assertThat(dataResult.getAvailability()).isEqualTo("availability demo");
    assertThat(dataResult.isCan_lift_50lbs()).isTrue();
    assertThat(dataResult.getPallet_capacity()).isEqualTo(1);
  }

  /** Capability fields are required (no silent "least-capable" default) -- see F4/T3. */
  @Test
  void updateRejectsMissingCanLiftAnswer() {
    Map<String, String> params = new HashMap<>();
    params.put(DriverController.PageParams.comments.name(), "comments demo");
    params.put(DriverController.PageParams.location.name(), "location demo");
    params.put(DriverController.PageParams.licensePlates.name(), "plates demo");
    params.put(DriverController.PageParams.availability.name(), "availability demo");
    params.put(DriverController.PageParams.palletCapacity.name(), "1");

    var response = driverController.updateDriver("123-123-4444", params);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).contains("lift 50 lbs");
    // The rejected save must not have touched the stored row.
    var dataResult = DriverDao.lookupByPhone(jdbiTest, driver.getPhone()).orElseThrow();
    assertThat(dataResult.getLocation()).isEqualTo(driver.getLocation());
  }

  @Test
  void updateRejectsMissingPalletCapacity() {
    Map<String, String> params = new HashMap<>();
    params.put(DriverController.PageParams.comments.name(), "comments demo");
    params.put(DriverController.PageParams.location.name(), "location demo");
    params.put(DriverController.PageParams.licensePlates.name(), "plates demo");
    params.put(DriverController.PageParams.availability.name(), "availability demo");
    params.put(DriverController.PageParams.canLift50lbs.name(), "true");

    var response = driverController.updateDriver("123-123-4444", params);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getBody()).contains("pallets");
  }

  @Test
  void updateRejectsOutOfRangePalletCapacity() {
    Map<String, String> params = new HashMap<>();
    params.put(DriverController.PageParams.comments.name(), "comments demo");
    params.put(DriverController.PageParams.location.name(), "location demo");
    params.put(DriverController.PageParams.licensePlates.name(), "plates demo");
    params.put(DriverController.PageParams.availability.name(), "availability demo");
    params.put(DriverController.PageParams.canLift50lbs.name(), "true");
    params.put(DriverController.PageParams.palletCapacity.name(), "not-a-number");

    var response = driverController.updateDriver("123-123-4444", params);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void updateRejectsMissingLocation() {
    Map<String, String> params = new HashMap<>();
    params.put(DriverController.PageParams.comments.name(), "comments demo");
    params.put(DriverController.PageParams.location.name(), "  ");
    params.put(DriverController.PageParams.licensePlates.name(), "plates demo");
    params.put(DriverController.PageParams.availability.name(), "availability demo");
    params.put(DriverController.PageParams.canLift50lbs.name(), "true");
    params.put(DriverController.PageParams.palletCapacity.name(), "1");

    var response = driverController.updateDriver("123-123-4444", params);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  /**
   * For a given driver in the database, flip their active flag. Reload the driver from database
   * again and then assert that their active flag has changed. Then toggle the active flag again and
   * assert that the active status has changed once more.
   */
  @Test
  void changeDriverActiveStatus() {
    boolean active = driver.isActive();

    driverController.changeDriverActiveStatus(driver.getPhone());

    var dataResult = DriverDao.lookupByPhone(jdbiTest, driver.getPhone()).orElseThrow();
    assertThat(dataResult.isActive()).isNotEqualTo(active);

    driverController.changeDriverActiveStatus(driver.getPhone());
    dataResult = DriverDao.lookupByPhone(jdbiTest, driver.getPhone()).orElseThrow();
    assertThat(dataResult.isActive()).isEqualTo(active);
  }

  /**
   * A user can hold the DRIVER role before any driver row exists. The portal must render (not
   * redirect) with an empty, active-by-default form rather than requiring a pre-existing row.
   */
  @Test
  void renderPortalForDriverWithoutRow() {
    insertUserWithoutDriverRow("555-000-1111");

    var modelAndView = driverController.showDriverPortal("555-000-1111");

    assertThat(modelAndView.getViewName()).isEqualTo("driver/portal");
    Arrays.stream(DriverController.PageParams.values())
        .forEach(
            param -> assertThat(modelAndView.getModelMap().getAttribute(param.name())).isNotNull());
    assertThat(modelAndView.getModelMap().getAttribute(DriverController.PageParams.location.name()))
        .isEqualTo("");
    assertThat(modelAndView.getModelMap().getAttribute(DriverController.PageParams.active.name()))
        .isEqualTo(true);
  }

  /** Saving a driver who has no row yet creates it; the new row is active by default. */
  @Test
  void updateCreatesDriverRowWhenMissing() {
    insertUserWithoutDriverRow("555-000-1111");
    Map<String, String> params = new HashMap<>();
    params.put(DriverController.PageParams.comments.name(), "new comments");
    params.put(DriverController.PageParams.location.name(), "new location");
    params.put(DriverController.PageParams.licensePlates.name(), "PLATE1");
    params.put(DriverController.PageParams.availability.name(), "weekends");
    params.put(DriverController.PageParams.canLift50lbs.name(), "false");
    params.put(DriverController.PageParams.palletCapacity.name(), "2");

    var response = driverController.updateDriver("555-000-1111", params);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    var saved = DriverDao.lookupByPhone(jdbiTest, "555-000-1111").orElseThrow();
    assertThat(saved.getComments()).isEqualTo("new comments");
    assertThat(saved.getLocation()).isEqualTo("new location");
    assertThat(saved.getLicensePlates()).isEqualTo("PLATE1");
    assertThat(saved.getAvailability()).isEqualTo("weekends");
    assertThat(saved.getPallet_capacity()).isEqualTo(2);
    assertThat(saved.isActive()).isTrue();
  }

  /** A portal save must not clobber the active flag, which the driver owns via its own toggle. */
  @Test
  void updatePreservesActiveFlagOnExistingRow() {
    Driver inactive =
        TestConfiguration.buildDriver(-2000L, "555-222-3333").toBuilder().active(false).build();
    TestConfiguration.insertDriver(inactive);

    Map<String, String> params = new HashMap<>();
    params.put(DriverController.PageParams.comments.name(), "edited comments");
    params.put(DriverController.PageParams.location.name(), "edited location");
    params.put(DriverController.PageParams.licensePlates.name(), "EDIT1");
    params.put(DriverController.PageParams.availability.name(), "edited availability");
    params.put(DriverController.PageParams.canLift50lbs.name(), "true");
    params.put(DriverController.PageParams.palletCapacity.name(), "0");

    driverController.updateDriver("555-222-3333", params);

    var saved = DriverDao.lookupByPhone(jdbiTest, "555-222-3333").orElseThrow();
    assertThat(saved.isActive()).isFalse();
    assertThat(saved.getComments()).isEqualTo("edited comments");
  }

  /**
   * Toggling active for a driver with no row creates it as inactive (the portal shows a rowless
   * driver as active, so the link reads "Go Inactive"); a second toggle flips it back to active.
   */
  @Test
  void toggleCreatesInactiveRowWhenMissing() {
    insertUserWithoutDriverRow("555-000-1111");

    driverController.changeDriverActiveStatus("555-000-1111");
    assertThat(DriverDao.lookupByPhone(jdbiTest, "555-000-1111").orElseThrow().isActive())
        .isFalse();

    driverController.changeDriverActiveStatus("555-000-1111");
    assertThat(DriverDao.lookupByPhone(jdbiTest, "555-000-1111").orElseThrow().isActive()).isTrue();
  }

  /** Inserts a wss_user with the given phone but no backing driver row. */
  private static void insertUserWithoutDriverRow(String phone) {
    jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate(
                    "insert into wss_user(phone) values(:phone) on conflict(phone) do nothing")
                .bind("phone", PhoneNumberUtil.toCanonical(phone))
                .execute());
  }
}
