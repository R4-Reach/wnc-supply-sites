package org.r4reach.jobs.distance;

import java.util.List;
import java.util.Optional;
import lombok.Data;
import org.jdbi.v3.core.Jdbi;

public class DistanceDao {

  @Data
  public static class DistanceResult {
    double distance;
    long durationSeconds;
    boolean valid;
  }

  /**
   * Returns the computed distance between two sites. Order of the sites does not matter, distances
   * are bi-directional.
   *
   * <p>Return empty if there is no distance computation. Typically because the address of one site
   * cannot be found or is otherwise invalid.
   */
  public static Optional<DistanceResult> queryDistance(Jdbi jdbi, long site1Id, long site2Id) {
    String query =
        """
    select
      distance_miles distance,
      drive_time_seconds durationSeconds,
      valid
    from site_distance_matrix
    where (site1_id = :site1Id and site2_id = :site2Id)
       or (site1_id = :site2Id and site2_id = :site1Id)
    """;
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(query)
                .bind("site1Id", site1Id)
                .bind("site2Id", site2Id)
                .mapToBean(DistanceResult.class)
                .findOne()
                .filter(DistanceResult::isValid));
  }

  public static void updateDistance(
      Jdbi jdbi, long site1Id, long site2Id, double distance, long durationSeconds) {
    String update =
        """
        update site_distance_matrix
        set distance_miles = :distance, drive_time_seconds = :durationSeconds, valid = true
        where (site1_id = :site1Id and site2_id = :site2Id) or (site1_id = :site2Id and site2_id = :site1Id)
        """;

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(update)
                .bind("distance", distance)
                .bind("durationSeconds", durationSeconds)
                .bind("site1Id", site1Id)
                .bind("site2Id", site2Id)
                .execute());
  }

  public static void updateDistanceInvalid(Jdbi jdbi, long site1Id, long site2Id) {
    String update =
        """
        update site_distance_matrix
        set valid = false
        where (site1_id = :site1Id and site2_id = :site2Id) or (site1_id = :site2Id and site2_id = :site1Id)
        """;

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(update)
                .bind("site1Id", site1Id)
                .bind("site2Id", site2Id)
                .execute());
  }

  @Data
  public static class SitePair {
    long siteId1;
    String address1;
    String city1;
    String state1;

    long siteId2;
    String address2;
    String city2;
    String state2;
  }

  /**
   * Returns all site pairs where there is no calculated distances, where we should try to calculate
   * a distance. All results are those where we will loop through and ask Google how far the two
   * sites are.
   */
  static List<SitePair> fetchUncalculatedPairs(Jdbi jdbi) {
    String query =
        """
    select
      s1.id siteId1,
      s1.address address1,
      s1.city city1,
      c1.state state1,
      s2.id siteId2,
      s2.address address2,
      s2.city city2,
      c2.state state2
    from site_distance_matrix sdm
    join site s1 on s1.id = sdm.site1_id
    join county c1 on c1.id = s1.county_id
    join site s2 on s2.id = sdm.site2_id
    join county c2 on c2.id = s2.county_id
    where sdm.valid is null and s1.active = true and s2.active = true;
    """;

    return jdbi.withHandle(handle -> handle.createQuery(query).mapToBean(SitePair.class).list());
  }
}
