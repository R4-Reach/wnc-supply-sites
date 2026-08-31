package org.r4reach.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.r4reach.delivery.DeliveryConfirmation.ConfirmRole;
import org.r4reach.supplies.site.details.SiteDetailController;

@ToString
@EqualsAndHashCode
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class Delivery {
  private final long deliveryNumber;
  private final String deliveryStatus;

  /** Read-only link to the public delivery manifest ({@code /delivery/{key}}, no code). */
  private final String detailLink;

  /**
   * Dispatcher (read/write) link to the same manifest, carrying the dispatch code so the confirm /
   * cancel controls render. Used by the dispatcher-gated kanban board; falls back to the read-only
   * {@link #detailLink} when a delivery has no dispatch code yet.
   */
  private final String dispatcherLink;

  private final String publicKey;

  private final String fromSite;
  private final String fromSiteLink;
  private final Long fromSiteId;

  private final String toSite;
  private final String toSiteLink;
  private final Long toSiteId;
  private final String deliveryDate;

  private final String driverName;
  private final String driverPhoneNumber;
  private final String driverLicensePlate;

  private final String dispatcherName;
  private final String dispatcherPhoneNumber;
  private final String dispatcherNotes;

  @Builder.Default private List<String> itemList = new ArrayList<>();
  @Builder.Default private List<DeliveryConfirmation> confirmations = new ArrayList<>();

  private final String fromAddress;
  private final String fromCity;
  private final String fromState;
  private final String fromContactName;
  private final String fromContactPhoneNumber;
  private final String fromHours;

  private final String toAddress;
  private final String toCity;
  private final String toState;
  private final String toContactName;
  private final String toContactPhoneNumber;
  private final String toHours;

  /**
   * Secret code for dispatchers to view delivery manifest page and get a 'confirm' button. Once
   * that is done, confirmations are generated, each person that is next to confirm will get their
   * own secret code as well.
   */
  private final String dispatchCode;

  /**
   * Returns the status value of the driver, should be the name of one of the enum values for
   * 'DriverStatus'
   */
  private final String driverStatus;

  /**
   * Secret code for a driver to update their status, similar to 'dispatchCode'. This value is
   * DIFFERENT from driverConfirmCode. The confirm code is used to confirm the delivery. The driver
   * code is used to update the driver status. @See DriverStatus.java
   */
  private final String driverCode;

  private final String cancelReason;

  Delivery(DeliveryDao.DeliveryData dbData) {
    this.publicKey = dbData.getPublicUrlKey();
    this.deliveryNumber = dbData.getDeliveryId();
    this.deliveryStatus =
        Optional.ofNullable(dbData.getDeliveryStatus())
            .orElse(DeliveryStatus.CREATING_DISPATCH.getAirtableName());
    this.detailLink = DeliveryController.buildDeliveryPageLink(dbData.getPublicUrlKey());
    this.deliveryDate = dbData.getTargetDeliveryDate();
    this.driverName = dbData.getDriverName();
    this.driverPhoneNumber = dbData.getDriverNumber();
    this.driverLicensePlate = dbData.getLicensePlateNumbers();
    this.dispatcherName = dbData.getDispatcherName();
    this.dispatcherPhoneNumber = dbData.getDispatcherNumber();
    this.dispatcherNotes = dbData.getDispatcherNotes();
    this.fromSite = dbData.getFromSiteName();
    this.fromSiteId = dbData.getFromSiteId();
    this.fromSiteLink =
        dbData.getFromSiteId() == null
            ? ""
            : SiteDetailController.buildSiteLink(dbData.getFromSiteId());
    this.fromAddress = dbData.getFromAddress();
    this.fromCity = dbData.getFromCity();
    this.fromState = dbData.getFromState();
    this.fromContactName = dbData.getFromContactName();
    this.fromContactPhoneNumber = dbData.getFromContactPhone();
    this.fromHours = dbData.getFromHours();
    this.toSite = dbData.getToSiteName();
    this.toSiteId = dbData.getToSiteId();
    this.toSiteLink =
        dbData.getToSiteId() == null
            ? ""
            : SiteDetailController.buildSiteLink(dbData.getToSiteId());
    this.toAddress = dbData.getToAddress();
    this.toCity = dbData.getToCity();
    this.toState = dbData.getToState();
    this.toContactName = dbData.getToContactName();
    this.toContactPhoneNumber = dbData.getToContactPhone();
    this.toHours = dbData.getToHours();

    this.dispatchCode = dbData.getDispatchCode();
    this.dispatcherLink =
        dbData.getDispatchCode() == null
            ? this.detailLink
            : DeliveryController.buildDeliveryPageLinkWithCode(
                dbData.getPublicUrlKey(), dbData.getDispatchCode());
    this.driverStatus = dbData.getDriverStatus();
    this.driverCode = dbData.getDriverCode();
    this.cancelReason = dbData.getCancelReason();
  }

  public int getItemCount() {
    return itemList.size();
  }

  /**
   * The driver-facing status text -- the single place a raw backend status (e.g. "Creating
   * Dispatch") is translated to the word a driver reads, reused for both the route filter and this
   * card's own "Status:" line so the two never disagree (driver-portal UX review F6).
   */
  public String getDriverFacingStatusLabel() {
    return DeliveryStatus.fromAirtableName(deliveryStatus)
        .map(DeliveryStatus::getDriverFacingLabel)
        .orElse(deliveryStatus == null ? "Unknown" : deliveryStatus);
  }

  /** Which of the driver portal's four workflow-ordered filter buckets this status falls into. */
  public String getDriverFacingStatusBucketId() {
    return DeliveryStatus.fromAirtableName(deliveryStatus)
        .map(DeliveryStatus::getDriverFacingBucketId)
        .orElse("pending");
  }

  @SuppressWarnings("unused")
  String getItemListTruncated() {
    return getItemListTruncated(itemList);
  }

  // @VisibleForTesting
  static String getItemListTruncated(List<String> itemList) {
    if (itemList == null || itemList.isEmpty()) {
      return "";
    } else if (itemList.size() < 4) {
      return itemList.stream().sorted().collect(Collectors.joining(", "));
    } else {
      return String.join(", ", itemList.stream().sorted().toList().subList(0, 3)) + "...";
    }
  }

  public String getDriverConfirmationCode() {
    return findConfirmationCode(ConfirmRole.DRIVER);
  }

  public String getPickupConfirmationCode() {
    return findConfirmationCode(ConfirmRole.PICKUP_SITE);
  }

  public String getDropOffConfirmationCode() {
    return findConfirmationCode(ConfirmRole.DROPOFF_SITE);
  }

  private String findConfirmationCode(ConfirmRole confirmRole) {
    return confirmations.stream()
        .filter(c -> ConfirmRole.valueOf(c.getConfirmRole()) == confirmRole)
        .findAny()
        .map(DeliveryConfirmation::getCode)
        .orElse(null);
  }

  public Optional<DeliveryConfirmation> getConfirmation(ConfirmRole confirmRole) {
    return confirmations.stream()
        .filter(c -> ConfirmRole.valueOf(c.getConfirmRole()) == confirmRole)
        .findAny();
  }

  public boolean isConfirmed() {
    return !confirmations.isEmpty()
        && confirmations.stream().allMatch(c -> c.getConfirmed() != null && c.getConfirmed());
  }

  public boolean hasCancellation() {
    return !confirmations.isEmpty()
        && confirmations.stream().anyMatch(c -> c.getConfirmed() != null && !c.getConfirmed());
  }

  /**
   * Returns list of error messages indicating any missing data. Confirmations process cannot start
   * until all needed data is present.
   */
  List<String> missingData() {
    List<String> unableToConfirmMessages = new ArrayList<>();

    if (getDeliveryDate() == null) {
      unableToConfirmMessages.add("Delivery date needs to be set");
    }
    if (getDispatcherPhoneNumber() == null || getDispatcherPhoneNumber().isBlank()) {
      unableToConfirmMessages.add("Dispatcher needs to be set and to have a phone number");
    }
    if (getDriverPhoneNumber() == null || getDriverPhoneNumber().isBlank()) {
      unableToConfirmMessages.add("Driver needs to be set and to have a phone number");
    }
    if (getFromContactPhoneNumber() == null || getFromContactPhoneNumber().isBlank()) {
      unableToConfirmMessages.add("Pickup site needs a phone number");
    }
    if (getToContactPhoneNumber() == null || getToContactPhoneNumber().isBlank()) {
      unableToConfirmMessages.add("Drop off site needs a phone number");
    }
    if (getItemList().isEmpty()) {
      unableToConfirmMessages.add("Items need to be added");
    }
    return unableToConfirmMessages;
  }

  public void addItems(List<String> items) {
    getItemList().addAll(items);
  }

  public List<String> getItemList() {
    if (itemList == null) {
      itemList = new ArrayList<>();
    }
    return itemList;
  }

  public void addConfirmations(List<DeliveryConfirmation> confirmations) {
    getConfirmations().addAll(confirmations);
  }

  public List<DeliveryConfirmation> getConfirmations() {
    if (confirmations == null) {
      confirmations = new ArrayList<>();
    }
    return confirmations;
  }
}
