package org.r4reach.manage;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.DeploymentAdvice;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.supplies.site.details.SiteDetailDao;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@AllArgsConstructor
@Slf4j
public class SelectSiteController {

  public static final String PATH_SELECT_SITE = "/manage/select-site";
  public static final String PATH_SITE_SELECTED = "/manage/site-selected";
  private final Jdbi jdbi;

  @Builder
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SiteSelection {
    Long id;
    String name;
  }

  /** User will be shown a page to select the site they want to manage. */
  @GetMapping(PATH_SELECT_SITE)
  ModelAndView showSelectSitePage(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> sites,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_STATE_LIST) List<String> states) {
    return showSelectSitePage(jdbi, sites, states);
  }

  public static ModelAndView showSelectSitePage(Jdbi jdbi, List<Long> sites, List<String> states) {
    Map<String, Object> pageParams = new HashMap<>();
    pageParams.put("hasSites", sites.isEmpty() ? false : true);
    pageParams.put(
        "sites", sites.isEmpty() ? null : ManageSiteDao.fetchSiteList(jdbi, sites, states));
    return new ModelAndView("manage/select-site", pageParams);
  }

  public static String buildSiteSelectedUrl(long siteId) {
    return PATH_SITE_SELECTED + "?siteId=" + siteId;
  }

  /**
   * After a site is selected, user selects which aspect they want to manage (eg: inventory, status)
   */
  @GetMapping(PATH_SITE_SELECTED)
  ModelAndView showSiteSelectedPage(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> sites,
      @RequestParam String siteId,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_STATE_LIST) List<String> states) {
    SiteDetailDao.SiteDetailData siteData =
        UserSiteAuthorization.isAuthorizedForSite(jdbi, sites, siteId).orElse(null);
    if (siteData == null) {
      return showSelectSitePage(jdbi, sites, states);
    }

    Map<String, String> pageParams = new HashMap<>();
    pageParams.put("siteName", siteData.getSiteName());
    pageParams.put("siteId", siteId);
    return new ModelAndView("manage/site-selected", pageParams);
  }

  public static List<ItemListing> createItemListing(
      String selectedValue, Collection<String> allValues) {
    return allValues.stream()
        .map(
            value ->
                ItemListing.builder()
                    .name(value)
                    .selected(value.equals(selectedValue) ? "selected" : "")
                    .build())
        .sorted(Comparator.comparing(ItemListing::getName))
        .toList();
  }

  @Builder
  @Data
  public static class ItemListing {
    String name;

    /** Should either be blank, or "selected" */
    String selected;
  }
}
