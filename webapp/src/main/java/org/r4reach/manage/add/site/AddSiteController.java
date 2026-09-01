package org.r4reach.manage.add.site;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.DeploymentAdvice;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.r4reach.data.CountyDao;
import org.r4reach.data.SiteType;
import org.r4reach.manage.ManageSiteDao;
import org.r4reach.manage.SelectSiteController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.HtmlUtils;

@Controller
@AllArgsConstructor
@Slf4j
public class AddSiteController {

  private final Jdbi jdbi;

  /** Shows the form for adding a brand new site */
  @GetMapping("/manage/new-site/add-site")
  ModelAndView showAddNewSiteForm(
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_FULL_STATE_LIST) List<String> stateList) {
    Map<String, Object> model = new HashMap<>();

    Map<String, List<String>> counties = CountyDao.fetchFullCountyListing(jdbi, stateList);
    model.put("fullCountyList", counties);
    model.put("stateList", SelectSiteController.createItemListing("NC", counties.keySet()));
    String defaultState = counties.keySet().stream().sorted().toList().getFirst();
    model.put(
        "countyList",
        SelectSiteController.createItemListing(
            counties.get(defaultState).getFirst(), counties.get(defaultState)));

    List<SelectOption> maxSupplyDeliveryOptions =
        ManageSiteDao.getAllMaxSupplyOptions(jdbi).stream()
            .map(
                v ->
                    SelectOption.builder()
                        .name(v.getName())
                        .selected(v.isDefaultSelection())
                        .build())
            .toList();

    model.put("maxSupplyDeliveryOptions", maxSupplyDeliveryOptions);

    return new ModelAndView("manage/new-site/add-site", model);
  }

  @Builder
  @Value
  static class SelectOption {
    String name;
    Boolean selected;
  }

  /**
   * Creates a new site. Posted by the add-site page's htmx form as url-encoded params; on success
   * responds with an {@code HX-Redirect} header so htmx navigates the browser to the new site's
   * management page.
   */
  @PostMapping("/manage/add-site")
  @ResponseBody
  ResponseEntity<String> postNewSite(
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String phone,
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam Map<String, String> params) {
    // Role check: creating sites is a site-manager/admin capability, not something every
    // logged-in user may do. Without it, any user could pollute the catalog with arbitrary sites.
    if (!UserRole.canManageSites(roles)) {
      log.warn("Unauthorized add-site attempt, roles: {}, params: {}", roles, params);
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not authorized to add sites");
    }
    log.info("Received add new site data: {}", params);
    var addSiteData =
        AddSiteData.builder()
            .siteName(params.get("siteName"))
            .streetAddress(params.get("streetAddress"))
            .city(params.get("city"))
            .state(params.get("state"))
            .county(params.get("county"))
            .website(params.get("website"))
            .facebook(params.get("facebook"))
            .siteType(SiteType.parseSiteType(params.get("siteType")))
            .siteHours(params.get("siteHours"))
            .maxSupplyLoad(params.get("maxSupplyLoad"))
            .receivingNotes(params.get("receivingNotes"))
            .contactName(params.get("contactName"))
            .contactNumber(phone)
            .build();
    if (addSiteData.isMissingRequiredData()) {
      log.warn(
          "Add new site data is missing required data. Add new site data received: {}",
          addSiteData);
      // front end should be enforcing required data, error messaging back to user here is
      // pretty minimal.
      return ResponseEntity.badRequest().body("Failed, missing required data.");
    }
    try {
      long newSiteId = AddSiteDao.addSite(jdbi, addSiteData);

      String manageSiteUrl = SelectSiteController.buildSiteSelectedUrl(newSiteId);
      return ResponseEntity.ok()
          .header("HX-Redirect", manageSiteUrl)
          .body(
              String.format(
                  """
               { "result": "success", "manageSiteUrl": "%s" }
              """,
                  manageSiteUrl));
    } catch (AddSiteDao.DuplicateSiteException e) {
      return ResponseEntity.badRequest()
          .body("{\"result\": \"fail\", \"error\": \"site name already exists\"}");
    } catch (IllegalArgumentException e) {
      // The message echoes user-supplied input (e.g. the county) and the page inserts this body via
      // innerHTML, so escape it — an unescaped value would be a reflected-XSS sink.
      return ResponseEntity.badRequest()
          .body(
              String.format(
                  "{\"result\": \"fail\", \"error\": \"%s\"}",
                  HtmlUtils.htmlEscape(e.getMessage())));
    }
  }
}
