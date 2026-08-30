package org.r4reach.manage.add.site;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.r4reach.auth.user.UserRoleService;
import org.r4reach.util.PhoneNumberUtil;

@Slf4j
public class AddSiteDao {

  public static class DuplicateSiteException extends RuntimeException {
    DuplicateSiteException(String message) {
      super(message);
    }
  }

  /**
   * Adds a new site and returns the ID of that site.
   *
   * @throws DuplicateSiteException Thrown if site name already exists
   * @throws IllegalArgumentException If an invalid county is specified
   * @throws UnableToExecuteStatementException if required fields are missing
   */
  public static long addSite(Jdbi jdbi, AddSiteData siteData) {
    String insert =
        """
        insert into site(
          name,
          address,
          city,
          county_id,
          website,
          facebook,
          site_type_id,
          hours,
          max_supply_load_id,
          receiving_notes
        ) values(
          :siteName,
          :address,
          :city,
          (select id from county where name = :countyName and state = :state),
          :website,
          :facebook,
          (select id from site_type where name = :siteType),
          :hours,
          (select id from max_supply_load where name = :maxSupplyLoadName),
          :receivingNotes
         )
        """;

    try {
      long siteId =
          jdbi.withHandle(
              handle ->
                  handle
                      .createUpdate(insert)
                      .bind("siteName", siteData.getSiteName())
                      .bind("address", siteData.getStreetAddress())
                      .bind("city", siteData.getCity())
                      .bind("countyName", siteData.getCounty())
                      .bind("state", siteData.getState())
                      .bind("website", siteData.getWebsite())
                      .bind("facebook", siteData.getFacebook())
                      .bind("siteType", siteData.getSiteType().getText())
                      .bind("hours", siteData.getSiteHours())
                      .bind("maxSupplyLoadName", siteData.getMaxSupplyLoad())
                      .bind("receivingNotes", siteData.getReceivingNotes())
                      .executeAndReturnGeneratedKeys("id")
                      .mapTo(Long.class)
                      .one());

      String addToDimensionMatrix =
          String.format(
              """
              insert into site_distance_matrix(site1_id, site2_id)
              select id, %s from site where id != %s
              """,
              siteId, siteId);

      jdbi.withHandle(handle -> handle.createUpdate(addToDimensionMatrix).execute());

      linkPrimaryContact(jdbi, siteId, siteData.getContactName(), siteData.getContactNumber());

      return siteId;
    } catch (UnableToExecuteStatementException e) {
      if (e.getMessage()
          .contains("duplicate key value violates unique constraint \"site_name_key\"")) {
        throw new DuplicateSiteException(
            "Duplicate, site name already exists: " + siteData.getSiteName());
      } else if (e.getMessage().contains("null value in column \"county_id\"")) {
        throw new IllegalArgumentException("Invalid county specified: " + siteData.getCounty());
      } else {
        throw e;
      }
    }
  }

  /**
   * Links the new site's primary and original contact to the user for the given phone: ensures that
   * user exists with SITE_MANAGER access, records their name, points both site contact foreign keys
   * at them, and adds the site membership. The original-contact link is a permanent record of the
   * creator and is never changed afterward. A blank phone leaves the site with no contact.
   */
  private static void linkPrimaryContact(
      Jdbi jdbi, long siteId, String contactName, String contactNumber) {
    if (contactNumber == null || contactNumber.isBlank()) {
      return;
    }
    // grantSiteManager only creates a user for a valid phone; an invalid one leaves the site with
    // no contact rather than crashing.
    UserRoleService.grantSiteManager(jdbi, contactNumber);
    Optional<Long> userIdOpt =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery("select id from wss_user where phone = :phone")
                    .bind("phone", PhoneNumberUtil.toCanonical(contactNumber))
                    .mapTo(Long.class)
                    .findOne());
    if (userIdOpt.isEmpty()) {
      return;
    }
    long userId = userIdOpt.get();
    String trimmedName = contactName == null || contactName.isBlank() ? null : contactName.trim();
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate("update wss_user set name = coalesce(:name, name) where id = :id")
                .bind("name", trimmedName)
                .bind("id", userId)
                .execute());
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    update site
                      set primary_contact_wss_user_id = :userId,
                          og_contact_wss_user_id = :userId
                      where id = :siteId
                    """)
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
  }
}
