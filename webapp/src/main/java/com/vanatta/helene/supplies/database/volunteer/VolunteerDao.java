package com.vanatta.helene.supplies.database.volunteer;

import com.vanatta.helene.supplies.database.volunteer.VolunteerService.DeliveryForm;
import com.vanatta.helene.supplies.database.volunteer.VolunteerService.Item;
import com.vanatta.helene.supplies.database.volunteer.VolunteerService.Site;
import com.vanatta.helene.supplies.database.volunteer.VolunteerService.SiteSelect;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;

@Slf4j
public class VolunteerDao {

  static List<SiteSelect> fetchSiteSelect(Jdbi jdbi, List<String> states) {
    // todo: Write test
    if (states == null || states.isEmpty()) {
      return List.of();
    }

    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                select s.id, s.name, c.name as county, c.state
                from site s
                join county c on c.id = s.county_id
                where
                c.state in (<states>)
                and
                s.publicly_visible = true
                and
                s.active = true
                and
                s.accepting_donations = true
                and exists (
                  select 1
                  from site_item si
                  join item_status ist on si.item_status_id = ist.id
                  Where
                  si.site_id = s.id
                  and
                  ist.is_need = true)
                order by lower(s.name)
                """)
                .bindList("states", states)
                .mapToBean(SiteSelect.class)
                .list());
  }

  static Site fetchSiteItems(Jdbi jdbi, Long siteId) {
    return jdbi.withHandle(
        handle -> {
          Site site =
              handle
                  .createQuery(
                      """
                select s.id, s.name, s.address ,c.name as county, c.state
                from site s
                join county c on c.id = s.county_id
                where
                s.id = :siteId
                and
                s.active = true
                and
                s.publicly_visible = true
              """)
                  .bind("siteId", siteId)
                  .mapToBean(Site.class)
                  .findOne()
                  .orElse(null);

          if (site == null) {
            return null;
          }

          List<Item> items =
              handle
                  .createQuery(
                      """
              select si.id as id, i.name as name, ist.name as status
              from site_item si
              join item i on si.item_id = i.id
              join item_status ist on si.item_status_id = ist.id
              where si.site_id = :siteId
              and ist.is_need
              order by ist.sort_order, i.name
              """)
                  .bind("siteId", siteId)
                  .mapToBean(VolunteerService.Item.class)
                  .list();
          site.setItems(items);
          return site;
        });
  }

  static Long createVolunteerDelivery(Jdbi jdbi, DeliveryForm form) {
    // Create Delivery
    String insertDelivery =
        """
          INSERT INTO volunteer_delivery (
            volunteer_name,
            volunteer_phone,
            site_id,
            url_key
          ) values (
            :volunteerName,
            :volunteerPhone,
            :siteId,
            :URLKey
          )
        """;

    return jdbi.withHandle(
        handle ->
            handle
                .createUpdate(insertDelivery)
                .bind("volunteerName", form.getVolunteerName())
                .bind("volunteerPhone", form.getVolunteerContact())
                .bind("siteId", Integer.parseInt(form.getSite()))
                .bind("URLKey", form.getUrlKey())
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
  }

  static void createVolunteerDeliveryItems(Jdbi jdbi, Long deliveryId, List<Long> itemIds) {

    String insertItem =
        """
          INSERT INTO volunteer_delivery_item (
            site_item_id,
            volunteer_delivery_id
          ) VALUES (
            :site_item_id,
            :volunteerDeliveryId
          )
          """;

    for (Long itemId : itemIds) {
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(insertItem)
                  .bind("site_item_id", itemId)
                  .bind("volunteerDeliveryId", deliveryId)
                  .execute());
    }
    ;
  }

  static VolunteerService.VolunteerDelivery getVolunteerDeliveryById(Jdbi jdbi, Long deliveryId) {
    String query =
        """
        SELECT id, volunteer_phone, volunteer_name, site_id, url_key
        FROM volunteer_delivery
        WHERE volunteer_delivery.id = :id
        """;
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(query)
                .bind("id", deliveryId)
                .mapToBean(VolunteerService.VolunteerDelivery.class)
                .one());
  }

  static VolunteerService.VolunteerDeliveryRequest getVolunteerDeliveryByUrlKey(
      Jdbi jdbi, String urlKey) {
    String query =
        """
        SELECT
          vd.id,
          vd.volunteer_phone,
          vd.volunteer_name,
          vd.url_key,
          vd.status,
          site.id as site_id,
          site.name as site_name,
          site.address,
          site.city,
          site.contact_number as site_contact_number,
          site.contact_name as site_contact_name
        FROM volunteer_delivery vd
        LEFT JOIN site
        ON vd.site_id = site.id
        WHERE vd.url_key = :urlKey
        """;

    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(query)
                .bind("urlKey", urlKey)
                .mapToBean(VolunteerService.VolunteerDeliveryRequest.class)
                .one());
  }

  static List<VolunteerService.VolunteerDeliveryRequestItem> getVolunteerDeliveryItems(
      Jdbi jdbi, Long deliveryId) {
    String query =
        """
        SELECT
            i.name AS name,
            ist.name AS status
        FROM volunteer_delivery_item vdi
        JOIN site_item si ON vdi.site_item_id = si.id
        JOIN item i ON si.item_id = i.id
        JOIN item_status ist ON si.item_status_id = ist.id
        WHERE vdi.volunteer_delivery_id = :volunteerDeliveryId
        """;

    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(query)
                .bind("volunteerDeliveryId", deliveryId)
                .mapToBean(VolunteerService.VolunteerDeliveryRequestItem.class)
                .list());
  }

  static String updateDeliveryStatus(Jdbi jdbi, String urlKey, String status) {
    String updateStatus =
        """
          UPDATE volunteer_delivery
          SET status = CAST(:status AS volunteer_delivery_status_enum)
          WHERE url_key = :urlKey
          RETURNING url_key
        """;
    return jdbi.withHandle(
        handle ->
            handle
                .createUpdate(updateStatus)
                .bind("status", status)
                .bind("urlKey", urlKey)
                .executeAndReturnGeneratedKeys("url_key")
                .mapTo(String.class)
                .one());
  }
}
