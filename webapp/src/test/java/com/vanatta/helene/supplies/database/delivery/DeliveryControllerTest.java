package com.vanatta.helene.supplies.database.delivery;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.data.GoogleMapWidget;
import com.vanatta.helene.supplies.database.delivery.DeliveryController.TemplateParams;
import com.vanatta.helene.supplies.database.test.util.TestDataFile;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.servlet.ModelAndView;

/**
 * Test that focuses on how the delivery manifest page is rendered. Firstly we need to be sure the
 * page renders all of the manifest data. Then we go into detailed tests that depending on DB state,
 * that the confirmation buttons are shown.
 */
class DeliveryControllerTest {

  @BeforeEach
  void setupDatabase() {
    TestConfiguration.setupDatabase();
  }

  DeliveryController deliveryController =
      new DeliveryController(jdbiTest, new GoogleMapWidget("dummy api key"));

  @Nested
  class RenderDetailPage {

    @Test
    void detailPageHasAllParameters() {
      ModelAndView result = deliveryController.showDeliveryDetailPage("XKCD", null);
      var templateDataMap = result.getModelMap();

      List<String> expectedTemplateParams =
          Arrays.stream(TemplateParams.values())
              .filter(e -> e != TemplateParams.confirmMessage)
              .filter(e -> e != TemplateParams.unableToConfirmMessages)
              .map(Enum::name)
              .sorted()
              .toList();
      assertThat(templateDataMap.keySet().stream().sorted().toList())
          .containsAll(expectedTemplateParams);
    }

    @Test
    void renderPageWithMostlyNull() {
      // delivery '-3' has almost all null values, it is minimum data
      // for us to store a delivery record
      ModelAndView result = deliveryController.showDeliveryDetailPage("ABCD", null);
      var templateDataMap = result.getModelMap();

      List<String> expectedTemplateParams =
          Arrays.stream(TemplateParams.values())
              // phone number values are null when not set - this lets the front end handle
              // creating links around the phone number or not. We need to filter them out.
              .filter(
                  e ->
                      !List.of(
                              TemplateParams.cancelReason,
                              // null when no confirmation-role user is viewing the page
                              TemplateParams.code,
                              TemplateParams.confirmMessage,
                              TemplateParams.deliveryDate,
                              TemplateParams.dispatcherNotes,
                              TemplateParams.dispatcherPhone,
                              TemplateParams.driverConfirmed,
                              TemplateParams.driverPhone,
                              TemplateParams.driverStatus,
                              TemplateParams.dropOffConfirmed,
                              TemplateParams.fromContactPhone,
                              TemplateParams.pickupConfirmed,
                              TemplateParams.toContactPhone,
                              TemplateParams.unableToConfirmMessages)
                          .contains(e))
              .map(Enum::name)
              .sorted()
              .toList();
      for (String param : expectedTemplateParams) {
        assertThat(templateDataMap.get(param)).describedAs(param).isNotNull();
      }
    }
  }

  /**
   *
   *
   * <pre>
   * Store data for a delivery.
   * Request the delivery page using a correct dispatchCode.
   * Validate that we have the "sendConfirmationUrl" populated.
   * Request the delivery page using an incorrect dispatchCode
   * Validate that the "sendConfirmationUrl" parameter is not populated.
   * </pre>
   */
  @Test
  void showConfirmationButton_forDispatcher() {
    var input = readTestData();
    input.store(jdbiTest);
    assertThat(
            DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, input.publicUrlKey)
                .orElseThrow()
                .missingData())
        .isEmpty();

    // request delivery page with dispatch code, for dispatcher
    var response =
        deliveryController.showDeliveryDetailPage(
            input.getPublicUrlKey(), input.getDispatcherCode());
    // delivery is good to go, dispatcher should have the option to confirm the delivery.
    assertFieldsAreNotNull(
        response, TemplateParams.sendConfirmationVisible, TemplateParams.confirmMessage);
    assertFieldsAreNull(response, TemplateParams.unableToConfirmMessages);
  }

  /**
   * 'code' is incorrect for any role. We should show a vanilla delivery page with no confirmation
   * options.
   */
  @Test
  void showConfirmationButton_forDispatcher_doesNotShowWithIncorrectCode() {
    var input = readTestData();
    input.store(jdbiTest);

    // incorrect dispatchCode
    var response = deliveryController.showDeliveryDetailPage(input.getPublicUrlKey(), "____");
    assertFieldsAreNull(response, TemplateParams.unableToConfirmMessages);
    assertFieldsAreFalse(
        response, TemplateParams.sendConfirmationVisible, TemplateParams.sendDeclineVisible);

    // no dispatch code
    response = deliveryController.showDeliveryDetailPage(input.getPublicUrlKey(), null);
    assertFieldsAreNull(response, TemplateParams.unableToConfirmMessages);
    assertFieldsAreFalse(
        response, TemplateParams.sendConfirmationVisible, TemplateParams.sendDeclineVisible);
  }

  /**
   * Go through various scenarios where a delivery does not have full data yet, cannot offer
   * dispatcher to confirm.
   *
   * <p>cases:
   *
   * <pre>
   *   missing date
   *   missing pickup/dropoff site
   *   missing driver
   *   missing items
   *   missing dispatcher
   * </pre>
   */
  @MethodSource
  @ParameterizedTest
  void doNotShowConfirmationButton_deliveryNotReadyForDispatcherConfirmation(
      DeliveryFixture deliveryUpdate) {
    deliveryUpdate.store(jdbiTest);

    // validate incoming test data is "missing data", indicating we are not ready to start
    // confirmation process.
    assertThat(
            DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, deliveryUpdate.publicUrlKey)
                .orElseThrow()
                .missingData())
        .describedAs(
            DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, deliveryUpdate.publicUrlKey)
                .orElseThrow()
                .toString())
        .isNotEmpty();

    var response =
        deliveryController.showDeliveryDetailPage(
            deliveryUpdate.getPublicUrlKey(), deliveryUpdate.getDispatcherCode());

    // should be showing messages indicating there is data missing
    // decline &c onfirm URL are always populated
    assertFieldsAreNotNull(
        response,
        TemplateParams.unableToConfirmMessages,
        TemplateParams.confirmButton,
        TemplateParams.sendDeclineUrl);

    // confirm & decline button not visible.
    assertFieldsAreFalse(
        response, TemplateParams.sendConfirmationVisible, TemplateParams.sendDeclineVisible);
  }

  private static void assertFieldsAreNotNull(ModelAndView response, TemplateParams... params) {
    for (TemplateParams field : Arrays.asList(params)) {
      assertThat(response.getModelMap().getAttribute(field.name())).isNotNull();
    }
  }

  private static void assertFieldsAreNull(ModelAndView response, TemplateParams... params) {
    for (TemplateParams field : Arrays.asList(params)) {
      assertThat(response.getModelMap().getAttribute(field.name())).isNull();
    }
  }

  private static void assertFieldsAreTrue(ModelAndView response, TemplateParams... params) {
    for (TemplateParams field : Arrays.asList(params)) {
      assertThat((Boolean) response.getModelMap().getAttribute(field.name())).isTrue();
    }
  }

  private static void assertFieldsAreFalse(ModelAndView response, TemplateParams... params) {
    for (TemplateParams field : Arrays.asList(params)) {
      assertThat((Boolean) response.getModelMap().getAttribute(field.name()))
          .describedAs(field.name())
          .isFalse();
    }
  }

  /**
   * Variety of cases where a delivery is not yet "ready"for and a dispatcher (missing data) cannot
   * start the confirmation process yet.
   */
  static List<DeliveryFixture>
      doNotShowConfirmationButton_deliveryNotReadyForDispatcherConfirmation() {
    return List.of(
        readTestData().toBuilder().targetDeliveryDate(null).build(),
        readTestData().toBuilder().dispatcherNumber(List.of()).build(),
        readTestData().toBuilder().driverNumber(List.of()).build(),
        readTestData().toBuilder().pickupContactPhone(List.of()).build(),
        readTestData().toBuilder().dropoffContactPhone(List.of()).build(),
        readTestData().toBuilder().itemList(List.of()).itemListWssIds(List.of()).build());
  }

  private static DeliveryFixture readTestData() {
    return DeliveryFixture.parseJson(TestDataFile.DELIVERY_DATA_JSON.readData());
  }

  /**
   * After a dispatcher confirms, the confirm button is no longer visible for the dispatcher. A
   * confirm button should now be visible for driver & others.
   */
  @Test
  void dispatcherHasConfirmed() {
    var deliveryUpdate = readTestData();
    deliveryUpdate.store(jdbiTest);
    ConfirmationDao.dispatcherConfirm(jdbiTest, deliveryUpdate.getPublicUrlKey());

    var response =
        deliveryController.showDeliveryDetailPage(
            deliveryUpdate.getPublicUrlKey(), deliveryUpdate.getDispatcherCode());

    // dispatcher has already confirmed, assert that the confirm button is disabled
    assertFieldsAreFalse(
        response, TemplateParams.sendConfirmationVisible, TemplateParams.sendDeclineVisible);
    assertFieldsAreNull(response, TemplateParams.unableToConfirmMessages);

    // loop through all of the confirmation codes and assert we will show 'accept' / 'decline'
    // buttons.

    Delivery delivery =
        DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, deliveryUpdate.getPublicUrlKey())
            .orElseThrow();
    for (String code :
        List.of(
            delivery.getDriverConfirmationCode(),
            delivery.getPickupConfirmationCode(),
            delivery.getDropOffConfirmationCode())) {
      response = deliveryController.showDeliveryDetailPage(deliveryUpdate.getPublicUrlKey(), code);

      assertFieldsAreTrue(
          response, TemplateParams.sendConfirmationVisible, TemplateParams.sendDeclineVisible);
      assertFieldsAreNull(response, TemplateParams.unableToConfirmMessages);
    }
  }

  /**
   * Confirm we accurately show confirmation values. When dispatcher confirms, confirm we populate
   * confirmations.
   */
  @Test
  void confirmationStates() {

    var delivery = DeliveryHelper.withNewDelivery();

    var response = deliveryController.showDeliveryDetailPage(delivery.getPublicKey(), null);
    assertFieldsAreNull(
        response,
        TemplateParams.driverConfirmed,
        TemplateParams.pickupConfirmed,
        TemplateParams.dropOffConfirmed);

    // have the dispatcher confirm
    ConfirmationDao.dispatcherConfirm(jdbiTest, delivery.getPublicKey());

    response = deliveryController.showDeliveryDetailPage(delivery.getPublicKey(), null);

    // now all of the confirmations should be populated
    for (TemplateParams confirmation :
        List.of(
            TemplateParams.driverConfirmed,
            TemplateParams.pickupConfirmed,
            TemplateParams.dropOffConfirmed)) {

      // confirmation row should exist for all roles, but no confirmation decision yet made.
      DeliveryConfirmation confirm = getConfirmation(response, confirmation);
      assertThat(confirm).isNotNull();
      assertThat(confirm.getConfirmed()).isNull();
    }
  }

  private static DeliveryConfirmation getConfirmation(
      ModelAndView response, TemplateParams confirmation) {
    return (DeliveryConfirmation) response.getModelMap().getAttribute(confirmation.name());
  }

  /** Approve all and validate we show approval. */
  @Test
  void confirmationApprovals() {
    var delivery = DeliveryHelper.withConfirmedDelivery();

    var response = deliveryController.showDeliveryDetailPage(delivery.getPublicKey(), null);

    for (TemplateParams param :
        List.of(
            TemplateParams.driverConfirmed,
            TemplateParams.pickupConfirmed,
            TemplateParams.dropOffConfirmed)) {
      assertThat(getConfirmation(response, param).getConfirmed()).isTrue();
    }
  }

  /** Cancel all and validate we show cancels. */
  @Test
  void confirmationCancels() {
    var delivery = DeliveryHelper.withDispatcherConfirmedDelivery();

    Arrays.stream(DeliveryConfirmation.ConfirmRole.values())
        .forEach(
            role ->
                ConfirmationDao.cancelDelivery(
                    jdbiTest, delivery.getPublicKey(), "no reason", role));

    var response = deliveryController.showDeliveryDetailPage(delivery.getPublicKey(), null);

    for (TemplateParams param :
        List.of(
            TemplateParams.driverConfirmed,
            TemplateParams.pickupConfirmed,
            TemplateParams.dropOffConfirmed)) {
      assertThat(getConfirmation(response, param).getConfirmed()).isFalse();
    }
  }

  /** Do not show confirmation buttons when cancelled. */
  @Test
  void doNotShowConfirmationWhenCancelled() {
    Delivery delivery = DeliveryHelper.withDispatcherConfirmedDelivery();
    ConfirmationDao.cancelDelivery(
        jdbiTest,
        delivery.getPublicKey(),
        "cancel reason",
        DeliveryConfirmation.ConfirmRole.DRIVER);

    delivery =
        DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
    for (String code :
        List.of(
            delivery.getDriverConfirmationCode(),
            delivery.getPickupConfirmationCode(),
            delivery.getDropOffConfirmationCode())) {

      var response = deliveryController.showDeliveryDetailPage(delivery.getPublicKey(), code);
      assertFieldsAreFalse(
          response, TemplateParams.sendConfirmationVisible, TemplateParams.sendDeclineVisible);
      // there should be a message that the delivery is cancel
      assertFieldsAreNotNull(response, TemplateParams.unableToConfirmMessages);
    }
  }

  /**
   * After confirmations, driver can click "start delivery", then "arrived at pickup", "leaving
   * pickup", "arrived to dropoff". This test validates that we cycle through these states.
   */
  @Test
  void driverStatus() {
    // setup a delivery to be confirmed & pending
    Delivery delivery = DeliveryHelper.withConfirmedDelivery();

    for (DriverStatus driverStatus : DriverStatus.values()) {
      ConfirmationDao.updateDriverStatus(jdbiTest, delivery.getPublicKey(), driverStatus);
      delivery =
          DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
      assertThat(delivery.getDriverStatus()).isEqualTo(driverStatus.name());

      var response = renderDriverDeliveryPage(delivery);
      DeliveryController.ConfirmButton confirmButton =
          (DeliveryController.ConfirmButton)
              response.getModelMap().getAttribute(TemplateParams.confirmButton.name());
      assertThat(confirmButton.getText())
          .isEqualTo(DriverStatus.nextStatus(driverStatus).getButtonText());
      assertThat(confirmButton.getUrl())
          .isEqualTo(DeliveryConfirmationController.buildDriverStatusLink(delivery));
    }
  }

  private ModelAndView renderDriverDeliveryPage(Delivery delivery) {
    return deliveryController.showDeliveryDetailPage(
        delivery.getPublicKey(),
        delivery.getConfirmation(DeliveryConfirmation.ConfirmRole.DRIVER).orElseThrow().getCode());
  }

  private ModelAndView renderDispatcherDeliveryPage(Delivery delivery) {
    return deliveryController.showDeliveryDetailPage(
        delivery.getPublicKey(), delivery.getDispatchCode());
  }

  /** Show confirmations table if dispatcher has confirmed, and if we are not fully confirmed. */
  @Test
  void showConfirmationFields() {
    Delivery delivery = DeliveryHelper.withNewDelivery();

    var result = renderDispatcherDeliveryPage(delivery);
    assertThat((boolean) result.getModelMap().getAttribute(TemplateParams.hasConfirmations.name()))
        .isFalse();

    ConfirmationDao.dispatcherConfirm(jdbiTest, delivery.getPublicKey());

    result = renderDispatcherDeliveryPage(delivery);
    assertThat((boolean) result.getModelMap().getAttribute(TemplateParams.hasConfirmations.name()))
        .isTrue();

    // 1 confirmation -> now show confirmation table
    ConfirmationDao.confirmDelivery(
        jdbiTest, delivery.getPublicKey(), DeliveryConfirmation.ConfirmRole.DRIVER);
    result = renderDispatcherDeliveryPage(delivery);
    assertThat((boolean) result.getModelMap().getAttribute(TemplateParams.hasConfirmations.name()))
        .isTrue();

    // 2 confirmations -> now show confirmation table
    ConfirmationDao.confirmDelivery(
        jdbiTest, delivery.getPublicKey(), DeliveryConfirmation.ConfirmRole.PICKUP_SITE);
    result = renderDispatcherDeliveryPage(delivery);
    assertThat((boolean) result.getModelMap().getAttribute(TemplateParams.hasConfirmations.name()))
        .isTrue();

    // 3 confirmations -> fully confirmed - do NOT show confirmation table
    ConfirmationDao.confirmDelivery(
        jdbiTest, delivery.getPublicKey(), DeliveryConfirmation.ConfirmRole.DROPOFF_SITE);
    result = renderDispatcherDeliveryPage(delivery);
    assertThat((boolean) result.getModelMap().getAttribute(TemplateParams.hasConfirmations.name()))
        .isFalse();
  }
}
