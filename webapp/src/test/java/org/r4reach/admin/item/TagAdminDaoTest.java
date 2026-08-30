package org.r4reach.admin.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.TestConfiguration.ItemResult;

class TagAdminDaoTest {

  @BeforeEach
  void setup() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void createTagAddsToRegistry() {
    Optional<Long> id = TagAdminDao.createTag(jdbiTest, "Baby");

    assertThat(id).isPresent();
    assertThat(TagAdminDao.fetchAllTags(jdbiTest))
        .extracting(TagAdminDao.TagRow::getName)
        .contains("Baby");
  }

  @Test
  void createTagTrimsWhitespace() {
    TagAdminDao.createTag(jdbiTest, "  Livestock  ");

    assertThat(TagAdminDao.fetchAllTags(jdbiTest))
        .extracting(TagAdminDao.TagRow::getName)
        .contains("Livestock");
  }

  @Test
  void createTagRejectsBlankTooLongCommaAndDuplicate() {
    assertThat(TagAdminDao.createTag(jdbiTest, "   ")).isEmpty();
    assertThat(TagAdminDao.createTag(jdbiTest, "no,commas")).isEmpty();
    assertThat(TagAdminDao.createTag(jdbiTest, "x".repeat(65))).isEmpty();

    assertThat(TagAdminDao.createTag(jdbiTest, "Dup")).isPresent();
    assertThat(TagAdminDao.createTag(jdbiTest, "Dup")).isEmpty();
  }

  @Test
  void renameTagChangesTheName() {
    long id = TagAdminDao.createTag(jdbiTest, "Old").orElseThrow();

    assertThat(TagAdminDao.renameTag(jdbiTest, id, "New")).isTrue();

    assertThat(TagAdminDao.fetchAllTags(jdbiTest))
        .extracting(TagAdminDao.TagRow::getName)
        .contains("New")
        .doesNotContain("Old");
  }

  @Test
  void renameTagRejectsCollisionWithAnotherTag() {
    TagAdminDao.createTag(jdbiTest, "Taken");
    long id = TagAdminDao.createTag(jdbiTest, "Mine").orElseThrow();

    assertThat(TagAdminDao.renameTag(jdbiTest, id, "Taken")).isFalse();
    assertThat(TagAdminDao.fetchAllTags(jdbiTest))
        .extracting(TagAdminDao.TagRow::getName)
        .contains("Mine", "Taken");
  }

  @Test
  void deleteTagRemovesItAndItsAssignments() {
    ItemResult item = TestConfiguration.addItem("Diapers");
    long tagId = TagAdminDao.createTag(jdbiTest, "Baby").orElseThrow();
    TagAdminDao.setAssignment(jdbiTest, item.getId(), tagId, true);

    TagAdminDao.deleteTag(jdbiTest, tagId);

    assertThat(TagAdminDao.fetchAllTags(jdbiTest))
        .extracting(TagAdminDao.TagRow::getName)
        .doesNotContain("Baby");
    assertThat(TagAdminDao.fetchAssignments(jdbiTest)).isEmpty();
  }

  @Test
  void setAssignmentTogglesAndIsIdempotent() {
    ItemResult item = TestConfiguration.addItem("Blankets");
    long tagId = TagAdminDao.createTag(jdbiTest, "Winter").orElseThrow();

    // assign twice — no error, one row
    TagAdminDao.setAssignment(jdbiTest, item.getId(), tagId, true);
    TagAdminDao.setAssignment(jdbiTest, item.getId(), tagId, true);
    assertThat(TagAdminDao.fetchAssignments(jdbiTest))
        .containsExactly(new TagAdminDao.Assignment(item.getId(), tagId));

    // unassign twice — no error, gone
    TagAdminDao.setAssignment(jdbiTest, item.getId(), tagId, false);
    TagAdminDao.setAssignment(jdbiTest, item.getId(), tagId, false);
    assertThat(TagAdminDao.fetchAssignments(jdbiTest)).isEmpty();
  }

  @Test
  void fetchAllTagsReportsAssignmentCount() {
    ItemResult item1 = TestConfiguration.addItem("Water");
    ItemResult item2 = TestConfiguration.addItem("Soap");
    long tagId = TagAdminDao.createTag(jdbiTest, "Essential").orElseThrow();
    TagAdminDao.createTag(jdbiTest, "Unused");

    TagAdminDao.setAssignment(jdbiTest, item1.getId(), tagId, true);
    TagAdminDao.setAssignment(jdbiTest, item2.getId(), tagId, true);

    List<TagAdminDao.TagRow> tags = TagAdminDao.fetchAllTags(jdbiTest);
    assertThat(tags)
        .filteredOn(t -> t.getName().equals("Essential"))
        .singleElement()
        .satisfies(t -> assertThat(t.getItemCount()).isEqualTo(2));
    assertThat(tags)
        .filteredOn(t -> t.getName().equals("Unused"))
        .singleElement()
        .satisfies(t -> assertThat(t.getItemCount()).isZero());
  }
}
