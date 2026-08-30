package com.vanatta.helene.supplies.database.manage.contact;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.manage.SelectSiteController;
import com.vanatta.helene.supplies.database.manage.UserSiteAuthorization;
import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
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
public class SiteContactController {

  private final Jdbi jdbi;

  public static final String PATH_MANAGE_CONTACTS = "/manage/contact/contact";

  public static String buildManageContactsPath(long siteId) {
    return PATH_MANAGE_CONTACTS + "?siteId=" + siteId;
  }

  /** Fetches data for the manage site page */
  @GetMapping(PATH_MANAGE_CONTACTS)
  ModelAndView showSiteContactPage(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> sites, @RequestParam String siteId) {
    SiteDetailDao.SiteDetailData siteData =
        UserSiteAuthorization.isAuthorizedForSite(jdbi, sites, siteId).orElse(null);
    if (siteData == null) {
      return new ModelAndView("redirect:" + SelectSiteController.PATH_SELECT_SITE);
    }

    SiteDetailDao.SiteDetailData data = SiteDetailDao.lookupSiteById(jdbi, Long.parseLong(siteId));
    Map<String, Object> pageParams = new HashMap<>();
    pageParams.put(PageParam.SITE_ID.text, siteId);
    pageParams.put(PageParam.SITE_NAME.text, data.getSiteName());
    pageParams.put(
        PageParam.SITE_CONTACT_NAME.text, Optional.ofNullable(data.getContactName()).orElse(""));
    pageParams.put(
        PageParam.SITE_CONTACT_NUMBER.text,
        Optional.ofNullable(data.getContactNumber()).orElse(""));
    pageParams.put(
        PageParam.ADDITIONAL_CONTACTS.text, ContactDao.getManagers(jdbi, Long.parseLong(siteId)));

    return new ModelAndView("manage/contact/contact", pageParams);
  }

  @AllArgsConstructor
  public enum PageParam {
    SITE_ID("siteId"),
    SITE_NAME("siteName"),
    SITE_CONTACT_NAME("siteContactName"),
    SITE_CONTACT_NUMBER("siteContactNumber"),
    ADDITIONAL_CONTACTS("additionalContacts"),
    ;
    final String text;
  }

  /**
   * Removes an additional site manager. Posted by the manager row's htmx Remove button; returns an
   * empty body so htmx swaps the row out of the page.
   */
  @PostMapping("/manage/remove-manager")
  @ResponseBody
  ResponseEntity<String> removeManager(@RequestParam Map<String, String> params) {
    log.info("/manage/remove-manager received params: {}", params);

    long siteId = Long.parseLong(params.get("siteId"));
    Long managerId =
        Optional.ofNullable(params.get("managerId"))
            .map(s -> s.isBlank() ? null : s)
            .map(Long::parseLong)
            .orElse(null);

    ContactDao.removeAdditionalSiteManager(jdbi, siteId, managerId);

    return ResponseEntity.ok().header("Content-Type", "text/html; charset=UTF-8").body("");
  }

  /**
   * Adds a new additional site manager (when managerId is blank) or updates an existing one. Posted
   * by the manager forms; returns an HTML fragment: the saved row on its own for an update, or the
   * saved row plus a fresh blank add-form for a new manager, so the user can keep adding.
   */
  @PostMapping("/manage/add-manager")
  ModelAndView addManager(@RequestParam Map<String, String> params) {
    log.info("/manage/add-manager received params: {}", params);

    long siteId = Long.parseLong(params.get("siteId"));
    Long managerId =
        Optional.ofNullable(params.get("managerId"))
            .map(s -> s.isBlank() ? null : s)
            .map(Long::parseLong)
            .orElse(null);
    String contactName = params.get("name");
    String contactPhone = params.get("phone");

    final long idUpdated;
    if (managerId == null) {
      try {
        idUpdated = ContactDao.addAdditionalSiteManager(jdbi, siteId, contactName, contactPhone);
      } catch (Exception e) {
        boolean duplicate =
            e.getMessage() != null && e.getMessage().contains("duplicate key value");
        if (!duplicate) {
          log.error(
              "Error saving: siteId = {}, contact name = {}, contact phone ={}",
              siteId,
              contactName,
              contactPhone,
              e);
        }
        Map<String, Object> errorModel = new HashMap<>();
        errorModel.put("siteId", String.valueOf(siteId));
        errorModel.put(
            "errorMessage", duplicate ? "Duplicate phone number" : "Database error saving data");
        return new ModelAndView("manage/contact/manager-error", errorModel);
      }
    } else {
      var manager =
          ContactDao.SiteManager.builder()
              .id(managerId)
              .name(contactName)
              .phone(contactPhone)
              .build();
      ContactDao.updateAdditionalSiteManager(jdbi, siteId, manager);
      idUpdated = managerId;
    }

    Map<String, Object> model = new HashMap<>();
    model.put("siteId", String.valueOf(siteId));
    model.put("id", idUpdated);
    model.put("name", contactName);
    model.put("phone", contactPhone);
    return new ModelAndView(
        managerId == null ? "manage/contact/manager-added" : "manage/contact/manager-row", model);
  }
}
