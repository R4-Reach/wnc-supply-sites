package com.vanatta.helene.supplies.database.incoming.webhook;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supports a webhook endpoint that can take a 'from' site, 'to' site, then invokes a MAKE webhook
 * to send an update of which supplies can be delivered between the two.
 */
@Slf4j
@RestController
public class NeedsMatchingController {

  private static final String PATH_ADD_NEEDS = "/webhook/add-supplies-to-delivery";
  private final Jdbi jdbi;

  NeedsMatchingController(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  /**
   * Data result for a needs match request. A needs match are all eligible items at a 'from' site
   * that match match the needs of a 'to' site.
   */
  @Builder
  @lombok.Value
  public static class ComputedNeeds {
    /** ID of the delivery, as received from Airtable */
    long deliveryId;

    /** Results, list of items names */
    List<String> itemList;
  }

  /**
   * Given inputs: [from site, to site, delivery id]<br>
   * Compute which needs can be sent from site to the to site.<br>
   * Invoke make job to update the target delivery id with the computed needs.
   */
  @PostMapping(PATH_ADD_NEEDS)
  ResponseEntity<String> addSuppliesToDelivery(
      @RequestBody String body) { // Map<String, String> body) {
    log.info("{}, received data: {}", PATH_ADD_NEEDS, body);
    LinkedTreeMap json = new Gson().fromJson(body, LinkedTreeMap.class);
    long deliveryId = ((Double) json.get("deliveryId")).longValue();

    List<Double> fromSite = (List<Double>) json.get("fromSiteWssId");
    List<Double> toSite = (List<Double>) json.get("toSiteWssId");

    if (fromSite.isEmpty() || toSite.isEmpty()) {
      log.warn(
          "Add items requested, except the requested sites are not in WSS though. "
              + "No results are being returned. Request: {}",
          body);
      return ResponseEntity.ok("No matches, sites are not in WSS");
    }

    long fromWssId = fromSite.getFirst().longValue();
    long toSiteWssId = toSite.getFirst().longValue();

    List<String> neededItems = computeNeedsMatch(jdbi, fromWssId, toSiteWssId);
    log.info("Received needs computation request: {}, matched with needs: {}", body, neededItems);

    return ResponseEntity.ok("Matches: " + neededItems.size());
  }

  // @VisibleForTesting
  static List<String> computeNeedsMatch(Jdbi jdbi, long fromSiteWssId, long toSiteWssId) {
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
