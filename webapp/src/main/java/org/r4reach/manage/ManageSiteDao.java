package org.r4reach.manage;

import jakarta.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.user.UserRoleService;
import org.r4reach.data.SiteType;
import org.r4reach.manage.SelectSiteController.SiteSelection;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.PiiCrypto;

@Slf4j
public class ManageSiteDao {

  static List<SiteSelection> fetchSiteList(Jdbi jdbi, List<Long> sites, List<String> states) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                   select s.id, s.name
                   from site s
                   join county c on c.id = s.county_id
                   where s.id in (<sites>) and c.state in (<states>)
                   order by lower(s.name)
                    """)
                .bindList("sites", sites)
                .bindList("states", states)
                .mapToBean(SiteSelection.class)
                .list());
  }

  @Data
  public static class MaxSupplyOption {
    String name;
    boolean defaultSelection;
  }

  public static List<MaxSupplyOption> getAllMaxSupplyOptions(Jdbi jdbi) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    "select name, default_selection from max_supply_load order by sort_order")
                .mapToBean(MaxSupplyOption.class)
                .list());
  }

  @AllArgsConstructor
  @Getter
  public enum SiteField {
    SITE_NAME("name", "Site Name", true, false),
    STREET_ADDRESS("address", "Street Address", true, true),
    CITY("city", "City", true, true),
    STATE("state", "State", true, true),
    COUNTY("county", "County", true, true),
    WEBSITE("website", "Website", false, false),
    FACEBOOK("facebook", "Facebook", false, false),
    SITE_HOURS("hours", "Site Hours", false, false),
    CONTACT_NAME("contact_name", "Contact Name", false, false),
    CONTACT_NUMBER("contact_number", "Contact Number", false, false),
    ADDITIONAL_CONTACTS("additional_contacts", "Additional Contacts", false, false),
    MAX_SUPPLY_LOAD("max_supply_load", "max supply load", false, false),
    RECEIVING_NOTES("receiving_notes", "receiving notes", false, false),
    WEEKLY_SERVED("weekly_served", "Weekly Served", false, false);

    private final String columnName;
    private final String frontEndName;
    private final boolean required;

    /**
     * isLocationField identifies where a site is located. This flag helps us know when we need to
     * recalculate distances.
     */
    private final boolean isLocationField;

    public static Optional<SiteField> lookupField(String name) {
      return Arrays.stream(SiteField.values()).filter(f -> f.frontEndName.equals(name)).findAny();
    }
  }

  static class RequiredFieldException extends IllegalArgumentException {
    RequiredFieldException(String fieldName) {
      super("Required field " + fieldName + " cannot be deleted");
    }
  }

  public static void updateSiteField(Jdbi jdbi, long siteId, SiteField field, String newValue) {
    log.info("Updating site: {}, field: {}, value: {}", siteId, field, newValue);

    if (field.isRequired() && (newValue == null || newValue.isEmpty())) {
      throw new RequiredFieldException(field.frontEndName);
    }

    // The contact number is stored (and mirrored into wss_user) in canonical 11-digit form.
    if (field == SiteField.CONTACT_NUMBER && newValue != null && !newValue.isBlank()) {
      newValue = PhoneNumberUtil.toCanonical(newValue);
    }

    @Nullable final String oldValue;
    if (field == SiteField.COUNTY || field == SiteField.STATE) {
      if (!newValue.contains(",")) {
        throw new IllegalStateException(
            "New county value must be encoded as 'COUNTY,STATE'; Illegal value: " + newValue);
      }
      String[] split = newValue.split(",");
      String county = split[0];
      String state = split[1];
      oldValue = updateCounty(jdbi, siteId, county, state);
    } else if (field == SiteField.MAX_SUPPLY_LOAD) {
      oldValue = updateMaxSupply(jdbi, siteId, newValue);
    } else if (field == SiteField.CONTACT_NUMBER) {
      oldValue = updatePrimaryContactNumber(jdbi, siteId, newValue);
    } else if (field == SiteField.CONTACT_NAME) {
      oldValue = updatePrimaryContactName(jdbi, siteId, newValue);
    } else {
      oldValue = updateSiteColumn(jdbi, siteId, field, newValue);
    }
    addToAuditTrail(
        jdbi, siteId, field, oldValue, newValue == null || newValue.isBlank() ? "-" : newValue);

    // if location as changed, then we need to delete previous distances and re-calculate
    if (field.isLocationField()) {
      String deleteDistances =
          """
          update site_distance_matrix
          set distance_miles = null, drive_time_seconds = null, valid = null
          where site1_id = :siteId or site2_id = :siteId
          """;
      jdbi.withHandle(
          handle -> handle.createUpdate(deleteDistances).bind("siteId", siteId).execute());
    }
  }

  /**
   * Points the site's primary contact at the user for this (canonical) phone: ensures that user
   * exists with SITE_MANAGER portal access, sets site.primary_contact_wss_user_id, and records the
   * site membership in wss_user_sites. A blank value clears the primary contact. Returns the
   * previous primary contact's phone for the audit trail.
   */
  static String updatePrimaryContactNumber(Jdbi jdbi, long siteId, String newValue) {
    String oldValue =
        jdbi.withHandle(
                handle ->
                    handle
                        .createQuery(
                            """
                            select pc.phone_enc
                            from site s
                            left join wss_user pc on pc.id = s.primary_contact_wss_user_id
                            where s.id = :siteId
                            """)
                        .bind("siteId", siteId)
                        .mapTo(String.class)
                        .findOne())
            .map(PiiCrypto::decrypt)
            .orElse(null);

    if (newValue == null || newValue.isBlank()) {
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(
                      "update site set primary_contact_wss_user_id = null where id = :siteId")
                  .bind("siteId", siteId)
                  .execute());
      return oldValue;
    }

    // grantSiteManager only creates a user for a valid phone; for an invalid one leave the current
    // primary contact untouched.
    UserRoleService.grantSiteManager(jdbi, newValue);
    Optional<Long> userIdOpt =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("select id from wss_user where phone_hmac = :phoneHmac")
                    .bind("phoneHmac", PiiCrypto.blindIndex(PhoneNumberUtil.toCanonical(newValue)))
                    .mapTo(Long.class)
                    .findOne());
    if (userIdOpt.isEmpty()) {
      return oldValue;
    }
    long userId = userIdOpt.get();
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    "update site set primary_contact_wss_user_id = :userId where id = :siteId")
                .bind("userId", userId)
                .bind("siteId", siteId)
                .execute());
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into wss_user_sites(wss_user_id, site_id) values(:userId, :siteId)
                    on conflict (wss_user_id, site_id) do nothing
                    """)
                .bind("userId", userId)
                .bind("siteId", siteId)
                .execute());
    return oldValue;
  }

  /** Sets the display name of the site's primary contact user. Returns the previous name. */
  static String updatePrimaryContactName(Jdbi jdbi, long siteId, String newValue) {
    String oldValue =
        jdbi.withHandle(
                handle ->
                    handle
                        .createQuery(
                            """
                            select pc.name_enc
                            from site s
                            left join wss_user pc on pc.id = s.primary_contact_wss_user_id
                            where s.id = :siteId
                            """)
                        .bind("siteId", siteId)
                        .mapTo(String.class)
                        .findOne())
            .map(PiiCrypto::decrypt)
            .orElse(null);
    String trimmed = newValue == null || newValue.isBlank() ? null : newValue.trim();
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    update wss_user set name_enc = :nameEnc
                    where id = (select primary_contact_wss_user_id from site where id = :siteId)
                    """)
                .bind("nameEnc", PiiCrypto.encrypt(trimmed))
                .bind("siteId", siteId)
                .execute());
    return oldValue;
  }

  /**
   * Returns old value and then updates. Old value is encoded as "COUNTY,STATE" (single string,
   * comma delimited)
   */
  //  @VisibleForTesting
  static String updateCounty(Jdbi jdbi, long siteId, String newCounty, String newState) {
    // first lookup the old value from database before we change it.
    String oldValueQuery =
        """
        select c.name || ',' || c.state
        from site s
        join county c on c.id = s.county_id
        where s.id = :siteId
        """;
    String oldValue =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery(oldValueQuery) //
                    .bind("siteId", siteId)
                    .mapTo(String.class)
                    .one());

    // now actually update the county value
    String updateCounty =
        """
        update site
        set
          county_id = (select id from county where name = :name and state = :state)
        where id = :id
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(updateCounty)
                .bind("name", newCounty)
                .bind("state", newState)
                .bind("id", siteId)
                .execute());
    return oldValue;
  }

  public static String updateMaxSupply(Jdbi jdbi, long siteId, String newMaxSupply) {
    if (newMaxSupply == null || newMaxSupply.isBlank()) {
      throw new IllegalArgumentException("Illegal null value for max supply, siteId: " + siteId);
    }
    String oldValueQuery =
        """
        select msl.name
        from max_supply_load msl
        join site s on s.max_supply_load_id = msl.id
        where s.id = :siteId
        """;
    String oldValue =
        jdbi.withHandle(
                handle ->
                    handle
                        .createQuery(oldValueQuery)
                        .bind("siteId", siteId)
                        .mapTo(String.class)
                        .findOne())
            .orElse("-");

    String update =
        """
          update site
          set max_supply_load_id = (select id from max_supply_load where name = :max)
          where id = :siteId
          """;
    final String maxSupplyValue = newMaxSupply;
    int updateCount =
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(update)
                    .bind("max", maxSupplyValue)
                    .bind("siteId", siteId)
                    .execute());
    if (updateCount == 0) {
      log.error("Received bad value for max supply load update: {}", newMaxSupply);
      throw new IllegalArgumentException("Invalid max supply value received: " + newMaxSupply);
    }

    return oldValue;
  }

  /** Returns old value and then updates. */
  static String updateSiteColumn(Jdbi jdbi, long siteId, SiteField column, String newValue) {

    String oldValueQuery =
        java.lang.String.format(
            "select s.%s from site s where s.id = :siteId", column.getColumnName());

    String oldValue =
        jdbi.withHandle(
            handle ->
                handle.createQuery(oldValueQuery).bind("siteId", siteId).mapTo(String.class).one());

    if (Objects.equals(column.getFrontEndName(), "Weekly Served")) {
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(
                      "update site set "
                          + column.getColumnName()
                          + " = :newValue where id = :siteId")
                  .bind("newValue", Integer.parseInt(newValue))
                  .bind("siteId", siteId)
                  .execute());
    } else {
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(
                      "update site set "
                          + column.getColumnName()
                          + " = :newValue where id = :siteId")
                  .bind("newValue", newValue)
                  .bind("siteId", siteId)
                  .execute());
    }
    return oldValue;
  }

  private static void addToAuditTrail(
      Jdbi jdbi, long siteId, SiteField field, String oldValue, String newValue) {
    String insert =
        """
    insert into site_audit_trail(site_id, field_name, old_value, new_value)
    values(
       :siteId, :fieldName, :oldValue, :newValue
    )
    """;

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(insert)
                .bind("siteId", siteId)
                .bind("fieldName", field.getColumnName())
                .bind("oldValue", oldValue)
                .bind("newValue", newValue)
                .execute());
  }

  /** Returns null if ID is not valid or DNE. */
  static String fetchSiteName(Jdbi jdbi, String siteId) {
    if (siteId == null || siteId.isBlank()) {
      return null;
    }

    try {
      long id = Long.parseLong(siteId);
      return ManageSiteDao.fetchSiteName(jdbi, id);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  public static String fetchSiteName(Jdbi jdbi, long siteId) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select name from site where id = :siteId")
                .bind("siteId", siteId)
                .mapTo(String.class)
                .one());
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SiteStatus {
    boolean active;
    boolean publiclyVisible;
    boolean acceptingDonations;
    boolean distributingSupplies;
    String inactiveReason;
    String siteType;

    public SiteType getSiteTypeEnum() {
      return Arrays.stream(SiteType.values())
          .filter(s -> s.getText().equals(siteType))
          .findAny()
          .orElseThrow(() -> new IllegalArgumentException("Unknown site type: " + siteType));
    }
  }

  public static SiteStatus fetchSiteStatus(Jdbi jdbi, long siteId) {
    String query =
        """
        select
          s.active,
          s.accepting_donations,
          s.publicly_visible,
          s.distributing_supplies,
          s.inactive_reason,
          st.name siteType
        from site s
        join site_type st on st.id = s.site_type_id
        where s.id = :siteId
        """;

    SiteStatus siteStatus =
        jdbi.withHandle(
            handle ->
                handle.createQuery(query).bind("siteId", siteId).mapToBean(SiteStatus.class).one());
    if (siteStatus == null) {
      throw new IllegalArgumentException("Invalid site id: " + siteId);
    } else {
      return siteStatus;
    }
  }

  public static void updateSiteAcceptingDonationsFlag(Jdbi jdbi, long siteId, boolean newValue) {
    updateSiteFlag(jdbi, siteId, "accepting_donations", newValue);
  }

  public static void updateSiteDistributingDonationsFlag(Jdbi jdbi, long siteId, boolean newValue) {
    updateSiteFlag(jdbi, siteId, "distributing_supplies", newValue);
  }

  public static void updateSiteActiveFlag(Jdbi jdbi, long siteId, boolean newValue) {
    updateSiteFlag(jdbi, siteId, "active", newValue);
  }

  public static void updateSitePubliclyVisible(Jdbi jdbi, long siteId, boolean newValue) {
    updateSiteFlag(jdbi, siteId, "publicly_visible", newValue);
  }

  private static void updateSiteFlag(Jdbi jdbi, long siteId, String column, boolean newValue) {
    String update =
        java.lang.String.format(
            "update site set %s = :newValue, last_updated = now() where id = :siteId", column);

    int updateCount =
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(update)
                    .bind("newValue", newValue)
                    .bind("siteId", siteId)
                    .execute());

    if (updateCount == 0) {
      throw new IllegalArgumentException("Invalid site id: " + siteId);
    }
  }

  public static void updateInactiveReason(Jdbi jdbi, long siteId, String inactiveReason) {
    String update =
        """
        update site set inactive_reason = :inactiveReason where id = :siteId
        """;

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(update)
                .bind("inactiveReason", inactiveReason)
                .bind("siteId", siteId)
                .execute());
  }

  /** Fetches ALL items. Indicates which items are active / inactive for a given site. */
  public static List<SiteInventory> fetchSiteInventory(Jdbi jdbi, long siteId) {
    String query =
        """
        with inventory as (
         select
              i.id item_id,
              s.id site_id,
              stat.name status_name
         from site s
         join site_item si on si.site_id = s.id
         join item i on i.id = si.item_id
         join item_status stat on stat.id = si.item_status_id
         where s.id = :siteId
        )
        select
            i.id item_id,
            i.name item_name,
            case when inv.site_id is null then false else true end active,
            inv.status_name item_status,
            string_agg(t.name, ',') itemTags
        from item i
        left join inventory inv on inv.item_id = i.id
        left join item_tag it on it.item_id = i.id
        left join tag t on t.id = it.tag_id
        group by i.id, i.name, inv.site_id, inv.status_name
    """;

    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(query)
                .bind("siteId", siteId) //
                .mapToBean(SiteInventory.class)
                .list());
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SiteInventory {
    long itemId;
    String itemName;
    String itemStatus;
    String itemTags;
    boolean active;

    public List<ItemTagData> getTags() {
      if (itemTags == null) {
        return List.of();
      }
      return Arrays.stream(itemTags.split(","))
          .map(tag -> new ItemTagData(tag, "#7fffd4"))
          .toList();
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ItemTagData {
    private String tagName;
    private String tagColor;
  }

  /**
   * Updates that the site inventory was last updated now, and because the site inventory was
   * updated.
   */
  public static void updateSiteInventoryLastUpdated(Jdbi jdbi, long siteId) {
    String updateSiteLastUpdated =
        "update site set inventory_last_updated = now() where id = :siteId";
    jdbi.withHandle(
        handle -> handle.createUpdate(updateSiteLastUpdated).bind("siteId", siteId).execute());
  }

  public static void updateSiteType(Jdbi jdbi, long siteId, SiteType siteType) {
    String update =
        """
        update site set site_type_id = (select id from site_type where name = :siteTypeName)
           where id = :siteId;
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(update)
                .bind("siteId", siteId)
                .bind("siteTypeName", siteType.getText())
                .execute());
  }
}
