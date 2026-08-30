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

  @Test
  void nameErrorReportsTheSpecificReason() {
    assertThat(TagAdminDao.nameError("   ")).get().asString().contains("Enter");
    assertThat(TagAdminDao.nameError("x".repeat(65))).get().asString().contains("64");
    assertThat(TagAdminDao.nameError("no,commas")).get().asString().contains("comma");
    assertThat(TagAdminDao.nameError("Fine")).isEmpty();
  }

  @Test
  void createTagRejectsCaseInsensitiveDuplicate() {
    assertThat(TagAdminDao.createTag(jdbiTest, "Medical")).isPresent();
    // A different casing of an existing name must not create a second tag.
    assertThat(TagAdminDao.createTag(jdbiTest, "medical")).isEmpty();
    assertThat(TagAdminDao.fetchAllTags(jdbiTest))
        .filteredOn(t -> t.getName().equalsIgnoreCase("medical"))
        .hasSize(1);
  }

  @Test
  void renameRejectsCaseInsensitiveCollisionButAllowsOwnRecasing() {
    TagAdminDao.createTag(jdbiTest, "Baby");
    long id = TagAdminDao.createTag(jdbiTest, "Winter").orElseThrow();

    // Colliding with another tag, even by casing only, is rejected.
    assertThat(TagAdminDao.renameTag(jdbiTest, id, "baby")).isFalse();
    // Re-casing a tag's own name is allowed.
    assertThat(TagAdminDao.renameTag(jdbiTest, id, "WINTER")).isTrue();
    assertThat(TagAdminDao.fetchTag(jdbiTest, id))
        .get()
        .extracting(TagAdminDao.TagRow::getName)
        .isEqualTo("WINTER");
  }

  @Test
  void fetchTagReturnsLiveCount() {
    ItemResult item = TestConfiguration.addItem("Gloves");
    long tagId = TagAdminDao.createTag(jdbiTest, "Warm").orElseThrow();
    assertThat(TagAdminDao.fetchTag(jdbiTest, tagId))
        .get()
        .satisfies(t -> assertThat(t.getItemCount()).isZero());

    TagAdminDao.setAssignment(jdbiTest, item.getId(), tagId, true);
    assertThat(TagAdminDao.fetchTag(jdbiTest, tagId))
        .get()
        .satisfies(t -> assertThat(t.getItemCount()).isEqualTo(1));
  }

  @Test
  void bulkAssignAndRemoveAcrossManyItems() {
    ItemResult a = TestConfiguration.addItem("Rice");
    ItemResult b = TestConfiguration.addItem("Beans");
    ItemResult c = TestConfiguration.addItem("Flour");
    long tagId = TagAdminDao.createTag(jdbiTest, "Pantry").orElseThrow();

    TagAdminDao.setAssignmentBulk(jdbiTest, List.of(a.getId(), b.getId(), c.getId()), tagId, true);
    assertThat(TagAdminDao.fetchTag(jdbiTest, tagId))
        .get()
        .satisfies(t -> assertThat(t.getItemCount()).isEqualTo(3));

    // Idempotent: re-assigning is a no-op, removing a subset works, empty list is a no-op.
    TagAdminDao.setAssignmentBulk(jdbiTest, List.of(a.getId(), b.getId()), tagId, true);
    TagAdminDao.setAssignmentBulk(jdbiTest, List.of(), tagId, false);
    TagAdminDao.setAssignmentBulk(jdbiTest, List.of(a.getId(), b.getId()), tagId, false);
    assertThat(TagAdminDao.fetchTag(jdbiTest, tagId))
        .get()
        .satisfies(t -> assertThat(t.getItemCount()).isEqualTo(1));
  }
}
