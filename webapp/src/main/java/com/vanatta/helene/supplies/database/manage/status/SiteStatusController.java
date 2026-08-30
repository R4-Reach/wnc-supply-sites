package com.vanatta.helene.supplies.database.manage.status;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.data.SiteType;
import com.vanatta.helene.supplies.database.manage.ManageSiteDao;
import com.vanatta.helene.supplies.database.manage.SelectSiteController;
import com.vanatta.helene.supplies.database.manage.UserSiteAuthorization;
import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import com.vanatta.helene.supplies.database.util.EnumUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@AllArgsConstructor
@Slf4j
public class SiteStatusController {

  private final Jdbi jdbi;

  /** Displays the 'manage-status' page. */
  @GetMapping("/manage/status/status")
  ModelAndView showManageStatusPage(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> sites, @RequestParam String siteId) {
    SiteDetailDao.SiteDetailData siteDetailData =
        UserSiteAuthorization.isAuthorizedForSite(jdbi, sites, siteId).orElse(null);
    if (siteDetailData == null) {
      return new ModelAndView("redirect:" + SelectSiteController.PATH_SELECT_SITE);
    }

    Map<String, String> pageParams = new HashMap<>();
    pageParams.put("siteName", siteDetailData.getSiteName());
    pageParams.put("siteId", siteId);

    ManageSiteDao.SiteStatus siteStatus =
        ManageSiteDao.fetchSiteStatus(jdbi, Long.parseLong(siteId));
    pageParams.put("siteActive", siteStatus.isActive() ? "true" : null);
    pageParams.put("sitePublic", siteStatus.isPubliclyVisible() ? "true" : null);
    pageParams.put(
        "inactiveReason", Optional.ofNullable(siteStatus.getInactiveReason()).orElse(""));

    pageParams.put("siteAcceptingDonations", siteStatus.isAcceptingDonations() ? "true" : null);
    pageParams.put(
        "siteDistributingDonations", siteStatus.isDistributingSupplies() ? "true" : null);

    pageParams.put(
        "distributionSiteChecked",
        siteStatus.getSiteTypeEnum() == SiteType.DISTRIBUTION_CENTER ? "checked" : "");
    pageParams.put(
        "supplyHubChecked", siteStatus.getSiteTypeEnum() == SiteType.SUPPLY_HUB ? "checked" : "");
    pageParams.put(
        "foodPantryChecked", siteStatus.getSiteTypeEnum() == SiteType.FOOD_PANTRY ? "checked" : "");

    return new ModelAndView("manage/status/status", pageParams);
  }

  @AllArgsConstructor
  @Getter
  public enum EnumStatusUpdateFlag {
    ACTIVE("active"),
    SITE_TYPE("distSite"),
    ACCEPTING_SUPPLIES("acceptingSupplies"),
    DISTRIBUTING_SUPPLIES("distributingSupplies"),
    PUBLICLY_VISIBLE("publiclyVisible"),
    INACTIVE_REASON("inactiveReason"),
    ;
    final String text;

    static Optional<EnumStatusUpdateFlag> fromText(String input) {
      return EnumUtil.mapText(values(), EnumStatusUpdateFlag::getText, input);
    }
  }

  /**
   * Toggles the status of a site (active/accepting donations/etc). Posted by the status page's htmx
   * forms as url-encoded params; returns an HTML confirmation fragment that htmx swaps in beside
   * the control that was changed.
   */
  @PostMapping("/manage/update-status")
  @ResponseBody
  ResponseEntity<?> updateStatus(@RequestParam Map<String, String> params) {
    String siteId = params.get("siteId");
    String statusFlag = params.get("statusFlag");
    String newValue = params.get("newValue");

    String siteName = ManageSiteDao.fetchSiteName(jdbi, Long.parseLong(siteId));
    if (siteName == null) {
      log.warn(
          "Invalid site update value received, invalid site id (not found), params: {}", params);
      return ResponseEntity.badRequest().body("Invalid site id: " + siteId);
    }

    log.info("Site update received, site name: {}, params; {}", siteName, params);

    var flag = EnumStatusUpdateFlag.fromText(statusFlag).orElse(null);
    if (flag == null) {
      log.warn("Status page, invalid status flag received. Params: {}", params);
      return ResponseEntity.badRequest().body("Invalid status flag: " + statusFlag);
    }

    switch (flag) {
      case ACCEPTING_SUPPLIES:
        ManageSiteDao.updateSiteAcceptingDonationsFlag(
            jdbi, Long.parseLong(siteId), Boolean.parseBoolean(newValue));
        break;
      case DISTRIBUTING_SUPPLIES:
        ManageSiteDao.updateSiteDistributingDonationsFlag(
            jdbi, Long.parseLong(siteId), Boolean.parseBoolean(newValue));
        break;
      case SITE_TYPE:
        var siteType = SiteType.parseSiteType(newValue);
        ManageSiteDao.updateSiteType(jdbi, Long.parseLong(siteId), siteType);
        break;
      case PUBLICLY_VISIBLE:
        ManageSiteDao.updateSitePubliclyVisible(
            jdbi, Long.parseLong(siteId), Boolean.parseBoolean(newValue));
        break;
      case ACTIVE:
        ManageSiteDao.updateSiteActiveFlag(
            jdbi, Long.parseLong(siteId), Boolean.parseBoolean(newValue));
        break;
      case INACTIVE_REASON:
        ManageSiteDao.updateInactiveReason(jdbi, Long.parseLong(siteId), newValue);
        break;
      default:
        throw new IllegalArgumentException("Unmapped status flag: " + statusFlag);
    }

    return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body(confirmationFragment(flag, newValue));
  }

  /** HTML shown next to a status control after a successful update. */
  private static String confirmationFragment(EnumStatusUpdateFlag flag, String newValue) {
    boolean on = Boolean.parseBoolean(newValue);
    String message =
        switch (flag) {
          case ACCEPTING_SUPPLIES ->
              "Site status set to " + (on ? "" : "NOT ") + "accepting supplies";
          case DISTRIBUTING_SUPPLIES ->
              "Site status set to " + (on ? "" : "NOT ") + "distributing supplies";
          case SITE_TYPE -> "Site type set to " + newValue;
          case PUBLICLY_VISIBLE ->
              "Site set to " + (on ? "publicly visible" : "visible to logged in users only");
          case ACTIVE -> "Site status set to " + (on ? "active" : "inactive");
          case INACTIVE_REASON ->
              newValue == null || newValue.isBlank()
                  ? "Inactive reason cleared"
                  : "Inactive reason updated";
        };
    return "<span class=\"green-check\">&#10003;</span> " + message;
  }
}
