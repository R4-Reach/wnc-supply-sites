package com.vanatta.helene.supplies.database.manage;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@AllArgsConstructor
@Slf4j
public class UpdateSiteDataController {
  private final Jdbi jdbi;

  /** Info update for a site, eg: site-rename, site contact info changed. */
  @PostMapping("/manage/update-site")
  @ResponseBody
  ResponseEntity<?> updateSiteData(@RequestBody Map<String, String> params) {
    log.info("Update site data request received: {}", params);

    String siteId = params.get("siteId");
    String field = params.get("field");
    String newValue = params.get("newValue");

    if (newValue != null) {
      newValue = newValue.trim();
    }

    if (ManageSiteDao.fetchSiteName(jdbi, Long.parseLong(siteId)) == null) {
      log.warn("invalid site id: {}, params: {}", siteId, params);
      return ResponseEntity.badRequest().body("Invalid site id");
    }

    var siteField = ManageSiteDao.SiteField.lookupField(field).orElse(null);
    if (siteField == null) {
      log.warn("Invalid field requested for update: {}, params: {}", field, params);
      return ResponseEntity.badRequest().body("Invalid field: " + field);
    }

    ManageSiteDao.updateSiteField(jdbi, Long.parseLong(siteId), siteField, newValue);
    log.info("Site updated: {}", params);
    return ResponseEntity.ok().body("Updated");
  }
}
