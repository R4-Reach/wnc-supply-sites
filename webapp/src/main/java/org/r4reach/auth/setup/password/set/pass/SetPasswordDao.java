package org.r4reach.auth.setup.password.set.pass;

import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.HashingUtil;

class SetPasswordDao {

  /**
   * Updates the password for a user, bcrypts the password and stores it. We know the identify of a
   * user based on the validation token present in the 'sms_passcode' table.
   *
   * @return true if the change password is successful
   */
  public static boolean updatePassword(Jdbi jdbi, String validationToken, String password) {

    String updatePassword =
        """
        update wss_user
         set password_bcrypt = :hashedPassword,
             last_updated = now()
        where id = (
          select wss_user_id from sms_passcode
          where validation_token_used = false
          and validation_key_sha256 = :hashedToken
          )
        """;

    boolean updated =
        jdbi.withHandle(
                handle ->
                    handle
                        .createUpdate(updatePassword)
                        .bind("hashedToken", HashingUtil.sha256(validationToken))
                        .bind("hashedPassword", HashingUtil.bcrypt(password))
                        .execute())
            == 1;

    if (!updated) {
      return false;
    }

    // password update success! Do bookkeeping & update validation token as used

    String markValidationTokenAsUsed =
        """
        update sms_passcode set validation_token_used = true
        where validation_key_sha256 = :validationToken;

        """;

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(markValidationTokenAsUsed)
                .bind("validationToken", HashingUtil.sha256(validationToken))
                .execute());

    String updatePasswordChangeHistory =
        """
        insert into wss_user_pass_change_history (wss_user_id) values(
          (select wss_user_id from sms_passcode where validation_key_sha256 = :validationToken)
        )
        """;

    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(updatePasswordChangeHistory)
                .bind("validationToken", HashingUtil.sha256(validationToken))
                .execute());

    // End all existing sessions on a password change so a reset evicts anyone holding an old token.
    String revokeAuthTokens =
        """
        delete from wss_user_auth_key
        where wss_user_id = (
          select wss_user_id from sms_passcode where validation_key_sha256 = :validationToken
        )
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(revokeAuthTokens)
                .bind("validationToken", HashingUtil.sha256(validationToken))
                .execute());

    return true;
  }
}
