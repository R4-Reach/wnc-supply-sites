package org.r4reach.manage.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.GLOVES_WSS_ID;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;

class ItemTagDaoTest {

  @BeforeEach
  void setup() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void addTagsUpdatesListOfAllTags() {
    ItemTagDao.updateDescriptionTags(jdbiTest, GLOVES_WSS_ID, List.of("tag1"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).containsExactly("tag1");

    // update the tag on gloves
    ItemTagDao.updateDescriptionTags(jdbiTest, GLOVES_WSS_ID, List.of("new-tag"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).containsExactly("new-tag");

    // add new item, with 2 new tags
    ItemTagDao.updateDescriptionTags(
        jdbiTest, TestConfiguration.WATER_WSS_ID, List.of("tag2", "tag3"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).contains("new-tag", "tag2", "tag3");

    // add a new item, with 1 new tag, and 1 tag that is already on another item.
    ItemTagDao.updateDescriptionTags(
        jdbiTest, TestConfiguration.HEATER_WSS_ID, List.of("tag3", "tag4"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest))
        .contains("new-tag", "tag2", "tag3", "tag4");

    // update tags on the second item added, removing one of its tags.
    // That tag is still on the most recent item, so the tag list should not change
    ItemTagDao.updateDescriptionTags(jdbiTest, TestConfiguration.WATER_WSS_ID, List.of("tag2"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).contains("new-tag", "tag2", "tag3");
  }

  /** Add the same tag multiple times to the same item. Should be fine, no errors. */
  @Test
  void addingSameTagsIsNoIssue() {
    ItemTagDao.updateDescriptionTags(jdbiTest, GLOVES_WSS_ID, List.of("tag1"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).containsExactly("tag1");

    // redo, should throw no errors
    ItemTagDao.updateDescriptionTags(jdbiTest, GLOVES_WSS_ID, List.of("tag1"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).containsExactly("tag1");
  }

  /**
   * Add tags to an item, then add the same tag again but with leading and trailing whitespace. The
   * whitespace should be trimmed.
   */
  @Test
  void leadingAndTrailingWhitespaceOnTagsIsTrimmed() {
    ItemTagDao.updateDescriptionTags(jdbiTest, GLOVES_WSS_ID, List.of("tag1"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).containsExactly("tag1");

    ItemTagDao.updateDescriptionTags(jdbiTest, GLOVES_WSS_ID, List.of("   tag1   "));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).containsExactly("tag1");
  }

  /** If we send an empty tag list for an item, then we are deleting the tags. */
  @Test
  void removeTagsOnAnItem() {
    ItemTagDao.updateDescriptionTags(jdbiTest, GLOVES_WSS_ID, List.of("new-tag"));
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).containsExactly("new-tag");

    // delete the tags on gloves, tag list should now be empty.
    ItemTagDao.updateDescriptionTags(jdbiTest, GLOVES_WSS_ID, List.of());
    assertThat(ItemTagDao.fetchAllDescriptionTags(jdbiTest)).isEmpty();
  }
}
