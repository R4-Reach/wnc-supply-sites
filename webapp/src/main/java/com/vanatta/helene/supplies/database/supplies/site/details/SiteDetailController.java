package com.vanatta.helene.supplies.database.supplies.site.details;

import com.vanatta.helene.supplies.database.DeploymentAdvice;
import com.vanatta.helene.supplies.database.auth.CookieAuthenticator;
import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.delivery.Delivery;
import com.vanatta.helene.supplies.database.delivery.DeliveryDao;
import com.vanatta.helene.supplies.database.delivery.DeliveryStatus;
import com.vanatta.helene.supplies.database.manage.contact.SiteContactController;
import com.vanatta.helene.supplies.database.manage.inventory.InventoryController;
import com.vanatta.helene.supplies.database.manage.receiving.SiteReceivingController;
import com.vanatta.helene.supplies.database.supplies.SiteSupplyRequest;
import com.vanatta.helene.supplies.database.supplies.SuppliesController;
import com.vanatta.helene.supplies.database.supplies.SuppliesDao;
import com.vanatta.helene.supplies.database.util.ListSplitter;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Value;
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
public class SiteDetailController {

  static final String PATH_SITE_DETAIL = "/supplies/site-detail";

  private final Jdbi jdbi;
  private final CookieAuthenticator cookieAuthenticator;

  public static String buildSiteLink(long siteId) {
    return PATH_SITE_DETAIL + "?id=" + siteId;
  }

  @AllArgsConstructor
  enum TemplateParams {
    EDIT_CONTACT_LINK("editContactLink"),
    EDIT_RECEIVING_LINK("editReceivingLink"),
    RECEIVING_NOTES("receivingNotes"),
    MAX_SUPPLY_LOAD("maxSupplyLoad"),
    EDIT_INVENTORY_LINK("editInventoryLink"),
    SITE_NAME("siteName"),
    WEBSITE("website"),
    FACEBOOK("facebook"),
    HOURS("hours"),
    ADDRESS_LINE1("addressLine1"),
    ADDRESS_LINE2("addressLine2"),
    GOOGLE_MAPS_ADDRESS("googleMapsAddress"),
    CONTACT_NAME("contactName"),
    CONTACT_NUMBER("contactNumber"),
    ADDITIONAL_CONTACTS("additionalContacts"),

    HAS_NEEDS("hasNeeds"),
    NEEDS_LIST("needsList"),
    NEEDS_ITEMS_CT("needsItemsCt"),
    NEEDS_LIST1("needsList1"),
    NEEDS_LIST2("needsList2"),

    HAS_AVAILABLE("hasAvailable"),
    AVAILABLE_ITEMS_CT("availableItemsCt"),
    AVAILABLE_LIST("availableList"),
    AVAILABLE_LIST1("availableList1"),
    AVAILABLE_LIST2("availableList2"),

    NEEDS_MATCHING("needsMatching"),
    NEEDS_MATCH_COUNT("matchCount"),

    HAS_INCOMING_DELIVERIES("hasIncomingDeliveries"),
    INCOMING_DELIVERIES("incomingDeliveries"),

    HAS_OUTGOING_DELIVERIES("hasOutgoingDeliveries"),
    OUTGOING_DELIVERIES("outgoingDeliveries"),

    showEditLinks("showEditLinks"),
    ;
    final String text;
  }

  @GetMapping(PATH_SITE_DETAIL)
  public ModelAndView siteDetail(
      @ModelAttribute(LoggedInAdvice.USER_SITES) List<Long> userSites,
      @ModelAttribute(DeploymentAdvice.DEPLOYMENT_STATE_LIST) List<String> stateList,
      @RequestParam(required = false) Long id,
      @RequestParam(required = false) Long wssId,
      HttpServletRequest request) {
    return siteDetail(
        userSites, stateList, id, wssId, cookieAuthenticator.isAuthenticated(request));
  }

  // @VisibleForTesting
  public ModelAndView siteDetail(
      List<Long> userSites, List<String> stateList, Long id, Long wssId, boolean isLoggedIn) {
    if (id == null && wssId == null) {
      return new ModelAndView("redirect:" + SuppliesController.PATH_SUPPLY_SEARCH);
    }

    if (id == null) {
      if (wssId != null) {
        id = SiteDetailDao.lookupSiteIdByWssId(jdbi, wssId);
        if (id == null) {
          log.warn("Invalid wss id received for site detail lookup: {}", wssId);
          return new ModelAndView("redirect:" + SuppliesController.PATH_SUPPLY_SEARCH);
        }
      }
    }
    assert id != null;

    SiteDetailDao.SiteDetailData siteDetailData = SiteDetailDao.lookupSiteById(jdbi, id);

    // if site not found, not accessible, or for logged in only users, then redirect
    if (siteDetailData == null
        || !siteDetailData.isActive()
        || (!isLoggedIn && !siteDetailData.isPubliclyVisible())) {
      return new ModelAndView("redirect:" + SuppliesController.PATH_SUPPLY_SEARCH);
    }

    Map<String, Object> siteDetails = new HashMap<>();

    siteDetails.put(TemplateParams.showEditLinks.text, userSites.contains(id));
    siteDetails.put(
        TemplateParams.EDIT_CONTACT_LINK.text, SiteContactController.buildManageContactsPath(id));
    siteDetails.put(
        TemplateParams.EDIT_INVENTORY_LINK.text, InventoryController.buildInventoryPath(id));
    siteDetails.put(TemplateParams.SITE_NAME.text, siteDetailData.getSiteName());

    siteDetails.put(
        TemplateParams.WEBSITE.text,
        siteDetailData.getWebsite() == null || siteDetailData.getWebsite().isBlank()
            ? null
            : new WebsiteLink(siteDetailData.getWebsite()));
    siteDetails.put(
        TemplateParams.FACEBOOK.text,
        siteDetailData.getFacebook() == null || siteDetailData.getFacebook().isBlank()
            ? null
            : new WebsiteLink(siteDetailData.getFacebook()));
    siteDetails.put(
        TemplateParams.HOURS.text,
        siteDetailData.getHours() == null || siteDetailData.getHours().isBlank()
            ? null
            : siteDetailData.getHours());

    // if we should show address, then address line 1 is street address
    // and line 2 will be city/state, otherwise line 1 is the city/state and there is no line 2
    // We show address if user is logged in, or if the site is publicly accessible.
    final String addressLine1 =
        (isLoggedIn
                || siteDetailData.isAcceptingDonations()
                || siteDetailData.isDistributingSupplies())
            ? siteDetailData.getAddress()
            : String.format("%s, %s", siteDetailData.getCity(), siteDetailData.getState());
    final String addressLine2 =
        (isLoggedIn
                || siteDetailData.isAcceptingDonations()
                || siteDetailData.isDistributingSupplies())
            ? String.format("%s, %s", siteDetailData.getCity(), siteDetailData.getState())
            : "";

    siteDetails.put(TemplateParams.ADDRESS_LINE1.text, addressLine1);

    siteDetails.put(TemplateParams.ADDRESS_LINE2.text, addressLine2);
    siteDetails.put(
        TemplateParams.GOOGLE_MAPS_ADDRESS.text,
        String.format("%s, %s", urlEncode(addressLine1), urlEncode(addressLine2)));

    // site supplies
    List<SuppliesDao.SuppliesQueryResult> supplies =
        SuppliesDao.getSupplyResults(
            jdbi,
            SiteSupplyRequest.builder().sites(List.of(siteDetailData.siteName)).build(),
            stateList);

    List<InventoryItem> needs =
        supplies.stream()
            .filter(i -> i.getItem() != null)
            .filter(i -> ItemStatus.fromTextValue(i.getItemStatus()).isNeeded())
            .map(InventoryItem::new)
            .sorted(Comparator.comparing(InventoryItem::getName))
            .toList();

    siteDetails.put(TemplateParams.NEEDS_LIST.text, needs);
    siteDetails.put(TemplateParams.NEEDS_ITEMS_CT.text, needs.size());

    List<List<InventoryItem>> needsSplit = ListSplitter.splitItemList(needs, 8);
    siteDetails.put(TemplateParams.NEEDS_LIST1.text, needsSplit.getFirst());
    siteDetails.put(
        TemplateParams.NEEDS_LIST2.text, needsSplit.size() > 1 ? needsSplit.get(1) : List.of());
    siteDetails.put(TemplateParams.HAS_NEEDS.text, needs.isEmpty() ? null : true);

    List<InventoryItem> available =
        supplies.stream()
            .filter(i -> i.getItem() != null)
            .filter(i -> !ItemStatus.fromTextValue(i.getItemStatus()).isNeeded())
            .map(InventoryItem::new)
            .sorted(Comparator.comparing(InventoryItem::getName))
            .toList();

    siteDetails.put(TemplateParams.AVAILABLE_LIST.text, available);
    siteDetails.put(TemplateParams.AVAILABLE_ITEMS_CT.text, available.size());

    List<List<InventoryItem>> availableSplit = ListSplitter.splitItemList(available, 8);
    siteDetails.put(TemplateParams.AVAILABLE_LIST1.text, availableSplit.getFirst());
    siteDetails.put(
        TemplateParams.AVAILABLE_LIST2.text,
        availableSplit.size() > 1 ? availableSplit.get(1) : List.of());
    siteDetails.put(TemplateParams.HAS_AVAILABLE.text, available.isEmpty() ? null : true);

    if (isLoggedIn) {
      siteDetails.put(
          TemplateParams.CONTACT_NAME.text,
          siteDetailData.getContactName() == null || siteDetailData.getContactName().isBlank()
              ? null
              : siteDetailData.getContactName());
      siteDetails.put(
          TemplateParams.CONTACT_NUMBER.text,
          siteDetailData.getContactNumber() == null || siteDetailData.getContactNumber().isBlank()
              ? null
              : siteDetailData.getContactNumber());

      siteDetails.put(
          TemplateParams.ADDITIONAL_CONTACTS.text,
          SiteDetailDao.lookupAdditionalSiteContacts(jdbi, id));

      siteDetails.put(
          TemplateParams.EDIT_RECEIVING_LINK.text, SiteReceivingController.buildLink(id));
      siteDetails.put(
          TemplateParams.RECEIVING_NOTES.text,
          siteDetailData.getReceivingNotes() == null || siteDetailData.getReceivingNotes().isBlank()
              ? null
              : siteDetailData.getReceivingNotes());
      siteDetails.put(TemplateParams.MAX_SUPPLY_LOAD.text, siteDetailData.getMaxSupply());

      List<Delivery> allDeliveries = DeliveryDao.fetchDeliveriesBySiteId(jdbi, id);

      // Should filter out any deliveries that are cancelled or deleted
      List<Delivery> incomingDeliveries =
          allDeliveries.stream()
              .filter(d -> siteDetailData.getSiteName().equals(d.getToSite()))
              .filter(
                  d ->
                      !d.getDeliveryStatus()
                          .equals(DeliveryStatus.DELIVERY_CANCELLED.getAirtableName()))
              .filter(
                  d ->
                      !d.getDeliveryStatus()
                          .equals(DeliveryStatus.DELIVERY_COMPLETED.getAirtableName()))
              .toList();
      siteDetails.put(TemplateParams.HAS_INCOMING_DELIVERIES.text, !incomingDeliveries.isEmpty());
      siteDetails.put(TemplateParams.INCOMING_DELIVERIES.text, incomingDeliveries);

      // Should filter out any deliveries that are cancelled or deleted
      List<Delivery> outgoingDeliveries =
          allDeliveries.stream()
              .filter(d -> siteDetailData.getSiteName().equals(d.getFromSite()))
              .filter(
                  d ->
                      !d.getDeliveryStatus()
                          .equals(DeliveryStatus.DELIVERY_CANCELLED.getAirtableName()))
              .filter(
                  d ->
                      !d.getDeliveryStatus()
                          .equals(DeliveryStatus.DELIVERY_COMPLETED.getAirtableName()))
              .toList();
      siteDetails.put(TemplateParams.HAS_OUTGOING_DELIVERIES.text, !outgoingDeliveries.isEmpty());
      siteDetails.put(TemplateParams.OUTGOING_DELIVERIES.text, outgoingDeliveries);

      // site needs list
      List<NeedsMatchingDao.NeedsMatchingResult> needsMatching =
          NeedsMatchingDao.executeByInternalId(jdbi, id, stateList);
      siteDetails.put(TemplateParams.NEEDS_MATCHING.text, needsMatching);
      siteDetails.put(TemplateParams.NEEDS_MATCH_COUNT.text, needsMatching.size());
    }
    return new ModelAndView("supplies/site-detail", siteDetails);
  }

  @Value
  static class InventoryItem {
    private final String name;
    private final String displayClass;

    InventoryItem(SuppliesDao.SuppliesQueryResult result) {
      name = result.getItem();
      displayClass = ItemStatus.fromTextValue(result.getItemStatus()).getCssClass();
    }
  }

  @Getter
  static class WebsiteLink {
    private final String href;
    private final String title;

    WebsiteLink(String link) {
      if (link.endsWith("/")) {
        link = link.substring(0, link.length() - 1);
      }

      if (link.startsWith("http://")) {
        href = link;
        title = link.substring("http://".length());
      } else if (link.startsWith("https://")) {
        href = link;
        title = link.substring("https://".length());
      } else {
        href = "http://" + link;
        title = link;
      }
    }
  }

  /** Does a quick URL encoding of a given value. */
  private static String urlEncode(String toEncode) {
    return URLEncoder.encode(toEncode, StandardCharsets.UTF_8);
  }
}
