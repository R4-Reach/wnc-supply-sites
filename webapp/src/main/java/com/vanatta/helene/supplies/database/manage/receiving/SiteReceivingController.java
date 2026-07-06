package com.vanatta.helene.supplies.database.manage.receiving;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.manage.ManageSiteDao;
import com.vanatta.helene.supplies.database.manage.SelectSiteController;
import com.vanatta.helene.supplies.database.manage.UserSiteAuthorization;
import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import com.vanatta.helene.supplies.database.util.HtmlSelectOptionsUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@AllArgsConstructor
@Slf4j
public class SiteReceivingController {

  private final Jdbi jdbi;

  public static String buildLink(Long id) {
    return "/manage/receiving/receiving?siteId=" + id;
  }

  /** Fetches data for the manage site page */
  @GetMapping("/manage/receiving/receiving")
  ModelAndView showSiteContactPage(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> sites, @RequestParam String siteId) {
    SiteDetailDao.SiteDetailData data =
        UserSiteAuthorization.isAuthorizedForSite(jdbi, sites, siteId).orElse(null);
    if (data == null) {
      return new ModelAndView("redirect:" + SelectSiteController.PATH_SELECT_SITE);
    }

    Map<String, Object> pageParams = new HashMap<>();
    pageParams.put(PageParam.SITE_ID.text, siteId);
    pageParams.put(PageParam.SITE_NAME.text, data.getSiteName());
    pageParams.put(PageParam.HOURS.text, Optional.ofNullable(data.getHours()).orElse(""));

    pageParams.put(PageParam.RECEIVING_NOTES.text, data.getReceivingNotes());

    List<HtmlSelectOptionsUtil.ItemListing> maxSupplyOptions =
        HtmlSelectOptionsUtil.createItemListing(
            data.getMaxSupply(),
            ManageSiteDao.getAllMaxSupplyOptions(jdbi).stream()
                .map(ManageSiteDao.MaxSupplyOption::getName)
                .toList());
    pageParams.put(PageParam.MAX_SUPPLY_OPTIONS.text, maxSupplyOptions);

    return new ModelAndView("manage/receiving/receiving", pageParams);
  }

  @AllArgsConstructor
  enum PageParam {
    SITE_ID("siteId"),
    SITE_NAME("siteName"),
    HOURS("hours"),
    MAX_SUPPLY_OPTIONS("maxSupplyDeliveryOptions"),
    RECEIVING_NOTES("receivingNotes"),
    ;
    final String text;
  }
}
