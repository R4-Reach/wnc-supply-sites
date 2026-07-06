package com.vanatta.helene.supplies.database.supplies;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.CookieAuthenticator;
import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.data.SiteType;
import com.vanatta.helene.supplies.database.delivery.ConfirmationDao;
import com.vanatta.helene.supplies.database.delivery.DeliveryDao;
import com.vanatta.helene.supplies.database.delivery.DeliveryHelper;
import com.vanatta.helene.supplies.database.delivery.DeliveryStatus;
import com.vanatta.helene.supplies.database.manage.ManageSiteDao;
import com.vanatta.helene.supplies.database.manage.inventory.InventoryDao;
import java.util.List;
import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SuppliesControllerTest {

  static final Jdbi jdbiTest = TestConfiguration.jdbiTest;

  private final SuppliesController suppliesController =
      new SuppliesController(jdbiTest, new CookieAuthenticator(jdbiTest));

  @BeforeAll
  static void clearDatabase() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void emptyRequestReturnsData() {
    var result =
        suppliesController.getSuppliesData(SiteSupplyRequest.builder().build(), List.of("NC"));

    // All active sites should be returned
    assertThat(result.getResultCount()).isEqualTo(6);
  }

  @Test
  void requestSiteWithNoItems() {
    // because we list all item statuses, we will effectively ignore that list and return sites
    // that have no items.
    var result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .itemStatus(ItemStatus.allItemStatus())
                .sites(List.of("site5"))
                .build(),
            List.of("NC"));

    assertThat(result.getResultCount()).isEqualTo(1);
    assertThat(result.getResults().getFirst().getAvailableItems()).isEmpty();
    assertThat(result.getResults().getFirst().getNeededItems()).isEmpty();
  }

  @Test
  void requestBySite() {
    var result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().sites(List.of("site1")).build(), List.of("NC"));

    assertThat(result.getResultCount()).isEqualTo(1);
    assertThat(result.getResults().getFirst().getSite()).isEqualTo("site1");

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().sites(List.of("site1", "site2")).build(), List.of("NC"));

    assertThat(result.getResultCount()).isEqualTo(2);
  }

  @Test
  void requestByItem() {
    var result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().items(List.of("water")).build(), List.of("NC"));

    assertThat(result.getResultCount()).isEqualTo(2);

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().items(List.of("water", "new clothes")).build(),
            List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(2);

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().items(List.of("random stuff")).build(), List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(0);

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().items(List.of("random stuff", "new clothes")).build(),
            List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(2);
  }

  @Test
  void requestByCounty() {
    var result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().counties(List.of("Haywood, NC")).build(), List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(0);

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().counties(List.of("Los Angeles, CA")).build(),
            List.of("CA"));
    assertThat(result.getResultCount()).isEqualTo(1);

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().counties(List.of("Buncombe, NC")).build(), List.of("NC"));
    result
        .getResults()
        .forEach(
            r -> {
              assertThat(r.getCounty()).isEqualTo("Buncombe");
              assertThat(r.getState()).isEqualTo("NC");
            });
    assertThat(
            result.getResults().stream().map(SiteSupplyResponse.SiteSupplyData::getSite).toList())
        .contains("site2", "site4");

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().counties(List.of("Watauga, NC")).build(), List.of("NC"));
    assertThat(
            result.getResults().stream().map(SiteSupplyResponse.SiteSupplyData::getSite).toList())
        .contains("site1");
    result
        .getResults()
        .forEach(
            r -> {
              assertThat(r.getCounty()).isEqualTo("Watauga");
              assertThat(r.getState()).isEqualTo("NC");
            });

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .counties(List.of("Ashe, NC", "Watauga, NC", "Buncombe, NC"))
                .build(),
            List.of("NC"));
    assertThat(
            result.getResults().stream().map(SiteSupplyResponse.SiteSupplyData::getSite).toList())
        .contains("site1", "site2", "site4");

    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().counties(List.of("Ashe, NC", "Buncombe, NC")).build(),
            List.of("NC"));
    assertThat(
            result.getResults().stream().map(SiteSupplyResponse.SiteSupplyData::getSite).toList())
        .contains("site2", "site4");
  }

  @Test
  void requestByItemStatus() {
    for (ItemStatus status : ItemStatus.values()) {
      var result =
          suppliesController.getSuppliesData(
              SiteSupplyRequest.builder().itemStatus(List.of(status.getText())).build(),
              List.of("NC"));
      result.getResults().stream()
          .map(SiteSupplyResponse.SiteSupplyData::getAvailableItems)
          .flatMap(List::stream)
          .forEach(item -> assertThat(item.getDisplayClass()).isEqualTo(status.getCssClass()));
      result.getResults().stream()
          .map(SiteSupplyResponse.SiteSupplyData::getNeededItems)
          .flatMap(List::stream)
          .forEach(item -> assertThat(item.getDisplayClass()).isEqualTo(status.getCssClass()));
    }
  }

  @Test
  void multipleItemStatus() {
    var result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .itemStatus(
                    List.of(ItemStatus.OVERSUPPLY.getText(), ItemStatus.URGENTLY_NEEDED.getText()))
                .build(),
            List.of("NC"));

    result.getResults().stream()
        .map(SiteSupplyResponse.SiteSupplyData::getNeededItems)
        .flatMap(List::stream)
        .forEach(
            item ->
                assertThat(item.getDisplayClass()).isIn(ItemStatus.URGENTLY_NEEDED.getCssClass()));
    result.getResults().stream()
        .map(SiteSupplyResponse.SiteSupplyData::getAvailableItems)
        .flatMap(List::stream)
        .forEach(
            item -> assertThat(item.getDisplayClass()).isIn(ItemStatus.OVERSUPPLY.getCssClass()));
  }

  @Test
  void mixedRequestWithDifferentFilters() {
    // no sites with the requested county, or requested item
    var result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .counties(List.of("Haywood, NC"))
                .items(List.of("Random stuff"))
                .build(),
            List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(0);

    // there exists a site with 'Watauga' county, but no items
    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .counties(List.of("Watauga, NC"))
                .items(List.of("Random stuff"))
                .build(),
            List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(0);

    // there exists a site with the target county, but no item
    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .sites(List.of("site1"))
                .counties(List.of("Watauga, NC"))
                .items(List.of("Random stuff"))
                .build(),
            List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(0);

    // site name and county match
    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .sites(List.of("site1"))
                .counties(List.of("Watauga, NC"))
                .build(),
            List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(1);

    // both sites have water, but only one is in Watauga county
    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .items(List.of("water"))
                .counties(List.of("Watauga, NC"))
                .build(),
            List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(1);
  }

  /** Validate that we aggregate the item list together by site. */
  @Test
  void multipleItemsAreAggregated() {
    var result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder().sites(List.of("site1")).build(), List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(1);
  }

  /**
   * We have one site that is accepting donations and active, we should only get one site back when
   * querying for sites that are accepting donations.
   */
  @Test
  void queryForAcceptingDonationsOnly() {
    // show exactly the sites accepting donations
    var result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .acceptingDonations(true)
                .notAcceptingDonations(false)
                .build(),
            List.of("NC"));
    // must contain sites that are active & accepting donations
    // (we exclude site5 because its name can change
    assertThat(result.getResults().stream().map(SiteSupplyResponse.SiteSupplyData::getSite))
        .contains("site1", "site4");
    // site2 is definitely excluded because active and not accepting donations
    // site3 is excluded because it is not active
    assertThat(result.getResults().stream().map(SiteSupplyResponse.SiteSupplyData::getSite))
        .doesNotContain("site2", "site3");

    assertThat(result.getResults().getFirst().isAcceptingDonations()).isTrue();

    // show exactly the sites not accepting donations
    result =
        suppliesController.getSuppliesData(
            SiteSupplyRequest.builder()
                .acceptingDonations(false)
                .notAcceptingDonations(true)
                .build(),
            List.of("NC"));
    assertThat(result.getResultCount()).isEqualTo(1);
    assertThat(result.getResults().getFirst().isAcceptingDonations()).isFalse();

    // show all sites (false to both would always return no results, instead we just ignore the flag
    int resultCount =
        suppliesController
            .getSuppliesData(
                SiteSupplyRequest.builder()
                    .acceptingDonations(false)
                    .notAcceptingDonations(false)
                    .build(),
                List.of("NC"))
            .getResultCount();

    // show all sites
    int allSiteResultCount =
        suppliesController
            .getSuppliesData(
                SiteSupplyRequest.builder()
                    .acceptingDonations(true)
                    .notAcceptingDonations(true)
                    .build(),
                List.of("NC"))
            .getResultCount();

    assertThat(resultCount).isEqualTo(allSiteResultCount);
  }

  @Nested
  class SiteTypeTest {
    /** Specify a specific site type, all results should come back with that site type */
    @ParameterizedTest
    @ValueSource(strings = {"Supply Hub", "Distribution Center"})
    void siteType(String siteType) {
      var result =
          suppliesController.getSuppliesData(
              SiteSupplyRequest.builder().siteType(List.of(siteType)).build(), List.of("NC"));

      assertThat(result.getResultCount()).isGreaterThan(0);
      result.getResults().forEach(r -> assertThat(r.getSiteType()).isEqualTo(siteType));
    }

    /** Querying for neither site type, or all site types should return the same results */
    @Test
    void noSiteTypeOrAllSiteTypes() {
      var noSiteTypes =
          suppliesController.getSuppliesData(
              SiteSupplyRequest.builder().siteType(List.of()).build(), List.of("NC"));
      var allSiteTypes =
          suppliesController.getSuppliesData(
              SiteSupplyRequest.builder().siteType(SiteType.allSiteTypes()).build(), List.of("NC"));
      assertThat(noSiteTypes.getResultCount()).isGreaterThan(0);
      assertThat(noSiteTypes.getResultCount()).isEqualTo(allSiteTypes.getResultCount());
    }
  }

  @Test
  void privateSitesAreFiltered() {
    String newSiteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(newSiteName);

    // validate we can find the new site without being authenticated

    boolean authenticated = false;
    List<String> siteNames = doSearch(authenticated);
    assertThat(siteNames).contains(newSiteName);

    // now hide the site & repeat the search
    ManageSiteDao.updateSitePubliclyVisible(TestConfiguration.jdbiTest, siteId, false);
    siteNames = doSearch(authenticated);
    assertThat(siteNames).doesNotContain(newSiteName);

    // repeat the search again, but this time authenticated
    authenticated = true;
    siteNames = doSearch(authenticated);
    assertThat(siteNames).contains(newSiteName);
  }

  private List<String> doSearch(boolean authenticated) {
    return suppliesController
        .getSuppliesData(SiteSupplyRequest.builder().build(), authenticated, List.of("NC"))
        .getResults()
        .stream()
        .map(SiteSupplyResponse.SiteSupplyData::getSite)
        .toList();
  }

  /**
   * Integration test for site search.
   *
   * <pre>
   *   1. add a site, validate it appears in the search
   *   2. add inventory to the site, validate the inventory appears in the search
   *   3. add an empty delivery for the site, validate the site still appears in the search
   * </pre>
   */
  @Test
  void sitesWithEmptyDeliveryShowUp() {
    String siteName = TestConfiguration.addSite();
    var searchResults =
        suppliesController
            .getSuppliesData(
                SiteSupplyRequest.builder().sites(List.of(siteName)).build(), false, List.of("NC"))
            .getResults()
            .stream()
            .toList();
    assertThat(searchResults).isNotEmpty();
    assertThat(searchResults.getFirst().getNeededItems()).isEmpty();
    ;

    // add an item to the site
    var itemName = UUID.randomUUID().toString();
    InventoryDao.addNewItem(jdbiTest, itemName);
    long siteId = TestConfiguration.getSiteId(siteName);
    InventoryDao.updateSiteItemActive(jdbiTest, siteId, itemName, ItemStatus.NEEDED.getText());

    // search again
    assertSiteAppearsInSearchWithItems(siteName);

    // add a delivery to the site
    var delivery = DeliveryHelper.withNewDelivery(siteId, siteId);
    assertSiteAppearsInSearchWithItems(siteName);

    // check regression does not happen, sites that had deliveries in various statuses were not
    // showing their inventory.
    ConfirmationDao.dispatcherConfirm(jdbiTest, delivery.getPublicKey());
    for (DeliveryStatus status : DeliveryStatus.values()) {
      DeliveryDao.updateDeliveryStatus(jdbiTest, delivery.getPublicKey(), status);
      assertSiteAppearsInSearchWithItems(siteName);
    }
  }

  private void assertSiteAppearsInSearchWithItems(String siteName) {
    var searchResults =
        suppliesController
            .getSuppliesData(
                SiteSupplyRequest.builder().sites(List.of(siteName)).build(), true, List.of("NC"))
            .getResults()
            .stream()
            .toList();
    assertThat(searchResults).isNotEmpty();
    assertThat(searchResults.getFirst().getNeededItems()).isNotEmpty();
  }

  /**
   * If someone has their web browser open for an absurdly long time, we can have county lists that
   * have no comma.
   *
   * <p>If we have that, then just ignore the county.
   */
  @Test
  void validateBug_oldCountyList() {
    var searchResults =
        suppliesController
            .getSuppliesData(
                SiteSupplyRequest.builder().counties(List.of("Polk", "Watauga, NC")).build(),
                List.of("NC"))
            .getResults()
            .stream()
            .toList();
    assertThat(searchResults).isNotEmpty();
    searchResults.forEach(result -> assertThat(result.getCounty()).isEqualTo("Watauga"));
  }
}
