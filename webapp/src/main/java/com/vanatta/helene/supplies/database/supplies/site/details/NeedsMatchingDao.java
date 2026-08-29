package com.vanatta.helene.supplies.database.supplies.site.details;

import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.data.SiteAddress;
import com.vanatta.helene.supplies.database.util.DurationFormatter;
import com.vanatta.helene.supplies.database.util.ListSplitter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.Jdbi;

public class NeedsMatchingDao {

  /**
   * Groups up database results, what is many rows, to rows aggregated by site. The difference
   * between rows is the item listing and the item urgency.
   */
  // @VisibleForTesting
  static List<NeedsMatchingResult> aggregate(List<NeedsMatchingDbResult> dbResult) {
    Map<Long, NeedsMatchingResult> needsMatchingResult = new HashMap<>();

    dbResult.forEach(
        needsMatchingDbResult ->
            needsMatchingResult
                .computeIfAbsent(
                    needsMatchingDbResult.siteId,
                    _ ->
                        NeedsMatchingResult.builder()
                            .siteLink(
                                SiteDetailController.buildSiteLink(needsMatchingDbResult.siteId))
                            .siteName(needsMatchingDbResult.siteName)
                            .siteAddress(needsMatchingDbResult.siteAddress)
                            .city(needsMatchingDbResult.city)
                            .county(needsMatchingDbResult.county)
                            .state(needsMatchingDbResult.state)
                            .driveTimeSeconds(needsMatchingDbResult.driveTimeSeconds)
                            .distanceMiles(needsMatchingDbResult.distanceMiles)
                            .build())
                .addItem(
                    NeedsMatchingResult.Item.builder()
                        .name(needsMatchingDbResult.itemName)
                        .urgencyCssClass(
                            ItemStatus.fromTextValue(needsMatchingDbResult.urgency).getCssClass())
                        .build()));

    return needsMatchingResult.values().stream()
        .sorted(
            Comparator.comparingInt(NeedsMatchingResult::getItemCount)
                .reversed()
                .thenComparing(NeedsMatchingResult::getSiteName))
        .toList();
  }

  @Value
  @Builder(toBuilder = true)
  @AllArgsConstructor
  static class NeedsMatchingResult {
    @Value
    @Builder
    static class Item {
      String name;
      String urgencyCssClass;
    }

    String siteName;
    String siteLink;
    String siteAddress;
    String city;
    String county;
    String state;
    Integer driveTimeSeconds;
    Double distanceMiles;
    @Builder.Default List<Item> items = new ArrayList<>();

    String getGoogleMapsAddress() {
      return SiteAddress.builder()
          .address(siteAddress)
          .city(city)
          .state(state)
          .build()
          .toEncodedUrlValue();
    }

    String getDriveTime() {
      return driveTimeSeconds == null
          ? ""
          : DurationFormatter.formatDuration(Duration.ofSeconds(driveTimeSeconds));
    }

    List<Item> getItems1() {
      List<List<Item>> splitLists =
          ListSplitter.splitItemList(
              items.stream().sorted(Comparator.comparing(i -> i.name)).toList(), 5);
      return splitLists.getFirst();
    }

    List<Item> getItems2() {
      List<List<Item>> splitLists =
          ListSplitter.splitItemList(
              items.stream().sorted(Comparator.comparing(i -> i.name)).toList(), 5);
      return splitLists.size() > 1 ? splitLists.get(1) : List.of();
    }

    void addItem(Item item) {
      this.items.add(item);
    }

    int getItemCount() {
      return items.size();
    }
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class NeedsMatchingDbResult {
    long siteId;
    String siteName;
    String siteAddress;
    String city;
    String county;
    String state;
    String itemName;
    String urgency;
    int itemCount;
    Integer driveTimeSeconds;
    Double distanceMiles;
  }

  public static List<NeedsMatchingResult> executeByInternalId(
      Jdbi jdbi, long siteId, List<String> stateList) {
    String query =
        """
         WITH needy_items AS (
            SELECT si.item_id, ist.name urgency
            FROM site_item si
            JOIN item_status ist ON si.item_status_id = ist.id
            JOIN site s on s.id = si.site_id
            JOIN county c on c.id = s.county_id
            WHERE s.id = :siteId AND ist.name IN ('Urgently Needed', 'Needed')
        ),
        oversupply_sites AS (
            SELECT si.site_id, si.item_id
            FROM site_item si
            JOIN item_status ist ON si.item_status_id = ist.id
            JOIN site s on s.id = si.site_id
            JOIN county c on c.id = s.county_id
            JOIN site_type st on st.id = s.site_type_id
            WHERE
              s.active = true
              and c.state in (<stateList>)
              and (ist.name = 'Oversupply' or (st.name = 'Supply Hub' and ist.name in ('Available', 'Oversupply')))
        ), need_match AS (
            SELECT
                s.name AS siteName,
                s.id AS siteId,
                s.address AS siteAddress,
                s.city as city,
                c.name as county,
                c.state as state,
                i.name AS itemName,
                ni.urgency AS urgency,
                sdm.drive_time_seconds AS driveTimeSeconds,
                sdm.distance_miles AS distanceMiles
            FROM
                oversupply_sites os
            JOIN
                needy_items ni ON os.item_id = ni.item_id
            JOIN
                site s ON os.site_id = s.id
            LEFT JOIN
                site_distance_matrix sdm on
                  (sdm.site1_id = :siteId and sdm.site2_id = os.site_id) or
                  (sdm.site2_id = :siteId and sdm.site1_id = os.site_id)
            JOIN
                county c on c.id = s.county_id
            JOIN
                item i ON os.item_id = i.id
            order by lower(i.name)
        )
        select
          A.siteName,
          A.siteId,
          A.siteAddress,
          A.city,
          county,
          state,
          itemName,
          urgency,
          driveTimeSeconds,
          distanceMiles
        from need_match A
        ORDER BY A.siteId
        """;
    List<NeedsMatchingDbResult> dbResults =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery(query)
                    .bind("siteId", siteId)
                    .bindList("stateList", stateList)
                    .mapToBean(NeedsMatchingDbResult.class)
                    .list());

    return aggregate(dbResults);
  }
}
