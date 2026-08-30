package org.r4reach.driver;

import java.util.Optional;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.PhoneNumberUtil;

public class DriverDao {

  public static Optional<Driver> lookupByPhone(Jdbi jdbi, String phoneNumber) {
    return jdbi.withHandle(
        h ->
            h.createQuery(
                    """
                    select
                      d.wss_id,
                      u.name fullName,
                      u.phone,
                      d.active,
                      d.black_listed,
                      d.location,
                      d.license_plates,
                      d.availability,
                      d.comments,
                      d.can_lift_50lbs,
                      d.pallet_capacity
                    from driver d
                    join wss_user u on u.id = d.wss_user_id
                    where u.phone = :phone
                    """)
                .bind("phone", PhoneNumberUtil.toCanonical(phoneNumber))
                .mapToBean(Driver.class)
                .findOne());
  }

  /** Updates the editable driver-portal fields. Identity (name/phone) lives on wss_user. */
  public static void upsert(Jdbi jdbi, Driver driver) {
    jdbi.withHandle(
        h ->
            h.createUpdate(
                    """
            update driver set
               location = :location,
               active = :active,
               black_listed = :blacklisted,
               license_plates = :licensePlates,
               comments = :comments,
               availability = :availability,
               can_lift_50lbs = :can_lift_50lbs,
               pallet_capacity = :pallet_capacity
            where wss_id = :wssId
            """)
                .bind("wssId", driver.getWssId())
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
                          active = not active,
                          last_updated = now()
                        where wss_user_id = (select id from wss_user where phone = :phone)
                        """)
                .bind("phone", PhoneNumberUtil.toCanonical(phone))
                .execute());
  }
}
