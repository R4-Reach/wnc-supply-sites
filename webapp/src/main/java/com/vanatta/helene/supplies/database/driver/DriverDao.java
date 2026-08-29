package com.vanatta.helene.supplies.database.driver;

import com.vanatta.helene.supplies.database.util.PhoneNumberUtil;
import java.util.Optional;
import org.jdbi.v3.core.Jdbi;

public class DriverDao {

  public static Optional<Driver> lookupByPhone(Jdbi jdbi, String phoneNumber) {
    return jdbi.withHandle(
        h ->
            h.createQuery(
                    """
                    select
                      wss_id,
                      name fullName,
                      phone,
                      active,
                      black_listed,
                      location,
                      license_plates,
                      availability,
                      comments,
                      can_lift_50lbs,
                      pallet_capacity
                    from driver where regexp_replace(phone, '[^0-9]+', '', 'g') = :phone
                    """)
                .bind("phone", PhoneNumberUtil.removeNonNumeric(phoneNumber))
                .mapToBean(Driver.class)
                .findOne());
  }

  public static void upsert(Jdbi jdbi, Driver driver) {
    jdbi.withHandle(
        h ->
            h.createUpdate(
                    """
            insert into driver(
                  wss_id, name, phone, location,
                  active, black_listed, license_plates,
                  comments, availability, can_lift_50lbs, pallet_capacity)
            values(
               :wssId,
               :name,
               :phone,
               :location,
               :active,
               :blacklisted,
               :licensePlates,
               :comments,
               :availability,
               :can_lift_50lbs,
               :pallet_capacity
            ) on conflict(wss_id) do update set
               name = :name,
               phone = :phone,
               location = :location,
               active = :active,
               black_listed = :blacklisted,
               license_plates = :licensePlates,
               comments = :comments,
               availability = :availability,
               can_lift_50lbs = :can_lift_50lbs,
               pallet_capacity = :pallet_capacity
            """)
                .bind("wssId", driver.getWssId())
                .bind("name", driver.getFullName())
                .bind("phone", PhoneNumberUtil.removeNonNumeric(driver.getPhone()))
                .bind("location", driver.getLocation())
                .bind("active", driver.isActive())
                .bind("blacklisted", driver.isBlacklisted())
                .bind("licensePlates", driver.getLicensePlates())
                .bind("comments", driver.getComments())
                .bind("availability", driver.getAvailability())
                .bind("can_lift_50lbs", driver.isCan_lift_50lbs())
                .bind("pallet_capacity", driver.getPallet_capacity())
                .execute());
  }

  static void toggleActiveStatus(Jdbi jdbi, String phone) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                        update driver set
                          active = (
                            select not active
                            from driver
                            where regexp_replace(phone, '[^0-9]+', '', 'g') = :phone
                          ),
                          last_updated = now()
                        where phone = :phone
                        """)
                .bind("phone", PhoneNumberUtil.removeNonNumeric(phone))
                .execute());
  }
}
