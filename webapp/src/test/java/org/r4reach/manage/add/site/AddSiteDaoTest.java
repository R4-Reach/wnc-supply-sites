package org.r4reach.manage.add.site;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.data.SiteType;
import org.r4reach.supplies.site.details.SiteDetailDao;
import org.r4reach.supplies.site.details.SiteDetailDao.SiteDetailData;

public class AddSiteDaoTest {

  @BeforeAll
  static void setUp() {
    TestConfiguration.setupDatabase();
  }

  private static final AddSiteData siteData1 =
      AddSiteData.builder()
          .siteName("site data 1")
          .contactNumber("555-555-5555")
          .website("www.website.com")
          .siteType(SiteType.DISTRIBUTION_CENTER)
          .streetAddress("data 1 address")
          .city("data 1 city")
          .county("Ashe")
          .state("NC")
          .maxSupplyLoad("Car")
          .contactNumber("555-555-5555")
          .build();

  private static final AddSiteData siteData2 =
      AddSiteData.builder()
          .siteName("site data 2")
          .siteType(SiteType.SUPPLY_HUB)
          .streetAddress("data 2 address")
          .city("data 2 city")
          .county("Ashe")
          .state("NC")
          .maxSupplyLoad("Car")
          .contactNumber("5550000000")
          .build();

  @Test
  void addSite() {
    AddSiteDao.addSite(TestConfiguration.jdbiTest, siteData1);

    long id = TestConfiguration.getSiteId(siteData1.getSiteName());
    SiteDetailData details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, id);

    assertThat(details.getContactNumber()).isEqualTo("15555555555");
    assertThat(details.getWebsite()).isEqualTo(siteData1.getWebsite());
    assertThat(details.getSiteType()).isEqualTo(siteData1.getSiteType().getText());
    assertThat(details.getSiteName()).isEqualTo(siteData1.getSiteName());
    assertThat(details.getAddress()).isEqualTo(siteData1.getStreetAddress());
    assertThat(details.getCity()).isEqualTo(siteData1.getCity());
    assertThat(details.getCounty()).isEqualTo(siteData1.getCounty());
  }

  @Test
  void addSiteWithOnlyRequiredFields() {
    AddSiteDao.addSite(TestConfiguration.jdbiTest, siteData2);

    long id = TestConfiguration.getSiteId(siteData2.getSiteName());
    SiteDetailData details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, id);

    assertThat(details.getContactNumber()).isEqualTo("15550000000");
    assertThat(details.getWebsite()).isNull();
    assertThat(details.getSiteType()).isEqualTo(siteData2.getSiteType().getText());
    assertThat(details.getSiteName()).isEqualTo(siteData2.getSiteName());
    assertThat(details.getAddress()).isEqualTo(siteData2.getStreetAddress());
    assertThat(details.getCity()).isEqualTo(siteData2.getCity());
    assertThat(details.getCounty()).isEqualTo(siteData2.getCounty());
  }

  @Nested
  class InsertFailCases {

    @Test
    void addDuplicateShouldFail() {
      AddSiteDao.addSite(
          TestConfiguration.jdbiTest, siteData2.toBuilder().siteName("duplicate").build());

      assertThrows(
          AddSiteDao.DuplicateSiteException.class,
          () ->
              AddSiteDao.addSite(
                  TestConfiguration.jdbiTest, siteData2.toBuilder().siteName("duplicate").build()));
    }

    @Test
    void addSiteWithBadCountyShouldFail() {
      assertThrows(
          IllegalArgumentException.class,
          () ->
              AddSiteDao.addSite(
                  TestConfiguration.jdbiTest,
                  siteData2.toBuilder().county("invalid-county").build()));
    }
  }

  @Nested
  class AddToDistanceMatrix {

    /**
     * When we add a site, we should add rows to 'site_distance_matrix' equal to the number of
     * existing sites. For example, if there are 3 sites, then the 4th site should add 3 more rows
     * to the distance matrix.
     */
    @Test
    void addingSite() {
      int numberOfSites = countNumberOfSites();
      int previousNumberOfMatrixRows = countSiteDistanceRows();

      AddSiteDao.addSite(
          TestConfiguration.jdbiTest,
          siteData2.toBuilder().siteName(UUID.randomUUID().toString()).build());

      int numberOfMatrixRows = countSiteDistanceRows();
      assertThat(numberOfMatrixRows).isEqualTo(previousNumberOfMatrixRows + numberOfSites);
    }

    private int countNumberOfSites() {
      String count = "select count(*) from site";
      return TestConfiguration.jdbiTest.withHandle(
          handle -> handle.createQuery(count).mapTo(Integer.class).one());
    }

    private int countSiteDistanceRows() {
      String count = "select count(*) from site_distance_matrix";
      return TestConfiguration.jdbiTest.withHandle(
          handle -> handle.createQuery(count).mapTo(Integer.class).one());
    }
  }
}
