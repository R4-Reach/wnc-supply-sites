package com.vanatta.helene.supplies.database.admin.item;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.TestConfiguration.ItemResult;
import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.delivery.DeliveryDao;
import com.vanatta.helene.supplies.database.delivery.DeliveryUpdate;
import com.vanatta.helene.supplies.database.manage.inventory.InventoryDao;
import com.vanatta.helene.supplies.database.manage.inventory.ItemTagDao;
import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MergeItemsControllerTest {

  @BeforeAll
  static void cleanDb() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void queryForItems() {
    var results = MergeItemsController.fetchAllItems(jdbiTest);

    results.forEach(
        result -> {
          assertThat(result.getId()).isGreaterThan(0);
          assertThat(result.getItemName()).isNotNull();
          assertThat(result.getDateCreated()).isNotNull();
        });
  }

  @Test
  void merge() {
    // create one site with item A
    // create another site with item B
    // create another site with items A,B,C
    // create another site with items B,C

    // merge B & C into A
    // expect that each site has just item A

    String site1 = TestConfiguration.addSite("site1");
    long site1Id = TestConfiguration.getSiteId(site1);

    String site2 = TestConfiguration.addSite("site2");
    long site2Id = TestConfiguration.getSiteId(site2);

    String site3 = TestConfiguration.addSite("site3");
    long site3Id = TestConfiguration.getSiteId(site3);

    String site4 = TestConfiguration.addSite("site4");
    long site4Id = TestConfiguration.getSiteId(site4);

    ItemResult itemA = TestConfiguration.addItem("A");
    ItemResult itemB = TestConfiguration.addItem("B");
    ItemResult itemC = TestConfiguration.addItem("C");

    // add a tags, we do not expect tags to be merged, but we need to be sure we handle the FK
    // constraints.
    ItemTagDao.updateDescriptionTags(jdbiTest, itemA.getWssId(), List.of("Tag 1"));
    ItemTagDao.updateDescriptionTags(jdbiTest, itemB.getWssId(), List.of("Tag 2"));

    TestConfiguration.addItemToSite(site1Id, ItemStatus.AVAILABLE, itemA.getName(), -500);
    TestConfiguration.addItemToSite(site2Id, ItemStatus.AVAILABLE, itemB.getName(), -501);
    TestConfiguration.addItemToSite(site3Id, ItemStatus.AVAILABLE, itemA.getName(), -502);
    TestConfiguration.addItemToSite(site3Id, ItemStatus.AVAILABLE, itemB.getName(), -503);
    TestConfiguration.addItemToSite(site3Id, ItemStatus.AVAILABLE, itemC.getName(), -504);
    TestConfiguration.addItemToSite(site4Id, ItemStatus.AVAILABLE, itemB.getName(), -505);
    TestConfiguration.addItemToSite(site4Id, ItemStatus.AVAILABLE, itemC.getName(), -506);

    // now set up site2 with a delivery of item A, B
    // set up site3 with a delivery of items: B, C
    // After merge we expect all of these deliveries to contain the same item names!
    // Deliveries can store items by name, rather than ID. In the merge, we should
    // move the items from ID to be a denormalized name that is now part of the delivery.
    long site2WssId = SiteDetailDao.lookupSiteById(jdbiTest, site2Id).getWssId();
    long site3WssId = SiteDetailDao.lookupSiteById(jdbiTest, site2Id).getWssId();

    InventoryDao.updateSiteItemAudit(jdbiTest, site2Id, itemB.getName(), "old", "new");
    DeliveryDao.upsert(
        jdbiTest,
        DeliveryUpdate.builder()
            .deliveryId(-3001L)
            .dispatcherCode("ZAQW")
            .publicUrlKey("ZAQW")
            .dropOffSiteWssId(List.of(site2WssId))
            .itemListWssIds(List.of(itemA.getWssId(), itemB.getWssId()))
            .build());
    DeliveryDao.upsert(
        jdbiTest,
        DeliveryUpdate.builder()
            .deliveryId(-3002L)
            .dispatcherCode("ZAQZ")
            .publicUrlKey("ZAQZ")
            .dropOffSiteWssId(List.of(site3WssId))
            .itemListWssIds(List.of(itemB.getWssId(), itemC.getWssId()))
            .build());

    new MergeItemsController(jdbiTest)
        .doMerge(
            Map.of(
                "mergeInto", itemA.getId(), "mergeItems", List.of(itemB.getId(), itemC.getId())));

    // verify the site inventory is updated
    for (long siteId : List.of(site1Id, site2Id, site3Id, site4Id)) {
      var results = fetchItemsForSite(siteId);
      assertThat(results)
          .describedAs(
              String.format(
                  "Site ID: %s, name: %s",
                  siteId, SiteDetailDao.lookupSiteById(jdbiTest, siteId).getSiteName()))
          .containsExactly(itemA.getName());
    }

    // verify the site delivery data is updated
    assertThat(DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, "ZAQW").orElseThrow().getItemList())
        .contains(itemA.getName(), itemB.getName());
    assertThat(DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, "ZAQZ").orElseThrow().getItemList())
        .containsExactly(itemB.getName(), itemC.getName());

    // validate that the item is removed from the master item list
    assertThat(fetchAllItems()).doesNotContain(itemB.getName(), itemC.getName());
  }

  static List<String> fetchItemsForSite(long siteId) {
    String query =
        """
         select
              i.name
         from site s
         join site_item si on si.site_id = s.id
         join item i on i.id = si.item_id
         where s.id = :siteId
    """;
    return jdbiTest.withHandle(
        handle -> handle.createQuery(query).bind("siteId", siteId).mapTo(String.class).list());
  }

  static List<String> fetchAllItems() {
    return jdbiTest.withHandle(
        h -> h.createQuery("select name from item").mapTo(String.class).list());
  }

  @Test
  void fetchWssIds() {
    var newItem = TestConfiguration.addItem("new item");

    var result = MergeItemsController.fetchWssIdsOfItems(jdbiTest, List.of(newItem.getId()));

    assertThat(result).containsExactly(newItem.getWssId());
  }
}
