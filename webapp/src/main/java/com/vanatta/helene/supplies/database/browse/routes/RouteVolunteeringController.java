package com.vanatta.helene.supplies.database.browse.routes;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Handles driver volunteer requests for deliveries from the route-browser page. */
@Controller
@Slf4j
@AllArgsConstructor
public class RouteVolunteeringController {

  private final Jdbi jdbi;

  @PostMapping("/browse/routes/volunteer")
  ResponseEntity<String> volunteer(
      @RequestBody DeliveryVolunteerRequest params,
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String driverPhone) {
    log.info("/browse/routes/volunteer - Received driver volunteer request: {}", params);
    throw new UnsupportedOperationException("TODO: need to re-implement");
  }

  @Builder(toBuilder = true)
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class DeliveryVolunteerRequest {
    long fromSiteWssId;
    long toSiteWssId;
    String fromSiteName;
    String toSiteName;
    List<Long> itemList;
    String fromDate;
    String toDate;
    long driverAirtableId;
    String driverName;
  }
}
