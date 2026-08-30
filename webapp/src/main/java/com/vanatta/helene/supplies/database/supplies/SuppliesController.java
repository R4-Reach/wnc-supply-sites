package com.vanatta.helene.supplies.database.supplies;

import com.vanatta.helene.supplies.database.DeploymentAdvice;
import com.vanatta.helene.supplies.database.auth.CookieAuthenticator;
import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.supplies.SiteSupplyResponse.SiteItem;
import com.vanatta.helene.supplies.database.supplies.SiteSupplyResponse.SiteSupplyData;
import com.vanatta.helene.supplies.database.supplies.filters.FilterDataController;
import com.vanatta.helene.supplies.database.supplies.filters.FilterDataResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@RestController
@AllArgsConstructor
public class SuppliesController {
  public static final String PATH_SUPPLY_SEARCH = "/supplies/site-list";

  /** Session key under which the current page's filter selections persist across visits. */
  private static final String FILTER_SESSION_KEY = "suppliesFilterState";

  private final Jdbi jdbi;
  private final CookieAuthenticator cookieAuthenticator;

  @GetMapping("/supplies/needs")
  public ModelAndView needs(
      HttpServletRequest request,
      HttpSession session,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_STATE_LIST) List<String> stateList,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_FULL_STATE_LIST) List<String> fullStateList) {
    return supplies("donate", request, session, stateList, fullStateList);
  }

  /**
   * Renders the supplies search page. Filter dropdowns, the current selections, checkbox/sort
   * state, and the initial results table are all rendered server-side; subsequent filtering is
   * driven by htmx posts to {@link #suppliesData}. A {@code mode} of "donate" (the /supplies/needs
   * entry point) pre-selects the "what sites need" view; otherwise the last-used filters saved in
   * the session are restored.
   */
  @GetMapping(PATH_SUPPLY_SEARCH)
  public ModelAndView supplies(
      @RequestParam(required = false) String mode,
      HttpServletRequest request,
      HttpSession session,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_STATE_LIST) List<String> stateList,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_FULL_STATE_LIST) List<String> fullStateList) {
    boolean authenticated = cookieAuthenticator.isAuthenticated(request);
    List<String> effectiveStateList = stateList.isEmpty() ? fullStateList : stateList;

    FilterState state = (FilterState) session.getAttribute(FILTER_SESSION_KEY);
    if (mode != null || state == null) {
      state = FilterState.defaults("donate".equalsIgnoreCase(mode));
      session.setAttribute(FILTER_SESSION_KEY, state);
    }

    FilterDataResponse filterData =
        new FilterDataController(jdbi, cookieAuthenticator)
            .getFilterData(authenticated, effectiveStateList);

    Map<String, Object> model = new HashMap<>();
    model.put("siteOptions", filterData.getSites());
    model.put("stateOptions", filterData.getStates());
    model.put("countyOptions", filterData.getCounties());
    model.put("itemOptions", filterData.getItems());

    model.put("siteSelections", state.getSites());
    model.put("stateSelections", state.getStates());
    model.put("countySelections", state.getCounties());
    model.put("itemSelections", state.getItems());

    model.put(
        "urgentChecked", state.getItemStatus().contains(ItemStatus.URGENTLY_NEEDED.getText()));
    model.put("neededChecked", state.getItemStatus().contains(ItemStatus.NEEDED.getText()));
    model.put("availableChecked", state.getItemStatus().contains(ItemStatus.AVAILABLE.getText()));
    model.put("oversupplyChecked", state.getItemStatus().contains(ItemStatus.OVERSUPPLY.getText()));
    model.put("acceptingChecked", state.isAcceptingDonations());
    model.put("notAcceptingChecked", state.isNotAcceptingDonations());
    model.put("foodPantryChecked", state.getSiteType().contains("Food Pantry"));
    model.put("distributionCenterChecked", state.getSiteType().contains("Distribution Center"));
    model.put("supplyHubChecked", state.getSiteType().contains("Supply Hub"));

    model.put("sortLastUpdated", "last-updated".equals(state.getSort()));
    model.put("sortLastUpdatedReverse", "last-updated-reverse".equals(state.getSort()));
    model.put("sortAlphabetical", "alphabetical".equals(state.getSort()));

    List<SiteSupplyData> results =
        sortResults(
            getSuppliesData(state.toRequest(), authenticated, effectiveStateList).getResults(),
            state.getSort());
    model.put("results", buildRows(results));
    model.put("resultCount", results.size());

    return new ModelAndView("supplies/supplies", model);
  }

  private static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MMM-d");

  /**
   * Posted by the supplies page's htmx filter form (url-encoded). Persists the selections in the
   * session and returns the results-table fragment (rows plus an out-of-band result count) that
   * htmx swaps into the page.
   */
  @PostMapping(value = "/supplies/site-data")
  public ModelAndView suppliesData(
      HttpServletRequest httpRequest,
      HttpSession session,
      @RequestParam(required = false) List<String> sites,
      @RequestParam(required = false) List<String> states,
      @RequestParam(required = false) List<String> counties,
      @RequestParam(required = false) List<String> items,
      @RequestParam(required = false) List<String> itemStatus,
      @RequestParam(required = false) List<String> siteType,
      @RequestParam(defaultValue = "false") boolean acceptingDonations,
      @RequestParam(defaultValue = "false") boolean notAcceptingDonations,
      @RequestParam(defaultValue = "last-updated") String sort,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_STATE_LIST) List<String> stateList,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_FULL_STATE_LIST) List<String> fullStateList) {
    boolean authenticated = cookieAuthenticator.isAuthenticated(httpRequest);
    List<String> effectiveStateList = stateList.isEmpty() ? fullStateList : stateList;

    FilterState state =
        new FilterState(
            nullSafe(sites),
            nullSafe(states),
            nullSafe(counties),
            nullSafe(items),
            nullSafe(itemStatus),
            nullSafe(siteType),
            acceptingDonations,
            notAcceptingDonations,
            sort);
    session.setAttribute(FILTER_SESSION_KEY, state);

    List<SiteSupplyData> results =
        sortResults(
            getSuppliesData(state.toRequest(), authenticated, effectiveStateList).getResults(),
            sort);

    Map<String, Object> model = new HashMap<>();
    model.put("results", buildRows(results));
    model.put("resultCount", results.size());
    return new ModelAndView("supplies/results-fragment", model);
  }

  private static List<String> nullSafe(List<String> input) {
    return input == null ? new ArrayList<>() : input;
  }

  // @VisibleForTesting
  SiteSupplyResponse getSuppliesData(SiteSupplyRequest request, List<String> stateList) {
    return getSuppliesData(request, false, stateList);
  }

  // @VisibleForTesting
  SiteSupplyResponse getSuppliesData(
      SiteSupplyRequest request, boolean isAuthenticated, List<String> stateList) {
    request = request.toBuilder().isAuthenticatedUser(isAuthenticated).build();

    List<SuppliesDao.SuppliesQueryResult> results =
        SuppliesDao.getSupplyResults(jdbi, request, stateList);

    Map<Long, SiteSupplyData> aggregatedResults = new HashMap<>();

    results.forEach(
        result -> {
          var siteSupplyData =
              aggregatedResults.computeIfAbsent(
                  result.getSiteId(),
                  _ ->
                      SiteSupplyData.builder()
                          .id(result.getSiteId())
                          .site(result.getSite())
                          .siteType(result.getSiteType())
                          .county(result.getCounty())
                          .state(result.getState())
                          .acceptingDonations(result.isAcceptingDonations())
                          .inventoryLastUpdated(
                              result.getInventoryLastUpdated().format(dateTimeFormatter))
                          .lastDelivery(
                              isAuthenticated
                                  ? Optional.ofNullable(result.getLastDeliveryDate())
                                      .map(d -> d.format(dateTimeFormatter))
                                      .orElse(null)
                                  : null)
                          .build());
          // add items to the corresponding needed or available lists
          if (result.getItem() != null) {
            var itemStatus = ItemStatus.fromTextValue(result.getItemStatus());
            var item =
                SiteItem.builder()
                    .name(result.getItem())
                    .displayClass(itemStatus.getCssClass())
                    .tags(
                        result.getItemTags() == null
                            ? List.of()
                            : Arrays.stream(result.getItemTags().split(","))
                                .distinct()
                                .sorted()
                                .toList())
                    .build();

            if (itemStatus.isNeeded()) {
              if (isAuthenticated || result.isGivingDonations()) {
                siteSupplyData.getNeededItems().add(item);
              }
            } else {
              if (isAuthenticated || result.isGivingDonations()) {
                siteSupplyData.getAvailableItems().add(item);
              }
            }
          }
        });
    List<SiteSupplyData> resultData =
        aggregatedResults.values().stream() //
            .sorted(
                Comparator.comparing(SiteSupplyData::getCounty)
                    .thenComparing(SiteSupplyData::getSite))
            .toList();

    return SiteSupplyResponse.builder() //
        .resultCount(resultData.size())
        .results(resultData)
        .build();
  }

  /** Applies the page's "sort results by" selection to the aggregated site list. */
  private static List<SiteSupplyData> sortResults(List<SiteSupplyData> results, String sort) {
    List<SiteSupplyData> sorted = new ArrayList<>(results);
    switch (sort == null ? "" : sort) {
      case "last-updated" ->
          sorted.sort(Comparator.comparing(SuppliesController::lastUpdated).reversed());
      case "last-updated-reverse" ->
          sorted.sort(Comparator.comparing(SuppliesController::lastUpdated));
      case "alphabetical" ->
          sorted.sort(Comparator.comparing(r -> r.getSite().toLowerCase(Locale.ENGLISH)));
      default -> {
        // leave in the default county/site order
      }
    }
    return sorted;
  }

  private static LocalDate lastUpdated(SiteSupplyData data) {
    try {
      return LocalDate.parse(data.getInventoryLastUpdated(), dateTimeFormatter);
    } catch (RuntimeException e) {
      return LocalDate.MIN;
    }
  }

  /** Builds the render-ready rows for the results table from the aggregated site data. */
  private static List<ResultRow> buildRows(List<SiteSupplyData> results) {
    List<ResultRow> rows = new ArrayList<>();
    for (SiteSupplyData data : results) {
      rows.add(
          new ResultRow(
              data.getId(),
              data.getSite(),
              data.getCounty(),
              data.getState(),
              "Supply Hub".equals(data.getSiteType()),
              !data.isAcceptingDonations(),
              data.getInventoryLastUpdated(),
              data.getLastDelivery(),
              toColumns(data.getNeededItems()),
              toColumns(data.getAvailableItems())));
    }
    return rows;
  }

  /**
   * Splits an item list into one or two columns, matching the legacy JS layout: a short list stays
   * in a single column, a longer one is split in half.
   */
  private static List<ItemColumn> toColumns(List<SiteItem> items) {
    List<ItemView> views = items.stream().map(ItemView::new).toList();
    if (views.size() < 5) {
      return List.of(new ItemColumn(views));
    }
    int half = views.size() / 2;
    return List.of(
        new ItemColumn(views.subList(0, half)), new ItemColumn(views.subList(half, views.size())));
  }

  @Value
  public static class ResultRow {
    Long id;
    String site;
    String county;
    String state;
    boolean supplyHub;
    boolean notAccepting;
    String inventoryLastUpdated;
    String lastDelivery;
    List<ItemColumn> neededColumns;
    List<ItemColumn> availableColumns;
  }

  @Value
  public static class ItemColumn {
    List<ItemView> items;
  }

  @Value
  public static class ItemView {
    String name;
    String displayClass;
    String tags;

    ItemView(SiteItem item) {
      this.name = item.getName();
      this.displayClass = item.getDisplayClass();
      this.tags = String.join(",", item.getTags());
    }
  }

  /** The set of supplies-page filters, persisted in the HTTP session between visits. */
  @Value
  public static class FilterState {
    List<String> sites;
    List<String> states;
    List<String> counties;
    List<String> items;
    List<String> itemStatus;
    List<String> siteType;
    boolean acceptingDonations;
    boolean notAcceptingDonations;
    String sort;

    static FilterState defaults(boolean donateMode) {
      List<String> itemStatus = new ArrayList<>();
      itemStatus.add(ItemStatus.URGENTLY_NEEDED.getText());
      itemStatus.add(ItemStatus.NEEDED.getText());
      if (!donateMode) {
        itemStatus.add(ItemStatus.AVAILABLE.getText());
        itemStatus.add(ItemStatus.OVERSUPPLY.getText());
      }
      return new FilterState(
          new ArrayList<>(),
          new ArrayList<>(),
          new ArrayList<>(),
          new ArrayList<>(),
          itemStatus,
          new ArrayList<>(List.of("Food Pantry", "Distribution Center", "Supply Hub")),
          true,
          !donateMode,
          "last-updated");
    }

    SiteSupplyRequest toRequest() {
      return SiteSupplyRequest.builder()
          .sites(sites)
          .states(states)
          .counties(counties)
          .items(items)
          .itemStatus(itemStatus)
          .siteType(siteType)
          .acceptingDonations(acceptingDonations)
          .notAcceptingDonations(notAcceptingDonations)
          .build();
    }
  }
}
