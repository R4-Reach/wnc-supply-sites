package com.vanatta.helene.supplies.database.delivery;

import com.google.gson.Gson;
import com.vanatta.helene.supplies.database.util.SecretCodeGenerator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Jdbi;

/**
 * Test fixture for seeding a delivery (and its items) into the database. Deliveries are identified
 * by their public wss_id; {@link #deliveryId} is that value.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryFixture {
  Long deliveryId;

  String publicUrlKey;
  String dispatcherCode;

  String deliveryStatus;
  List<String> dispatcherName;
  List<String> dispatcherNumber;
  List<String> driverName;
  List<String> driverNumber;
  List<Long> dropOffSiteWssId;
  List<Long> pickupSiteWssId;
  List<Long> itemListWssIds;
  List<String> itemList;
  List<String> licensePlateNumbers;
  String targetDeliveryDate;
  String dispatcherNotes;

  List<String> pickupSiteName;
  List<String> pickupContactName;
  List<String> pickupContactPhone;
  List<String> pickupHours;
  List<String> pickupAddress;
  List<String> pickupCity;
  List<String> pickupState;

  List<String> dropoffSiteName;
  List<String> dropoffContactName;
  List<String> dropoffContactPhone;
  List<String> dropoffHours;
  List<String> dropoffAddress;
  List<String> dropoffCity;
  List<String> dropoffState;

  static DeliveryFixture parseJson(String inputJson) {
    return new Gson().fromJson(inputJson, DeliveryFixture.class);
  }

  /** Inserts (or updates, keyed on wss_id) the delivery and replaces its item list. */
  public void store(Jdbi jdbi) {
    String upsert =
        """
        insert into delivery(
          from_site_id, to_site_id, delivery_status, target_delivery_date,
          dispatcher_name, dispatcher_number, driver_name, driver_number,
          driver_license_plates, wss_id, dispatcher_notes, public_url_key,
          dispatch_code, driver_code,
          pickup_site_name, pickup_contact_name, pickup_contact_phone,
          pickup_hours, pickup_address, pickup_city, pickup_state,
          dropoff_site_name, dropoff_contact_name, dropoff_contact_phone,
          dropoff_hours, dropoff_address, dropoff_city, dropoff_state)
        values(
          (select id from site where wss_id = :fromSiteWssId),
          (select id from site where wss_id = :toSiteWssId),
          :deliveryStatus,
          to_date(:targetDeliveryDate, 'YYYY-MM-DD'),
          :dispatcherName,
          :dispatcherNumber,
          :driverName,
          :driverNumber,
          :driverLicensePlateNumbers,
          :wssId,
          :dispatcherNotes,
          :publicUrlKey,
          :dispatchCode,
          :driverCode,
          :pickupSiteName,
          :pickupContactName,
          :pickupContactPhone,
          :pickupHours,
          :pickupAddress,
          :pickupCity,
          :pickupState,
          :dropoffSiteName,
          :dropoffContactName,
          :dropoffContactPhone,
          :dropoffHours,
          :dropoffAddress,
          :dropoffCity,
          :dropoffState
        ) on conflict(wss_id) do update set
          from_site_id = (select id from site where wss_id = :fromSiteWssId),
          to_site_id = (select id from site where wss_id = :toSiteWssId),
          delivery_status = :deliveryStatus,
          target_delivery_date = to_date(:targetDeliveryDate, 'YYYY-MM-DD'),
          dispatcher_name = :dispatcherName,
          dispatcher_number = :dispatcherNumber,
          driver_name = :driverName,
          driver_number = :driverNumber,
          driver_license_plates = :driverLicensePlateNumbers,
          dispatcher_notes = :dispatcherNotes,
          dispatch_code = :dispatchCode,
          pickup_site_name = :pickupSiteName,
          pickup_contact_name = :pickupContactName,
          pickup_contact_phone = :pickupContactPhone,
          pickup_hours = :pickupHours,
          pickup_address = :pickupAddress,
          pickup_city = :pickupCity,
          pickup_state = :pickupState,
          dropoff_site_name = :dropoffSiteName,
          dropoff_contact_name = :dropoffContactName,
          dropoff_contact_phone = :dropoffContactPhone,
          dropoff_hours = :dropoffHours,
          dropoff_address = :dropoffAddress,
          dropoff_city = :dropoffCity,
          dropoff_state = :dropoffState
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(upsert)
                .bind("fromSiteWssId", firstValue(pickupSiteWssId))
                .bind("toSiteWssId", firstValue(dropOffSiteWssId))
                .bind("deliveryStatus", deliveryStatus)
                .bind("targetDeliveryDate", targetDeliveryDate)
                .bind("dispatcherName", firstValue(dispatcherName))
                .bind("dispatcherNumber", firstValue(dispatcherNumber))
                .bind("driverName", firstValue(driverName))
                .bind("driverNumber", firstValue(driverNumber))
                .bind("driverLicensePlateNumbers", firstValue(licensePlateNumbers))
                .bind("wssId", deliveryId)
                .bind("dispatcherNotes", dispatcherNotes)
                .bind("dispatchCode", dispatcherCode)
                .bind("driverCode", SecretCodeGenerator.generateCode())
                .bind("publicUrlKey", publicUrlKey)
                .bind("pickupSiteName", firstValue(pickupSiteName))
                .bind("pickupContactName", firstValue(pickupContactName))
                .bind("pickupContactPhone", firstValue(pickupContactPhone))
                .bind("pickupHours", firstValue(pickupHours))
                .bind("pickupAddress", firstValue(pickupAddress))
                .bind("pickupCity", firstValue(pickupCity))
                .bind("pickupState", firstValue(pickupState))
                .bind("dropoffSiteName", firstValue(dropoffSiteName))
                .bind("dropoffContactName", firstValue(dropoffContactName))
                .bind("dropoffContactPhone", firstValue(dropoffContactPhone))
                .bind("dropoffHours", firstValue(dropoffHours))
                .bind("dropoffAddress", firstValue(dropoffAddress))
                .bind("dropoffCity", firstValue(dropoffCity))
                .bind("dropoffState", firstValue(dropoffState))
                .execute());

    String deletePreviousItems =
        """
        delete from delivery_item where delivery_id =
          (select id from delivery where wss_id = :deliveryId)
        """;
    jdbi.withHandle(
        handle ->
            handle.createUpdate(deletePreviousItems).bind("deliveryId", deliveryId).execute());

    String insertById =
        """
        insert into delivery_item(delivery_id, item_id)
        values(
          (select id from delivery where wss_id = :deliveryId),
          (select id from item where wss_id = :itemWssId)
        )
        """;
    if (itemListWssIds != null) {
      for (long itemWssId : itemListWssIds) {
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(insertById)
                    .bind("deliveryId", deliveryId)
                    .bind("itemWssId", itemWssId)
                    .execute());
      }
    }

    // items may also be provided by name (sometimes items won't have a WSS-ID)
    String insertByName =
        """
        insert into delivery_item(delivery_id, item_name)
        values(
          (select id from delivery where wss_id = :deliveryId),
          :itemName
        )
        """;
    if (itemList != null) {
      for (String itemName : itemList) {
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(insertByName)
                    .bind("deliveryId", deliveryId)
                    .bind("itemName", itemName)
                    .execute());
      }
    }
  }

  private static <T> T firstValue(List<T> input) {
    return input == null || input.isEmpty() ? null : input.getFirst();
  }
}
