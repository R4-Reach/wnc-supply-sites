package com.vanatta.helene.supplies.database.supplies.site.details;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.CookieAuthenticator;
import com.vanatta.helene.supplies.database.delivery.DeliveryDao;
import com.vanatta.helene.supplies.database.delivery.DeliveryUpdate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

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
        siteDetailController.siteDetail(
            List.of(site1Id), List.of("NC", "TN"), site1Id, null, null, true);

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

    DeliveryDao.upsert(
        jdbiTest,
        DeliveryUpdate.builder()
            .deliveryId(-800L)
            .dropOffSiteWssId(List.of(wssId))
            .publicUrlKey("keyA")
            .dispatcherCode("DZAA")
            .build());

    siteDetailController.siteDetail(List.of(siteId), List.of("NC"), siteId, null, null, true);
  }
}
