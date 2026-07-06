package com.vanatta.helene.supplies.database.manage.receiving;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.manage.ManageSiteDao;
import com.vanatta.helene.supplies.database.util.HtmlSelectOptionsUtil;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SiteReceivingControllerTest {
  SiteReceivingController siteReceivingController =
      new SiteReceivingController(TestConfiguration.jdbiTest);

  @BeforeAll
  static void setupDb() {
    TestConfiguration.setupDatabase();
  }

  /** For a known site, validates that we populate all the page params. */
  @ParameterizedTest
  @EnumSource(SiteReceivingController.PageParam.class)
  void showPageParams(SiteReceivingController.PageParam param) {
    // site1 should have every field populated.
    long siteId = TestConfiguration.getSiteId("site1");

    var response =
        siteReceivingController.showSiteContactPage(List.of(siteId), String.valueOf(siteId));
    assertThat(response.getModelMap()).containsKey(param.text);
    assertThat(response.getModelMap().get(param.text)).isNotNull();
  }

  /** Sets a max supply value, gets page params and validates that the correct one is selected. */
  @Test
  void correctMaxSupplySelected() {
    long siteId = TestConfiguration.getSiteId("site1");
    ManageSiteDao.updateMaxSupply(TestConfiguration.jdbiTest, siteId, "Car");

    var response =
        siteReceivingController.showSiteContactPage(List.of(siteId), String.valueOf(siteId));

    var items =
        (List<HtmlSelectOptionsUtil.ItemListing>)
            response
                .getModelMap()
                .getAttribute(SiteReceivingController.PageParam.MAX_SUPPLY_OPTIONS.text);
    assertThat(items)
        .contains(
            HtmlSelectOptionsUtil.ItemListing.builder().name("Car").selected("selected").build());
  }
}
