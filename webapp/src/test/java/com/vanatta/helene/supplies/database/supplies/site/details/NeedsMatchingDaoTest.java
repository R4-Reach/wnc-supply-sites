package com.vanatta.helene.supplies.database.supplies.site.details;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.supplies.site.details.NeedsMatchingDao.NeedsMatchingDbResult;
import com.vanatta.helene.supplies.database.supplies.site.details.NeedsMatchingDao.NeedsMatchingResult.Item;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NeedsMatchingDaoTest {

  static long site1Id;

  @BeforeAll
  static void setUp() {
    TestConfiguration.setupDatabase();
    site1Id = TestConfiguration.getSiteId("site1");
  }

  /** Very quick test to make sure the needs-query works. */
  @Test
  void needsQueryRuns() {
    var result =
        NeedsMatchingDao.executeByInternalId(TestConfiguration.jdbiTest, site1Id, List.of("NC"));

    assertThat(result).isNotNull();

    var firstResult = result.getFirst();
    assertThat(firstResult.getSiteName()).isNotNull();
    assertThat(firstResult.getSiteAddress()).isNotNull();
    assertThat(firstResult.getCity()).isNotNull();
    assertThat(firstResult.getCounty()).isNotNull();
    assertThat(firstResult.getState()).isNotNull();
    assertThat(firstResult.getItems()).isNotEmpty();
    assertThat(firstResult.getItemCount()).isGreaterThan(0);
  }

  @Test
  void validateResultAggregation() {
    List<NeedsMatchingDbResult> dbResults =
        List.of(
            NeedsMatchingDbResult.builder()
                .siteId(10L) //
                .siteName("site-10")
                .siteAddress("address-10")
                .city("city")
                .county("county")
                .state("state")
                .itemName("water")
                .urgency(ItemStatus.NEEDED.getText())
                .itemCount(3)
                .driveTimeSeconds(100)
                .distanceMiles(30.0)
                .build(),
            NeedsMatchingDbResult.builder()
                .siteId(10L) //
                .siteName("site-10")
                .siteAddress("address-10")
                .city("city")
                .county("county")
                .state("state")
                .itemName("coffee")
                .urgency(ItemStatus.URGENTLY_NEEDED.getText())
                .itemCount(3)
                .build(),
            NeedsMatchingDbResult.builder()
                .siteId(10L) //
                .siteName("site-10")
                .siteAddress("address-10")
                .city("city")
                .county("county")
                .state("state")
                .itemName("snacks")
                .urgency(ItemStatus.URGENTLY_NEEDED.getText())
                .itemCount(3)
                .build(),
            NeedsMatchingDbResult.builder()
                .siteId(1L) //
                .siteName("site")
                .siteAddress("address")
                .city("city")
                .county("county")
                .state("state")
                .itemName("water")
                .urgency(ItemStatus.URGENTLY_NEEDED.getText())
                .itemCount(2)
                .build(),
            NeedsMatchingDbResult.builder()
                .siteId(1L) //
                .siteName("site")
                .siteAddress("address")
                .city("city")
                .county("county")
                .state("state")
                .itemName("gloves")
                .urgency(ItemStatus.NEEDED.getText())
                .itemCount(2)
                .build(),
            NeedsMatchingDbResult.builder()
                .siteId(2L) //
                .siteName("site2")
                .siteAddress("address2")
                .city("city2")
                .county("county2")
                .state("state2")
                .itemName("water")
                .urgency(ItemStatus.NEEDED.getText())
                .itemCount(1)
                .build());

    var results = NeedsMatchingDao.aggregate(dbResults);
    assertThat(results)
        .contains(
            NeedsMatchingDao.NeedsMatchingResult.builder()
                .siteLink(SiteDetailController.buildSiteLink(10L))
                .siteName("site-10")
                .siteAddress("address-10")
                .city("city")
                .county("county")
                .state("state")
                .distanceMiles(30.0)
                .driveTimeSeconds(100)
                .items(
                    List.of(
                        Item.builder()
                            .name("water") //
                            .urgencyCssClass(ItemStatus.NEEDED.getCssClass())
                            .build(),
                        Item.builder()
                            .name("coffee")
                            .urgencyCssClass(ItemStatus.URGENTLY_NEEDED.getCssClass())
                            .build(),
                        Item.builder()
                            .name("snacks")
                            .urgencyCssClass(ItemStatus.URGENTLY_NEEDED.getCssClass())
                            .build()))
                .build(),
            NeedsMatchingDao.NeedsMatchingResult.builder()
                .siteLink(SiteDetailController.buildSiteLink(1L))
                .siteName("site")
                .siteAddress("address")
                .city("city")
                .county("county")
                .state("state")
                .items(
                    List.of(
                        Item.builder()
                            .name("water")
                            .urgencyCssClass(ItemStatus.URGENTLY_NEEDED.getCssClass())
                            .build(),
                        Item.builder()
                            .name("gloves") //
                            .urgencyCssClass(ItemStatus.NEEDED.getCssClass())
                            .build()))
                .build(),
            NeedsMatchingDao.NeedsMatchingResult.builder()
                .siteLink(SiteDetailController.buildSiteLink(2L))
                .siteName("site2")
                .siteAddress("address2")
                .city("city2")
                .county("county2")
                .state("state2")
                .items(
                    List.of(
                        Item.builder()
                            .name("water") //
                            .urgencyCssClass(ItemStatus.NEEDED.getCssClass())
                            .build()))
                .build());
  }
}
