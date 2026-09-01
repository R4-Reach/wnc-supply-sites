package org.r4reach.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.auth.setup.password.SetupPasswordHelper;
import org.r4reach.util.HashingUtil;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.PiiCrypto;

class LoginDaoTest {

  static class Helper {
    static long countLoginHistoryRows() {
      String query = "select count(*) from login_history";
      return TestConfiguration.jdbiTest.withHandle(
          handle -> handle.createQuery(query).mapTo(Long.class).one());
    }
  }

  @Test
  void loginHistory() {
    long preCount = Helper.countLoginHistoryRows();

    LoginDao.recordLoginSuccess(TestConfiguration.jdbiTest, "199-199-199-199");

    long postCount = Helper.countLoginHistoryRows();
    assertThat(postCount).isEqualTo(preCount + 1);

    LoginDao.recordLoginFailure(TestConfiguration.jdbiTest, "199-199-199-199");

    long postFailureCount = Helper.countLoginHistoryRows();
    assertThat(postFailureCount).isEqualTo(postCount + 1);
  }

  @Test
  void isLoggedIn_and_GenerateAuthToken() {
    SetupPasswordHelper.setup();
    String number = "1113332244";
    SetupPasswordHelper.withRegisteredNumber(number);

    String token = LoginDao.generateAuthToken(TestConfiguration.jdbiTest, number);
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, token)).isTrue();
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, "wrong value")).isFalse();
  }

  @Test
  void isLoggedIn_falseForRemovedUser() {
    SetupPasswordHelper.setup();
    String number = "1114442255";
    SetupPasswordHelper.withRegisteredNumber(number);
    String token = LoginDao.generateAuthToken(TestConfiguration.jdbiTest, number);
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, token)).isTrue();

    markRemoved(number);
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, token)).isFalse();
  }

  @Test
  void isLoggedIn_falseForExpiredToken() {
    SetupPasswordHelper.setup();
    String number = "1115552266";
    SetupPasswordHelper.withRegisteredNumber(number);
    String token = LoginDao.generateAuthToken(TestConfiguration.jdbiTest, number);

    expireToken(token);
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, token)).isFalse();
  }

  @Test
  void revokeToken_endsSession() {
    SetupPasswordHelper.setup();
    String number = "1116662277";
    SetupPasswordHelper.withRegisteredNumber(number);
    String token = LoginDao.generateAuthToken(TestConfiguration.jdbiTest, number);
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, token)).isTrue();

    LoginDao.revokeToken(TestConfiguration.jdbiTest, token);
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, token)).isFalse();
  }

  @Test
  void revokeAllTokensForUser_endsEverySession() {
    SetupPasswordHelper.setup();
    String number = "1117772288";
    SetupPasswordHelper.withRegisteredNumber(number);
    String tokenOne = LoginDao.generateAuthToken(TestConfiguration.jdbiTest, number);
    String tokenTwo = LoginDao.generateAuthToken(TestConfiguration.jdbiTest, number);

    LoginDao.revokeAllTokensForUser(TestConfiguration.jdbiTest, number);
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, tokenOne)).isFalse();
    assertThat(LoginDao.isLoggedIn(TestConfiguration.jdbiTest, tokenTwo)).isFalse();
  }

  @Test
  void isLoginThrottled_afterTooManyFailures() {
    String number = "1118882299";
    assertThat(LoginDao.isLoginThrottled(TestConfiguration.jdbiTest, number)).isFalse();

    for (int i = 0; i < LoginDao.MAX_FAILED_ATTEMPTS; i++) {
      LoginDao.recordLoginFailure(TestConfiguration.jdbiTest, number);
    }
    assertThat(LoginDao.isLoginThrottled(TestConfiguration.jdbiTest, number)).isTrue();
  }

  @Test
  void isLoginThrottled_ignoresSuccesses() {
    String number = "1119990011";
    for (int i = 0; i < LoginDao.MAX_FAILED_ATTEMPTS; i++) {
      LoginDao.recordLoginSuccess(TestConfiguration.jdbiTest, number);
    }
    assertThat(LoginDao.isLoginThrottled(TestConfiguration.jdbiTest, number)).isFalse();
  }

  /** Failures in different phone formats must all count toward one lockout (canonical matching). */
  @Test
  void isLoginThrottled_countsAcrossPhoneFormats() {
    for (int i = 0; i < 5; i++) {
      LoginDao.recordLoginFailure(TestConfiguration.jdbiTest, "222-333-4455");
    }
    for (int i = 0; i < 5; i++) {
      LoginDao.recordLoginFailure(TestConfiguration.jdbiTest, "12223334455");
    }
    assertThat(LoginDao.isLoginThrottled(TestConfiguration.jdbiTest, "2223334455")).isTrue();
  }

  private static void markRemoved(String number) {
    TestConfiguration.jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate("update wss_user set removed = true where phone_hmac = :hmac")
                .bind("hmac", PiiCrypto.blindIndex(PhoneNumberUtil.toCanonical(number)))
                .execute());
  }

  private static void expireToken(String token) {
    TestConfiguration.jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate(
                    "update wss_user_auth_key set expires_at = now() - interval '1 day'"
                        + " where token_sha256 = :token")
                .bind("token", HashingUtil.sha256(token))
                .execute());
  }
}
