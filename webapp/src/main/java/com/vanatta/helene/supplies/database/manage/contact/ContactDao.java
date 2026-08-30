package com.vanatta.helene.supplies.database.manage.contact;

import com.vanatta.helene.supplies.database.auth.user.UserRoleService;
import com.vanatta.helene.supplies.database.util.PhoneNumberUtil;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Jdbi;

public class ContactDao {

  /**
   * Adds a site manager: ensures a wss_user exists for the phone (with portal access), records the
   * name on that user, and links them to the site via wss_user_sites. The returned id is the
   * wss_user_sites row id, which identifies the manager for later update/remove. Throws on a
   * duplicate (the user already manages this site).
   */
  public static long addAdditionalSiteManager(Jdbi jdbi, long siteId, String name, String phone) {
    // grantSiteManager upserts the wss_user and the SITE_MANAGER role.
    UserRoleService.grantSiteManager(jdbi, phone);
    long userId = userIdForPhone(jdbi, phone);
    setUserName(jdbi, userId, name);

    return jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    "insert into wss_user_sites(wss_user_id, site_id) values(:userId, :siteId)")
                .bind("userId", userId)
                .bind("siteId", siteId)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
  }

  static void updateAdditionalSiteManager(Jdbi jdbi, long siteId, SiteManager siteManager) {
    UserRoleService.grantSiteManager(jdbi, siteManager.getPhone());
    long userId = userIdForPhone(jdbi, siteManager.getPhone());
    setUserName(jdbi, userId, siteManager.getName());

    // Repoint the membership row at the (possibly new) user for the edited phone.
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    update wss_user_sites
                      set wss_user_id = :userId
                      where id = :id and site_id = :siteId
                    """)
                .bind("userId", userId)
                .bind("siteId", siteId)
                .bind("id", siteManager.getId())
                .execute());
  }

  static List<SiteManager> getManagers(Jdbi jdbi, long siteId) {
    String select =
        """
            select ws.id, u.name, u.phone
            from wss_user_sites ws
            join wss_user u on u.id = ws.wss_user_id
            join site s on s.id = ws.site_id
            where ws.site_id = :siteId
              and ws.wss_user_id is distinct from s.primary_contact_wss_user_id
              and ws.wss_user_id is distinct from s.og_contact_wss_user_id
            order by u.name
            """;

    return jdbi.withHandle(
        handle ->
            handle.createQuery(select).bind("siteId", siteId).mapToBean(SiteManager.class).list());
  }

  /**
   * Removes an additional manager (a wss_user_sites row) from the site. The primary and original
   * contacts cannot be removed this way -- they are managed through the site's own contact fields.
   */
  public static void removeAdditionalSiteManager(Jdbi jdbi, long siteId, Long managerId) {
    String delete =
        """
      delete from wss_user_sites ws
      using site s
      where s.id = ws.site_id
        and ws.site_id = :siteId
        and ws.id = :managerId
        and ws.wss_user_id is distinct from s.primary_contact_wss_user_id
        and ws.wss_user_id is distinct from s.og_contact_wss_user_id
      """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(delete)
                .bind("siteId", siteId)
                .bind("managerId", managerId)
                .execute());
  }

  private static long userIdForPhone(Jdbi jdbi, String phone) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select id from wss_user where phone = :phone")
                .bind("phone", PhoneNumberUtil.toCanonical(phone))
                .mapTo(Long.class)
                .one());
  }

  private static void setUserName(Jdbi jdbi, long userId, String name) {
    String trimmed = name == null || name.isBlank() ? null : name.trim();
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate("update wss_user set name = coalesce(:name, name) where id = :id")
                .bind("name", trimmed)
                .bind("id", userId)
                .execute());
  }

  @Builder(toBuilder = true)
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SiteManager {
    long id;
    String name;
    String phone;
  }
}
