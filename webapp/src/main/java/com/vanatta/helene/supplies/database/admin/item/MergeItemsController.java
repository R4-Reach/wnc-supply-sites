package com.vanatta.helene.supplies.database.admin.item;

import com.vanatta.helene.supplies.database.util.DateTimeFormat;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
@AllArgsConstructor
public class MergeItemsController {

  private final Jdbi jdbi;

  @GetMapping("/admin/merge-items")
  ModelAndView showMergeItems() {
    Map<String, Object> params = new HashMap<>();
    params.put("items", fetchAllItems(jdbi));
    return new ModelAndView("admin/merge-items", params);
  }

  static List<InventoryItem> fetchAllItems(Jdbi jdbi) {
    String query =
        """
      select
        id,
        name itemName,
        date_created
      from item
      order by lower(name);
      """;
    return jdbi.withHandle(
        handle -> handle.createQuery(query).mapToBean(InventoryItem.class).list());
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InventoryItem {
    long id;
    String itemName;
    LocalDateTime dateCreated;

    String getDateCreated() {
      return DateTimeFormat.format(dateCreated);
    }
  }

  @SuppressWarnings("unchecked")
  @PostMapping("/admin/merge-items/do-merge")
  ResponseEntity<String> doMerge(@RequestBody Map<String, Object> params) {
    log.info("/admin/merge-items/do-merge received merge request: {}", params);
    long mergeIntoItemId = Long.parseLong(String.valueOf(params.get("mergeInto")));
    if (mergeIntoItemId == 0) {
      log.warn("Empty 'mergeInto' parameter, aborting merge.");
      return ResponseEntity.badRequest()
          .body("{\"result\": \"no item to merge into was specified\"}");
    }

    if (params.get("mergeItems") == null || ((List<Object>) params.get("mergeItems")).isEmpty()) {
      log.warn("Empty 'mergeItems' list received, aborting merge");
      return ResponseEntity.badRequest()
          .body("{\"result\": \"no items to be merged were specified\"}");
    }

    List<Long> mergeItemsId =
        ((List<Object>) params.get("mergeItems"))
            .stream().map(String::valueOf).map(Long::parseLong).toList();

    // do some logging, get the item names so we know what is being merged
    String mergeItemName = itemNameById(jdbi, mergeIntoItemId);
    List<String> toMergeItemNames =
        mergeItemsId.stream().map(itemId -> itemNameById(jdbi, itemId)).toList();
    log.info("Merging into item: {}, items: {}", mergeItemName, toMergeItemNames);

    List<Long> itemsMergedWssIds = fetchWssIdsOfItems(jdbi, mergeItemsId);
    merge(jdbi, mergeIntoItemId, mergeItemsId);
    return ResponseEntity.ok("{\"result\": \"success\"}");
  }

  static List<Long> fetchWssIdsOfItems(Jdbi jdbi, List<Long> itemIds) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                select wss_id from item where id in (<itemIds>)
                """)
                .bindList("itemIds", itemIds)
                .mapTo(Long.class)
                .list());
  }

  @SuppressWarnings("SqlSourceToSinkFlow")
  private static void merge(Jdbi jdbi, long mergeIntoItemId, List<Long> itemsToMergeIds) {
    String mergeIntoItemName = itemNameById(jdbi, mergeIntoItemId);

    for (long deleteItemId : itemsToMergeIds) {
      String itemName = itemNameById(jdbi, deleteItemId);

      List<Long> sitesWithTheItem =
          jdbi.withHandle(
              handle ->
                  handle
                      .createQuery(
                          "select site_id from site_item_audit where item_id in (<itemIds>)")
                      .bindList("itemIds", itemsToMergeIds)
                      .mapTo(Long.class)
                      .list());
      log.info(
          "Merging item ID: {}, name: {}, number of sites with that item: {}",
          deleteItemId,
          itemName,
          sitesWithTheItem.size());
      for (long siteId : sitesWithTheItem) {
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(
                        """
                              insert into site_item_audit (item_id, site_id, old_value, new_value)
                              values(:itemId, :siteId, 'item old name: :itemName', 'item merged into: :mergeIntoItemName')
                          """)
                    .bind("itemName", itemName)
                    .bind("mergeIntoItemName", mergeIntoItemName)
                    .bind("itemId", deleteItemId)
                    .bind("siteId", siteId)
                    .execute());
        // now update all the old item-IDs to the new item ID
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(
                        """
                          update site_item_audit set item_id = :newId where item_id = :oldId
                          """)
                    .bind("oldId", deleteItemId)
                    .bind("newId", mergeIntoItemId)
                    .execute());
      }

      // update site_item mapping, insert the 'toMerge' item
      // and then next we'll delete the to-be merged items.
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(
                      String.format(
                          """
                      insert into site_item(site_id, item_id, item_status_id)
                      select site_id, %s, item_status_id
                      from site_item
                      where item_id = :itemId
                      on conflict do nothing
                      """,
                          mergeIntoItemId))
                  .bind("itemId", deleteItemId)
                  .execute());
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(
                      """
                      delete from site_item where item_id = :itemId
                      """)
                  .bind("itemId", deleteItemId)
                  .execute());

      // for we want to move the item from the 'item_id' column to to the 'item_name'
      // column. This way the delivery stays effectively the same and we can still
      // delete 'item_id' later.
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(
                      String.format(
                          """
                      insert into delivery_item(delivery_id, item_name)
                      select di.delivery_id, i.name
                      from delivery_item di
                      join item i on i.id = di.item_id
                      where item_id = :itemId
                      on conflict do nothing
                      """,
                          mergeIntoItemId))
                  .bind("itemId", deleteItemId)
                  .execute());
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(
                      """
                      delete from delivery_item where item_id = :itemId
                      """)
                  .bind("itemId", deleteItemId)
                  .execute());
    }

    // delete tags
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    delete from item_tag where item_id in (<itemIds>)
                    """)
                .bindList("itemIds", itemsToMergeIds)
                .execute());

    // now remove the items from the 'item' table to complete the merge
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    delete from item where id in (<itemIds>)
                    """)
                .bindList("itemIds", itemsToMergeIds)
                .execute());
  }

  private static String itemNameById(Jdbi jdbi, long id) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select name from item where id = :id")
                .bind("id", id)
                .mapTo(String.class)
                .one());
  }
}
