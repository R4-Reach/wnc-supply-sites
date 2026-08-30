package org.r4reach.manage.address;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.r4reach.TestConfiguration;
import org.r4reach.supplies.site.details.SiteDetailDao;
import org.r4reach.util.HtmlSelectOptionsUtil;
import org.springframework.web.servlet.ModelAndView;

class SiteAddressControllerTest {
  SiteAddressController siteAddressController =
      new SiteAddressController(TestConfiguration.jdbiTest);

  @BeforeAll
  static void setupDb() {
    TestConfiguration.setupDatabase();
  }

  /** For a known site, validates that we populate all the page params. */
  @ParameterizedTest
  @EnumSource(SiteAddressController.PageParam.class)
  void showPageParams(SiteAddressController.PageParam param) {
    // site1 should have every field populated.
    long siteId = TestConfiguration.getSiteId("site1");
    var response =
        siteAddressController.showSiteContactPage(
            List.of(siteId), String.valueOf(siteId), List.of("NC"));
    assertThat(response.getModelMap()).containsKey(param.text);
    assertThat(response.getModelMap().getAttribute(param.text)).isNotNull();
  }

  /**
   * Make sure that the county listing displayed has the correct county selected for a given site
   */
  @Test
  void manageContactSelectsCorrectCounty() {
    long siteId = TestConfiguration.getSiteId();
    SiteDetailDao.SiteDetailData siteDetailData =
        SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);

    ModelAndView result =
        siteAddressController.showSiteContactPage(
            List.of(siteId), String.valueOf(siteId), List.of("NC"));

    List<HtmlSelectOptionsUtil.ItemListing> countyListResult =
        (List<HtmlSelectOptionsUtil.ItemListing>)
            result.getModelMap().getAttribute(SiteAddressController.PageParam.COUNTY_LIST.text);

    // validate that all listings are populated with non-null data
    countyListResult.forEach(
        listing -> {
          assertThat(listing.getName()).isNotNull();
          assertThat(listing.getName()).isNotBlank();
          // all values for 'selected' should eitehr be blank or 'selected'
          assertThat(listing.getSelected()).isNotNull();
          assertThat(listing.getSelected()).isIn("", "selected");
        });

    // find the listing for the county of the site, it should be selected
    var listing =
        countyListResult.stream()
            .filter(f -> f.getName().equals(siteDetailData.getCounty()))
            .findAny()
            .orElseThrow();
    assertThat(listing.getSelected()).isEqualTo("selected");

    // find all other county listings, they should not be selected.
    countyListResult.stream()
        .filter(f -> !f.getName().equals(siteDetailData.getCounty()))
        .forEach(r -> assertThat(r.getSelected()).isEmpty());
  }

  /** Make sure state list has the correct state selected for a given site */
  @Test
  void manageContactSelectsCorrectState() {
    long siteId = TestConfiguration.getSiteId();
    SiteDetailDao.SiteDetailData siteDetailData =
        SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);

    ModelAndView result =
        siteAddressController.showSiteContactPage(
            List.of(siteId), String.valueOf(siteId), List.of("NC"));

    List<HtmlSelectOptionsUtil.ItemListing> stateListing =
        (List<HtmlSelectOptionsUtil.ItemListing>)
            result.getModelMap().getAttribute(SiteAddressController.PageParam.STATE_LIST.text);

    // validate that all listings are populated with non-null data
    stateListing.forEach(
        listing -> {
          assertThat(listing.getName()).isNotNull();
          assertThat(listing.getName()).isNotBlank();
          // all values for 'selected' should eitehr be blank or 'selected'
          assertThat(listing.getSelected()).isNotNull();
          assertThat(listing.getSelected()).isIn("", "selected");
        });

    // find the listing for the state of the site, it should be selected
    var listing =
        stateListing.stream()
            .filter(f -> f.getName().equals(siteDetailData.getState()))
            .findAny()
            .orElseThrow();
    assertThat(listing.getSelected()).isEqualTo("selected");

    // find all other county listings, they should not be selected.
    stateListing.stream()
        .filter(f -> !f.getName().equals(siteDetailData.getState()))
        .forEach(r -> assertThat(r.getSelected()).isEmpty());
  }
}
