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
      jdbi.withHandle(
          h ->
              h.createUpdate(
                      """
                   insert into item_tag(item_id, tag_name)
                   values(
                     (select id from item where wss_id = :wssId),
                      :tagName
                   ) on conflict(item_id, tag_name) do nothing
                   """)
                  .bind("wssId", wssId)
                  .bind("tagName", tagToInsert)
                  .execute());
    }
  }

  public static List<String> fetchAllDescriptionTags(Jdbi jdbi) {
    return jdbi.withHandle(
        h ->
            h.createQuery(
                    """
                    select distinct tag_name from item_tag order by tag_name
                    """)
                .mapTo(String.class)
                .list());
  }
}
