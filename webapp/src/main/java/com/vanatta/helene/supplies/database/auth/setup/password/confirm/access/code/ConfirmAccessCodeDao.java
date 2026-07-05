package com.vanatta.helene.supplies.database.auth.setup.password.confirm.access.code;

import com.vanatta.helene.supplies.database.util.HashingUtil;
import org.jdbi.v3.core.Jdbi;

public class ConfirmAccessCodeDao {

  /** A passcode is only valid for this many minutes after it is created. */
  static final int EXPIRY_MINUTES = 15;

  /** A passcode is locked out after this many failed confirmation attempts. */
  static final int MAX_ATTEMPTS = 10;

  /**
   * Confirms a passcode. A passcode is only accepted if it has not already been confirmed, has not
   * expired, and has not exceeded the failed-attempt limit. Every failed attempt against a matching
   * csrf increments the attempt counter, which locks the passcode out once it reaches {@link
   * #MAX_ATTEMPTS}.
   *
   * @return 1 if the passcode was confirmed, otherwise 0
   */
  public static int confirmAccessCode(
      Jdbi jdbi,
      ConfirmAccessCodeController.ConfirmAccessCodeRequest confirmAccessCodeRequest,
      String validationToken) {

    String csrf = HashingUtil.sha256(confirmAccessCodeRequest.getCsrf());
    String passcode = HashingUtil.sha256(confirmAccessCodeRequest.getConfirmCode());

    String update =
        """
        update sms_passcode set
          confirmed = true,
          validation_key_sha256 = :validationSha256,
          date_confirmed = now()
        where csrf_sha256 = :csrf
          and passcode_sha256 = :passcode
          and confirmed = false
          and failed_access_attempts < :maxAttempts
          and date_created > now() - (:expiryMinutes * interval '1 minute')
        """;

    int confirmed =
        jdbi.withHandle(
            handle ->
                handle
                    .createUpdate(update)
                    .bind("validationSha256", HashingUtil.sha256(validationToken))
                    .bind("csrf", csrf)
                    .bind("passcode", passcode)
                    .bind("maxAttempts", MAX_ATTEMPTS)
                    .bind("expiryMinutes", EXPIRY_MINUTES)
                    .execute());

    if (confirmed == 1) {
      return 1;
    }

    // Wrong passcode for this csrf: count the failed attempt so the code eventually locks out.
    String incrementAttempts =
        """
        update sms_passcode set
          failed_access_attempts = failed_access_attempts + 1
        where csrf_sha256 = :csrf
          and confirmed = false
        """;
    jdbi.withHandle(handle -> handle.createUpdate(incrementAttempts).bind("csrf", csrf).execute());

    return 0;
  }
}
