package org.r4reach.auth.setup.password;

import org.r4reach.TestConfiguration;
import org.r4reach.auth.setup.password.send.access.code.SendAccessTokenDao;
import org.r4reach.util.HashingUtil;

public class SetupPasswordHelper {

  public static void setup() {
    String script =
        """
        delete from wss_user_auth_key;
        delete from wss_user_pass_change_history;
        delete from sms_passcode;
        delete from wss_user_roles;
        update site set primary_contact_wss_user_id = null, og_contact_wss_user_id = null;
        update delivery set dispatcher_wss_user_id = null, driver_wss_user_id = null;
        delete from wss_user_sites;
        delete from driver;
        delete from wss_user;
        """;
    TestConfiguration.jdbiTest.withHandle(handle -> handle.createScript(script).execute());
  }

  public static void withRegisteredNumber(String number) {
    SendAccessTokenDao.createUser(TestConfiguration.jdbiTest, number);
  }

  public static int countSendHistoryRecords() {
    String count = "select count(*) from sms_send_history";
    return TestConfiguration.jdbiTest.withHandle(
        handle -> handle.createQuery(count).mapTo(Integer.class).one());
  }

  public static boolean accessTokenExists(String accessCode, String csrf) {
    String query =
        """
        select 1
        from sms_passcode
        where
          passcode_sha256 = :expectedPasscode
          and confirmed = false
          and csrf_sha256 = :expectedCsrf
          and validation_key_sha256 is null
        """;

    return TestConfiguration.jdbiTest
        .withHandle(
            handle ->
                handle
                    .createQuery(query)
                    .bind("expectedPasscode", HashingUtil.sha256(accessCode))
                    .bind("expectedCsrf", HashingUtil.sha256(csrf))
                    .mapTo(Integer.class)
                    .findOne())
        .isPresent();
  }

  public static boolean accessTokenExists(String accessCode, String csrf, String validationToken) {
    String query =
        """
        select 1
        from sms_passcode
        where
          passcode_sha256 = :expectedPasscode
          and confirmed = true
          and csrf_sha256 = :expectedCsrf
          and validation_key_sha256 = :expectedValidationKey
        """;

    return TestConfiguration.jdbiTest
        .withHandle(
            handle ->
                handle
                    .createQuery(query)
                    .bind("expectedPasscode", HashingUtil.sha256(accessCode))
                    .bind("expectedCsrf", HashingUtil.sha256(csrf))
                    .bind("expectedValidationKey", HashingUtil.sha256(validationToken))
                    .mapTo(Integer.class)
                    .findOne())
        .isPresent();
  }
}
