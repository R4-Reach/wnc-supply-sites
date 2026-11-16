package org.r4reach.manage.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.data.ItemStatus;
import org.r4reach.manage.SelectSiteController;
import org.springframework.web.servlet.ModelAndView;

class InventoryControllerTest {

  private final InventoryController controller = new InventoryController(TestConfiguration.jdbiTest);

  @BeforeAll
  static void setup() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void updateSiteItem_unauthorizedSite_redirects() {
    long siteId = TestConfiguration.getSiteId("site1");
    ModelAndView mv =
        controller.updateSiteItem(
            List.of(), // user has no sites
            Map.of("siteId", String.valueOf(siteId), "itemName", "water", "active", "true"));
    assertThat(mv.getViewName()).isEqualTo("redirect:" + SelectSiteController.PATH_SELECT_SITE);
  }

  @Test
  void updateSiteItem_changesStatusAcrossAllFourTypes() {
    long siteId = TestConfiguration.getSiteId("site1");
    List<Long> userSites = List.of(siteId);
    String itemName = "gloves";

    // Ensure initial clean state: inactive
    InventoryDao.updateSiteItemInactive(TestConfiguration.jdbiTest, siteId, itemName);
    assertThat(InventoryDao.isItemActive(TestConfiguration.jdbiTest, siteId, itemName)).isFalse();

    // 1. Activate item with default status (Available)
    ModelAndView mv =
        controller.updateSiteItem(
            userSites,
            Map.of("siteId", String.valueOf(siteId), "itemName", itemName, "active", "true"));
    InventoryController.ItemInventoryDisplay row =
        (InventoryController.ItemInventoryDisplay) mv.getModel().get("row");
    assertThat(row.getItemChecked()).isEqualTo("checked");
    assertThat(row.getAvailableChecked()).isEqualTo("checked");
    assertThat(InventoryDao.isItemActive(TestConfiguration.jdbiTest, siteId, itemName)).isTrue();
    assertThat(InventoryDao.fetchItemStatus(TestConfiguration.jdbiTest, siteId, itemName))
        .isEqualTo(ItemStatus.AVAILABLE);

    // 2. Change status to Urgently Needed
    mv =
        controller.updateSiteItem(
            userSites,
            Map.of(
                "siteId",
                String.valueOf(siteId),
                "itemName",
                itemName,
                "active",
                "true",
                "itemStatus",
                ItemStatus.URGENTLY_NEEDED.getText()));
    row = (InventoryController.ItemInventoryDisplay) mv.getModel().get("row");
    assertThat(row.getUrgentChecked()).isEqualTo("checked");
    assertThat(row.getAvailableChecked()).isEmpty();
    assertThat(InventoryDao.fetchItemStatus(TestConfiguration.jdbiTest, siteId, itemName))
        .isEqualTo(ItemStatus.URGENTLY_NEEDED);

    // 3. Change status to Needed
    mv =
        controller.updateSiteItem(
            userSites,
            Map.of(
                "siteId",
                String.valueOf(siteId),
                "itemName",
                itemName,
                "active",
                "true",
                "itemStatus",
                ItemStatus.NEEDED.getText()));
    row = (InventoryController.ItemInventoryDisplay) mv.getModel().get("row");
    assertThat(row.getNeededChecked()).isEqualTo("checked");
    assertThat(row.getUrgentChecked()).isEmpty();
    assertThat(InventoryDao.fetchItemStatus(TestConfiguration.jdbiTest, siteId, itemName))
        .isEqualTo(ItemStatus.NEEDED);

    // 4. Change status to Oversupply
    mv =
        controller.updateSiteItem(
            userSites,
            Map.of(
                "siteId",
                String.valueOf(siteId),
                "itemName",
                itemName,
                "active",
                "true",
                "itemStatus",
                ItemStatus.OVERSUPPLY.getText()));
    row = (InventoryController.ItemInventoryDisplay) mv.getModel().get("row");
    assertThat(row.getOversupplyChecked()).isEqualTo("checked");
    assertThat(row.getNeededChecked()).isEmpty();
    assertThat(InventoryDao.fetchItemStatus(TestConfiguration.jdbiTest, siteId, itemName))
        .isEqualTo(ItemStatus.OVERSUPPLY);

    // 5. Change status back to Available
    mv =
        controller.updateSiteItem(
            userSites,
            Map.of(
                "siteId",
                String.valueOf(siteId),
                "itemName",
                itemName,
                "active",
                "true",
                "itemStatus",
                ItemStatus.AVAILABLE.getText()));
    row = (InventoryController.ItemInventoryDisplay) mv.getModel().get("row");
    assertThat(row.getAvailableChecked()).isEqualTo("checked");
    assertThat(row.getOversupplyChecked()).isEmpty();
    assertThat(InventoryDao.fetchItemStatus(TestConfiguration.jdbiTest, siteId, itemName))
        .isEqualTo(ItemStatus.AVAILABLE);

    // 6. Deactivate item
    mv =
        controller.updateSiteItem(
            userSites, Map.of("siteId", String.valueOf(siteId), "itemName", itemName));
    row = (InventoryController.ItemInventoryDisplay) mv.getModel().get("row");
    assertThat(row.getItemChecked()).isEmpty();
    assertThat(InventoryDao.isItemActive(TestConfiguration.jdbiTest, siteId, itemName)).isFalse();
  }
}
