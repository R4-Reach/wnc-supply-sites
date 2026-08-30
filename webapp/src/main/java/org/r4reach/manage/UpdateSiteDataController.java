package org.r4reach.manage;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@AllArgsConstructor
@Slf4j
public class UpdateSiteDataController {
  private final Jdbi jdbi;

  /**
   * Info update for a single site field (site-rename, contact info, etc). Posted by each manage
   * page's htmx field form as url-encoded params; returns an HTML confirmation fragment that htmx
   * swaps in beside the field.
   */
  @PostMapping("/manage/update-site")
  @ResponseBody
  ResponseEntity<?> updateSiteData(@RequestParam Map<String, String> params) {
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
    String message =
        newValue == null || newValue.isBlank() ? field + " was deleted" : field + " updated";
    return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body("<span class=\"green-check\">&#10003;</span> " + message);
  }
}
