package com.vanatta.helene.supplies.database.util;

public class PhoneNumberUtil {

  public static String removeNonNumeric(String input) {
    return input.replaceAll("[^\\d]", "");
  }

  /**
   * Canonical phone form used for storage and lookups: digits only, always with the US country code
   * so every number is 11 digits (leading {@code 1}). A 10-digit number gets a {@code 1} prefixed;
   * a number already in 11-digit country-code form is returned as-is. Anything else (an invalid
   * number) is returned as its bare digits, which simply won't match a stored phone.
   *
   * <p>{@code wss_user.phone} and the phone columns that mirror it are stored in this form, so
   * lookups bind {@code toCanonical(input)} and compare against the digits of the stored value.
   */
  public static String toCanonical(String input) {
    String digits = removeNonNumeric(input == null ? "" : input);
    return digits.length() == 10 ? "1" + digits : digits;
  }

  /**
   * Checks if a given phone number looks valid: exactly 10 digits, or 11 digits in US country-code
   * form (leading {@code 1}). Both canonicalize to the same 11-digit value.
   */
  public static boolean isValid(String input) {
    if (input == null) {
      return false;
    }
    String digits = removeNonNumeric(input);
    return digits.length() == 10 || (digits.length() == 11 && digits.startsWith("1"));
  }
}
