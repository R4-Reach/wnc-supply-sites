package com.vanatta.helene.supplies.database.delivery;

import com.vanatta.helene.supplies.database.data.GoogleMapWidget;
import com.vanatta.helene.supplies.database.data.SiteAddress;
import com.vanatta.helene.supplies.database.util.EnumUtil;
import com.vanatta.helene.supplies.database.util.ListSplitter;
import com.vanatta.helene.supplies.database.util.TruncateString;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Shows the delivery manifest page, potentially with confirmation buttons if we are accessing the
 * page with a 'code'.
 */
@Controller
@Slf4j
@AllArgsConstructor
class DeliveryController {

  public static String buildDeliveryPageLink(String publicUrlKey) {
    return "/delivery/" + publicUrlKey;
  }

  public static String buildDeliveryPageLinkWithCode(String publicUrlKey, String code) {
    return "/delivery/" + publicUrlKey + "?code=" + code;
  }

  public static String buildDeliveryPageLinkForDriver(Delivery delivery) {
    return buildDeliveryPageLink(delivery, DeliveryConfirmation.ConfirmRole.DRIVER);
  }

  public static String buildDeliveryPageLink(
      Delivery delivery, DeliveryConfirmation.ConfirmRole role) {
    String key = delivery.getPublicKey();
    String code = delivery.getConfirmation(role).orElseThrow().getCode();
    return buildDeliveryPageLinkForDriver(key, code);
  }

  public static String buildDeliveryPageLinkForDriver(String publicUrlKey, String driverCode) {
    return "/delivery/" + publicUrlKey + "?code=" + driverCode;
  }

  private final Jdbi jdbi;
  private final GoogleMapWidget googleMapWidget;

  enum TemplateParams {
    deliveryId,
    deliveryDate,
    deliveryStatus,
    driverStatus,

    driverName,
    driverPhone,
    licensePlate,

    dispatcherName,
    dispatcherPhone,

    fromSiteName,
    fromSiteLink,
    fromAddress,
    fromAddressLine2,
    fromContactName,
    fromContactPhone,
    fromHours,

    toSiteName,
    toSiteLink,
    toAddress,
    toAddressLine2,
    toContactName,
    toContactPhone,
    toHours,

    googleMapLink,

    dispatcherNotes,
    itemCount,
    items1,
    items2,
    items3,

    sendConfirmationVisible,
    sendDeclineUrl,
    sendDeclineVisible,

    hasConfirmations,

    driverConfirmed,
    pickupConfirmed,
    dropOffConfirmed,

    confirmMessage,
    unableToConfirmMessages,
    confirmButton,

    cancelReason,

    deliveryKey,
    code,
    ;
  }

  @Builder
  @Value
  public static class ConfirmButton {
    String text;
    String url;

    static ConfirmButton confirmDelivery(String deliveryKey, String code) {
      return ConfirmButton.builder()
          .text("CLICK TO CONFIRM")
          .url(DeliveryConfirmationController.buildConfirmUrl(deliveryKey, code))
          .build();
    }

    static ConfirmButton driverStatus(Delivery delivery) {
      return ConfirmButton.builder()
          .text(DriverStatus.nextStatus(delivery.getDriverStatus()).getButtonText())
          .url(DeliveryConfirmationController.buildDriverStatusLink(delivery))
          .build();
    }
  }

  @GetMapping("/delivery/{publicUrlKey}")
  ModelAndView showDeliveryDetailPage(
      @PathVariable("publicUrlKey") String publicUrlKey,
      @RequestParam(required = false) String code) {
    Map<String, Object> templateParams = new HashMap<>();

    Delivery delivery =
        DeliveryDao.fetchDeliveryByPublicKey(jdbi, publicUrlKey)
            .orElseThrow(
                () -> new IllegalArgumentException("Invalid delivery key: " + publicUrlKey));

    DeliveryConfirmation driverConfirm =
        delivery.getConfirmation(DeliveryConfirmation.ConfirmRole.DRIVER).orElse(null);
    DeliveryConfirmation pickupConfirm =
        delivery.getConfirmation(DeliveryConfirmation.ConfirmRole.PICKUP_SITE).orElse(null);
    DeliveryConfirmation dropOffConfirm =
        delivery.getConfirmation(DeliveryConfirmation.ConfirmRole.DROPOFF_SITE).orElse(null);

    if (delivery.getConfirmations().isEmpty()) {
      assert driverConfirm == null && pickupConfirm == null && dropOffConfirm == null;
    } else {
      assert driverConfirm != null && pickupConfirm != null && dropOffConfirm != null;
    }

    if (delivery.isConfirmed()) {
      templateParams.put(TemplateParams.confirmButton.name(), ConfirmButton.driverStatus(delivery));
    } else {
      templateParams.put(
          TemplateParams.confirmButton.name(),
          ConfirmButton.confirmDelivery(delivery.getPublicKey(), code));
    }

    templateParams.put(
        TemplateParams.sendDeclineUrl.name(),
        DeliveryConfirmationController.buildCancelUrl(delivery.getPublicKey(), code));
    templateParams.put(TemplateParams.deliveryKey.name(), delivery.getPublicKey());
    templateParams.put(TemplateParams.code.name(), code);

    // code == null means we have someone without a confirmation role viewing the current page
    if (code == null) {
      templateParams.put(TemplateParams.sendDeclineVisible.name(), false);
      templateParams.put(TemplateParams.sendConfirmationVisible.name(), false);

      // check for dispatcher code and we have not requested confirmations
      // show controls for a dispatcher
    } else if (delivery.getDispatchCode().equals(code) && delivery.getConfirmations().isEmpty()) {
      List<String> unableToConfirmMessages = delivery.missingData();
      if (unableToConfirmMessages.isEmpty()) {
        // show the 'confirm' button to dispatcher
        templateParams.put(TemplateParams.sendConfirmationVisible.name(), true);
        templateParams.put(
            TemplateParams.confirmMessage.name(), "Confirm will send SMS confirmation requests");
      } else {
        templateParams.put(TemplateParams.sendConfirmationVisible.name(), false);
        templateParams.put(TemplateParams.sendDeclineVisible.name(), false);
        templateParams.put(TemplateParams.unableToConfirmMessages.name(), unableToConfirmMessages);
      }
    } else if (!delivery.getConfirmations().isEmpty()) {
      // don't show confirm/decline buttons if delivery is cancelled or confirmed
      if (delivery.hasCancellation() || delivery.isConfirmed()) {

        if (delivery.isConfirmed() && driverConfirm.getCode().equals(code)) {
          // show buttons for driver to change the driver status
          if (DriverStatus.valueOf(delivery.getDriverStatus())
              == DriverStatus.ARRIVED_AT_DROP_OFF) {
            templateParams.put(TemplateParams.sendConfirmationVisible.name(), false);
            templateParams.put(TemplateParams.sendDeclineVisible.name(), false);
          } else {
            var confirmButton = ConfirmButton.driverStatus(delivery);
            templateParams.put(TemplateParams.sendConfirmationVisible.name(), true);
            templateParams.put(TemplateParams.confirmButton.name(), confirmButton);
            templateParams.put(TemplateParams.sendDeclineVisible.name(), false);
          }
        } else {
          templateParams.put(TemplateParams.sendConfirmationVisible.name(), false);
          templateParams.put(TemplateParams.sendDeclineVisible.name(), false);
        }

        if (delivery.hasCancellation()) {
          templateParams.put(
              TemplateParams.unableToConfirmMessages.name(), List.of("Delivery is cancelled."));
        }
      } else if (delivery.getDispatchCode().equals(code)) {
        templateParams.put(TemplateParams.sendConfirmationVisible.name(), false);
        templateParams.put(TemplateParams.sendDeclineVisible.name(), false);
      } else {
        boolean isConfirmed =
            (delivery.getDriverConfirmationCode().equals(code)
                    && driverConfirm.getConfirmed() != null)
                || (delivery.getPickupConfirmationCode().equals(code)
                    && pickupConfirm.getConfirmed() != null)
                || (delivery.getDropOffConfirmationCode().equals(code)
                    && dropOffConfirm.getConfirmed() != null);

        if (isConfirmed) {
          templateParams.put(TemplateParams.sendConfirmationVisible.name(), false);
          templateParams.put(TemplateParams.sendDeclineVisible.name(), false);
        } else {
          templateParams.put(TemplateParams.sendConfirmationVisible.name(), true);
          templateParams.put(TemplateParams.sendDeclineVisible.name(), true);
        }
      }
    } else {
      templateParams.put(TemplateParams.sendDeclineVisible.name(), false);
      templateParams.put(TemplateParams.sendConfirmationVisible.name(), false);
    }

    templateParams.put(TemplateParams.driverConfirmed.name(), driverConfirm);
    templateParams.put(TemplateParams.pickupConfirmed.name(), pickupConfirm);
    templateParams.put(TemplateParams.dropOffConfirmed.name(), dropOffConfirm);
    templateParams.put(
        TemplateParams.hasConfirmations.name(),
        !delivery.getConfirmations().isEmpty() && !delivery.isConfirmed());

    templateParams.put(TemplateParams.deliveryId.name(), delivery.getDeliveryNumber());
    templateParams.put(TemplateParams.deliveryDate.name(), delivery.getDeliveryDate());
    templateParams.put(
        TemplateParams.deliveryStatus.name(), nullsToDash(delivery.getDeliveryStatus()));

    templateParams.put(TemplateParams.cancelReason.name(), delivery.getCancelReason());

    DeliveryStatus deliveryStatus =
        EnumUtil.mapText(
                DeliveryStatus.values(),
                DeliveryStatus::getAirtableName,
                delivery.getDeliveryStatus())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unrecognized enum value: " + delivery.getDeliveryStatus()));

    // show driver status only if the delivery is confirmed or in progress.
    if (List.of(
            DeliveryStatus.CONFIRMED,
            DeliveryStatus.DELIVERY_IN_PROGRESS,
            DeliveryStatus.DELIVERY_COMPLETED.getAirtableName())
        .contains(deliveryStatus)) {
      templateParams.put(TemplateParams.driverStatus.name(), delivery.getDriverStatus());
    } else {
      templateParams.put(TemplateParams.driverStatus.name(), null);
    }

    templateParams.put(
        TemplateParams.dispatcherNotes.name(),
        delivery.getDispatcherNotes() == null || delivery.getDispatcherNotes().isBlank()
            ? null
            : delivery.getDispatcherNotes());
    templateParams.put(TemplateParams.itemCount.name(), delivery.getItemCount());
    templateParams.put(TemplateParams.driverName.name(), nullsToDash(delivery.getDriverName()));
    templateParams.put(TemplateParams.driverPhone.name(), delivery.getDriverPhoneNumber());
    templateParams.put(
        TemplateParams.licensePlate.name(), nullsToDash(delivery.getDriverLicensePlate()));
    templateParams.put(
        TemplateParams.dispatcherName.name(), nullsToDash(delivery.getDispatcherName()));
    templateParams.put(TemplateParams.dispatcherPhone.name(), delivery.getDispatcherPhoneNumber());
    templateParams.put(
        TemplateParams.fromSiteName.name(), TruncateString.truncate(delivery.getFromSite(), 30));
    templateParams.put(TemplateParams.fromSiteLink.name(), delivery.getFromSiteLink());
    templateParams.put(TemplateParams.fromAddress.name(), delivery.getFromAddress());
    templateParams.put(
        TemplateParams.fromAddressLine2.name(),
        delivery.getFromCity() + ", " + delivery.getFromState());
    templateParams.put(
        TemplateParams.fromContactName.name(), nullsToDash(delivery.getFromContactName()));
    templateParams.put(
        TemplateParams.fromContactPhone.name(), delivery.getFromContactPhoneNumber());
    templateParams.put(TemplateParams.fromHours.name(), nullsToDash(delivery.getFromHours()));
    templateParams.put(
        TemplateParams.toSiteName.name(), TruncateString.truncate(delivery.getToSite(), 30));
    templateParams.put(TemplateParams.toSiteLink.name(), delivery.getToSiteLink());
    templateParams.put(TemplateParams.toAddress.name(), nullsToDash(delivery.getToAddress()));
    templateParams.put(
        TemplateParams.toAddressLine2.name(), delivery.getToCity() + ", " + delivery.getToState());
    templateParams.put(
        TemplateParams.toContactName.name(), nullsToDash(delivery.getToContactName()));
    templateParams.put(
        TemplateParams.toContactPhone.name(), nullsToDash(delivery.getToContactPhoneNumber()));
    templateParams.put(TemplateParams.toHours.name(), nullsToDash(delivery.getToHours()));

    templateParams.put(
        TemplateParams.googleMapLink.name(),
        delivery.getFromAddress() == null || delivery.getToAddress() == null
            ? null
            : googleMapWidget.generateMapSrcRef(
                SiteAddress.builder()
                    .address(delivery.getFromAddress())
                    .city(delivery.getFromCity())
                    .state(delivery.getFromState())
                    .build(),
                SiteAddress.builder()
                    .address(delivery.getToAddress())
                    .city(delivery.getToCity())
                    .state(delivery.getToState())
                    .build()));
    List<List<String>> split = ListSplitter.splitItemList(delivery.getItemList());

    templateParams.put(TemplateParams.items1.name(), split.get(0));
    templateParams.put(TemplateParams.items2.name(), split.size() > 1 ? split.get(1) : List.of());
    templateParams.put(TemplateParams.items3.name(), split.size() > 2 ? split.get(2) : List.of());

    return new ModelAndView("delivery/delivery", templateParams);
  }

  private static String nullsToDash(String input) {
    return Optional.ofNullable(input).orElse("-");
  }
}
