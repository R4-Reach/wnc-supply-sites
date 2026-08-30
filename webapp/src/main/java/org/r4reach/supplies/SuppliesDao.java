package org.r4reach.supplies;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.data.SiteType;

@Slf4j
public class SuppliesDao {

  @NoArgsConstructor
  @Data
  public static class SuppliesQueryResult {
    Long siteId;
    boolean acceptingDonations;
    boolean givingDonations;
    String site;
    String siteType;
    String county;
    String state;
    String item;
    String itemTags;
    String itemStatus;
    LocalDate inventoryLastUpdated;
    LocalDate lastDeliveryDate;
  }

  public static List<SuppliesQueryResult> getSupplyResults(
      Jdbi jdbi, SiteSupplyRequest request, List<String> stateList) {

    StringBuilder query =
        new StringBuilder(
            """
      select
        s.id siteId,
        s.accepting_donations acceptingDonations,
        s.distributing_supplies givingDonations,
        s.name site,
        st.name siteType,
        c.name county,
        c.state state,
        i.name item,
        ist.name itemStatus,
        s.inventory_last_updated inventoryLastUpdated,
        string_agg(it.tag_name, ',') itemTags,
        max(d.target_delivery_date) filter (where d.delivery_status = 'Delivery Completed') lastDeliveryDate
      from site s
      join site_type st on st.id = s.site_type_id
      join county c on c.id = s.county_id
      left join site_item si on si.site_id = s.id
      left join item i on i.id = si.item_id
      left join item_tag it on it.item_id = i.id
      left join item_status ist on ist.id = si.item_status_id
      left join delivery d on d.to_site_id = s.id
      where s.active = true
        and c.state in (<stateList>)
      """);

    if (!request.getSites().isEmpty()) {
      query.append("and s.name in (<sites>)\n");
    }
    if (!request.getCounties().isEmpty()) {
      // build up a string to do a lot of "or" matching of county + state
      // pairs. We encode the bind variable with a counter variable at the end
      // to make it unique, so we can bind it later.
      List<String> queryParts = new ArrayList<>();
      for (int i = 0; i < request.getCounties().size(); i++) {
        queryParts.add("(c.name = :county" + i + " and c.state = :state" + i + ")");
      }

      query.append("and (\n");
      query.append(String.join("\n or ", queryParts));
      query.append("\n)\n");
    }
    if (!request.getItems().isEmpty()) {
      query.append("and i.name in (<items>)\n");
    }

    // if item status length is 3, then we are asking for all item status
    // but, if we do that, we filter out sites with no item status.
    // If all item statuses are requested, then we treat it as if none are requested.
    if (!request.getItemStatus().isEmpty()
        && request.getItemStatus().size() < SiteSupplyRequest.ITEM_STATUS_COUNT) {
      query.append("and ist.name in (<item_status>)\n");
    }

    if (!request.getSiteType().isEmpty()
        && request.getSiteType().size() < SiteType.values().length) {
      query.append("and st.name in (<site_type>)\n");
    }

    if (request.getAcceptingDonations() != request.getNotAcceptingDonations()) {
      query
          .append("and s.accepting_donations = ")
          .append(request.getAcceptingDonations())
          .append("\n");
    }

    if (!request.getIsAuthenticatedUser()) {
      query.append("and s.publicly_visible = true").append("\n");
    }

    query.append(
        """
      group by
        s.id,
        s.accepting_donations,
        s.distributing_supplies,
        s.name,
        st.name,
        c.name,
        c.state,
        i.name,
        ist.name,
        s.inventory_last_updated,
        ist.sort_order
      order by c.name, s.name, ist.sort_order, i.name
      """);

    List<String> decodedSites =
        request.getSites().stream().map(s -> s.replace("&amp;", "&")).toList();

    return jdbi.withHandle(
        handle -> {
          var queryBuilder = handle.createQuery(query.toString());
          if (!request.getSites().isEmpty()) {
            queryBuilder.bindList("sites", decodedSites);
          }
          if (!request.getCounties().isEmpty()) {

            for (int i = 0; i < request.getCounties().size(); i++) {
              String c = request.getCounties().get(i);
              if (c.contains(",")) {
                String county = c.split(",")[0].trim();
                String state = c.split(",")[1].trim();

                queryBuilder.bind("county" + i, county);
                queryBuilder.bind("state" + i, state);
              } else {
                log.warn(
                    "Supply search, county filter, received unexpected county "
                        + "result that was not in this format 'county,state': {}",
                    c);
                queryBuilder.bind("county" + i, "");
                queryBuilder.bind("state" + i, "");
              }
            }
          }
          if (!request.getItems().isEmpty()) {
            queryBuilder.bindList("items", request.getItems());
          }
          if (!request.getItemStatus().isEmpty()
              && request.getItemStatus().size() < SiteSupplyRequest.ITEM_STATUS_COUNT) {
            queryBuilder.bindList("item_status", request.getItemStatus());
          }

          if (!request.getSiteType().isEmpty()
              && request.getSiteType().size() < SiteType.values().length) {
            queryBuilder.bindList("site_type", request.getSiteType());
          }

          if (!request.getStates().isEmpty()) {
            queryBuilder.bindList("stateList", request.getStates());
          } else {
            queryBuilder.bindList("stateList", stateList);
          }

          return queryBuilder.mapToBean(SuppliesQueryResult.class).list();
        });
  }
}
