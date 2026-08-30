package com.vanatta.helene.supplies.database.auth;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.setup.password.SetupPasswordHelper;
import com.vanatta.helene.supplies.database.auth.user.UserRoleService;
import com.vanatta.helene.supplies.database.auth.user.whitelist.UserWhiteListWebhook;
import com.vanatta.helene.supplies.database.manage.ManageSiteDao;
import com.vanatta.helene.supplies.database.manage.contact.ContactDao;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Various tests to validate how we can authenticate users. */
class LoggedInAdviceTest {

  String number = "3334442244";
  String token;

  @BeforeEach
  void setup() {
    SetupPasswordHelper.setup();
    TestConfiguration.setupDatabase();
    SetupPasswordHelper.withRegisteredNumber(number);
    token = LoginDao.generateAuthToken(jdbiTest, number);
  }

  @Test
  void noAuth() {
    assertThat(LoggedInAdvice.computeUserRoles(jdbiTest, "bad token")).isEmpty();
  }

  @Test
  void defaultUserRole() {
    assertThat(LoggedInAdvice.computeUserRoles(jdbiTest, token))
        .containsExactly(UserRole.AUTHORIZED);
  }

  /** Roles are read from wss_user_roles; the driver role is one of them. */
  @Test
  void driverUserRole() {
    UserRoleService.grantRole(jdbiTest, number, UserRole.DRIVER);

    assertThat(LoggedInAdvice.computeUserRoles(jdbiTest, token))
        .containsExactly(UserRole.AUTHORIZED, UserRole.DRIVER);
  }

  /** Site managers can get their role by being a primary contact at a site. */
  @Test
  void siteManagerRole_primaryContact() {
    String siteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(siteName);
    ManageSiteDao.updateSiteField(jdbiTest, siteId, ManageSiteDao.SiteField.CONTACT_NUMBER, number);
    assertThat(LoggedInAdvice.computeUserRoles(jdbiTest, token))
        .containsExactly(UserRole.AUTHORIZED, UserRole.SITE_MANAGER);
  }

  /** Site manager role is also granted if a user is an additional contact */
  @Test
  void siteManagerRole_secondaryContact() {
    String siteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(siteName);
    ContactDao.addAdditionalSiteManager(jdbiTest, siteId, "name", number);
    assertThat(LoggedInAdvice.computeUserRoles(jdbiTest, token))
        .containsExactly(UserRole.AUTHORIZED, UserRole.SITE_MANAGER);
  }

  /** Dispatcher role is only granted through the wss_roles table and white listing. */
  @Test
  void dispatcherRole() {
    UserWhiteListWebhook.updateUserAndRoles(
        jdbiTest,
        UserWhiteListWebhook.UserWhiteListRequest.builder()
            .roles(List.of(UserRole.DISPATCHER.name()))
            .phoneNumber(number)
            .build());
    assertThat(LoggedInAdvice.computeUserRoles(jdbiTest, token))
        .containsExactly(UserRole.AUTHORIZED, UserRole.DISPATCHER);
  }

  /** Data admin role is only granted through the wss_roles table and white listing. */
  @Test
  void dataAdminRole() {
    UserWhiteListWebhook.updateUserAndRoles(
        jdbiTest,
        UserWhiteListWebhook.UserWhiteListRequest.builder()
            .roles(List.of(UserRole.DATA_ADMIN.name()))
            .phoneNumber(number)
            .build());
    assertThat(LoggedInAdvice.computeUserRoles(jdbiTest, token))
        .containsExactly(UserRole.AUTHORIZED, UserRole.DATA_ADMIN);
  }

  /** User-admin role is granted through the wss_user_roles table. */
  @Test
  void userAdminRole() {
    UserWhiteListWebhook.updateUserAndRoles(
        jdbiTest,
        UserWhiteListWebhook.UserWhiteListRequest.builder()
            .roles(List.of(UserRole.USER_ADMIN.name()))
            .phoneNumber(number)
            .build());
    assertThat(LoggedInAdvice.computeUserRoles(jdbiTest, token))
        .containsExactly(UserRole.AUTHORIZED, UserRole.USER_ADMIN);
  }

  /** By default, no sites for a user. */
  @Test
  void noSites() {
    assertThat(LoggedInAdvice.computeUserSites(jdbiTest, token, List.of(UserRole.AUTHORIZED)))
        .isEmpty();
  }

  /** Add a user as a primary manager to a few sites and validate we gain access. */
  @Test
  void sites_primaryManager() {
    String siteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(siteName);
    ManageSiteDao.updateSiteField(jdbiTest, siteId, ManageSiteDao.SiteField.CONTACT_NUMBER, number);

    assertThat(LoggedInAdvice.computeUserSites(jdbiTest, token, List.of(UserRole.SITE_MANAGER)))
        .containsExactly(siteId);

    long anotherSiteId = TestConfiguration.getSiteId(TestConfiguration.addSite());
    ManageSiteDao.updateSiteField(
        jdbiTest, anotherSiteId, ManageSiteDao.SiteField.CONTACT_NUMBER, number);
    assertThat(LoggedInAdvice.computeUserSites(jdbiTest, token, List.of(UserRole.SITE_MANAGER)))
        .contains(siteId, anotherSiteId);
  }

  /**
   * Add a user as a secondary manager to a few sites and validate we gain access to those sites.
   */
  @Test
  void sites_secondaryManager() {
    long siteId = TestConfiguration.getSiteId(TestConfiguration.addSite());
    ContactDao.addAdditionalSiteManager(jdbiTest, siteId, "name", number);
    assertThat(LoggedInAdvice.computeUserSites(jdbiTest, token, List.of(UserRole.SITE_MANAGER)))
        .containsExactly(siteId);

    long anotherSiteId = TestConfiguration.getSiteId(TestConfiguration.addSite());
    ContactDao.addAdditionalSiteManager(jdbiTest, anotherSiteId, "name", number);
    assertThat(LoggedInAdvice.computeUserSites(jdbiTest, token, List.of(UserRole.SITE_MANAGER)))
        .contains(siteId, anotherSiteId);
  }

  /**
   * Add a user as a primary manager to one site, and as a secondary manager to another site.
   * Validate that they have access to both sites.
   */
  @Test
  void sites_mixtureOfPrimaryAndSecondaryManager() {
    // add user as primary
    long primarySiteId = TestConfiguration.getSiteId(TestConfiguration.addSite());
    ManageSiteDao.updateSiteField(
        jdbiTest, primarySiteId, ManageSiteDao.SiteField.CONTACT_NUMBER, number);
    // add user as secondary
    long secondarySiteId = TestConfiguration.getSiteId(TestConfiguration.addSite());
    ContactDao.addAdditionalSiteManager(jdbiTest, secondarySiteId, "name", number);

    assertThat(LoggedInAdvice.computeUserSites(jdbiTest, token, List.of(UserRole.SITE_MANAGER)))
        .contains(primarySiteId, secondarySiteId);
  }

  /**
   * Whitelist user as a dispatcher and validate they automatically get several sites without being
   * the manager.
   */
  @Test
  void dispatcherGetsAllSites() {
    // dispatchershould get lots of sites, more than just a couple.
    assertThat(LoggedInAdvice.computeUserSites(jdbiTest, token, List.of(UserRole.DISPATCHER)))
        .hasSizeGreaterThan(2);
  }

  /** Similar to dispatcher test, data admin get god-mode access as well. */
  @Test
  void dataAdminGetsAllSites() {
    // data admin should get lots of sites, more than just a couple.
    assertThat(LoggedInAdvice.computeUserSites(jdbiTest, token, List.of(UserRole.DATA_ADMIN)))
        .hasSizeGreaterThan(2);
  }
}
