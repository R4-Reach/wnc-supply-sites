package org.r4reach.auth.user.whitelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.r4reach.auth.UserRole;
import org.r4reach.util.PhoneNumberUtil;

class UserWhiteListWebhookTest {
  UserWhiteListWebhook webhook = new UserWhiteListWebhook(jdbiTest);

  String input =
      """
     {"name":"test","phoneNumber":"9995554444","roles":["SITE_MANAGER","DATA_ADMIN"]}
  """;

  @BeforeEach
  void setup() {
    String cleanup =
        """
        delete from wss_user_pass_change_history;
        delete from sms_passcode;
        delete from wss_user_roles;
        update site set primary_contact_wss_user_id = null, og_contact_wss_user_id = null;
        delete from wss_user_sites;
        delete from driver;
        delete from wss_user
        """;
    jdbiTest.withHandle(handle -> handle.execute(cleanup));
  }

  @Test
  void canParse() {
    var request = UserWhiteListWebhook.UserWhiteListRequest.parse(input);
    assertThat(request.getPhoneNumber()).isEqualTo("19995554444");
    assertThat(request.getRoles()).contains("SITE_MANAGER", "DATA_ADMIN");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // bad role name
        """
        {"name":"test","phoneNumber":"9995554444","roles":["DNE"]}
        """,
        // phone number too short
        """
      {"name":"test","phoneNumber":"999555444","roles":["SITE_MANAGER"]}
      """,
        // phone number is not numeric
        """
      {"name":"test","phoneNumber":"999555444X","roles":["SITE_MANAGER"]}
      """,
        // phone number too long
        """
      {"name":"test","phoneNumber":"99955544445","roles":["SITE_MANAGER"]}
      """
      })
  void badInputs(String badInput) {
    var response = webhook.whiteListUser(badInput);
    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  /**
   * When adding a user to white list - they should be added to 'wss_user' and have their roles
   * added.
   */
  @Test
  void whiteListUser() {
    var response = webhook.whiteListUser(input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    List<String> roles = lookupRoles("9995554444");
    assertThat(roles).contains("SITE_MANAGER", "DATA_ADMIN");
  }

  @ParameterizedTest
  @EnumSource(UserRole.class)
  void canAddEachOfTheRole(UserRole role) {
    String input =
        String.format(
            """
            {"name":"test","phoneNumber":"9995554444","roles":["%s"]}
            """,
            role.name());

    var response = webhook.whiteListUser(input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    List<String> roles = lookupRoles("9995554444");
    assertThat(roles).containsExactly(role.name());
  }

  /** Lookup the stored roles of a user, lookup by phone number. */
  private static List<String> lookupRoles(String phone) {
    String query =
        """
            select
              roleName.name
            from wss_user wu
            join wss_user_roles roles on wu.id = roles.wss_user_id
            join wss_user_role roleName on roleName.id = roles.wss_user_role_id
            where wu.phone = :phone;
            """;

    return jdbiTest.withHandle(
        handle ->
            handle
                .createQuery(query)
                .bind("phone", PhoneNumberUtil.toCanonical(phone))
                .mapTo(String.class)
                .list());
  }

  /**
   * If we have a phone number registered already, and get another request, it should be fine. This
   * can happen if a user record is deleted and then recreated.
   */
  @Test
  void duplicatePhoneNumber_overwrites() {
    String input =
        """
     {"name":"test","phoneNumber":"9995554444","roles":["SITE_MANAGER","DATA_ADMIN"]}
    """;

    // white list user with 2 roles
    webhook.whiteListUser(input);

    // white list user again, overwriting all roles.
    input =
        """
       {"name":"test","phoneNumber":"9995554444","roles":["DATA_ADMIN", "DRIVER"]}
    """;

    var response = webhook.whiteListUser(input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    List<String> roles = lookupRoles("9995554444");
    assertThat(roles).contains("DATA_ADMIN", "DRIVER");
  }

  @Test
  void updateRoles() {
    var input =
        """
       {"name":"test","phoneNumber":"9995554444","roles":["DATA_ADMIN", "DRIVER"]}
    """;
    var response = webhook.whiteListUser(input);

    var update =
        """
       {"name":"test","phoneNumber":"9995554444","roles":["SITE_MANAGER"]}
    """;
    response = webhook.updateUser(update);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    List<String> roles = lookupRoles("9995554444");
    assertThat(roles).containsExactly("SITE_MANAGER");
  }

  @Test
  void updateRoles_toRemoved() {
    var input =
        """
       {"name":"test","phoneNumber":"9995554444","roles":["DATA_ADMIN", "DRIVER"]}
    """;
    var response = webhook.whiteListUser(input);

    var update =
        """
       {"name":"test","phoneNumber":"9995554444","roles":["DATA_ADMIN", "DRIVER"], "removed": true}
    """;
    response = webhook.updateUser(update);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    List<String> roles = lookupRoles("9995554444");
    // removed flag is set, all roles should be removed
    assertThat(roles).isEmpty();
  }

  /** If user does not exist, then upsert and return 200 */
  @Test
  void updateUserThatDoesNotExist() {
    var input =
        """
       {"name":"test","phoneNumber":"9995554444","roles":["DATA_ADMIN", "DRIVER"]}
    """;
    // we are updating without having first done an insert, user DNE
    var response = webhook.updateUser(input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }
}
