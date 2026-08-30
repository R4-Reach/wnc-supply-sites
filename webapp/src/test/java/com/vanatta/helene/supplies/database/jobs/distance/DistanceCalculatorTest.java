package com.vanatta.helene.supplies.database.jobs.distance;

import static org.assertj.core.api.Assertions.*;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.data.GoogleDistanceApi;
import com.vanatta.helene.supplies.database.data.SiteAddress;
import com.vanatta.helene.supplies.database.siteconfig.SiteConfigService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DistanceCalculatorTest {

  @BeforeEach
  void setup() {
    DistanceTestHelper.setup();
  }

  @Test
  void distanceCalculation() {

    /* distance API returns hardcoded values */
    DistanceCalculator calculator =
        new DistanceCalculator(
            TestConfiguration.jdbiTest,
            new GoogleDistanceApi((SiteConfigService) null) {
              @Override
              public GoogleDistanceResponse queryDistance(SiteAddress from, SiteAddress to) {
                return GoogleDistanceResponse.builder()
                    .distance(100.1)
                    .duration(360L)
                    .status(GoogleDistanceApi.ResponseStatus.OK)
                    .build();
              }
            },
            true,
            0);
    calculator.calculateDistances();

    long site1Id = TestConfiguration.getSiteId("site1");
    long site2Id = TestConfiguration.getSiteId("site4");

    Optional<DistanceDao.DistanceResult> result =
        DistanceDao.queryDistance(TestConfiguration.jdbiTest, site1Id, site2Id);
    assertThat(result).isPresent();
    assertThat(result.get().getDistance()).isEqualTo(100.1);
    assertThat(result.get().getDurationSeconds()).isEqualTo(360L);
  }

  @Test
  void distanceCalculationBadAddress() {
    /* Distance API calculator does not return data (invalid address case) */
    DistanceCalculator calculator =
        new DistanceCalculator(
            TestConfiguration.jdbiTest,
            new GoogleDistanceApi((SiteConfigService) null) {
              @Override
              public GoogleDistanceResponse queryDistance(SiteAddress from, SiteAddress to) {
                return GoogleDistanceResponse.builder()
                    .status(GoogleDistanceApi.ResponseStatus.INVALID_PAIR)
                    .build();
              }
            },
            true,
            0);
    calculator.calculateDistances();

    long site2Id = TestConfiguration.getSiteId("site2");
    long site4Id = TestConfiguration.getSiteId("site4");
    Optional<DistanceDao.DistanceResult> result =
        DistanceDao.queryDistance(TestConfiguration.jdbiTest, site2Id, site4Id);
    assertThat(result).isEmpty();
  }

  /**
   * When Google itself is unhealthy (bad key, billing, throttle), the scheduler must NOT mark
   * pending pairs as invalid — that would poison the cache and require manual SQL recovery once
   * Google comes back. The pair must stay NULL so the next tick retries.
   */
  @Test
  void transientFailureLeavesPairPendingForRetry() {
    DistanceCalculator calculator =
        new DistanceCalculator(
            TestConfiguration.jdbiTest,
            new GoogleDistanceApi((SiteConfigService) null) {
              @Override
              public GoogleDistanceResponse queryDistance(SiteAddress from, SiteAddress to) {
                return GoogleDistanceResponse.builder()
                    .status(GoogleDistanceApi.ResponseStatus.TRANSIENT_FAILURE)
                    .build();
              }
            },
            true,
            0);
    calculator.calculateDistances();

    // The (site1, site4) row started as valid=NULL. After a transient failure tick it must
    // STILL be NULL — not false — so the next tick retries it once Google recovers.
    long site1Id = TestConfiguration.getSiteId("site1");
    long site4Id = TestConfiguration.getSiteId("site4");
    Boolean valid =
        TestConfiguration.jdbiTest.withHandle(
            h ->
                h.createQuery(
                        """
                        select valid from site_distance_matrix
                        where (site1_id = :a and site2_id = :b)
                           or (site1_id = :b and site2_id = :a)
                        """)
                    .bind("a", site1Id)
                    .bind("b", site4Id)
                    .mapTo(Boolean.class)
                    .findOne()
                    .orElse(null));
    assertThat(valid).isNull();
  }
}
