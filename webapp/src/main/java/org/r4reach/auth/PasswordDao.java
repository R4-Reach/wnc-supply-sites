package org.r4reach.auth;

import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.HashingUtil;
import org.r4reach.util.PhoneNumberUtil;

public class PasswordDao {

  /** Checks if a given plaintext password matches the hashed password stored for a given usre. */
  public static boolean confirmPassword(Jdbi jdbi, String phoneNumber, String password) {
    if (phoneNumber == null || password == null || password.length() < 5) {
      return false;
    }
    if (!PhoneNumberUtil.isValid(phoneNumber)) {
      return false;
    }
    final String cleanedPhoneNumber = PhoneNumberUtil.toCanonical(phoneNumber);

    String select =
        """
        select password_bcrypt
        from wss_user
        where regexp_replace(phone, '[^0-9]+', '', 'g') = :phoneNumber
    """;
    String passwordHash =
        jdbi.withHandle(
                handle ->
                    handle
                        .createQuery(select)
                        .bind("phoneNumber", cleanedPhoneNumber)
                        .mapTo(String.class)
                        .findOne())
            .orElse(null);
    if (passwordHash == null) {
      return false;
    }

    return HashingUtil.verifyBCryptHash(password, passwordHash);
  }

  /**
   * Whether a wss_user account row exists for the phone (regardless of whether a password is set).
   */
  public static boolean hasPassword(Jdbi jdbi, String phoneNumber) {
    String select =
        "select 1 from wss_user where regexp_replace(phone, '[^0-9]+', '', 'g') = :phoneNumber";
    return jdbi.withHandle(
            handle ->
                handle
                    .createQuery(select)
                    .bind("phoneNumber", PhoneNumberUtil.toCanonical(phoneNumber))
                    .mapTo(Long.class)
                    .findOne())
        .isPresent();
  }

  /**
   * Whether the user has actually set a password. Distinct from {@link #hasPassword}: whitelisted
   * users and auto-provisioned site managers / drivers have an account row before they set a
   * password, so this checks the password column rather than mere row existence.
   */
  public static boolean passwordIsSet(Jdbi jdbi, String phoneNumber) {
    String select =
        """
        select 1 from wss_user
        where regexp_replace(phone, '[^0-9]+', '', 'g') = :phoneNumber
          and password_bcrypt is not null
        """;
    return jdbi.withHandle(
            handle ->
                handle
                    .createQuery(select)
                    .bind("phoneNumber", PhoneNumberUtil.toCanonical(phoneNumber))
                    .mapTo(Long.class)
                    .findOne())
        .isPresent();
  }
}
