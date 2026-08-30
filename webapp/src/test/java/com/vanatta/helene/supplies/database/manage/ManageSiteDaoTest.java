package com.vanatta.helene.supplies.database.manage;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.data.SiteType;
import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ManageSiteDaoTest {

  static class Helper {
    static long getSiteId() {
      return TestConfiguration.getSiteId();
    }

    static long getSiteId(String siteName) {
      return TestConfiguration.getSiteId(siteName);
    }
  }

  @BeforeAll
  static void setUp() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void fetchSites() {
    var results =
        ManageSiteDao.fetchSiteList(
            TestConfiguration.jdbiTest,
            List.of(TestConfiguration.getSiteId("site1"), TestConfiguration.getSiteId("site2")),
            List.of("NC"));
    assertThat(results).isNotEmpty();

    results.forEach(result -> assertThat(result.getId()).isNotEqualTo(0L));
    var names =
        results.stream()
            .map(SelectSiteController.SiteSelection::getName)
            .collect(Collectors.toList());
    assertThat(names).contains("site1");
    names.forEach(name -> assertThat(name).isNotNull());
  }

  /**
   * Update site5 to have different site field values. Validate that those fields change value. Use
   * site5 to not interfere with any other tests (no other tests use 'site5')
   */
  @Test
  void updateSite() {
    long siteId = Helper.getSiteId("site5");

    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.SITE_NAME, "new site name");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.STREET_ADDRESS, "new address");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.CITY, "new city");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.COUNTY, "Buncombe,NC");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.STATE, "Buncombe,NC");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.WEBSITE, "new website");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.FACEBOOK, "new facebook");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.SITE_HOURS, "M-F 9-5pm");
    // A site's primary-contact user is created when the contact number is set; the contact name is
    // then recorded on that user, so the number must be set first.
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.CONTACT_NUMBER, "999-596-1112");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.CONTACT_NAME, "Smith Williams");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest,
        siteId,
        ManageSiteDao.SiteField.ADDITIONAL_CONTACTS,
        "More: 22-333");

    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.MAX_SUPPLY_LOAD, "Car");

    var dataLookup = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
    assertThat(dataLookup.getSiteName()).isEqualTo("new site name");
    assertThat(dataLookup.getAddress()).isEqualTo("new address");
    assertThat(dataLookup.getCity()).isEqualTo("new city");
    assertThat(dataLookup.getCounty()).isEqualTo("Buncombe");
    assertThat(dataLookup.getState()).isEqualTo("NC");
    assertThat(dataLookup.getWebsite()).isEqualTo("new website");
    assertThat(dataLookup.getFacebook()).isEqualTo("new facebook");
    assertThat(dataLookup.getHours()).isEqualTo("M-F 9-5pm");
    assertThat(dataLookup.getContactName()).isEqualTo("Smith Williams");
    assertThat(dataLookup.getContactNumber()).isEqualTo("19995961112");
    assertThat(dataLookup.getMaxSupply()).isEqualTo("Car");
  }

  @Test
  void validateSomeSiteFieldsCannotBeDeleted() {
    long siteId = Helper.getSiteId("site1");

    List.of(
            ManageSiteDao.SiteField.SITE_NAME,
            ManageSiteDao.SiteField.CITY,
            ManageSiteDao.SiteField.COUNTY,
            ManageSiteDao.SiteField.STREET_ADDRESS)
        .forEach(
            field ->
                org.junit.jupiter.api.Assertions.assertThrows(
                    ManageSiteDao.RequiredFieldException.class,
                    () ->
                        ManageSiteDao.updateSiteField(
                            TestConfiguration.jdbiTest, siteId, field, "")));
  }

  @Test
  void fetchSiteName() {
    long siteId = Helper.getSiteId();

    String result = ManageSiteDao.fetchSiteName(TestConfiguration.jdbiTest, siteId);

    assertThat(result).isEqualTo("site1");
  }

  /**
   * Validate we get the behavior of returning the old value when we update a field. This way we can
   * keep an audit log of what has changed.
   */
  @Test
  void updatesReturnOldValue() {
    // setup: get a site, validate the old value is not what we will change it to.

    // remember the old value
    long siteId = Helper.getSiteId("site4");
    var dataLookup = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
    assertThat(dataLookup.getHours()).isNotEqualTo("evening");
    String oldValue = dataLookup.getHours();

    // update the value.
    String oldValueResult =
        ManageSiteDao.updateSiteColumn(
            TestConfiguration.jdbiTest, siteId, ManageSiteDao.SiteField.SITE_HOURS, "evening");

    assertThat(oldValueResult).isEqualTo(oldValue);
  }

  @Test
  void updateCountyReturnsOldValue() {
    // setup: get a site, validate the old value is not what we will change it to.
    // remember the old value
    long siteId = Helper.getSiteId("site4");
    var dataLookup = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
    assertThat(dataLookup.getCounty()).isNotEqualTo("Halifax");
    assertThat(dataLookup.getCounty()).isNotEqualTo("VA");
    String oldCounty = dataLookup.getCounty();
    String oldState = dataLookup.getState();

    String oldValueResult =
        ManageSiteDao.updateCounty(TestConfiguration.jdbiTest, siteId, "Halifax", "VA");

    assertThat(oldValueResult).isEqualTo(String.format("%s,%s", oldCounty, oldState));
  }

  @Test
  void updatingFieldValuesAddToAuditLog() {
    int startingCount = auditLogCount();

    // trigger an update
    long siteId = Helper.getSiteId("site2");
    ManageSiteDao.updateSiteField(
        TestConfiguration.jdbiTest,
        siteId,
        ManageSiteDao.SiteField.CONTACT_NAME,
        "updated site2 contact name");

    // get the new count of how many audit logs we have, count should be incremented by one.
    int endingCount = auditLogCount();
    assertThat(endingCount).isEqualTo(startingCount + 1);
  }

  private static int auditLogCount() {
    String query = "select count(*) from site_audit_trail";
    return TestConfiguration.jdbiTest.withHandle(
        handle -> handle.createQuery(query).mapTo(Integer.class).one());
  }

  @Nested
  class SiteStatus {

    @Test
    void siteStatusActive() {
      long siteId = Helper.getSiteId("site1");
      var result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isActive()).isTrue();

      ManageSiteDao.updateSiteActiveFlag(TestConfiguration.jdbiTest, siteId, false);
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isActive()).isFalse();

      ManageSiteDao.updateSiteActiveFlag(TestConfiguration.jdbiTest, siteId, true);
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isActive()).isTrue();

      siteId = Helper.getSiteId("site2");
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isActive()).isTrue();

      siteId = Helper.getSiteId("site3");
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isActive()).isFalse();
    }

    @Test
    void setStatusAcceptingDonations() {
      long siteId = Helper.getSiteId("site1");
      var result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isAcceptingDonations()).isTrue();

      ManageSiteDao.updateSiteAcceptingDonationsFlag(TestConfiguration.jdbiTest, siteId, false);
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isAcceptingDonations()).isFalse();

      ManageSiteDao.updateSiteAcceptingDonationsFlag(TestConfiguration.jdbiTest, siteId, true);
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isAcceptingDonations()).isTrue();

      siteId = Helper.getSiteId("site2");
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isAcceptingDonations()).isFalse();

      siteId = Helper.getSiteId("site3");
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isAcceptingDonations()).isTrue();
    }

    @Test
    void setStatusDistributingDonations() {
      long siteId = Helper.getSiteId("site1");
      ManageSiteDao.updateSiteDistributingDonationsFlag(TestConfiguration.jdbiTest, siteId, true);
      var result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isDistributingSupplies()).isTrue();

      ManageSiteDao.updateSiteDistributingDonationsFlag(TestConfiguration.jdbiTest, siteId, false);
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isDistributingSupplies()).isFalse();

      ManageSiteDao.updateSiteDistributingDonationsFlag(TestConfiguration.jdbiTest, siteId, true);
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isDistributingSupplies()).isTrue();
    }

    @Test
    void sitePubliclyVisible() {
      long siteId = Helper.getSiteId("site1");

      ManageSiteDao.updateSitePubliclyVisible(TestConfiguration.jdbiTest, siteId, false);
      var result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isPubliclyVisible()).isFalse();

      ManageSiteDao.updateSitePubliclyVisible(TestConfiguration.jdbiTest, siteId, true);
      result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.isPubliclyVisible()).isTrue();
    }

    @Test
    void updateInactiveReason() {
      long siteId = Helper.getSiteId("site3");
      ManageSiteDao.updateInactiveReason(TestConfiguration.jdbiTest, siteId, "some reasons");
      var result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.getInactiveReason()).isEqualTo("some reasons");
    }

    @Test
    void fetchSiteStatus_SiteType() {
      long siteId = Helper.getSiteId("site1");
      var result = ManageSiteDao.fetchSiteStatus(TestConfiguration.jdbiTest, siteId);
      assertThat(result.getSiteTypeEnum()).isNotNull();
    }

    @Test
    void lookupSiteDetailReturnsSiteType() {
      long siteId = Helper.getSiteId("site1");
      var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.getSiteType()).isNotNull();
    }

    @Test
    void updateSiteType() {
      long siteId = Helper.getSiteId("site1");

      ManageSiteDao.updateSiteType(
          TestConfiguration.jdbiTest, siteId, SiteType.DISTRIBUTION_CENTER);

      var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.getSiteType()).isEqualTo(SiteType.DISTRIBUTION_CENTER.getText());

      ManageSiteDao.updateSiteType(TestConfiguration.jdbiTest, siteId, SiteType.SUPPLY_HUB);

      details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.getSiteType()).isEqualTo(SiteType.SUPPLY_HUB.getText());
    }
  }

  @Test
  void fetchSiteInventory() {
    long siteId = Helper.getSiteId("site1");
    var result = ManageSiteDao.fetchSiteInventory(TestConfiguration.jdbiTest, siteId);

    ManageSiteDao.SiteInventory water = findItemByName(result, "water");
    assertThat(water.isActive()).isTrue();
    assertThat(water.getItemStatus()).isEqualTo(ItemStatus.AVAILABLE.getText());

    ManageSiteDao.SiteInventory clothes = findItemByName(result, "new clothes");
    assertThat(clothes.isActive()).isTrue();
    assertThat(clothes.getItemStatus()).isEqualTo(ItemStatus.URGENTLY_NEEDED.getText());

    ManageSiteDao.SiteInventory usedClothes = findItemByName(result, "used clothes");
    assertThat(usedClothes.isActive()).isTrue();
    assertThat(usedClothes.getItemStatus()).isEqualTo(ItemStatus.OVERSUPPLY.getText());

    ManageSiteDao.SiteInventory gloves = findItemByName(result, "gloves");
    assertThat(gloves.isActive()).isFalse();
    assertThat(gloves.getItemStatus()).isNull();

    ManageSiteDao.SiteInventory randomStuff = findItemByName(result, "random stuff");
    assertThat(randomStuff.isActive()).isFalse();
    assertThat(randomStuff.getItemStatus()).isNull();
  }

  private static ManageSiteDao.SiteInventory findItemByName(
      List<ManageSiteDao.SiteInventory> items, String itemName) {
    return items.stream()
        .filter(r -> r.getItemName().equalsIgnoreCase(itemName))
        .findAny()
        .orElseThrow();
  }

  @Test
  void updateMaxSupply() {
    long siteId = Helper.getSiteId("site1");

    ManageSiteDao.updateMaxSupply(TestConfiguration.jdbiTest, siteId, "Car");
    var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
    assertThat(details.getMaxSupply()).isEqualTo("Car");

    ManageSiteDao.updateMaxSupply(TestConfiguration.jdbiTest, siteId, "Pickup Truck");
    details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
    assertThat(details.getMaxSupply()).isEqualTo("Pickup Truck");
  }

  @Test
  void getAllMaxSupplyLoadCapabilities() {
    var result = ManageSiteDao.getAllMaxSupplyOptions(TestConfiguration.jdbiTest);
    assertThat(result).hasSizeGreaterThan(2);
  }

  @Nested
  class DistanceMatrixUpdates {

    /**
     * When a site updates a field that is an address field, we need to re-calculate distances. In
     * this test, we first fill in the distance matrix table so that everything "looks" computed. We
     * then do the upate, and then validate that rows are cleared.
     */
    @ParameterizedTest
    @MethodSource
    void updateAddressFieldUpdatesDistanceMatrix(ManageSiteDao.SiteField field) {
      fillInDistanceMatrix();
      assertThat(countUncalculatedDistance()).isEqualTo(0);

      long siteId = TestConfiguration.getSiteId();
      ManageSiteDao.updateSiteField(
          TestConfiguration.jdbiTest,
          siteId,
          field,
          // if we are upating state or county, we must use valid values in the county table
          field == ManageSiteDao.SiteField.STATE || field == ManageSiteDao.SiteField.COUNTY
              ? "Buncombe,NC"
              : "new value test");

      // expected number of distances to now be uncalculated is the number of sites minus one.
      // The distance from this site to each and other one needs to be recalculated.
      int numberOfSites = countSites();
      assertThat(countUncalculatedDistance()).isEqualTo(numberOfSites - 1);
    }

    static List<ManageSiteDao.SiteField> updateAddressFieldUpdatesDistanceMatrix() {
      return Arrays.stream(ManageSiteDao.SiteField.values())
          .filter(ManageSiteDao.SiteField::isLocationField)
          .toList();
    }

    /**
     * Populates the site_distance_matrix so the whole thing appears to be filled in, all distances
     * calculated.
     */
    private static void fillInDistanceMatrix() {
      String setupData =
          """
          delete from site_distance_matrix;
          insert into site_distance_matrix(site1_id, site2_id, distance_miles, drive_time_seconds, valid)
          select s1.id, s2.id, 10.0, 60, true
          from site s1
          cross join site s2
          where s1.id < s2.id;
          """;

      TestConfiguration.jdbiTest.withHandle(handle -> handle.createScript(setupData).execute());
    }

    private static int countSites() {
      String countSites = "select count(*) from site";
      return TestConfiguration.jdbiTest.withHandle(
          handle -> handle.createQuery(countSites).mapTo(Integer.class).one());
    }

    private static int countUncalculatedDistance() {
      String countNumberOfUncalculatedDistances =
          "select count(*) from site_distance_matrix where valid is null";
      return TestConfiguration.jdbiTest.withHandle(
          handle ->
              handle.createQuery(countNumberOfUncalculatedDistances).mapTo(Integer.class).one());
    }
  }
}
