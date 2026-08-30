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

  /**
   * Saves the editable driver-portal fields for the driver identified by phone, creating the driver
   * row if it does not exist yet. A user may hold the DRIVER role before any driver row exists
   * (e.g. a freshly registered driver), so the first save inserts the row and later saves update it
   * in place. Identity (name/phone) lives on wss_user. {@code active} and {@code black_listed} are
   * owned elsewhere -- the driver's own active toggle and dispatch's blacklist toggle -- so they
   * are left untouched on update and take their column defaults only when the row is first created.
   */
  public static void upsert(Jdbi jdbi, Driver driver) {
    jdbi.withHandle(
        h ->
            h.createUpdate(
                    """
            insert into driver(
              wss_user_id, location, license_plates, availability, comments,
              can_lift_50lbs, pallet_capacity)
            values(
              (select id from wss_user where phone = :phone),
              :location, :licensePlates, :availability, :comments,
              :can_lift_50lbs, :pallet_capacity)
            on conflict(wss_user_id) do update set
               location = excluded.location,
               license_plates = excluded.license_plates,
               availability = excluded.availability,
               comments = excluded.comments,
               can_lift_50lbs = excluded.can_lift_50lbs,
               pallet_capacity = excluded.pallet_capacity,
               last_updated = now()
            """)
                .bind("phone", PhoneNumberUtil.toCanonical(driver.getPhone()))
                .bind("location", driver.getLocation())
                .bind("licensePlates", driver.getLicensePlates())
                .bind("comments", driver.getComments())
                .bind("availability", driver.getAvailability())
                .bind("can_lift_50lbs", driver.isCan_lift_50lbs())
                .bind("pallet_capacity", driver.getPallet_capacity())
                .execute());
  }

  static void toggleActiveStatus(Jdbi jdbi, String phone) {
    // Create the row if missing, then flip active. A driver with no row renders as active in the
    // portal (matching the column default), so the toggle link reads "Go Inactive"; inserting the
    // fresh row as inactive makes that first click land on the state the link promised.
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                        insert into driver(wss_user_id, active)
                        values((select id from wss_user where phone = :phone), false)
                        on conflict(wss_user_id) do update set
                          active = not driver.active,
                          last_updated = now()
                        """)
                .bind("phone", PhoneNumberUtil.toCanonical(phone))
                .execute());
  }
}
