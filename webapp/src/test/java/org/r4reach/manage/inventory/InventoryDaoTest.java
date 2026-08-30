package org.r4reach.manage.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.data.ItemStatus;
import org.r4reach.manage.ManageSiteDao;
import org.r4reach.supplies.SiteSupplyRequest;
import org.r4reach.supplies.SuppliesDao;
import org.r4reach.supplies.site.details.SiteDetailDao;

class InventoryDaoTest {

  @BeforeAll
  static void setup() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void updateSiteItemActive() {
    long siteId = TestConfiguration.getSiteId("site1");

    // first make sure 'gloves' are not active
    var result = ManageSiteDao.fetchSiteInventory(jdbiTest, siteId);
    ManageSiteDao.SiteInventory gloves = findItemByName(result, "gloves");
    assertThat(gloves.isActive()).isFalse();

    // set gloves to back to 'active'
    InventoryDao.updateSiteItemActive(jdbiTest, siteId, "gloves", "Oversupply");

    // verify gloves are active
    result = ManageSiteDao.fetchSiteInventory(jdbiTest, siteId);
    gloves = findItemByName(result, "gloves");
    assertThat(gloves.isActive()).isTrue();

    // set gloves to back to 'inactive'
    InventoryDao.updateSiteItemInactive(jdbiTest, siteId, "gloves");

    // verify gloves are inactive
    result = ManageSiteDao.fetchSiteInventory(jdbiTest, siteId);
    gloves = findItemByName(result, "gloves");
    assertThat(gloves.isActive()).isFalse();
  }

  @Test
  void updateSiteItemStatus() {
    long siteId = TestConfiguration.getSiteId("site1");

    // validate gloves status is 'Available'
    var result = ManageSiteDao.fetchSiteInventory(jdbiTest, siteId);
    ManageSiteDao.SiteInventory water = findItemByName(result, "water");
    assertThat(water.getItemStatus()).isEqualTo(ItemStatus.AVAILABLE.getText());

    // change gloves status to 'Urgent Need'
    InventoryDao.updateItemStatus(jdbiTest, siteId, "water", ItemStatus.URGENTLY_NEEDED.getText());

    // validation (1)
    var newStatus = InventoryDao.fetchItemStatus(jdbiTest, siteId, "water");
    assertThat(newStatus).isEqualTo(ItemStatus.URGENTLY_NEEDED);

    // validation (2) water status is updated 'Urgent Need'
    result = ManageSiteDao.fetchSiteInventory(jdbiTest, siteId);
    water = findItemByName(result, "water");
    assertThat(water.getItemStatus()).isEqualTo(ItemStatus.URGENTLY_NEEDED.getText());

    // change water status to 'Oversupply'
    InventoryDao.updateItemStatus(jdbiTest, siteId, "water", ItemStatus.OVERSUPPLY.getText());

    // validate water status is updated 'Oversupply'
    newStatus = InventoryDao.fetchItemStatus(jdbiTest, siteId, "water");
    assertThat(newStatus).isEqualTo(ItemStatus.OVERSUPPLY);

    result = ManageSiteDao.fetchSiteInventory(jdbiTest, siteId);
    water = findItemByName(result, "water");
    assertThat(water.getItemStatus()).isEqualTo(ItemStatus.OVERSUPPLY.getText());

    // change water status to 'Need'
    InventoryDao.updateItemStatus(jdbiTest, siteId, "water", ItemStatus.NEEDED.getText());

    // validate water status is updated 'Need'
    newStatus = InventoryDao.fetchItemStatus(jdbiTest, siteId, "water");
    assertThat(newStatus).isEqualTo(ItemStatus.NEEDED);

    result = ManageSiteDao.fetchSiteInventory(jdbiTest, siteId);
    water = findItemByName(result, "water");
    assertThat(water.getItemStatus()).isEqualTo(ItemStatus.NEEDED.getText());

    // change gloves status back to 'Available'
    InventoryDao.updateItemStatus(jdbiTest, siteId, "water", ItemStatus.AVAILABLE.getText());

    // validate gloves status is updated 'Need'
    result = ManageSiteDao.fetchSiteInventory(jdbiTest, siteId);
    water = findItemByName(result, "water");
    assertThat(water.getItemStatus()).isEqualTo(ItemStatus.AVAILABLE.getText());
  }

  @Test
  void addItem() {
    int itemCountPreInsert = countItems();

    boolean result = InventoryDao.addNewItem(jdbiTest, "new item");
    assertThat(result).isTrue();

    int itemCountPostInsert = countItems();
    assertThat(itemCountPreInsert + 1).isEqualTo(itemCountPostInsert);
  }

  private static int countItems() {
    return jdbiTest.withHandle(
        handle -> handle.createQuery("select count(*) from item").mapTo(Integer.class).one());
  }

  @Test
  void duplicateItemIsNoOp() {
    InventoryDao.addNewItem(jdbiTest, "some item");
    boolean result = InventoryDao.addNewItem(jdbiTest, "SOME ITEM");
    assertThat(result).isFalse();
  }

  private static ManageSiteDao.SiteInventory findItemByName(
      List<ManageSiteDao.SiteInventory> items, String itemName) {
    return items.stream()
        .filter(r -> r.getItemName().equalsIgnoreCase(itemName))
        .findAny()
        .orElseThrow();
  }

  @Test
  void validateSiteAuditChanges() {
    String name = UUID.randomUUID().toString();
    int startCount = countSiteItemAuditRecords();

    InventoryDao.addNewItem(jdbiTest, name);

    // adding a new item should not change the site_item_audit count
    assertThat(countSiteItemAuditRecords()).isEqualTo(startCount);

    long site1Id = TestConfiguration.getSiteId("site1");
    InventoryDao.updateSiteItemActive(jdbiTest, site1Id, name, ItemStatus.AVAILABLE.getText());
    assertThat(countSiteItemAuditRecords()).isEqualTo(startCount + 1);

    InventoryDao.updateItemStatus(jdbiTest, site1Id, name, ItemStatus.URGENTLY_NEEDED.getText());
    assertThat(countSiteItemAuditRecords()).isEqualTo(startCount + 2);
    InventoryDao.updateItemStatus(jdbiTest, site1Id, name, ItemStatus.NEEDED.getText());
    assertThat(countSiteItemAuditRecords()).isEqualTo(startCount + 3);

    InventoryDao.updateSiteItemInactive(jdbiTest, site1Id, name);
    assertThat(countSiteItemAuditRecords()).isEqualTo(startCount + 4);
  }

  private static int countSiteItemAuditRecords() {
    return jdbiTest.withHandle(
        handle ->
            handle.createQuery("select count(*) from site_item_audit").mapTo(Integer.class).one());
  }

  @Nested
  class MarkNoLongerNeeded {

    /**
     *
     *
     * <pre>
     * (1) Set up a site with various items that are both needed & available & oversupply
     * (2) Mark all these items as no longer needed.
     * (3) validate:
     *   - needed items are now available
     *   - available items are still available
     *   - oversupply items are still oversupply
     * </pre>
     */
    @Test
    void markSiteItemsNotNeeded() {
      String newSiteName = TestConfiguration.addSite();
      long newSiteId = TestConfiguration.getSiteId(newSiteName);
      var siteDetail = SiteDetailDao.lookupSiteById(jdbiTest, newSiteId);

      InventoryDao.updateSiteItemActive(
          jdbiTest, newSiteId, "gloves", ItemStatus.URGENTLY_NEEDED.getText());
      InventoryDao.updateSiteItemActive(
          jdbiTest, newSiteId, "water", ItemStatus.URGENTLY_NEEDED.getText());
      InventoryDao.updateSiteItemActive(
          jdbiTest, newSiteId, "batteries", ItemStatus.NEEDED.getText());
      InventoryDao.updateSiteItemActive(
          jdbiTest, newSiteId, "heater", ItemStatus.AVAILABLE.getText());
      InventoryDao.updateSiteItemActive(
          jdbiTest, newSiteId, "used clothes", ItemStatus.OVERSUPPLY.getText());
      InventoryDao.updateSiteItemActive(
          jdbiTest, newSiteId, "new clothes", ItemStatus.OVERSUPPLY.getText());

      InventoryDao.markItemsAsNotNeeded(
          jdbiTest,
          siteDetail.getWssId(),
          List.of(
              TestConfiguration.GLOVES_WSS_ID,
              TestConfiguration.WATER_WSS_ID,
              TestConfiguration.BATTERIES_WSS_ID,
              TestConfiguration.HEATER_WSS_ID,
              TestConfiguration.USED_CLOTHES_WSS_ID,
              TestConfiguration.NEW_CLOTHES_WSS_ID));

      var supplyResults =
          SuppliesDao.getSupplyResults(
              jdbiTest,
              SiteSupplyRequest.builder().sites(List.of(newSiteName)).build(),
              List.of("NC"));
      confirmItemStatus(supplyResults, "gloves", ItemStatus.AVAILABLE);
      confirmItemStatus(supplyResults, "water", ItemStatus.AVAILABLE);
      confirmItemStatus(supplyResults, "batteries", ItemStatus.AVAILABLE);
      confirmItemStatus(supplyResults, "heater", ItemStatus.AVAILABLE);
      confirmItemStatus(supplyResults, "used clothes", ItemStatus.OVERSUPPLY);
      confirmItemStatus(supplyResults, "new clothes", ItemStatus.OVERSUPPLY);
    }

    private static void confirmItemStatus(
        List<SuppliesDao.SuppliesQueryResult> results, String item, ItemStatus desiredStatus) {
      var result =
          results.stream()
              .filter(r -> r.getItem().equals(item))
              .findAny()
              .orElseThrow(() -> new IllegalStateException("Did not find item " + item));
      assertThat(result.getItemStatus()).isEqualTo(desiredStatus.getText());
    }

    /**
     * Make sure that if we try the mark 'incorrect' items is okay. An item might be removed from a
     * site, and then the delivery is completed. We should make sure that this is okay and that the
     * item is not added back to the site.
     */
    @Test
    void markItemsAsNotNeeded_inActiveItemsStayInactive() {
      String newSiteName = TestConfiguration.addSite();
      long newSiteId = TestConfiguration.getSiteId(newSiteName);
      var siteDetail = SiteDetailDao.lookupSiteById(jdbiTest, newSiteId);

      InventoryDao.markItemsAsNotNeeded(
          jdbiTest, siteDetail.getWssId(), List.of(TestConfiguration.GLOVES_WSS_ID));

      // we added no inventory to this new site. Even after marking an item as not needed
      // the site should still have no inventory.
      SuppliesDao.getSupplyResults(
              jdbiTest,
              SiteSupplyRequest.builder().sites(List.of(newSiteName)).build(),
              List.of("NC"))
          .forEach(r -> assertThat(r.getItem()).isNull());
    }

    @Test
    void itemTagsAreReturned() {
      String newSiteName = TestConfiguration.addSite();
      long newSiteId = TestConfiguration.getSiteId(newSiteName);
      InventoryDao.updateSiteItemActive(
          jdbiTest, newSiteId, "gloves", ItemStatus.URGENTLY_NEEDED.getText());
      ItemTagDao.updateDescriptionTags(
          jdbiTest, TestConfiguration.GLOVES_WSS_ID, List.of("toddler"));

      // we added no inventory to this new site. Even after marking an item as not needed
      // the site should still have no inventory.
      SuppliesDao.getSupplyResults(
              jdbiTest,
              SiteSupplyRequest.builder().sites(List.of(newSiteName)).build(),
              List.of("NC"))
          .forEach(r -> assertThat(r.getItemTags()).contains("toddler"));
    }
  }
}
