package com.vanatta.helene.supplies.database.auth.user;

import com.vanatta.helene.supplies.database.auth.UserRole;
import com.vanatta.helene.supplies.database.util.PhoneNumberUtil;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;

/**
 * Keeps wss_user / wss_user_roles as the single source of truth for user roles. Site-management
 * flows call {@link #grantSiteManager} when a phone becomes a site contact so that person keeps
 * portal access without a manual whitelist step.
 *
 * <p>Grants only: nothing here revokes a role. Removing someone as a site contact does not drop
 * their role; an admin does that in the user-management UI.
 */
@Slf4j
public class UserRoleService {

  public static void grantSiteManager(Jdbi jdbi, String phone) {
    grantRole(jdbi, phone, UserRole.SITE_MANAGER);
  }

  /**
   * Ensures a (non-removed) wss_user exists for the phone and holds the given role. No-op for a
   * phone that is not a valid 10-digit number, matching the login/whitelist rules.
   */
  public static void grantRole(Jdbi jdbi, String phoneInput, UserRole role) {
    if (!PhoneNumberUtil.isValid(phoneInput)) {
      return;
    }
    String phone = PhoneNumberUtil.removeNonNumeric(phoneInput);

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    "insert into wss_user(phone) values (:phone) on conflict(phone) do nothing")
                .bind("phone", phone)
                .execute());

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into wss_user_roles(wss_user_id, wss_user_role_id)
                    values(
                      (select id from wss_user where phone = :phone),
                      (select id from wss_user_role where name = :role))
                    on conflict (wss_user_id, wss_user_role_id) do nothing
                    """)
                .bind("phone", phone)
                .bind("role", role.name())
                .execute());
  }
}
