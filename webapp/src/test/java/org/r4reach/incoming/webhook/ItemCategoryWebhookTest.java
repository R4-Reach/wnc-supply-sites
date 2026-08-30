package org.r4reach.incoming.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.manage.ManageSiteDao.ItemTagData;
import org.r4reach.manage.inventory.InventoryDao;
import org.r4reach.manage.inventory.ItemTagDao;

class ItemCategoryWebhookTest {
  static final String liveStockInput =
      """
      {"wssId":212,"tags":["Livestock", "Baby"]}
      """;
  static final String sampleInputWithNoInputs =
      """
      {"wssId":214,"tags":[]}
      """;

  @Nested
  class Parsing {
    @Test
    void parseWithTags() {
      ItemCategoryWebhook.ItemTagsInput itemTagsInput =
          ItemCategoryWebhook.ItemTagsInput.parse(liveStockInput);
      assertThat(itemTagsInput.getWssId()).isEqualTo(212L);
      assertThat(itemTagsInput.getTags()).contains("Livestock", "Baby");
    }

    @Test
    void parseWithoutTags() {
      ItemCategoryWebhook.ItemTagsInput itemTagsInput =
          ItemCategoryWebhook.ItemTagsInput.parse(sampleInputWithNoInputs);
      assertThat(itemTagsInput.getWssId()).isEqualTo(214L);
      assertThat(itemTagsInput.getTags()).isEmpty();
    }
  }

  /**
   * Add an item category, attached to 'gloves'. Fetch inventory, assert that gloves are in the list
   * and have the new item categories.
   *
   * <p>Note: The inventory we are fetching is for the 'manage inventory' page, it is not the
   * inventory listed on the 'supply search' page.
   */
  @Test
  void itemCategoriesAreReturnedInInventoryResults() {
    ItemCategoryWebhook itemCategoryWebhook = new ItemCategoryWebhook(jdbiTest);
    String input =
        String.format(
            """
      {"wssId":%s,"tags":["Work", "Construction"]}
      """,
            TestConfiguration.GLOVES_WSS_ID);
    var response = itemCategoryWebhook.updateItemTags(input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).contains("Work", "Construction");

    var results = InventoryDao.fetchSiteInventory(jdbiTest, TestConfiguration.getSiteId());

    var gloves =
        results.stream().filter(i -> i.getItemName().equals("gloves")).findAny().orElseThrow();
    assertThat(gloves.getTags())
        .extracting(ItemTagData::getTagName)
        .contains("Work", "Construction");
  }

  /**
   * Validates that when we add item tags to an item, then update them, that we overwrite the
   * existing tags.
   */
  @Test
  void itemCategoryTagsAreOverwritten() {
    ItemCategoryWebhook itemCategoryWebhook = new ItemCategoryWebhook(jdbiTest);
    String input =
        String.format(
            """
      {"wssId":%s,"tags":["Work", "Construction"]}
      """,
            TestConfiguration.GLOVES_WSS_ID);
    var response = itemCategoryWebhook.updateItemTags(input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    /* We now overwrite the tag 'Work' with 'New Tag' */
    input =
        String.format(
            """
      {"wssId":%s,"tags":["New Tag", "Construction"]}
      """,
            TestConfiguration.GLOVES_WSS_ID);
    response = itemCategoryWebhook.updateItemTags(input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    var results = InventoryDao.fetchSiteInventory(jdbiTest, TestConfiguration.getSiteId());

    var gloves =
        results.stream().filter(i -> i.getItemName().equals("gloves")).findAny().orElseThrow();
    assertThat(gloves.getTags())
        .extracting(ItemTagData::getTagName)
        .contains("New Tag", "Construction");
    assertThat(gloves.getTags()).extracting(ItemTagData::getTagName).doesNotContain("Work");
  }

  /**
   * Check that if a tag is not attached to any items, that we do not return the tag.
   *
   * <p>Add some tags to an item, then change those tags, thereby orphaning the original tags.
   * Assert that the orphaned tags are not returned in the list of all tags.
   */
  @Test
  void itemTagsWithNoItemsAreNotReturned() {
    ItemCategoryWebhook itemCategoryWebhook = new ItemCategoryWebhook(jdbiTest);
    String input =
        String.format(
            """
      {"wssId":%s,"tags":["Orphan", "Construction"]}
      """,
            TestConfiguration.GLOVES_WSS_ID);
    var response = itemCategoryWebhook.updateItemTags(input);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    String updatedTags =
        String.format(
            """
      {"wssId":%s,"tags":["Construction"]}
      """,
            TestConfiguration.GLOVES_WSS_ID);
    response = itemCategoryWebhook.updateItemTags(updatedTags);
    assertThat(response.getStatusCode().value()).isEqualTo(200);

    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).doesNotContain("Orphan");
  }
}
