package org.r4reach.util;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.LongPasswordStrategies;
import org.apache.commons.codec.digest.DigestUtils;

public class HashingUtil {

  public static String sha256(String input) {
    return DigestUtils.sha256Hex(input);
  }

  public static String bcrypt(final String password) {
    return BCrypt.with(LongPasswordStrategies.none()).hashToString(10, password.toCharArray());
  }

  public static boolean verifyBCryptHash(final String password, final String hash) {
    return BCrypt.verifyer(null, LongPasswordStrategies.none())
        .verify(password.toCharArray(), hash.toCharArray())
        .verified;
  }
}
