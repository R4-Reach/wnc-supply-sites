package com.vanatta.helene.supplies.database.manage.add.site;

import com.vanatta.helene.supplies.database.DeploymentAdvice;
import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.data.CountyDao;
import com.vanatta.helene.supplies.database.data.SiteType;
import com.vanatta.helene.supplies.database.manage.ManageSiteDao;
import com.vanatta.helene.supplies.database.manage.SelectSiteController;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

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

  /** REST endpoint to create a new site */
  @PostMapping("/manage/add-site")
  @ResponseBody
  ResponseEntity<String> postNewSite(
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String phone,
      @RequestBody Map<String, String> params) {
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
      return ResponseEntity.ok(
          String.format(
              """
           { "result": "success", "manageSiteUrl": "%s" }
          """,
              manageSiteUrl));
    } catch (AddSiteDao.DuplicateSiteException e) {
      return ResponseEntity.badRequest()
          .body("{\"result\": \"fail\", \"error\": \"site name already exists\"}");
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest()
          .body(String.format("{\"result\": \"fail\", \"error\": \"%s\"}", e.getMessage()));
    }
  }
}
