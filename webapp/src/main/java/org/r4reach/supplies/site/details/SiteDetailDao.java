package org.r4reach.supplies.site.details;

import jakarta.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.PiiCrypto;

public class SiteDetailDao {

  @Nullable
  public static Long lookupSiteIdByWssId(Jdbi jdbi, long wssId) {
    return lookupIdentifier(jdbi, "wss_id", wssId);
  }

  private static Long lookupIdentifier(Jdbi jdbi, String lookupColumn, long idValue) {
    String query = String.format("select id from site where %s = :id", lookupColumn);
    return jdbi.withHandle(
            handle -> handle.createQuery(query).bind("id", idValue).mapTo(Long.class).findOne())
        .orElse(null);
  }

  @Data
  @NoArgsConstructor
  public static class SiteDetailData {
    String siteName;
    String siteType;
    String contactName;
    String contactNumber;
    String address;
    String city;
    String state;
    String county;
    String website;
    String facebook;
    boolean publiclyVisible;
    boolean active;
    boolean distributingSupplies;
    boolean acceptingDonations;
    String hours;
    long wssId;
    String inactiveReason;
    String maxSupply;
    String receivingNotes;
    Number weeklyServed;
  }

  @Nullable
  public static SiteDetailData lookupSiteById(Jdbi jdbi, long idToLookup) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                            select
                              s.name siteName,
                              st.name siteType,
                              pc.name_enc contactName,
                              pc.phone_enc contactNumber,
                              s.additional_contacts,
                              s.address,
                              s.city,
                              c.state,
                              c.name county,
                              s.website,
                              s.facebook,
                              s.publicly_visible,
                              s.active,
                              s.distributing_supplies,
                              s.accepting_donations,
                              s.hours,
                              s.wss_id,
                              s.inactive_reason,
                              msl.name maxSupply,
                              s.receiving_notes,
                              s.weekly_served
                            from site s
                            join county c on c.id = s.county_id
                            join site_type st on st.id = s.site_type_id
                            join max_supply_load msl on msl.id = s.max_supply_load_id
                            left join wss_user pc on pc.id = s.primary_contact_wss_user_id
                            where s.id = :siteId
                            """)
                .bind("siteId", idToLookup)
                .mapToBean(SiteDetailData.class)
                .findOne()
                .map(SiteDetailDao::decryptContact)
                .orElse(null));
  }

  // contactName/contactNumber are fetched as name_enc/phone_enc ciphertext; decrypt in place.
  private static SiteDetailData decryptContact(SiteDetailData data) {
    data.setContactName(PiiCrypto.decrypt(data.getContactName()));
    data.setContactNumber(PiiCrypto.decrypt(data.getContactNumber()));
    return data;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SiteContact {
    String name;
    String phone;
  }

  /**
   * The site's additional contacts: its managers in wss_user_sites other than the displayed primary
   * contact and the (hidden) permanent original contact.
   */
  public static List<SiteContact> lookupAdditionalSiteContacts(Jdbi jdbi, long siteId) {

    return jdbi
        .withHandle(
            handle ->
                handle
                    .createQuery(
                        """
                    select u.name_enc name, u.phone_enc phone
                    from wss_user_sites ws
                    join wss_user u on u.id = ws.wss_user_id
                    join site s on s.id = ws.site_id
                    where ws.site_id = :siteId
                      and ws.wss_user_id is distinct from s.primary_contact_wss_user_id
                      and ws.wss_user_id is distinct from s.og_contact_wss_user_id
                    """)
                    .bind("siteId", siteId)
                    .mapToBean(SiteContact.class)
                    .list())
        .stream()
        .map(
            c -> {
              c.setName(PiiCrypto.decrypt(c.getName()));
              c.setPhone(PiiCrypto.decrypt(c.getPhone()));
              return c;
            })
        // name/phone are encrypted, so the old `order by u.name` sorts here instead.
        .sorted(
            Comparator.comparing(
                SiteContact::getName, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
  }
}
