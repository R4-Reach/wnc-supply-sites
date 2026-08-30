package org.r4reach.supplies.site.details;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.auth.CookieAuthenticator;
import org.r4reach.delivery.DeliveryFixture;

class SiteDetailControllerTest {
  SiteDetailController siteDetailController =
      new SiteDetailController(jdbiTest, new CookieAuthenticator(jdbiTest));

  /**
   * Validate that the site detail page contains all values from
   * 'SiteDetailController.TemplateParams'
   */
  @Test
  void renderSiteDetail() {
    long site1Id = TestConfiguration.getSiteId("site1");

    var model =
        siteDetailController.siteDetail(List.of(site1Id), List.of("NC", "TN"), site1Id, null, true);

    assertThat(model.getModelMap().keySet())
        .containsAll(
            Arrays.stream(SiteDetailController.TemplateParams.values()).map(v -> v.text).toList());
  }

  /** Validates bug fix, page crashes if deliveries has a null to or from site. */
  @Test
  void renderSiteDetail_withDeliveryThatHasNullData() {
    String site = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(site);
    long wssId = SiteDetailDao.lookupSiteById(jdbiTest, siteId).getWssId();

    DeliveryFixture.builder()
        .deliveryId(-800L)
        .dropOffSiteWssId(List.of(wssId))
        .publicUrlKey("keyA")
        .dispatcherCode("DZAA")
        .build()
        .store(jdbiTest);

    siteDetailController.siteDetail(List.of(siteId), List.of("NC"), siteId, null, true);
  }
}
