package org.r4reach.volunteer;

import java.util.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.DeploymentAdvice;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.twilio.sms.SmsSender;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller
@AllArgsConstructor
@Slf4j
public class VolunteerController {
  private final Jdbi jdbi;
  private final VolunteerService volunteerService;
  private final SmsSender smsSender;

  /** Users will be shown a form to request to make a delivery */
  @GetMapping("/volunteer/delivery")
  ModelAndView deliveryForm(
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_STATE_LIST) List<String> states) {
    return deliveryForm(jdbi, states);
  }

  public static ModelAndView deliveryForm(Jdbi jdbi, List<String> states) {
    Map<String, Object> pageParams = new HashMap<>();

    List<VolunteerService.SiteSelect> sites = VolunteerDao.fetchSiteSelect(jdbi, states);

    pageParams.put("sites", sites);
    return new ModelAndView("volunteer/delivery-form", pageParams);
  }

  /**
   * Create Volunteer Delivery and adds it to the DB. Posted by the delivery form as url-encoded
   * fields; returns an HTML fragment (success message + portal link, or an error message) that htmx
   * swaps into the page.
   */
  @PostMapping("/volunteer/delivery")
  ModelAndView submitDeliveryRequest(@ModelAttribute VolunteerService.DeliveryForm request) {
    log.info("Received delivery request for site: {}", request.site);
    Map<String, Object> params = new HashMap<>();
    try {
      VolunteerService.VolunteerDeliveryRequest createdDelivery =
          volunteerService.createVolunteerDelivery(jdbi, request);

      // Build and send sms
      String updateMessage =
          String.format(
              "WNC Supply Sites: "
                  + "\n A delivery request to %s has been created. "
                  + "\n Visit: wnc-supply-sites.com%s to view delivery portal.",
              createdDelivery.getSiteName(), createdDelivery.getPortalURL());

      // todo: Send Text Notification to volunteer and site manager
      smsSender.send(createdDelivery.getCleanedSitePhoneNumber(), updateMessage);
      smsSender.send(createdDelivery.getCleanedVolunteerPhoneNumber(), updateMessage);

      params.put("urlKey", createdDelivery.urlKey);
    } catch (Exception e) {
      log.error(e.getMessage());
      params.put("error", true);
    }
    return new ModelAndView("volunteer/delivery-success", params);
  }

  /** Returns an HTML fragment (site address + needed-item checkboxes) for the selected site. */
  @GetMapping("/volunteer/site-items")
  ModelAndView getSiteItems(@RequestParam("site") String siteId) {
    VolunteerService.Site site = VolunteerDao.fetchSiteItems(jdbi, Long.parseLong(siteId));
    Map<String, Object> params = new HashMap<>();
    params.put("address", site.getAddress() + ", " + site.getCounty() + ", " + site.getState());
    params.put("items", site.getItems());
    return new ModelAndView("volunteer/site-items-fragment", params);
  }

  @GetMapping("/volunteer/delivery/request")
  ModelAndView deliveryPortal(
      @ModelAttribute(LoggedInAdvice.USER_PHONE) String userPhone,
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> userSites,
      @RequestParam String urlKey) {
    return deliveryPortal(jdbi, userPhone, userSites, urlKey);
  }

  /**
   * Checks if - delivery exists and - if the user is already logged in check if user is a site
   * manager or the volunteer Returns - the urlKey and - a boolean representing if the user requires
   * verification or not
   */
  public static ModelAndView deliveryPortal(
      Jdbi jdbi, String userPhone, List<Long> userSites, String urlKey) {
    // Get Volunteer Delivery
    log.info("Received request for Volunteer Delivery {}", urlKey.toUpperCase());
    VolunteerService.VolunteerDeliveryRequest deliveryRequest =
        VolunteerService.getVolunteerDeliveryRequest(jdbi, urlKey.toUpperCase());

    Map<String, Object> pageParams = new HashMap<>();

    // If volunteer Delivery is not available reroute them to home
    // todo: create a 404 not found page to redirect to

    pageParams.put("urlKey", urlKey);

    // Check if user requires phone verification
    // true if user sites does not include siteId and user's phone number is not the volunteer's
    // number
    pageParams.put(
        "userRequiresPhoneAuth",
        !userSites.contains(deliveryRequest.siteId)
            && !Objects.equals(deliveryRequest.volunteerPhone, userPhone));

    pageParams.put("userPhone", userPhone == null ? "" : userPhone);

    return new ModelAndView("volunteer/delivery/request", pageParams);
  }

  /**
   * Verifies the phone number is associated with the delivery. On success returns the delivery
   * detail fragment; otherwise re-renders the verification form with an error. Posted by the
   * request portal's htmx verification form.
   */
  @PostMapping("/volunteer/verify-delivery")
  ModelAndView verifyAndRetrieveDelivery(
      @RequestParam String urlKey, @RequestParam("userPhone") String phoneNumber) {
    String key = urlKey.toUpperCase();
    VolunteerService.Access access =
        VolunteerService.verifyVolunteerPortalAccess(jdbi, key, phoneNumber, "delivery");

    if (!access.isAuthorized()) {
      log.info("Verification failed for volunteer delivery: {}", key);
      Map<String, Object> params = new HashMap<>();
      params.put("urlKey", urlKey);
      params.put("error", true);
      return new ModelAndView("volunteer/delivery/verification-form", params);
    }

    VolunteerService.VolunteerDeliveryRequest deliveryRequest =
        VolunteerService.getVolunteerDeliveryRequest(jdbi, key);
    return renderRequestDetails(access, deliveryRequest, phoneNumber);
  }

  /**
   * Verifies the user, updates the delivery status, and returns the refreshed delivery detail
   * fragment. Posted by the request portal's htmx accept/decline/cancel buttons.
   */
  @PostMapping("/volunteer/delivery/update")
  ModelAndView updateDeliveryStatus(
      @RequestParam String urlKey, @RequestParam String status, @RequestParam String phoneNumber) {
    String key = urlKey.toUpperCase();
    log.info("Received delivery update: {} -> {}", key, status);

    VolunteerService.Access access =
        VolunteerService.verifyVolunteerPortalAccess(jdbi, key, phoneNumber, "delivery");
    if (!access.isAuthorized()) {
      Map<String, Object> params = new HashMap<>();
      params.put("urlKey", urlKey);
      params.put("error", true);
      return new ModelAndView("volunteer/delivery/verification-form", params);
    }

    VolunteerService.VolunteerDeliveryRequest deliveryRequest =
        VolunteerService.getVolunteerDeliveryRequest(jdbi, key);
    VolunteerService.VolunteerDeliveryRequest updatedRequest =
        VolunteerService.updateDeliveryStatus(jdbi, access, status.toUpperCase(), deliveryRequest);

    // Build and send sms
    String updateMessage =
        String.format(
            "WNC Supply Sites: "
                + "\n Delivery %s has been updated to %s. "
                + "\n Visit: wnc-supply-sites.com%s to view delivery portal.",
            updatedRequest.getUrlKey(), updatedRequest.getStatus(), updatedRequest.getPortalURL());

    // todo: Send Text Notification to volunteer and site manager
    smsSender.send(updatedRequest.getCleanedSitePhoneNumber(), updateMessage);
    smsSender.send(updatedRequest.getCleanedVolunteerPhoneNumber(), updateMessage);

    return renderRequestDetails(access, updatedRequest, phoneNumber);
  }

  /** Builds the delivery-detail fragment model, applying status-based data scrubbing. */
  private ModelAndView renderRequestDetails(
      VolunteerService.Access access,
      VolunteerService.VolunteerDeliveryRequest deliveryRequest,
      String userPhone) {
    Map<String, Object> params = new HashMap<>(deliveryRequest.scrubDataBasedOnStatus());
    String status = deliveryRequest.getStatus();

    params.put("statusPending", "PENDING".equals(status));
    params.put("statusAccepted", "ACCEPTED".equals(status));
    params.put("statusDeclined", "DECLINED".equals(status));
    params.put("statusCancelled", "CANCELLED".equals(status));

    boolean active = !("DECLINED".equals(status) || "CANCELLED".equals(status));
    params.put("active", active);
    params.put("showInactiveMessage", !active);
    params.put(
        "showAcceptDecline",
        "PENDING".equals(status) && Boolean.TRUE.equals(access.getHasManagerAccess()));
    params.put(
        "showCancel",
        ("PENDING".equals(status) && Boolean.TRUE.equals(access.getHasVolunteerAccess()))
            || "ACCEPTED".equals(status));

    params.put("userPhoneNumber", userPhone);

    Object address = params.get("address");
    if (address != null) {
      params.put("mapQuery", address + ", " + params.getOrDefault("city", ""));
    }
    return new ModelAndView("volunteer/delivery/request-details", params);
  }
}
