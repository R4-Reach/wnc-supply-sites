package org.r4reach.delivery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.SecretCodeGenerator;

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

  /** All deliveries, newest target date first — the data behind the dispatcher deliveries board. */
  public static List<Delivery> fetchAllDeliveries(Jdbi jdbi) {
    // fetchDeliveries always binds :id; "true" simply ignores it to select every row.
    return fetchDeliveries(jdbi, "true", "");
  }

  /** A site the dispatcher can pick as a delivery's pickup or drop-off endpoint. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SiteOption {
    long id;
    String name;
  }

  public static List<SiteOption> fetchSiteOptions(Jdbi jdbi) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select id, name from site order by lower(name)")
                .mapToBean(SiteOption.class)
                .list());
  }

  /** A dispatcher or driver a delivery can be assigned to, for a create-form dropdown. */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PersonOption {
    long id;
    String name;
    boolean selected;
  }

  /**
   * Everyone holding the DISPATCHER role, as dropdown options. {@code selectedUserId} (nullable)
   * marks the option to preselect — typically the current user, who is always a dispatcher on the
   * create page.
   */
  public static List<PersonOption> fetchDispatcherOptions(Jdbi jdbi, Long selectedUserId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    select distinct
                      u.id,
                      coalesce(nullif(u.name, ''), u.phone) name,
                      coalesce(u.id = cast(:selectedUserId as bigint), false) selected
                    from wss_user u
                    join wss_user_roles wur on wur.wss_user_id = u.id
                    join wss_user_role role on role.id = wur.wss_user_role_id
                    where role.name = 'DISPATCHER' and u.removed = false
                    order by name
                    """)
                .bind("selectedUserId", selectedUserId)
                .mapToBean(PersonOption.class)
                .list());
  }

  /**
   * Every driver (excluding blacklisted ones) as dropdown options, identified by their wss_user id.
   * {@code selectedUserId} (nullable) marks the option to preselect.
   */
  public static List<PersonOption> fetchDriverOptions(Jdbi jdbi, Long selectedUserId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    select
                      u.id,
                      coalesce(nullif(u.name, ''), u.phone) name,
                      coalesce(u.id = cast(:selectedUserId as bigint), false) selected
                    from driver d
                    join wss_user u on u.id = d.wss_user_id
                    where d.black_listed = false and u.removed = false
                    order by name
                    """)
                .bind("selectedUserId", selectedUserId)
                .mapToBean(PersonOption.class)
                .list());
  }

  /** The wss_user id for a phone number, used to preselect the current user in a dropdown. */
  public static Optional<Long> fetchUserIdByPhone(Jdbi jdbi, String phone) {
    if (phone == null || phone.isBlank()) {
      return Optional.empty();
    }
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select id from wss_user where phone = :phone and removed = false")
                .bind("phone", phone)
                .mapTo(Long.class)
                .findOne());
  }

  /**
   * The dispatcher-entered fields for a brand-new delivery. The dispatcher and driver are chosen
   * from dropdowns, so they arrive as wss_user references; their name and phone are derived from
   * that record at read time rather than stored here.
   */
  @Value
  @Builder
  public static class CreateDeliveryRequest {
    Long fromSiteId;
    Long toSiteId;
    DeliveryStatus deliveryStatus;
    String targetDeliveryDate;
    Long dispatcherWssUserId;
    Long driverWssUserId;
    String dispatcherNotes;
    @Builder.Default List<String> items = List.of();
  }

  /**
   * Creates a delivery from dispatcher-entered fields, minting its own public url key and secret
   * codes (these were supplied by the Airtable feed before it was removed). Returns the new
   * delivery's public url key. {@code wss_id} is assigned by its column default sequence.
   */
  public static String createDelivery(Jdbi jdbi, CreateDeliveryRequest request) {
    String publicUrlKey = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String insert =
        """
        insert into delivery(
          from_site_id, to_site_id, delivery_status, target_delivery_date,
          dispatcher_wss_user_id, driver_wss_user_id,
          dispatcher_notes, public_url_key, dispatch_code, driver_code)
        values(
          :fromSiteId, :toSiteId, :deliveryStatus,
          to_date(:targetDeliveryDate, 'YYYY-MM-DD'),
          :dispatcherWssUserId, :driverWssUserId,
          :dispatcherNotes, :publicUrlKey, :dispatchCode, :driverCode)
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(insert)
                .bind("fromSiteId", request.getFromSiteId())
                .bind("toSiteId", request.getToSiteId())
                .bind("deliveryStatus", request.getDeliveryStatus().getAirtableName())
                .bind("targetDeliveryDate", blankToNull(request.getTargetDeliveryDate()))
                .bind("dispatcherWssUserId", request.getDispatcherWssUserId())
                .bind("driverWssUserId", request.getDriverWssUserId())
                .bind("dispatcherNotes", blankToNull(request.getDispatcherNotes()))
                .bind("publicUrlKey", publicUrlKey)
                .bind("dispatchCode", SecretCodeGenerator.generateCode())
                .bind("driverCode", SecretCodeGenerator.generateCode())
                .execute());

    String insertItem =
        """
        insert into delivery_item(delivery_id, item_name)
        values((select id from delivery where public_url_key = :publicUrlKey), :itemName)
        """;
    for (String itemName : request.getItems()) {
      if (itemName == null || itemName.isBlank()) {
        continue;
      }
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(insertItem)
                  .bind("publicUrlKey", publicUrlKey)
                  .bind("itemName", itemName.strip())
                  .execute());
    }
    return publicUrlKey;
  }

  private static String blankToNull(String input) {
    return input == null || input.isBlank() ? null : input.strip();
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
    // The driver phone is either the referenced wss_user's canonical phone (new deliveries) or the
    // legacy driver_number, which was synced from Airtable and not stored canonically. Canonicalize
    // both sides: strip non-digits and prefix the country code onto 10-digit numbers.
    String whereClause =
        """
              case
                when length(
                  regexp_replace(coalesce(driverUser.phone, d.driver_number), '[^0-9]+', '', 'g'))
                  = 10
                  then '1'
                    || regexp_replace(coalesce(driverUser.phone, d.driver_number), '[^0-9]+', '', 'g')
                else regexp_replace(coalesce(driverUser.phone, d.driver_number), '[^0-9]+', '', 'g')
              end = :id
            """;
    return fetchDeliveries(jdbi, whereClause, PhoneNumberUtil.toCanonical(driverPhone));
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
      coalesce(dispatcher.name, d.dispatcher_name) dispatcherName,
      coalesce(dispatcher.phone, d.dispatcher_number) dispatcherNumber,
      d.dispatcher_notes dispatcherNotes,
      coalesce(driverUser.name, d.driver_name) driverName,
      coalesce(driverUser.phone, d.driver_number) driverNumber,
      d.driver_license_plates licensePlateNumbers,
      d.cancel_reason cancelReason,

      coalesce(fromSite.name, d.pickup_site_name) fromSiteName,
      fromSite.id fromSiteId,
      coalesce(fromSite.address, d.pickup_address) fromAddress,
      coalesce(fromSite.city, d.pickup_city) fromCity,
      coalesce(fromCounty.state, d.pickup_state) fromState,
      coalesce(fromPc.name, d.pickup_contact_name) fromContactName,
      coalesce(fromPc.phone, d.pickup_contact_phone) fromContactPhone,
      coalesce(fromSite.hours, d.pickup_hours) fromHours,

      coalesce(toSite.name, d.dropoff_site_name) toSiteName,
      toSite.id toSiteId,
      coalesce(toSite.address, d.dropoff_address) toAddress,
      coalesce(toSite.city, d.dropoff_city) toCity,
      coalesce(toCounty.state, d.dropoff_state) toState,
      coalesce(toPc.name, d.dropoff_contact_name) toContactName,
      coalesce(toPc.phone, d.dropoff_contact_phone) toContactPhone,
      coalesce(toSite.hours, d.dropoff_hours) toHours,

      d.dispatch_code,
      d.driver_status,
      d.driver_code driverCode
    from delivery d
    left join wss_user dispatcher on dispatcher.id = d.dispatcher_wss_user_id
    left join wss_user driverUser on driverUser.id = d.driver_wss_user_id
    left join site fromSite on fromSite.id = d.from_site_id
    left join county fromCounty on fromCounty.id = fromSite.county_id
    left join wss_user fromPc on fromPc.id = fromSite.primary_contact_wss_user_id
    left join site toSite on toSite.id = d.to_site_id
    left join county toCounty on toCounty.id = toSite.county_id
    left join wss_user toPc on toPc.id = toSite.primary_contact_wss_user_id
    where (%s)
    order by d.target_delivery_date desc
    """,
            whereClause);
    List<Delivery> deliveries =
        jdbi
            .withHandle(
                handle -> {
                  var query = handle.createQuery(select);
                  // The "fetch all" clause has no placeholder; binding an unused :id is rejected.
                  if (whereClause.contains(":id")) {
                    query.bind("id", idValue);
                  }
                  return query.mapToBean(DeliveryData.class).list();
                })
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
