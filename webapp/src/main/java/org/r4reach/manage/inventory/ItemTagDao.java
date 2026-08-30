package org.r4reach.manage.inventory;

import java.util.List;
import org.jdbi.v3.core.Jdbi;

public class ItemTagDao {

  public static void updateDescriptionTags(Jdbi jdbi, long wssId, List<String> tags) {
    // remove previous tags
    jdbi.withHandle(
        h ->
            h.createUpdate(
                    """
                       delete from item_tag where item_id =
                            (select id from item where wss_id = :wssId)
                       """)
                .bind("wssId", wssId)
                .execute());

    for (String tag : tags) {
      String tagToInsert = tag.trim();
      if (tagToInsert.isBlank() || tagToInsert.contains(",")) {
        continue;
      }
      // Tags are now first-class registry rows; register the name (if new) before assigning it.
      jdbi.withHandle(
          h ->
              h.createUpdate("insert into tag(name) values(:tagName) on conflict(name) do nothing")
                  .bind("tagName", tagToInsert)
                  .execute());
      jdbi.withHandle(
          h ->
              h.createUpdate(
                      """
                   insert into item_tag(item_id, tag_id)
                   values(
                     (select id from item where wss_id = :wssId),
                     (select id from tag where name = :tagName)
                   ) on conflict(item_id, tag_id) do nothing
                   """)
                  .bind("wssId", wssId)
                  .bind("tagName", tagToInsert)
                  .execute());
    }
  }

  /**
   * The tags actually assigned to at least one item (registered-but-unassigned tags are omitted).
   */
  public static List<String> fetchAllDescriptionTags(Jdbi jdbi) {
    return jdbi.withHandle(
        h ->
            h.createQuery(
                    """
                    select distinct t.name
                    from item_tag it
                    join tag t on t.id = it.tag_id
                    order by t.name
                    """)
                .mapTo(String.class)
                .list());
  }
}
