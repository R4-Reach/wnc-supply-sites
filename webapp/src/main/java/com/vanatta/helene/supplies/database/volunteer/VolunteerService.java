package com.vanatta.helene.supplies.database.volunteer;

import static com.vanatta.helene.supplies.database.util.URLKeyGenerator.generateUrlKey;
import static com.vanatta.helene.supplies.database.volunteer.VolunteerDao.*;

import com.vanatta.helene.supplies.database.util.PhoneNumberUtil;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
@Slf4j
public class VolunteerService {

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SiteSelect {
    Long id;
    String name;
    String county;
    String state;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Item {
    Long id;
    String name;
    String status;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Site {
    Long id;
    String name;
    String address;
    String county;
    String state;
    List<Item> items;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class DeliveryForm {
    List<Long> neededItems;
    String site;
    String volunteerContact;
    String volunteerName;
    String urlKey;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  @Builder
  public static class VolunteerDelivery {
    Long id;
    String volunteerName;
    String volunteerPhone;
    Long siteId;
    String urlKey;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class VolunteerDeliveryItem {
    Long id;
    Long site_item_id;
    Long volunteer_delivery_id;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class VolunteerDeliveryRequestItem {
    String name;
    String status;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class VolunteerDeliveryRequest {
    Long id;
    String status;
    String siteName;
    Long siteId;
    String urlKey;
    String address;
    String city;
    String volunteerName;
    String volunteerPhone;
    String siteContactNumber;
    String siteContactName;
    List<VolunteerDeliveryRequestItem> items;

    public void insertItems(List<VolunteerDeliveryRequestItem> deliveryItems) {
      this.items = deliveryItems;
    }

    public HashMap<String, Object> scrubDataBasedOnStatus() {
      HashMap<String, Object> scrubbedData = new HashMap<>();
      scrubbedData.put("status", this.status);
      scrubbedData.put("urlKey", this.urlKey);

      // Provide site name, volunteerName, site address, siteId, and items is request is still
      // active (pending/accepted);
      if (!Objects.equals(this.status, "DECLINED") && !Objects.equals(this.status, "CANCELLED")) {
        scrubbedData.put("siteName", this.siteName);
        scrubbedData.put("volunteerName", this.volunteerName);
        scrubbedData.put("address", this.address);
        scrubbedData.put("city", this.city);
        scrubbedData.put("siteId", this.siteId);
        scrubbedData.put("items", this.items);
      }

      // Provided user info and site contact if accepted
      if (Objects.equals(this.status, "ACCEPTED")) {
        scrubbedData.put("volunteerPhone", this.volunteerPhone);
        scrubbedData.put("siteContactNumber", this.siteContactNumber);
        scrubbedData.put("siteContactName", this.siteContactName);
      }
      ;
      return scrubbedData;
    }

    public String getCleanedSitePhoneNumber() {
      return PhoneNumberUtil.toCanonical(this.siteContactNumber);
    }

    public String getCleanedVolunteerPhoneNumber() {
      return PhoneNumberUtil.toCanonical(this.volunteerPhone);
    }

    public String getPortalURL() {
      return String.format("/volunteer/delivery/request?urlKey=%s", this.getUrlKey());
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class VerificationRequest {
    String phoneNumber;
    String urlKey;
    String section;
  }

  @Data
  @Builder(toBuilder = true)
  public static class Access {
    Boolean hasManagerAccess;
    Boolean hasVolunteerAccess;

    public Boolean isAuthorized() {
      return this.hasManagerAccess || this.hasVolunteerAccess;
    }
  }

  @Data
  public static class UpdateRequest {
    String phoneNumber;
    String urlKey;
    String status;
  }

  /** Creates a new volunteer delivery */
  public VolunteerService.VolunteerDeliveryRequest createVolunteerDelivery(
      Jdbi jdbi, DeliveryForm request) {
    Handle handle = jdbi.open();
    try {
      handle.begin();

      // Remove '-' from phone number
      request.volunteerContact = String.join("", request.volunteerContact.split("-"));

      // Add urlKey
      request.urlKey = generateUrlKey();

      Long volunteerDeliveryId = VolunteerDao.createVolunteerDelivery(handle.getJdbi(), request);

      createVolunteerDeliveryItems(handle.getJdbi(), volunteerDeliveryId, request.getNeededItems());

      handle.commit();
      log.info("Created volunteer delivery in DB of ID: {}", volunteerDeliveryId);

      return VolunteerService.getVolunteerDeliveryRequest(jdbi, request.urlKey);
    } catch (Exception e) {
      handle.rollback();
      log.error("Error while creating volunteer delivery. Transaction rolled back.", e);
      throw new RuntimeException("Error while creating volunteer delivery. Rolling back.", e);
    } finally {
      handle.close();
    }
  }

  /** Grabs a volunteer delivery request via ID */
  public static VolunteerDelivery getDeliveryById(Jdbi jdbi, Long id) {
    try {
      return VolunteerDao.getVolunteerDeliveryById(jdbi, id);
    } catch (Exception e) {
      log.error("Error while looking up delivery by id: ", e);
      throw new RuntimeException("Error while looking up delivery by id: ", e);
    }
  }

  /**
   * Grabs a volunteer delivery request via urlKey This method returns Delivery and Delivery Items
   */
  public static VolunteerDeliveryRequest getVolunteerDeliveryRequest(Jdbi jdbi, String urlKey) {
    try {
      VolunteerDeliveryRequest volunteerDeliveryRequest =
          getVolunteerDeliveryByUrlKey(jdbi, urlKey);

      List<VolunteerDeliveryRequestItem> deliveryItems =
          VolunteerDao.getVolunteerDeliveryItems(jdbi, volunteerDeliveryRequest.getId());

      volunteerDeliveryRequest.insertItems(deliveryItems);

      return volunteerDeliveryRequest;
    } catch (Exception e) {
      log.error("Error while looking up delivery by urlKey: ", e);
      throw new RuntimeException("Error while looking up delivery by urlKey: ", e);
    }
  }

  /**
   * Determines which delivery type this verify request is for and calls the correct verify-er
   * function
   */
  public static Access verifyVolunteerPortalAccess(
      Jdbi jdbi, String urlKey, String phoneNumber, String section) {
    switch (section) {
      case "delivery":
        // Grab delivery request and calls correct verify-er function
        VolunteerDeliveryRequest deliveryRequest =
            VolunteerDao.getVolunteerDeliveryByUrlKey(jdbi, urlKey);
        return verifyDeliveryPortalAccess(phoneNumber, deliveryRequest);
      default:
        return Access.builder().hasManagerAccess(false).hasVolunteerAccess(false).build();
    }
  }

  /**
   * Verify-er function. Determines if a user is a volunteer, manager , both or neither. returns the
   * result
   */
  private static Access verifyDeliveryPortalAccess(
      String userPhoneNumber, VolunteerDeliveryRequest deliveryRequest) {
    String cleanedUserPhoneNumber = PhoneNumberUtil.toCanonical(userPhoneNumber);

    Boolean hasVolunteerAccess =
        Objects.equals(deliveryRequest.getCleanedVolunteerPhoneNumber(), cleanedUserPhoneNumber);
    Boolean hasSiteManagerAccess =
        Objects.equals(deliveryRequest.getCleanedSitePhoneNumber(), cleanedUserPhoneNumber);

    return Access.builder()
        .hasManagerAccess(hasSiteManagerAccess)
        .hasVolunteerAccess(hasVolunteerAccess)
        .build();
  }

  /**
   * Updates the delivery status and returns the updated delivery If not valid, return the old
   * delivery
   */
  public static VolunteerDeliveryRequest updateDeliveryStatus(
      Jdbi jdbi, Access access, String newStatus, VolunteerDeliveryRequest delivery) {
    String urlKey = delivery.getUrlKey();
    Boolean requestIsValid = validateDeliveryUpdate(access, delivery, newStatus);
    if (requestIsValid) {
      VolunteerDao.updateDeliveryStatus(jdbi, urlKey, newStatus);
    }
    return getVolunteerDeliveryRequest(jdbi, urlKey);
  }

  /** Checks if the requested new status is a valid request */
  private static Boolean validateDeliveryUpdate(
      Access access, VolunteerDeliveryRequest delivery, String newStatus) {
    String currentStatus = delivery.getStatus();

    if (Objects.equals(newStatus, "ACCEPTED") || Objects.equals(newStatus, "DECLINED")) {
      // If new status is ACCEPTED or DECLINED
      // The current status must be PENDING
      // And the user must have manager access
      if (!access.getHasManagerAccess()) {
        log.error("Site update failed: user not authorized");
        return false;
      }
      if (!Objects.equals(currentStatus, "PENDING")) {
        log.error("Site update failed: Invalid current state");
        return false;
      }
    } else if (Objects.equals(newStatus, "CANCELLED")) {
      // If new status is CANCELLED
      // User has to be authorized (manager or volunteer)
      if (!access.isAuthorized()) {
        log.error("Site update failed: user not authorized");
        return false;
      }
    } else if (
    // If the new status to update to is not one of the accepted values
    !Objects.equals(newStatus, "ACCEPTED")
        && !Objects.equals(newStatus, "DECLINED")
        && !Objects.equals(newStatus, "CANCELLED")) {
      log.error("Site update failed: Invalid new status");
      return false;
    }

    return true;
  }
}
