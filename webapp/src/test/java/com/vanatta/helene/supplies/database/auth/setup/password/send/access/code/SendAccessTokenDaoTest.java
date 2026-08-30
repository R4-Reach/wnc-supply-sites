package com.vanatta.helene.supplies.database.auth.setup.password.send.access.code;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.UserRole;
import com.vanatta.helene.supplies.database.auth.setup.password.SetupPasswordHelper;
import com.vanatta.helene.supplies.database.auth.user.UserRoleService;
import com.vanatta.helene.supplies.database.manage.ManageSiteDao;
import com.vanatta.helene.supplies.database.manage.contact.ContactDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SendAccessTokenDaoTest {
  String number = "123___1234";

  @BeforeEach
  void cleanDb() {
    SetupPasswordHelper.setup();
  }

  @Test
  void phoneNumberIsRegistered_caseNotRegistered() {
    boolean result = SendAccessTokenDao.isPhoneNumberRegistered(jdbiTest, number);

    assertThat(result).isFalse();
  }

  @Test
  void registeredPhoneNumber() {
    SetupPasswordHelper.withRegisteredNumber(number);

    boolean result = SendAccessTokenDao.isPhoneNumberRegistered(jdbiTest, number);

    assertThat(result).isTrue();
  }

  @Test
  void userAccountExistsAndCreate() {
    assertThat(SendAccessTokenDao.userAccountExists(jdbiTest, number)).isFalse();

    SendAccessTokenDao.createUser(jdbiTest, number);

    assertThat(SendAccessTokenDao.userAccountExists(jdbiTest, number)).isTrue();
  }

  /**
   * Phone numbers that are listed as the primary contact for a site should appear as registered.
   */
  @Test
  void phoneNumberIsRegistered_caseSiteManager() {
    String siteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(siteName);
    ManageSiteDao.updateSiteField(
        jdbiTest, siteId, ManageSiteDao.SiteField.CONTACT_NUMBER, "1234560000");

    boolean result = SendAccessTokenDao.isPhoneNumberRegistered(jdbiTest, "1234560000");

    assertThat(result).isTrue();
  }

  @Test
  void phoneNumberIsRegistered_caseAdditionalContact() {
    String siteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(siteName);
    ContactDao.addAdditionalSiteManager(jdbiTest, siteId, "name", "432.222.2222");

    boolean result = SendAccessTokenDao.isPhoneNumberRegistered(jdbiTest, "432 222 2222");

    assertThat(result).isTrue();
  }

  /** A user granted the driver role (e.g. through the admin UI) is registered for login. */
  @Test
  void phoneNumberIsRegistered_caseDriver() {
    UserRoleService.grantRole(jdbiTest, "(333) 999 6666", UserRole.DRIVER);

    boolean result = SendAccessTokenDao.isPhoneNumberRegistered(jdbiTest, "333 999 6666");

    assertThat(result).isTrue();
  }

  @Test
  void insertAccessCode() {
    SetupPasswordHelper.withRegisteredNumber(number);
    String accessCode = "123";
    String csrfToken = "xyz";

    boolean tokenExists = SetupPasswordHelper.accessTokenExists(accessCode, csrfToken);
    assertThat(tokenExists).isFalse();

    SendAccessTokenDao.insertSmsPasscode(
        jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .accessCode(accessCode)
            .csrfToken(csrfToken)
            .build());

    tokenExists = SetupPasswordHelper.accessTokenExists(accessCode, csrfToken);
    assertThat(tokenExists).isTrue();
  }
}
