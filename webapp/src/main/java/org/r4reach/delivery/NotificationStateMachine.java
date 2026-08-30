package org.r4reach.delivery;

import jakarta.annotation.Nonnull;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Builder;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.DomainName;
import org.r4reach.delivery.DeliveryConfirmation.ConfirmRole;
import org.r4reach.jobs.distance.DistanceDao;
import org.r4reach.util.DateTimeFormat;
import org.r4reach.util.TruncateString;
import org.springframework.stereotype.Component;

/**
 * Module to determine what SMS messages should be sent depending upon delivery state (confirmation)
 * changes.
 */
@Component
class NotificationStateMachine {

  private final Jdbi jdbi;

  NotificationStateMachine(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * Returns formatted ETA based on the cached drive time in {@code site_distance_matrix}. Returns
   * "unknown" when the delivery has no site IDs (legacy data) or when the pair has no valid cached
   * distance yet. We deliberately do not call the Google API here — the SMS path used to do that
   * synchronously, which both burned API quota and 500'd the user when Google blipped.
   */
  private String estimateEta(Delivery delivery) {
    if (delivery.getFromSiteId() == null || delivery.getToSiteId() == null) {
      return "unknown";
    }
    return DistanceDao.queryDistance(jdbi, delivery.getFromSiteId(), delivery.getToSiteId())
        .map(
            r ->
                DateTimeFormat.formatTime(
                    LocalDateTime.now(ZoneId.of("America/New_York"))
                        .plusSeconds(r.getDurationSeconds())))
        .orElse("unknown");
  }

  @Builder
  @lombok.Value
  static class SmsMessage {
    @Nonnull String phone;
    @Nonnull String message;
  }

  List<SmsMessage> requestConfirmations(Delivery delivery) {
    String domainName = DomainName.DOMAIN_NAME;

    List<SmsMessage> messages = new ArrayList<>();

    messages.add(
        SmsMessage.builder()
            .phone(delivery.getDriverPhoneNumber())
            .message(
                String.format(
                    """
                    %s delivery requested. Please confirm.
                    https://%s

                    Delivery #%s
                    Date: %s
                    Heading to: %s, %s
                    Items (%s): %s
                    """,
                    domainName,
                    domainName
                        + DeliveryController.buildDeliveryPageLink(delivery, ConfirmRole.DRIVER),
                    delivery.getDeliveryNumber(),
                    delivery.getDeliveryDate(),
                    delivery.getToSite(),
                    delivery.getToCity(),
                    delivery.getItemCount(),
                    delivery.getItemListTruncated()))
            .build());

    messages.add(
        SmsMessage.builder()
            .phone(delivery.getFromContactPhoneNumber())
            .message(
                String.format(
                    """
                    %s delivery requested. Please confirm.
                    https://%s

                    Delivery #%s
                    Date: %s
                    Heading to: %s, %s
                    Items (%s): %s
                    """,
                    domainName,
                    domainName
                        + DeliveryController.buildDeliveryPageLink(
                            delivery, ConfirmRole.PICKUP_SITE),
                    delivery.getDeliveryNumber(),
                    delivery.getDeliveryDate(),
                    delivery.getToSite(),
                    delivery.getToCity(),
                    delivery.getItemCount(),
                    delivery.getItemListTruncated()))
            .build());

    messages.add(
        SmsMessage.builder()
            .phone(delivery.getToContactPhoneNumber())
            .message(
                String.format(
                    """
                    %s delivery requested. Please confirm.
                    https://%s

                    Delivery #%s
                    Date: %s
                    Heading to: %s, %s
                    Items (%s): %s
                    """,
                    domainName,
                    domainName
                        + DeliveryController.buildDeliveryPageLink(
                            delivery, ConfirmRole.DROPOFF_SITE),
                    delivery.getDeliveryNumber(),
                    delivery.getDeliveryDate(),
                    delivery.getToSite(),
                    delivery.getToCity(),
                    delivery.getItemCount(),
                    delivery.getItemListTruncated()))
            .build());
    return messages;
  }

  List<SmsMessage> confirm(Delivery delivery) {
    String domainName = DomainName.DOMAIN_NAME;
    if (delivery.isConfirmed()) {
      // fully confirmed, send a message to everyone!
      String messageToDriver =
          String.format(
              """
              Delivery #%s confirmed for %s
              Heading to: %s in %s
              View the delivery and notify us when you get started with this link:
              %s
              """,
              delivery.getDeliveryNumber(),
              delivery.getDeliveryDate(),
              delivery.getToSite(),
              delivery.getToCity(),
              domainName + DeliveryController.buildDeliveryPageLinkForDriver(delivery));
      String messageToOthers =
          String.format(
              """
              Delivery #%s confirmed for %s
              Heading to: %s in %s
              %s
              """,
              delivery.getDeliveryNumber(),
              delivery.getDeliveryDate(),
              delivery.getToSite(),
              delivery.getToCity(),
              domainName + DeliveryController.buildDeliveryPageLink(delivery.getPublicKey()));

      List<SmsMessage> messages = new ArrayList<>();
      messages.add(
          SmsMessage.builder()
              .phone(delivery.getDriverPhoneNumber())
              .message(messageToDriver)
              .build());

      messages.addAll(
          Stream.of(
                  delivery.getDispatcherPhoneNumber(),
                  delivery.getToContactPhoneNumber(),
                  delivery.getFromContactPhoneNumber())
              .map(number -> SmsMessage.builder().phone(number).message(messageToOthers).build())
              .toList());
      return messages;
    } else {
      // send a confirmation received notification to just the dispatcher
      return List.of(
          SmsMessage.builder()
              .phone(delivery.getDispatcherPhoneNumber())
              .message(
                  String.format(
                      """
              Confirmation received.
              Delivery #%s (%s items)
              %s: %s (Driver)
              %s: %s (Pickup)
              %s: %s (DropOff)
              """,
                      delivery.getDeliveryNumber(),
                      delivery.getItemCount(),
                      confirmationStatus(delivery, ConfirmRole.DRIVER),
                      delivery.getDriverName(),
                      confirmationStatus(delivery, ConfirmRole.PICKUP_SITE),
                      delivery.getFromSite(),
                      confirmationStatus(delivery, ConfirmRole.DROPOFF_SITE),
                      delivery.getToSite()))
              .build());
    }
  }

  private static String confirmationStatus(Delivery delivery, ConfirmRole confirmRole) {
    return Optional.ofNullable(delivery.getConfirmation(confirmRole).orElseThrow().getConfirmed())
        .filter(v -> v)
        .map(v -> "CONFIRMED")
        .orElse("PENDING");
  }

  List<SmsMessage> cancel(Delivery delivery) {
    final String domainName = DomainName.DOMAIN_NAME;

    if (delivery.getConfirmations().isEmpty()) {
      return List.of();
    } else {
      return Stream.of(
              delivery.getDispatcherPhoneNumber(),
              delivery.getDriverPhoneNumber(),
              delivery.getToContactPhoneNumber(),
              delivery.getFromContactPhoneNumber())
          .map(
              number ->
                  SmsMessage.builder()
                      .phone(number)
                      .message(
                          String.format(
                              """
                        Delivery #%s for date:%s is CANCELLED. %s
                        %s
                        """,
                              delivery.getDeliveryNumber(),
                              delivery.getDeliveryDate(),
                              delivery.getCancelReason() == null
                                      || delivery.getCancelReason().isBlank()
                                  ? ""
                                  : "\nReason: "
                                      + TruncateString.truncate(delivery.getCancelReason(), 96),
                              domainName
                                  + DeliveryController.buildDeliveryPageLink(
                                      delivery.getPublicKey())))
                      .build())
          .toList();
    }
  }

  List<SmsMessage> driverEnRoute(Delivery delivery) {
    String domainName = DomainName.DOMAIN_NAME;

    return Stream.of(delivery.getDispatcherPhoneNumber(), delivery.getFromContactPhoneNumber())
        .map(
            number ->
                SmsMessage.builder()
                    .phone(number)
                    .message(
                        String.format(
                            """
                        Driver is on the way to: %s
                        Driver: %s, License plate: %s, to pick up %s items.
                        Full Details: %s
                        """,
                            delivery.getToSite(),
                            delivery.getDriverName(),
                            delivery.getDriverLicensePlate(),
                            delivery.getItemCount(),
                            "wnc-supply-sites.com"
                                + DeliveryController.buildDeliveryPageLink(
                                    delivery.getPublicKey())))
                    .build())
        .toList();
  }

  List<SmsMessage> driverArrivedToPickup(Delivery delivery) {
    String domainName = DomainName.DOMAIN_NAME;

    return Stream.of(delivery.getDispatcherPhoneNumber(), delivery.getFromContactPhoneNumber())
        .map(
            number ->
                SmsMessage.builder()
                    .phone(number)
                    .message(
                        String.format(
                            """
                Driver has arrived at pickup %s
                License plate: %s, to pick up %s items.
                Full Details: %s
                """,
                            delivery.getToSite(),
                            delivery.getDriverLicensePlate(),
                            delivery.getItemCount(),
                            domainName
                                + DeliveryController.buildDeliveryPageLink(
                                    delivery.getPublicKey())))
                    .build())
        .toList();
  }

  List<SmsMessage> driverLeavingPickup(Delivery delivery) {
    String domainName = DomainName.DOMAIN_NAME;

    return Stream.of(delivery.getDispatcherPhoneNumber(), delivery.getToContactPhoneNumber())
        .map(
            number ->
                SmsMessage.builder()
                    .phone(number)
                    .message(
                        String.format(
                            """
                Driver is on the way to the drop off site: %s
                ETA: %s
                Driver: %s
                License plates: %s
                They just left %s in %s, transporting %s items.
                Full Details: %s
                """,
                            delivery.getToSite(),
                            estimateEta(delivery),
                            delivery.getDriverName(),
                            delivery.getDriverLicensePlate(),
                            delivery.getFromSite(),
                            delivery.getFromCity(),
                            delivery.getItemCount(),
                            domainName
                                + DeliveryController.buildDeliveryPageLink(
                                    delivery.getPublicKey())))
                    .build())
        .toList();
  }

  static List<SmsMessage> driverArrivedToDropOff(Delivery delivery) {
    return Stream.of(delivery.getDispatcherPhoneNumber(), delivery.getToContactPhoneNumber())
        .map(
            number ->
                SmsMessage.builder()
                    .phone(number)
                    .message(
                        String.format(
                            """
          Driver has arrived at the drop off site: %s
          Name: %s, license plate: %s
          #%s
          """,
                            delivery.getToSite(),
                            delivery.getDriverName(),
                            delivery.getDriverLicensePlate(),
                            HashTagGenerator.generate()))
                    .build())
        .toList();
  }
}
