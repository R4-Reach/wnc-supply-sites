package com.vanatta.helene.supplies.database.supplies.filters;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.CookieAuthenticator;
import com.vanatta.helene.supplies.database.manage.ManageSiteDao;
import java.util.List;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FilterDataControllerTest {

  private static final Jdbi jdbiTest = TestConfiguration.jdbiTest;
  private final FilterDataController filterDataController =
      new FilterDataController(jdbiTest, new CookieAuthenticator(TestConfiguration.jdbiTest));

  @BeforeAll
  static void setup() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void counties() {
    var response =
        filterDataController.getFilterData(AuthenticatedMode.NOT_AUTHENTICATED, List.of("NC"));

    // spot check we return a few expected values, counties of sites that are known to be active
    assertThat(response.getCounties()).contains("Buncombe, NC", "Watauga, NC");

    response =
        filterDataController.getFilterData(AuthenticatedMode.NOT_AUTHENTICATED, List.of("CA"));

    // spot check we return a few expected values, counties of sites that are known to be active
    assertThat(response.getCounties()).contains("Los Angeles, CA");
  }

  @Test
  void items() {
    var response =
        filterDataController.getFilterData(AuthenticatedMode.NOT_AUTHENTICATED, List.of("NC"));

    // spot check we return a few expected values
    assertThat(response.getItems()).contains("water", "new clothes", "gloves");
  }

  @Test
  void sites() {
    var response =
        filterDataController.getFilterData(AuthenticatedMode.NOT_AUTHENTICATED, List.of("NC"));
    // site3 is not active
    assertThat(response.getSites()).doesNotContain("site3");
    // all active sites should be returned
    assertThat(response.getSites()).contains("site1", "site4", "site5", "site6");

    response =
        filterDataController.getFilterData(AuthenticatedMode.NOT_AUTHENTICATED, List.of("CA"));
    assertThat(response.getSites()).doesNotContain("site1", "site2", "site3");
    assertThat(response.getSites()).contains("siteCA");
  }

  /** Validate that only authenticated users can see site listing for private sites. */
  @Test
  void siteListAndAuthentication() {
    String siteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(siteName);
    ManageSiteDao.updateSitePubliclyVisible(TestConfiguration.jdbiTest, siteId, false);

    var response =
        filterDataController.getFilterData(AuthenticatedMode.AUTHENTICATED, List.of("NC"));
    assertThat(response.getSites()).contains(siteName);

    //  site should drop off the filter list if user is not authenticated
    response =
        filterDataController.getFilterData(AuthenticatedMode.NOT_AUTHENTICATED, List.of("NC"));
    assertThat(response.getSites()).doesNotContain(siteName);
  }

  /**
   * Only authenticated users can see all sites. If a private-only site is the only one in a county,
   * then unauthenticcated users should not see that county.
   */
  @Test
  void countyListAndAuthentication() {
    // create a unique county for our site that will be private
    String siteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(siteName);
    ManageSiteDao.updateSitePubliclyVisible(TestConfiguration.jdbiTest, siteId, false);
    TestConfiguration.addCounty("unique", "AA");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.COUNTY, "unique,AA");

    var response =
        filterDataController.getFilterData(AuthenticatedMode.AUTHENTICATED, List.of("AA"));
    assertThat(response.getCounties()).contains("unique, AA");

    //  site should drop off the filter list if user is not authenticated
    response =
        filterDataController.getFilterData(AuthenticatedMode.NOT_AUTHENTICATED, List.of("NC"));
    assertThat(response.getCounties()).doesNotContain("unique");
  }
}
