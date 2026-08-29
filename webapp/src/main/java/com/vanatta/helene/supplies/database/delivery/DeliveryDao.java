package com.vanatta.helene.supplies.database.delivery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;

@Slf4j
public class DeliveryDao {

  public static void updateDeliveryStatus(
      Jdbi jdbi, String publicKey, DeliveryStatus deliveryStatus) {
    String update =
        """
      update delivery
        set delivery_status = :deliveryStatus
      where public_url_key = :publicKey
      """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(update)
                .bind("publicKey", publicKey)
                .bind("deliveryStatus", deliveryStatus.getAirtableName())
                .execute());
  }

  // get
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class DeliveryData {
    long deliveryId;
    String publicUrlKey;
    String deliveryStatus;
    String dispatcherName;
    String dispatcherNumber;
    String dispatcherNotes;
    String driverName;
    String driverNumber;
    String licensePlateNumbers;
    String targetDeliveryDate;

    String fromSiteName;
    Long fromSiteId;
    private String fromAddress;
    private String fromCity;
    private String fromState;
    private String fromContactName;
    private String fromContactPhone;
    private String fromHours;

    String toSiteName;
    Long toSiteId;
    private String toAddress;
    private String toCity;
    private String toState;
    private String toContactName;
    private String toContactPhone;
    private String toHours;

    private String dispatchCode;
    private String driverStatus;

    /**
     * Driver code is used to update driverStatus. It is not used to do confirmations. The driver
     * confirm code is used for confirmations.
     */
    private String driverCode;

    private String cancelReason;
  }

  public static Optional<Delivery> fetchDeliveryByPublicKey(Jdbi jdbi, String publicUrlKey) {
    String whereClause = "d.public_url_key = :id";
    var results = fetchDeliveries(jdbi, whereClause, publicUrlKey);
    if (results.isEmpty()) {
      return Optional.empty();
    } else {
      return Optional.of(results.getFirst());
    }
  }

  public static List<Delivery> fetchDeliveriesBySiteId(Jdbi jdbi, Long siteId) {
    String whereClause =
        """
    d.from_site_id = :id
    or d.to_site_id = :id
    """;
    return fetchDeliveries(jdbi, whereClause, siteId);
  }

  public static List<Delivery> fetchDeliveriesByDriverPhoneNumber(Jdbi jdbi, String driverPhone) {
    String whereClause =
        """
              regexp_replace(d.driver_number, '[^0-9]+', '', 'g') = :id
            """;
    return fetchDeliveries(jdbi, whereClause, driverPhone);
  }

  private static List<Delivery> fetchDeliveries(Jdbi jdbi, String whereClause, Object idValue) {
    String select =
        String.format(
            """
    select
      d.wss_id deliveryId,
      d.public_url_key publicUrlKey,
      d.delivery_status deliveryStatus,
      d.target_delivery_date targetDeliveryDate,
      d.dispatcher_name dispatcherName,
      d.dispatcher_number dispatcherNumber,
      d.dispatcher_notes dispatcherNotes,
      d.driver_name driverName,
      d.driver_number driverNumber,
      d.driver_license_plates licensePlateNumbers,
      d.cancel_reason cancelReason,

      coalesce(fromSite.name, d.pickup_site_name) fromSiteName,
      fromSite.id fromSiteId,
      coalesce(fromSite.address, d.pickup_address) fromAddress,
      coalesce(fromSite.city, d.pickup_city) fromCity,
      coalesce(fromCounty.state, d.pickup_state) fromState,
      coalesce(fromSite.contact_name, d.pickup_contact_name) fromContactName,
      coalesce(fromSite.contact_number, d.pickup_contact_phone) fromContactPhone,
      coalesce(fromSite.hours, d.pickup_hours) fromHours,

      coalesce(toSite.name, d.dropoff_site_name) toSiteName,
      toSite.id toSiteId,
      coalesce(toSite.address, d.dropoff_address) toAddress,
      coalesce(toSite.city, d.dropoff_city) toCity,
      coalesce(toCounty.state, d.dropoff_state) toState,
      coalesce(toSite.contact_name, d.dropoff_contact_name) toContactName,
      coalesce(toSite.contact_number, d.dropoff_contact_phone) toContactPhone,
      coalesce(toSite.hours, d.dropoff_hours) toHours,

      d.dispatch_code,
      d.driver_status,
      d.driver_code driverCode
    from delivery d
    left join site fromSite on fromSite.id = d.from_site_id
    left join county fromCounty on fromCounty.id = fromSite.county_id
    left join site toSite on toSite.id = d.to_site_id
    left join county toCounty on toCounty.id = toSite.county_id
    where (%s)
    order by d.target_delivery_date desc
    """,
            whereClause);
    List<Delivery> deliveries =
        jdbi
            .withHandle(
                handle ->
                    handle
                        .createQuery(select)
                        .bind("id", idValue)
                        .mapToBean(DeliveryData.class)
                        .list())
            .stream()
            .map(Delivery::new)
            .toList();

    String selectDeliveryItems =
        """
      select distinct A.name
      from
      (
      select
        i.name
      from delivery_item di
      join item i on i.id = di.item_id
      where di.delivery_id = (select id from delivery where wss_id = :deliveryId)
      union
      select
        di.item_name name
      from delivery_item di
      where di.delivery_id = (select id from delivery where wss_id = :deliveryId)
      ) A
      order by A.name;
      """;

    String selectConfirmations =
        """
      select
         dc.confirm_type confirmRole,
         dc.delivery_accepted confirmed,
         dc.secret_code code
      from delivery_confirmation dc
      join delivery d on d.id = dc.delivery_id
      where d.public_url_key = :publicUrlKey
      """;

    for (Delivery delivery : deliveries) {
      List<String> items =
          jdbi.withHandle(
              handle ->
                  handle
                      .createQuery(selectDeliveryItems)
                      .bind("deliveryId", delivery.getDeliveryNumber())
                      .mapTo(String.class)
                      .list());
      delivery.addItems(items.stream().filter(Objects::nonNull).sorted().toList());

      List<DeliveryConfirmation> confirmations =
          jdbi.withHandle(
              handle ->
                  handle
                      .createQuery(selectConfirmations)
                      .bind("publicUrlKey", delivery.getPublicKey())
                      .mapToBean(DeliveryConfirmation.class)
                      .list());
      delivery.addConfirmations(confirmations);
    }

    return deliveries;
  }
}
