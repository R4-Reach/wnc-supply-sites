package com.vanatta.helene.supplies.database.rcommons;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API endpoints for the R-Commons volunteer portal (served as static files from public/rcommons/).
 */
@RestController
@AllArgsConstructor
public class RCommonsController {

  private final Jdbi jdbi;

  /** Returns all active WSS supply sites in the format expected by the R-Commons frontend. */
  @GetMapping("/rcommons/api/sites")
  @CrossOrigin
  ResponseEntity<List<SiteData>> getSites() {
    return ResponseEntity.ok(fetchSites(jdbi));
  }

  // @VisibleForTesting
  static List<SiteData> fetchSites(Jdbi jdbi) {
    String query =
        """
        select
          s.id            as dbId,
          s.wss_id        as wssId,
          s.name          as site,
          st.name         as siteType,
          c.name          as county,
          c.state         as state,
          s.accepting_donations as acceptingDonations,
          count(i.name)   filter (where its.name = 'Urgently Needed')              as urgentCount,
          count(i.name)   filter (where its.name in ('Urgently Needed', 'Needed')) as neededCount,
          string_agg(i.name, ',') filter (where its.name = 'Urgently Needed')      as urgentItemsCsv
        from site s
        join county c     on c.id  = s.county_id
        join site_type st on st.id = s.site_type_id
        left join site_item si  on s.id   = si.site_id
        left join item i        on i.id   = si.item_id
        left join item_status its on its.id = si.item_status_id
        where s.active = true
        group by s.id, s.wss_id, s.name, st.name, c.name, c.state, s.accepting_donations
        order by c.state, c.name, s.name
        """;
    return jdbi
        .withHandle(handle -> handle.createQuery(query).mapToBean(SiteDbRow.class).list())
        .stream()
        .map(SiteData::new)
        .toList();
  }

  /** Raw DB result row — JDBI maps SQL aliases to these camelCase fields. */
  @Data
  @NoArgsConstructor
  public static class SiteDbRow {
    long dbId;
    long wssId;
    String site;
    String siteType;
    String county;
    String state;
    boolean acceptingDonations;
    int urgentCount;
    int neededCount;
    String urgentItemsCsv;
  }

  /** JSON shape sent to the R-Commons frontend. */
  @Value
  public static class SiteData {
    /** Internal site.id — used for /volunteer/site-items and /volunteer/delivery calls. */
    long dbId;

    /** wss_id — used as the public-facing site identifier in the UI. */
    long id;

    String site;
    String siteType;
    String county;
    String state;
    boolean acceptingDonations;
    int urgentCount;
    int neededCount;
    List<String> urgentItems;

    SiteData(SiteDbRow r) {
      this.dbId = r.dbId;
      this.id = r.wssId;
      this.site = r.site;
      this.siteType = r.siteType;
      this.county = r.county;
      this.state = r.state;
      this.acceptingDonations = r.acceptingDonations;
      this.urgentCount = r.urgentCount;
      this.neededCount = r.neededCount;
      this.urgentItems =
          r.urgentItemsCsv != null ? Arrays.asList(r.urgentItemsCsv.split(",")) : new ArrayList<>();
    }
  }
}
