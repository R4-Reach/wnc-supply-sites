package com.vanatta.helene.supplies.database.manage.contact;

import com.vanatta.helene.supplies.database.auth.user.UserRoleService;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Jdbi;

public class ContactDao {

  public static long addAdditionalSiteManager(Jdbi jdbi, long siteId, String name, String phone) {

    String insert =
        """
    insert into additional_site_manager
        (site_id, name, phone)
    values(
      :siteId,
      :name,
      :phone
    )
    """;

    long id =
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(insert)
                    .bind("siteId", siteId)
                    .bind("name", name)
                    .bind("phone", phone)
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one());
    // An additional site manager needs portal access under their phone number.
    UserRoleService.grantSiteManager(jdbi, phone);
    return id;
  }

  static void updateAdditionalSiteManager(Jdbi jdbi, long siteId, SiteManager siteManager) {
    String update =
        """
    update additional_site_manager
      set name = :name,
        phone = :phone
      where site_id = :siteId and id = :id
    """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(update)
                .bind("name", siteManager.getName())
                .bind("phone", siteManager.getPhone())
                .bind("siteId", siteId)
                .bind("id", siteManager.getId())
                .execute());
    UserRoleService.grantSiteManager(jdbi, siteManager.getPhone());
  }

  static List<SiteManager> getManagers(Jdbi jdbi, long siteId) {
    String select =
        """
            select id, name, phone
            from additional_site_manager
            where site_id = :siteId
            order by name;
            """;

    return jdbi.withHandle(
        handle ->
            handle.createQuery(select).bind("siteId", siteId).mapToBean(SiteManager.class).list());
  }

  public static void removeAdditionalSiteManager(Jdbi jdbi, long siteId, Long managerId) {
    String delete =
        """
      delete from additional_site_manager where site_id = :siteId and id = :managerId
      """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(delete)
                .bind("siteId", siteId)
                .bind("managerId", managerId)
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
