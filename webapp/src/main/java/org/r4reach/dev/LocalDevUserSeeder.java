package org.r4reach.dev;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.UserRole;
import org.r4reach.util.HashingUtil;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a known admin login on startup so that a developer running the full stack locally can sign
 * in with a known phone number and password.
 */
@Slf4j
@Component
@Profile("local")
public class LocalDevUserSeeder implements ApplicationRunner {

  private static final String PHONE = "11111111111";
  private static final String PASSWORD = "wncstrong";

  /**
   * Every grantable role, taken straight from {@link UserRole} so that a newly added role is seeded
   * automatically without touching this class. Excludes {@link UserRole#AUTHORIZED}, the implicit
   * role that is never stored in wss_user_roles. This makes the local admin a full admin.
   */
  private static final List<String> ROLES =
      UserRole.assignableRoles().stream().map(Enum::name).toList();

  private final Jdbi jdbi;

  LocalDevUserSeeder(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  @Override
  public void run(ApplicationArguments args) {
    jdbi.useTransaction(
        handle -> {
          // insert a seed admin user
          handle
              .createUpdate(
                  "insert into wss_user(phone) values(:phone)"
                      + " on conflict(phone) do update set removed = false")
              .bind("phone", PHONE)
              .execute();
          handle
              .createUpdate("update wss_user set password_bcrypt = :hash where phone = :phone")
              .bind("hash", HashingUtil.bcrypt(PASSWORD))
              .bind("phone", PHONE)
              .execute();
          for (String role : ROLES) {
            handle
                .createUpdate(
                    """
                    insert into wss_user_roles(wss_user_id, wss_user_role_id)
                    values(
                      (select id from wss_user where phone = :phone),
                      (select id from wss_user_role where name = :role)
                    )
                    on conflict(wss_user_id, wss_user_role_id) do nothing
                    """)
                .bind("phone", PHONE)
                .bind("role", role)
                .execute();
          }
        });
    log.warn(
        "LOCAL PROFILE: seeded admin login -> phone={} / password={} (roles {})",
        PHONE,
        PASSWORD,
        ROLES);
  }
}
