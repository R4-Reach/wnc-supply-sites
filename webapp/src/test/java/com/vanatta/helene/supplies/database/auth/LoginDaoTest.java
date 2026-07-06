package com.vanatta.helene.supplies.database.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.setup.password.SetupPasswordHelper;
import org.junit.jupiter.api.Test;

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
}
