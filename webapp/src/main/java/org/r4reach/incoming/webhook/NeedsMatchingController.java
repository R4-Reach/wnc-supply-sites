package org.r4reach.incoming.webhook;

import java.util.ArrayList;
import java.util.List;
import org.jdbi.v3.core.Jdbi;

/**
 * Computes the eligible-goods match between two sites: the items a drop-off site needs that a
 * pickup site has available to give. Formerly also exposed an Airtable webhook that pushed matches
 * into a MAKE job; that integration is retired, so only the reusable match computation remains,
 * driving the delivery detail page's "match goods" action.
 */
public final class NeedsMatchingController {

  private NeedsMatchingController() {}

  /**
   * The eligible-goods match for a delivery: the item names the drop-off ({@code toSite}) needs
   * that the pickup ({@code fromSite}) has available to give. Both site ids are {@code wss_id}
   * values.
   */
  public static List<String> computeNeedsMatch(Jdbi jdbi, long fromSiteWssId, long toSiteWssId) {
    String availableItemsQuery =
        """
        select
          si.item_id
        from site_item si
        join site s on s.id = si.site_id
        join site_type st on st.id = s.site_type_id
        join item_status its on its.id = si.item_status_id
        where s.wss_id = :fromSiteWssId
          and
          (
            ( upper(st.name) = 'SUPPLY HUB' and upper(its.name) in ('AVAILABLE', 'OVERSUPPLY') )
            or
            ( upper(st.name) = 'DISTRIBUTION CENTER' and upper(its.name) = 'OVERSUPPLY' )
          )
        """;
    List<Long> itemIdsAvailable =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery(availableItemsQuery)
                    .bind("fromSiteWssId", fromSiteWssId)
                    .mapTo(Long.class)
                    .list());
    if (itemIdsAvailable.isEmpty()) {
      return List.of();
    }

    String neededItemsQuery =
        """
        select
          si.item_id
        from site_item si
        join site s on s.id = si.site_id
        join item_status its on its.id = si.item_status_id
        where s.wss_id = :toSiteWssId
          and upper(its.name) in ('NEEDED', 'URGENTLY NEEDED')
        """;
    List<Long> itemsIdsNeeded =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery(neededItemsQuery)
                    .bind("toSiteWssId", toSiteWssId)
                    .mapTo(Long.class)
                    .list());

    List<Long> eligibleItemIds = new ArrayList<>(itemIdsAvailable);
    eligibleItemIds.retainAll(itemsIdsNeeded);

    if (eligibleItemIds.isEmpty()) {
      return List.of();
    }

    String queryNeedIds =
        """
        select
          i.name
        from site_item si
        join item i on i.id = si.item_id
        where
          si.site_id = (select id from site where wss_id = :siteWssId)
          and si.item_id in (<itemIds>)
        order by i.name asc;
        """;
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(queryNeedIds)
                .bind("siteWssId", toSiteWssId)
                .bindList("itemIds", eligibleItemIds)
                .mapTo(String.class)
                .list());
  }
}
