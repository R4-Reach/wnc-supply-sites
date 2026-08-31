package org.r4reach.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;
import static org.r4reach.delivery.DeliveryDao.fetchDeliveryByPublicKey;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.r4reach.TestConfiguration;
import org.r4reach.twilio.sms.SmsSender;

class DeliveryConfirmationControllerTest {

  @BeforeEach
  void setup() {
    TestConfiguration.setupDatabase();
  }

  DeliveryConfirmationController controller =
      new DeliveryConfirmationController(
          jdbiTest, SmsSender.newDisabled(jdbiTest), new NotificationStateMachine(jdbiTest));

  @Test
  void dispatcherConfirm() {
    Delivery delivery = DeliveryHelper.withNewDelivery();
    assertThat(delivery.getConfirmations()).isEmpty();

    controller.confirmRequest(delivery.getPublicKey(), delivery.getDispatchCode());

    // after dispatcher confirms, we should then generate confirmations.
    delivery = fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
    assertThat(delivery.getConfirmations()).isNotEmpty();
  }

  @EnumSource(DeliveryConfirmation.ConfirmRole.class)
  @ParameterizedTest
  void confirm(DeliveryConfirmation.ConfirmRole role) {
    Delivery delivery = DeliveryHelper.withDispatcherConfirmedDelivery();
    var confirmation = delivery.getConfirmation(role).orElseThrow();
    assertThat(confirmation.getConfirmed()).isNull();

    controller.confirmRequest(delivery.getPublicKey(), confirmation.getCode());

    delivery = fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
    confirmation = delivery.getConfirmation(role).orElseThrow();
    assertThat(confirmation.getConfirmed()).isTrue();
  }

  @Test
  void deliveryStatusChangesToConfirmed_afterAllConfirmationsReceived() {
    Delivery delivery = DeliveryHelper.withNewDelivery();
    DeliveryDao.updateDeliveryStatus(
        jdbiTest, delivery.getPublicKey(), DeliveryStatus.CREATING_DISPATCH);

    controller.confirmRequest(delivery.getPublicKey(), delivery.getDispatchCode());
    delivery = fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
    assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.CONFIRMING.getAirtableName());

    final var publicKey = delivery.getPublicKey();
    delivery
        .getConfirmations()
        .forEach(confirmation -> controller.confirmRequest(publicKey, confirmation.getCode()));
    delivery = fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
    assertThat(delivery.getDeliveryStatus()).isEqualTo(DeliveryStatus.CONFIRMED.getAirtableName());
  }

  @EnumSource(DeliveryConfirmation.ConfirmRole.class)
  @ParameterizedTest
  void cancel(DeliveryConfirmation.ConfirmRole role) {
    Delivery delivery = DeliveryHelper.withDispatcherConfirmedDelivery();
    var confirmation = delivery.getConfirmation(role).orElseThrow();
    assertThat(confirmation.getConfirmed()).isNull();

    controller.cancelRequest(delivery.getPublicKey(), confirmation.getCode(), "cancelReason");

    delivery = fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
    confirmation = delivery.getConfirmation(role).orElseThrow();
    assertThat(confirmation.getConfirmed()).isFalse();
  }

  /**
   * A cancel request with a code that matches no confirmation must not cancel the delivery: the
   * status flip and notifications are gated on a matching code, not just the audit record.
   */
  @Test
  void cancel_doesNothingIfCodeIsWrong() {
    Delivery delivery = DeliveryHelper.withDispatcherConfirmedDelivery();

    controller.cancelRequest(delivery.getPublicKey(), "WRONG-CODE", "cancelReason");

    delivery = fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
    assertThat(delivery.getDeliveryStatus())
        .isNotEqualTo(DeliveryStatus.DELIVERY_CANCELLED.getAirtableName());
  }

  @EnumSource(DriverStatus.class)
  @ParameterizedTest
  void buildDriverStatusLink(DriverStatus status) {
    var delivery =
        Delivery.builder().publicKey("KEY").driverCode("CODE").driverStatus(status.name()).build();

    var result = DeliveryConfirmationController.buildDriverStatusLink(delivery);

    assertThat(result)
        .isEqualTo(
            "/confirm/driver?deliveryKey=KEY&code=CODE&newDriverStatus="
                + DriverStatus.nextStatus(status).name());
  }

  @Test
  void driverStatusConfirmations() {
    var delivery = DeliveryHelper.withConfirmedDelivery();
    assertThat(delivery.getDriverStatus()).isEqualTo(DriverStatus.PENDING.name());

    for (DriverStatus status : DriverStatus.values()) {
      controller.confirmDriverStatus(
          delivery.getPublicKey(), delivery.getDriverCode(), status.name());
      delivery = fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey()).orElseThrow();
      assertThat(delivery.getDriverStatus()).isEqualTo(status.name());
    }
  }

  @Test
  void driverStatusConfirmation_DoesNothingIfCodeIsWrong() {
    var delivery = DeliveryHelper.withConfirmedDelivery();
    assertThat(delivery.getDriverStatus()).isEqualTo(DriverStatus.PENDING.name());

    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            controller.confirmDriverStatus(
                delivery.getPublicKey(), "INCORRECT", DriverStatus.DRIVER_EN_ROUTE.name()));
  }
}
