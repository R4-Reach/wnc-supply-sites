package org.r4reach.manage.contact;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.r4reach.TestConfiguration;

class SiteContactControllerTest {
  SiteContactController siteContactController =
      new SiteContactController(TestConfiguration.jdbiTest);

  @BeforeAll
  static void setupDb() {
    TestConfiguration.setupDatabase();
  }

  /** For a known site, validates that we populate all the page params. */
  @ParameterizedTest
  @EnumSource(SiteContactController.PageParam.class)
  void showPageParams(SiteContactController.PageParam param) {
    // site1 should have every field populated.
    long siteId = TestConfiguration.getSiteId("site1");
    var response =
        siteContactController.showSiteContactPage(List.of(siteId), String.valueOf(siteId));
    assertThat(response.getModelMap()).containsKey(param.text);
    assertThat(response.getModelMap().get(param.text)).isNotNull();
  }
}
