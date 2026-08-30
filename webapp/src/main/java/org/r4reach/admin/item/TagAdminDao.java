package org.r4reach.admin.item;

import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

/**
 * CRUD over the {@code tag} registry and the {@code item_tag} assignments that link tags to catalog
 * items, backing the item-tagging admin UI. Tag names are stored trimmed; the column caps them at
 * 64 chars and commas are rejected (a comma is the delimiter used when tags are aggregated for
 * display, see {@code ManageSiteDao}).
 */
public class TagAdminDao {

  /** varchar(64) in the schema. */
  static final int MAX_TAG_LENGTH = 64;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TagRow {
    long id;
    String name;
    long itemCount;

    /** Grammar-correct assignment count for the tag list, e.g. "1 item" / "3 items". */
    public String getItemCountLabel() {
      return itemCount + (itemCount == 1 ? " item" : " items");
    }
  }

  /** Every tag in the registry, assigned or not, with how many items carry it. */
  public static List<TagRow> fetchAllTags(Jdbi jdbi) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    select t.id, t.name, count(it.item_id) itemCount
                    from tag t
                    left join item_tag it on it.tag_id = t.id
                    group by t.id, t.name
                    order by lower(t.name)
                    """)
                .mapToBean(TagRow.class)
                .list());
  }

  /**
   * Creates a tag. Returns empty if the name is invalid (blank, too long, or contains a comma) or a
   * tag with that name already exists.
   */
  public static Optional<Long> createTag(Jdbi jdbi, String rawName) {
    String name = normalize(rawName);
    if (name == null) {
      return Optional.empty();
    }
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    insert into tag(name) values(:name)
                    on conflict(name) do nothing
                    returning id
                    """)
                .bind("name", name)
                .mapTo(Long.class)
                .findOne());
  }

  /**
   * Renames a tag. Returns false if the new name is invalid or already taken by another tag; true
   * if the tag was renamed.
   */
  public static boolean renameTag(Jdbi jdbi, long tagId, String rawName) {
    String name = normalize(rawName);
    if (name == null) {
      return false;
    }
    try {
      int updated =
          jdbi.withHandle(
              handle ->
                  handle
                      .createUpdate("update tag set name = :name where id = :id")
                      .bind("name", name)
                      .bind("id", tagId)
                      .execute());
      return updated > 0;
    } catch (UnableToExecuteStatementException e) {
      // Unique-name violation: another tag already owns this name.
      return false;
    }
  }

  /** Deletes a tag; its item assignments cascade away with it. */
  public static void deleteTag(Jdbi jdbi, long tagId) {
    jdbi.withHandle(
        handle ->
            handle.createUpdate("delete from tag where id = :id").bind("id", tagId).execute());
  }

  /** Adds or removes a single tag assignment on an item. Idempotent in both directions. */
  public static void setAssignment(Jdbi jdbi, long itemId, long tagId, boolean assigned) {
    if (assigned) {
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(
                      """
                      insert into item_tag(item_id, tag_id) values(:itemId, :tagId)
                      on conflict(item_id, tag_id) do nothing
                      """)
                  .bind("itemId", itemId)
                  .bind("tagId", tagId)
                  .execute());
    } else {
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate("delete from item_tag where item_id = :itemId and tag_id = :tagId")
                  .bind("itemId", itemId)
                  .bind("tagId", tagId)
                  .execute());
    }
  }

  /** The (item_id, tag_id) pairs currently assigned, for building the assignment grid. */
  public static List<Assignment> fetchAssignments(Jdbi jdbi) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select item_id itemId, tag_id tagId from item_tag")
                .mapToBean(Assignment.class)
                .list());
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Assignment {
    long itemId;
    long tagId;
  }

  /**
   * Trims and validates a tag name. Returns null when the name is unusable: blank, longer than the
   * column allows, or containing a comma (the tag-aggregation delimiter).
   */
  private static String normalize(String rawName) {
    if (rawName == null) {
      return null;
    }
    String name = rawName.trim();
    if (name.isBlank() || name.length() > MAX_TAG_LENGTH || name.contains(",")) {
      return null;
    }
    return name;
  }
}
