package org.r4reach.dispatch;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.PiiCrypto;

/**
 * Backs the dispatch drivers grid. A driver's identity (name, phone) lives on wss_user; the rest
 * (location, availability, notes, vehicle type, active) lives on the driver row, which is keyed by
 * its {@code wss_id}.
 */
public class DispatchDao {

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DriverRow {
    long wssId;
    long wssUserId;
    String fullName;
    String phone;
    String location;
    String availability;
    String comments;
    String licensePlates;
    boolean canLift50lbs;
    Integer palletCapacity;
    Integer vehicleTypeId;
    String vehicleTypeName;
    boolean active;
    boolean blackListed;
  }

  private static final String SELECT_ROWS =
      """
      select
        d.wss_id wssId,
        u.id wssUserId,
        u.name_enc fullName,
        u.phone_enc phone,
        coalesce(d.location, '') location,
        coalesce(d.availability, '') availability,
        coalesce(d.comments, '') comments,
        coalesce(d.license_plates, '') licensePlates,
        d.can_lift_50lbs canLift50lbs,
        d.pallet_capacity palletCapacity,
        d.vehicle_type_id vehicleTypeId,
        vt.name vehicleTypeName,
        d.active,
        d.black_listed blackListed
      from driver d
      join wss_user u on u.id = d.wss_user_id
      left join vehicle_type vt on vt.id = d.vehicle_type_id
      """;

  // name/phone are encrypted, so the old SQL `order by lower(u.name) nulls last, u.phone` can't run
  // in the database; sort the decrypted rows here to match it (blank names last).
  private static final Comparator<DriverRow> BY_NAME_THEN_PHONE =
      Comparator.comparing((DriverRow r) -> r.getFullName().isBlank())
          .thenComparing(r -> r.getFullName().toLowerCase())
          .thenComparing(DriverRow::getPhone);

  public static List<DriverRow> fetchAll(Jdbi jdbi) {
    return jdbi
        .withHandle(handle -> handle.createQuery(SELECT_ROWS).mapToBean(DriverRow.class).list())
        .stream()
        .map(DispatchDao::decryptIdentity)
        .sorted(BY_NAME_THEN_PHONE)
        .toList();
  }

  public static Optional<DriverRow> fetch(Jdbi jdbi, long wssId) {
    return jdbi.withHandle(
            handle ->
                handle
                    .createQuery(SELECT_ROWS + " where d.wss_id = :wssId")
                    .bind("wssId", wssId)
                    .mapToBean(DriverRow.class)
                    .findOne())
        .map(DispatchDao::decryptIdentity);
  }

  // fullName is fetched as name_enc ciphertext (empty string when the user has no name, matching
  // the
  // old coalesce(u.name, '')); phone is fetched as phone_enc ciphertext. Decrypt both in place.
  private static DriverRow decryptIdentity(DriverRow row) {
    String name = PiiCrypto.decrypt(row.getFullName());
    row.setFullName(name == null ? "" : name);
    row.setPhone(PiiCrypto.decrypt(row.getPhone()));
    return row;
  }

  /**
   * Creates a driver, minting the backing wss_user identity from the phone (and optional name) if
   * it does not exist yet. Returns false for an invalid phone number.
   */
  public static boolean createDriver(Jdbi jdbi, String phoneInput, String name) {
    if (!PhoneNumberUtil.isValid(phoneInput)) {
      return false;
    }
    String phone = PhoneNumberUtil.toCanonical(phoneInput);
    String trimmedName = name == null || name.isBlank() ? null : name.trim();
    jdbi.useTransaction(
        handle -> {
          long userId =
              handle
                  .createQuery(
                      """
                      insert into wss_user(phone_enc, phone_hmac, name_enc)
                      values (:phoneEnc, :phoneHmac, :nameEnc)
                      on conflict(phone_hmac) do update
                        set removed = false,
                            name_enc = coalesce(:nameEnc, wss_user.name_enc)
                      returning id
                      """)
                  .bind("phoneEnc", PiiCrypto.encrypt(phone))
                  .bind("phoneHmac", PiiCrypto.blindIndex(phone))
                  .bind("nameEnc", PiiCrypto.encrypt(trimmedName))
                  .mapTo(Long.class)
                  .one();
          handle
              .createUpdate(
                  """
                  insert into driver(wss_user_id) values (:userId)
                  on conflict (wss_user_id) do nothing
                  """)
              .bind("userId", userId)
              .execute();
        });
    return true;
  }

  public static void setFullName(Jdbi jdbi, long wssUserId, String name) {
    String trimmedName = name == null || name.isBlank() ? null : name.trim();
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate("update wss_user set name_enc = :nameEnc where id = :id")
                .bind("nameEnc", PiiCrypto.encrypt(trimmedName))
                .bind("id", wssUserId)
                .execute());
  }

  /**
   * Updates the driver's phone (their wss_user identity). Returns false for an invalid number, or
   * one already held by a different user, leaving the stored value unchanged.
   */
  public static boolean setPhone(Jdbi jdbi, long wssUserId, String phoneInput) {
    if (!PhoneNumberUtil.isValid(phoneInput)) {
      return false;
    }
    String phone = PhoneNumberUtil.toCanonical(phoneInput);
    boolean takenByOther =
        jdbi.withHandle(
                handle ->
                    handle
                        .createQuery(
                            "select count(*) from wss_user where phone_hmac = :phoneHmac and id <> :id")
                        .bind("phoneHmac", PiiCrypto.blindIndex(phone))
                        .bind("id", wssUserId)
                        .mapTo(Integer.class)
                        .one())
            > 0;
    if (takenByOther) {
      return false;
    }
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    update wss_user
                    set phone_enc = :phoneEnc, phone_hmac = :phoneHmac
                    where id = :id
                    """)
                .bind("phoneEnc", PiiCrypto.encrypt(phone))
                .bind("phoneHmac", PiiCrypto.blindIndex(phone))
                .bind("id", wssUserId)
                .execute());
    return true;
  }

  public static void setLocation(Jdbi jdbi, long wssId, String location) {
    updateDriverColumn(jdbi, wssId, "location", trimToNull(location));
  }

  public static void setAvailability(Jdbi jdbi, long wssId, String availability) {
    updateDriverColumn(jdbi, wssId, "availability", trimToNull(availability));
  }

  /** The grid's "notes" field is the existing driver.comments column. */
  public static void setNotes(Jdbi jdbi, long wssId, String notes) {
    updateDriverColumn(jdbi, wssId, "comments", trimToNull(notes));
  }

  public static void setLicensePlates(Jdbi jdbi, long wssId, String licensePlates) {
    updateDriverColumn(jdbi, wssId, "license_plates", trimToNull(licensePlates));
  }

  public static void setCanLift50lbs(Jdbi jdbi, long wssId, boolean canLift50lbs) {
    updateDriverColumn(jdbi, wssId, "can_lift_50lbs", canLift50lbs);
  }

  public static void setPalletCapacity(Jdbi jdbi, long wssId, int palletCapacity) {
    updateDriverColumn(jdbi, wssId, "pallet_capacity", palletCapacity);
  }

  public static void setActive(Jdbi jdbi, long wssId, boolean active) {
    updateDriverColumn(jdbi, wssId, "active", active);
  }

  public static void setBlackListed(Jdbi jdbi, long wssId, boolean blackListed) {
    updateDriverColumn(jdbi, wssId, "black_listed", blackListed);
  }

  /** Sets (or clears, with a null id) the driver's single vehicle type. */
  public static void setVehicleType(Jdbi jdbi, long wssId, Integer vehicleTypeId) {
    updateDriverColumn(jdbi, wssId, "vehicle_type_id", vehicleTypeId);
  }

  private static void updateDriverColumn(Jdbi jdbi, long wssId, String column, Object value) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    "update driver set "
                        + column
                        + " = :value, last_updated = now()"
                        + " where wss_id = :wssId")
                .bind("value", value)
                .bind("wssId", wssId)
                .execute());
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
