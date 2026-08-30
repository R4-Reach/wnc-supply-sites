package org.r4reach.jobs.distance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.supplies.site.details.SiteDetailDao;

class DistanceDaoTest {

  @BeforeAll
  static void setup() {
    DistanceTestHelper.setup();
  }

  @Test
  void fetchUncalculated() {
    var sitePairs = DistanceDao.fetchUncalculatedPairs(TestConfiguration.jdbiTest);

    assertThat(sitePairs).hasSize(1);
    long site1Id = TestConfiguration.getSiteId("site1");
    assertThat(sitePairs.getFirst().getSiteId1()).isEqualTo(site1Id);
    var site1Detail = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, site1Id);
    assertThat(sitePairs.getFirst().getAddress1()).isEqualTo(site1Detail.getAddress());
    assertThat(sitePairs.getFirst().getCity1()).isEqualTo(site1Detail.getCity());
    assertThat(sitePairs.getFirst().getState1()).isEqualTo(site1Detail.getState());

    long site4Id = TestConfiguration.getSiteId("site4");
    assertThat(sitePairs.getFirst().getSiteId2()).isEqualTo(site4Id);
    var site3Detail = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, site4Id);
    assertThat(sitePairs.getFirst().getAddress2()).isEqualTo(site3Detail.getAddress());
    assertThat(sitePairs.getFirst().getCity2()).isEqualTo(site3Detail.getCity());
    assertThat(sitePairs.getFirst().getState2()).isEqualTo(site3Detail.getState());
  }
}
