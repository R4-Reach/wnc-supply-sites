package com.vanatta.helene.supplies.database.supplies.site.details;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SiteDetailDaoTest {

  static long site1Id;

  @BeforeAll
  static void setUp() {
    TestConfiguration.setupDatabase();
    site1Id = TestConfiguration.getSiteId("site1");
  }

  @Test
  @SuppressWarnings("DataFlowIssue")
  void lookupIdByWssId() {
    long result =
        SiteDetailDao.lookupSiteIdByWssId(
            TestConfiguration.jdbiTest, TestConfiguration.SITE1_WSS_ID);
    assertThat(result).isEqualTo(site1Id);
  }

  @Test
  void lookupSite() {
    long idToLookup =
        TestConfiguration.jdbiTest.withHandle(
            handle ->
                handle
                    .createQuery("select id from site where name = 'site1'")
                    .mapTo(Long.class)
                    .one());

    var result = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, idToLookup);

    assertThat(result.getSiteName()).isEqualTo("site1");
    assertThat(result.getContactName()).isEqualTo("contact me");
    assertThat(result.getContactNumber()).isEqualTo("18280000001");
    assertThat(result.getAddress()).isEqualTo("address1");
    assertThat(result.getCity()).isEqualTo("city1");
    assertThat(result.getState()).isEqualTo("NC");
    assertThat(result.getCounty()).isEqualTo("Watauga");
    assertThat(result.getWebsite()).isEqualTo("site1website");
    assertThat(result.getSiteType()).isEqualTo("Distribution Center");
    assertThat(result.acceptingDonations).isTrue();
    assertThat(result.isActive()).isTrue();
    assertThat(result.getHours()).isEqualTo("our hours");
    assertThat(result.getFacebook()).isEqualTo("fb url");
    assertThat(result.isPubliclyVisible()).isTrue();
    assertThat(result.isDistributingSupplies()).isTrue();
  }

  @Test
  void nonExistentSiteIsNull() {
    assertThat(SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, -1)).isNull();
  }
}
