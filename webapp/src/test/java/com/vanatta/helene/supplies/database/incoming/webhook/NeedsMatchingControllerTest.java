package com.vanatta.helene.supplies.database.incoming.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.data.SiteType;
import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class NeedsMatchingControllerTest {

  @Nested
  class ControllerParsing {
    @Test
    void controller() {
      String input =
          """
          {"deliveryId":35,"fromSiteWssId":[337],"toSiteWssId":[115]}
          """;
      NeedsMatchingController controller = new NeedsMatchingController(TestConfiguration.jdbiTest);
      ResponseEntity<String> response = controller.addSuppliesToDelivery(input);
      assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
  }

  private static long supplySiteId;
  private static long supplySiteWssId;

  private static long warehouseSiteId;
  private static long warehouseSiteWssId;

  private static long toSiteId;
  private static long toSiteWssId;

  private static long toSiteIdNoOverlap;
  private static long toSiteWssIdNoOverlap;

  /**
   *
   *
   * <pre>
   * // create a from site HUB
   * // with needs supplies: {new clothes}  << this item is needed, should not be returned as eligible
   * // with HUB available supplies: {water, gloves}
   * // with oversupply: {batteries, heater}
   *
   * // create a second from site DIST-SITE
   * // with available supplies: {gloves}
   * // with oversupply: {batteries, water}
   *
   * // create a to site
   * // with need: {gloves, soap}
   * // with urgent need: {heater, batteries}
   *
   * // create a to site   (no overlap)
   * // with need: {used clothes}
   * </pre>
   */
  @BeforeAll
  static void setup() {
    TestConfiguration.setupDatabase();

    warehouseSiteId = TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.SUPPLY_HUB));
    warehouseSiteWssId =
        SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, warehouseSiteId).getWssId();
    TestConfiguration.addItemToSite(warehouseSiteId, ItemStatus.NEEDED, "new clothes", -200);
    TestConfiguration.addItemToSite(warehouseSiteId, ItemStatus.AVAILABLE, "water", -201);
    TestConfiguration.addItemToSite(warehouseSiteId, ItemStatus.AVAILABLE, "gloves", -202);
    TestConfiguration.addItemToSite(warehouseSiteId, ItemStatus.OVERSUPPLY, "batteries", -203);
    TestConfiguration.addItemToSite(warehouseSiteId, ItemStatus.OVERSUPPLY, "heater", -204);

    supplySiteId =
        TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.DISTRIBUTION_CENTER));
    supplySiteWssId =
        SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, supplySiteId).getWssId();

    TestConfiguration.addItemToSite(supplySiteId, ItemStatus.AVAILABLE, "gloves", -214);
    TestConfiguration.addItemToSite(supplySiteId, ItemStatus.OVERSUPPLY, "batteries", -215);
    TestConfiguration.addItemToSite(supplySiteId, ItemStatus.OVERSUPPLY, "heater", -216);

    toSiteId = TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.DISTRIBUTION_CENTER));
    toSiteWssId = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, toSiteId).getWssId();
    TestConfiguration.addItemToSite(toSiteId, ItemStatus.AVAILABLE, "water", -218);
    TestConfiguration.addItemToSite(toSiteId, ItemStatus.NEEDED, "new clothes", -219);
    TestConfiguration.addItemToSite(toSiteId, ItemStatus.NEEDED, "gloves", -220);
    TestConfiguration.addItemToSite(toSiteId, ItemStatus.NEEDED, "soap", -221);
    TestConfiguration.addItemToSite(toSiteId, ItemStatus.URGENTLY_NEEDED, "heater", -222);
    TestConfiguration.addItemToSite(toSiteId, ItemStatus.URGENTLY_NEEDED, "batteries", -223);

    toSiteIdNoOverlap =
        TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.DISTRIBUTION_CENTER));
    toSiteWssIdNoOverlap =
        SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, toSiteIdNoOverlap).getWssId();
    TestConfiguration.addItemToSite(toSiteId, ItemStatus.NEEDED, "used clothes", -230);
  }

  @Test
  void supplyMatchingFromWarehouse() {
    var results =
        NeedsMatchingController.computeNeedsMatch(
            TestConfiguration.jdbiTest, warehouseSiteWssId, toSiteWssId);
    assertThat(results).containsExactly("batteries", "gloves", "heater");
  }

  @Test
  void supplyMatchingFromSupplySite() {
    var results =
        NeedsMatchingController.computeNeedsMatch(
            TestConfiguration.jdbiTest, supplySiteWssId, toSiteWssId);
    // expected nonmatch: -220 gloves  (gloves are available at a dist site, and are not oversupply)
    assertThat(results).containsExactly("batteries", "heater");
  }

  /**
   * toSiteIdNoOverlap has no items needed that are available anywhere else. Result should always be
   * empty.
   */
  @Test
  void supplyMatchingToNonOverlapSite() {
    var results =
        NeedsMatchingController.computeNeedsMatch(
            TestConfiguration.jdbiTest, warehouseSiteWssId, toSiteWssIdNoOverlap);
    assertThat(results).isEmpty();

    results =
        NeedsMatchingController.computeNeedsMatch(
            TestConfiguration.jdbiTest, supplySiteWssId, toSiteWssIdNoOverlap);
    assertThat(results).isEmpty();

    results =
        NeedsMatchingController.computeNeedsMatch(
            TestConfiguration.jdbiTest, toSiteWssId, toSiteWssIdNoOverlap);
    assertThat(results).isEmpty();
  }
}
