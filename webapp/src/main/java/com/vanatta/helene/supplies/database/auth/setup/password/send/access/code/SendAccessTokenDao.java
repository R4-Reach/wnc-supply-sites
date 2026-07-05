package com.vanatta.helene.supplies.database.auth.setup.password.send.access.code;

import com.vanatta.helene.supplies.database.util.HashingUtil;
import com.vanatta.helene.supplies.database.util.PhoneNumberUtil;
import jakarta.annotation.Nonnull;
import lombok.Builder;
import lombok.Getter;
import org.jdbi.v3.core.Jdbi;

public class SendAccessTokenDao {

  /** Window over which access-code requests for a single phone number are counted. */
  static final int THROTTLE_MINUTES = 15;

  /** Maximum access-code requests allowed for a single phone number within the throttle window. */
  static final int THROTTLE_MAX_REQUESTS = 5;

  /**
   * Returns true if the phone number has already requested {@link #THROTTLE_MAX_REQUESTS} or more
   * access codes within the last {@link #THROTTLE_MINUTES} minutes. Reuses the sms_passcode table
   * as the rate-limit ledger.
   */
  static boolean isThrottled(Jdbi jdbi, String phoneNumber) {
    String query =
        """
        select count(*)
        from sms_passcode
        where wss_user_id = (select id from wss_user where phone = :phone)
          and date_created > now() - (:minutes * interval '1 minute')
        """;
    int recentRequests =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery(query)
                    .bind("phone", phoneNumber)
                    .bind("minutes", THROTTLE_MINUTES)
                    .mapTo(Integer.class)
                    .one());
    return recentRequests >= THROTTLE_MAX_REQUESTS;
  }

  public static boolean isPhoneNumberRegistered(Jdbi jdbi, String inputPhoneNumber) {
    if (inputPhoneNumber == null || inputPhoneNumber.isBlank()) {
      return false;
    }
    final String phoneNumber = PhoneNumberUtil.removeNonNumeric(inputPhoneNumber);

    String query =
        """
        select 1
        from wss_user
        where regexp_replace(phone, '[^0-9]+', '', 'g') = :phoneNumber
        union
        select 1
        from site
        where regexp_replace(contact_number, '[^0-9]+', '', 'g') = :phoneNumber
        union
        select 1
        from additional_site_manager
        where regexp_replace(phone, '[^0-9]+', '', 'g') = :phoneNumber
        union
        select 1
        from driver
        where regexp_replace(phone, '[^0-9]+', '', 'g') = :phoneNumber
        """;
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(query)
                .bind("phoneNumber", phoneNumber)
                .mapTo(Integer.class)
                .findOne()
                .isPresent());
  }

  public static void createUser(Jdbi jdbi, String number) {
    String insert =
        """
        insert into wss_user(phone) values(:phone)
        """;
    jdbi.withHandle(handle -> handle.createUpdate(insert).bind("phone", number).execute());
  }

  static boolean userAccountExists(Jdbi jdbi, String phoneNumber) {
    String query =
        """
        select 1 from wss_user where phone = :phone
        """;
    return jdbi.withHandle(
            handle ->
                handle.createQuery(query).bind("phone", phoneNumber).mapTo(Integer.class).findOne())
        .isPresent();
  }

  @Getter
  @Builder
  public static class InsertAccessCodeParams {
    @Nonnull String phoneNumber;
    @Nonnull String accessCode;
    @Nonnull String csrfToken;

    String getAccessCode() {
      return HashingUtil.sha256(accessCode);
    }

    String getCsrfToken() {
      return HashingUtil.sha256(csrfToken);
    }
  }

  public static void insertSmsPasscode(Jdbi jdbi, InsertAccessCodeParams params) {
    String insert =
        """

        insert into sms_passcode(wss_user_id, passcode_sha256, csrf_sha256)
        values(
          (select id from wss_user where phone = :phoneNumber),
          :passcode,
          :csrf
          )
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(insert)
                .bind("phoneNumber", params.getPhoneNumber())
                .bind("passcode", params.getAccessCode())
                .bind("csrf", params.getCsrfToken())
                .execute());
  }
}
