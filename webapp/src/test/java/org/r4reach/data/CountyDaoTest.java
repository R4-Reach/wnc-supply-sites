package org.r4reach.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.supplies.filters.AuthenticatedMode;

class CountyDaoTest {

  @BeforeAll
  static void setup() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void fetchFullCountyListByState() {
    assertThat(CountyDao.fetchFullCountyListByState(TestConfiguration.jdbiTest, "NC"))
        .contains("dummy", "Watauga");
    assertThat(CountyDao.fetchFullCountyListByState(TestConfiguration.jdbiTest, "TN"))
        .doesNotContain("Watauga");

    assertThat(CountyDao.fetchFullCountyListByState(TestConfiguration.jdbiTest, "TN"))
        .contains("Sevier");
    assertThat(CountyDao.fetchFullCountyListByState(TestConfiguration.jdbiTest, "VA"))
        .contains("Halifax");
  }

  /**
   * Active counties are just the ones that have an active site. The list of active counties should
   * be shorter than the list of all counties.
   */
  @Test
  void fetchActiveCountyList() {
    int fullCountyCount =
        CountyDao.fetchFullCountyListByState(TestConfiguration.jdbiTest, "NC").size();
    List<String> activeCounties =
        CountyDao.fetchActiveCountyList(
            TestConfiguration.jdbiTest, AuthenticatedMode.NOT_AUTHENTICATED, List.of("NC"));

    assertThat(activeCounties.size()).isGreaterThan(0);
    assertThat(fullCountyCount).isGreaterThan(activeCounties.size());

    assertThat(activeCounties).contains("Watauga, NC");
    assertThat(activeCounties).doesNotContain("dummy, NC", "siteCA");
  }

  @Test
  void fetchFullCountyListing() {
    Map<String, List<String>> fetchFullCountyListing =
        CountyDao.fetchFullCountyListing(TestConfiguration.jdbiTest, List.of("NC"));
    assertThat(fetchFullCountyListing.size()).isGreaterThan(0);
    assertThat(fetchFullCountyListing.get("NC")).contains("Watauga");

    fetchFullCountyListing =
        CountyDao.fetchFullCountyListing(TestConfiguration.jdbiTest, List.of("NC", "TN"));
    assertThat(fetchFullCountyListing.get("NC")).contains("Watauga");
    assertThat(fetchFullCountyListing.get("TN")).contains("Sevier");

    fetchFullCountyListing =
        CountyDao.fetchFullCountyListing(TestConfiguration.jdbiTest, List.of("NC", "TN", "VA"));
    assertThat(fetchFullCountyListing.get("NC")).contains("Watauga");
    assertThat(fetchFullCountyListing.get("TN")).contains("Sevier");
    assertThat(fetchFullCountyListing.get("VA")).contains("Halifax");

    fetchFullCountyListing =
        CountyDao.fetchFullCountyListing(TestConfiguration.jdbiTest, List.of("CA"));
    assertThat(fetchFullCountyListing.get("CA")).contains("Los Angeles");
  }
}
