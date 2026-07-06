package com.vanatta.helene.supplies.database.delivery;

import com.vanatta.helene.supplies.database.twilio.sms.SmsSender;
import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/** Has endpoints dedicated for handling delivery 'confirm' and 'cancel' button actions. */
@AllArgsConstructor
@Controller
@Slf4j
class DeliveryConfirmationController {
  private static final String confirmPath = "/confirm/delivery";
  private static final String cancelPath = "/confirm/cancel";
  private static final String driverPath = "/confirm/driver";

  private final Jdbi jdbi;
  private final SmsSender smsSender;
  private final NotificationStateMachine notificationStateMachine;

  public static String buildConfirmUrl(String deliveryPublicKey, String confirmationCode) {
    return String.format(
        "%s?deliveryKey=%s&code=%s", confirmPath, deliveryPublicKey, confirmationCode);
  }

  public static String buildCancelUrl(String deliveryPublicKey, String confirmationCode) {
    return String.format(
        "%s?deliveryKey=%s&code=%s", cancelPath, deliveryPublicKey, confirmationCode);
  }

  public static String buildDriverStatusLink(Delivery delivery) {
    return String.format(
        "%s?deliveryKey=%s&code=%s&newDriverStatus=%s",
        driverPath,
        delivery.getPublicKey(),
        delivery.getDriverCode(),
        DriverStatus.nextStatus(delivery.getDriverStatus()));
  }

  /**
   * @param deliveryKey The public URL key of the delivery.
   * @param code Secret code value for drivers that allows them to access URLs that change driver
   *     status.
   * @param newDriverStatus the new confirmation status. Shoudl be
   */
  @GetMapping(driverPath)
  ModelAndView confirmDriverStatus(
      @RequestParam String deliveryKey,
      @RequestParam String code,
      @RequestParam String newDriverStatus) {
    Delivery delivery =
        DeliveryDao.fetchDeliveryByPublicKey(jdbi, deliveryKey)
            .orElseThrow(
                () -> new IllegalArgumentException("Invalid delivery code: " + deliveryKey));

    if (!delivery.getDriverCode().equals(code)) {
      throw new IllegalArgumentException("Invalid driver code: " + code);
    }

    log.info(
        "Driving confirming status update, delivery: {}, old status: {}, new status: {}",
        deliveryKey,
        delivery.getDriverStatus(),
        newDriverStatus);

    DriverStatus newStatus = DriverStatus.valueOf(newDriverStatus);
    ConfirmationDao.updateDriverStatus(jdbi, deliveryKey, newStatus);

    switch (newStatus) {
      case PENDING -> {}
      case DRIVER_EN_ROUTE -> {
        notificationStateMachine
            .driverEnRoute(delivery)
            .forEach(message -> smsSender.send(message.getPhone(), message.getMessage()));
        DeliveryDao.updateDeliveryStatus(jdbi, deliveryKey, DeliveryStatus.DELIVERY_IN_PROGRESS);
      }
      case ARRIVED_AT_PICKUP -> {
        notificationStateMachine
            .driverArrivedToPickup(delivery)
            .forEach(message -> smsSender.send(message.getPhone(), message.getMessage()));
        DeliveryDao.updateDeliveryStatus(jdbi, deliveryKey, DeliveryStatus.DELIVERY_IN_PROGRESS);
      }
      case DEPARTED_PICKUP -> {
        notificationStateMachine
            .driverLeavingPickup(delivery)
            .forEach(message -> smsSender.send(message.getPhone(), message.getMessage()));
        DeliveryDao.updateDeliveryStatus(jdbi, deliveryKey, DeliveryStatus.DELIVERY_IN_PROGRESS);
      }
      case ARRIVED_AT_DROP_OFF -> {
        NotificationStateMachine.driverArrivedToDropOff(delivery)
            .forEach(message -> smsSender.send(message.getPhone(), message.getMessage()));
        DeliveryDao.updateDeliveryStatus(jdbi, deliveryKey, DeliveryStatus.DELIVERY_COMPLETED);
      }
    }

    String driverConfirmCode =
        delivery
            .getConfirmation(DeliveryConfirmation.ConfirmRole.DRIVER)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unexpected state, updating driver status before a delivery is confirmed. "
                            + "New driver status: "
                            + newDriverStatus
                            + ", Delivery: "
                            + delivery))
            .getCode();

    String url = DeliveryController.buildDeliveryPageLinkForDriver(deliveryKey, driverConfirmCode);
    return new ModelAndView("redirect:" + url);
  }

  /**
   * All delivery confirmation 'accept' requests route through this controller: dispatcher, driver,
   * pickup and drop off site. Once we have full confirmations, then we route 'driver status'
   * requests to the confirm driver status endpoint.
   */
  @GetMapping(confirmPath)
  ModelAndView confirmRequest(@RequestParam String deliveryKey, @RequestParam String code) {
    Delivery delivery =
        DeliveryDao.fetchDeliveryByPublicKey(jdbi, deliveryKey)
            .orElseThrow(
                () -> new IllegalArgumentException("Invalid delivery code: " + deliveryKey));

    if (delivery.getDispatchCode().equals(code)) {
      ConfirmationDao.dispatcherConfirm(jdbi, deliveryKey);
      var messages =
          notificationStateMachine.requestConfirmations(
              DeliveryDao.fetchDeliveryByPublicKey(jdbi, deliveryKey).orElseThrow());
      messages.forEach(message -> smsSender.send(message.getPhone(), message.getMessage()));
      DeliveryDao.updateDeliveryStatus(jdbi, deliveryKey, DeliveryStatus.CONFIRMING);
    } else if (!delivery.getConfirmations().isEmpty()) {
      Arrays.stream(DeliveryConfirmation.ConfirmRole.values())
          .map(delivery::getConfirmation)
          .filter(Optional::isPresent)
          .map(Optional::get)
          .filter(c -> c.getCode().equals(code))
          .findAny()
          .ifPresent(
              confirm ->
                  ConfirmationDao.confirmDelivery(
                      jdbi,
                      deliveryKey,
                      DeliveryConfirmation.ConfirmRole.valueOf(confirm.getConfirmRole())));
      delivery = DeliveryDao.fetchDeliveryByPublicKey(jdbi, deliveryKey).orElseThrow();

      var messages = notificationStateMachine.confirm(delivery);
      messages.forEach(message -> smsSender.send(message.getPhone(), message.getMessage()));
      if (delivery.isConfirmed()) {
        DeliveryDao.updateDeliveryStatus(jdbi, deliveryKey, DeliveryStatus.CONFIRMED);
      }
    }

    return new ModelAndView("redirect:/delivery/" + deliveryKey);
  }

  @GetMapping(cancelPath)
  ResponseEntity<String> cancelRequest(
      @RequestParam String deliveryKey,
      @RequestParam String code,
      @RequestParam(required = false) String cancelReason) {

    Delivery delivery =
        DeliveryDao.fetchDeliveryByPublicKey(jdbi, deliveryKey)
            .orElseThrow(
                () -> new IllegalArgumentException("Invalid delivery code: " + deliveryKey));

    Arrays.stream(DeliveryConfirmation.ConfirmRole.values())
        .map(delivery::getConfirmation)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .filter(c -> c.getCode().equals(code))
        .findAny()
        .ifPresent(
            confirm ->
                ConfirmationDao.cancelDelivery(
                    jdbi,
                    deliveryKey,
                    cancelReason,
                    DeliveryConfirmation.ConfirmRole.valueOf(confirm.getConfirmRole())));

    var messages =
        notificationStateMachine.cancel(
            DeliveryDao.fetchDeliveryByPublicKey(jdbi, deliveryKey).orElseThrow());
    messages.forEach(message -> smsSender.send(message.getPhone(), message.getMessage()));
    DeliveryDao.updateDeliveryStatus(jdbi, deliveryKey, DeliveryStatus.DELIVERY_CANCELLED);
    return ResponseEntity.ok(
        """
           {"status": "cancelled"}
           """);
  }
}
