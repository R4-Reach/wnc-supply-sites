package org.r4reach.manage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.r4reach.TestConfiguration;

class UpdateSiteDataControllerTest {
  UpdateSiteDataController updateSiteDataController =
      new UpdateSiteDataController(TestConfiguration.jdbiTest);

  /**
   * Loop through all fields that can be set to random data. Invoke the site update endpoint for a
   * quick end-to-end test, make sure simply that we get no errors.
   */
  @ParameterizedTest
  @MethodSource
  void updateSiteDataThrowsNoErrors(ManageSiteDao.SiteField field) {

    String newValue =
        switch (field) {
          case MAX_SUPPLY_LOAD -> "Car";
          case WEEKLY_SERVED -> "1000";
          default -> field.getFrontEndName() + " " + UUID.randomUUID().toString().substring(0, 10);
        };

    long siteId = TestConfiguration.getSiteId();
    Map<String, String> params =
        Map.of(
            "siteId",
            String.valueOf(siteId),
            "field",
            field.getFrontEndName(),
            "newValue",
            newValue);

    var response = updateSiteDataController.updateSiteData(List.of(siteId), params);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
  }

  /** A caller who does not manage the target site is refused (no per-site IDOR). */
  @org.junit.jupiter.api.Test
  void updateSiteData_forbiddenWhenSiteNotOwned() {
    long siteId = TestConfiguration.getSiteId();
    Map<String, String> params =
        Map.of("siteId", String.valueOf(siteId), "field", "siteName", "newValue", "hijacked");

    var response = updateSiteDataController.updateSiteData(List.of(), params);

    assertThat(response.getStatusCode().value()).isEqualTo(403);
  }

  static List<ManageSiteDao.SiteField> updateSiteDataThrowsNoErrors() {
    // filter out fields that require specific values, any field
    // that cannot just be set to random data.
    return Arrays.asList(ManageSiteDao.SiteField.values()).stream()
        .filter(
            f ->
                f != ManageSiteDao.SiteField.STATE
                    && f != ManageSiteDao.SiteField.COUNTY
                    && f != ManageSiteDao.SiteField.SITE_NAME)
        .toList();
  }
}
