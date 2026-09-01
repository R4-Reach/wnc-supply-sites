package org.r4reach.auth;

import java.util.UUID;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.HashingUtil;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.PiiCrypto;

public class LoginDao {

  /** A phone is locked out of password login after this many failures within the window below. */
  static final int MAX_FAILED_ATTEMPTS = 10;

  static final int THROTTLE_WINDOW_MINUTES = 15;

  public static void recordLoginSuccess(Jdbi jdbi, String phoneNumber) {
    String insert =
        """
        insert into login_history(phone_number, result) values
        (:phoneNumber, true);
        """;

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(insert)
                .bind("phoneNumber", PhoneNumberUtil.toCanonical(phoneNumber))
                .execute());
  }

  public static void recordLoginFailure(Jdbi jdbi, String phoneNumber) {
    String insert =
        """
        insert into login_history(phone_number, result) values
        (:phoneNumber, false);
        """;

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(insert)
                .bind("phoneNumber", PhoneNumberUtil.toCanonical(phoneNumber))
                .execute());
  }

  /**
   * Whether password login for this phone is currently locked out: at least {@link
   * #MAX_FAILED_ATTEMPTS} failed attempts within the last {@link #THROTTLE_WINDOW_MINUTES} minutes.
   * Counting is by canonical phone so an attacker can't sidestep the limit by varying the format.
   */
  public static boolean isLoginThrottled(Jdbi jdbi, String phoneNumber) {
    String query =
        """
        select count(*)
        from login_history
        where phone_number = :phoneNumber
          and result = false
          and login_date > now() - (:minutes * interval '1 minute')
        """;
    long recentFailures =
        jdbi.withHandle(
            handle ->
                handle
                    .createQuery(query)
                    .bind("phoneNumber", PhoneNumberUtil.toCanonical(phoneNumber))
                    .bind("minutes", THROTTLE_WINDOW_MINUTES)
                    .mapTo(Long.class)
                    .one());
    return recentFailures >= MAX_FAILED_ATTEMPTS;
  }

  public static String generateAuthToken(Jdbi jdbi, String user) {
    final String phone = PhoneNumberUtil.toCanonical(user);
    String token = UUID.randomUUID().toString();

    String insert =
        """
      insert into wss_user_auth_key(wss_user_id, token_sha256, expires_at)
      values(
        (select id from wss_user where phone_hmac = :userHmac),
        :token_sha256,
        now() + interval '14 days'
      )
      """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(insert)
                .bind("userHmac", PiiCrypto.blindIndex(phone))
                .bind("token_sha256", HashingUtil.sha256(token))
                .execute());

    return token;
  }

  /**
   * A token authenticates only while it is unexpired and belongs to a user that has not been
   * removed. Expiry bounds how long a captured token can be replayed; the removed check keeps a
   * de-provisioned user's existing tokens from continuing to work.
   */
  public static boolean isLoggedIn(Jdbi jdbi, String tokenValue) {
    String query =
        """
        select 1
        from wss_user_auth_key k
        join wss_user u on u.id = k.wss_user_id
        where k.token_sha256 = :token
          and k.expires_at > now()
          and u.removed = false
        """;
    return jdbi.withHandle(
            handle ->
                handle
                    .createQuery(query)
                    .bind("token", HashingUtil.sha256(tokenValue))
                    .mapTo(Integer.class)
                    .findOne())
        .isPresent();
  }

  /** Revokes a single auth token (used on logout) so it can't be replayed after sign-out. */
  public static void revokeToken(Jdbi jdbi, String tokenValue) {
    String delete = "delete from wss_user_auth_key where token_sha256 = :token";
    jdbi.withHandle(
        handle ->
            handle.createUpdate(delete).bind("token", HashingUtil.sha256(tokenValue)).execute());
  }

  /** Revokes every auth token for a user (used on password change) to end all existing sessions. */
  public static void revokeAllTokensForUser(Jdbi jdbi, String phoneNumber) {
    String delete =
        """
        delete from wss_user_auth_key
        where wss_user_id = (select id from wss_user where phone_hmac = :userHmac)
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(delete)
                .bind("userHmac", PiiCrypto.blindIndex(PhoneNumberUtil.toCanonical(phoneNumber)))
                .execute());
  }
}
