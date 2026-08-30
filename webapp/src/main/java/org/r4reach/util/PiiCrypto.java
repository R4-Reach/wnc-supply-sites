package org.r4reach.util;

import org.r4reach.siteconfig.DbBlindIndex;
import org.r4reach.siteconfig.DbSecretCipher;

/**
 * Static access to the app's user-PII encryption and blind index for the static DAO layer, which --
 * being plain utility classes that take a {@code Jdbi}, not Spring beans -- can't be injected with
 * the Spring-managed {@link DbSecretCipher}.
 *
 * <p>User names and phones are encrypted under their own {@code DB_PII_KEY}, deliberately separate
 * from the {@code DB_ENCRYPTION_KEY} that protects site-config secrets: the two share no key
 * material, so a leak of one key does not expose the data guarded by the other. The blind-index
 * HMAC key is in turn derived from {@code DB_PII_KEY} (see {@link DbBlindIndex}).
 *
 * <p>Encrypt/blind-index a value on write; decrypt on read. {@code phone} values passed to {@link
 * #blindIndex} must already be canonical (see {@link PhoneNumberUtil#toCanonical}) so formatting
 * variants collide to one lookup token.
 */
public final class PiiCrypto {

  // Local/test default so runs without the env var work out of the box; prod supplies DB_PII_KEY.
  private static final String DEFAULT_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  private static final DbSecretCipher CIPHER = new DbSecretCipher(masterKey());
  private static final DbBlindIndex BLIND_INDEX = new DbBlindIndex(masterKey());

  private PiiCrypto() {}

  private static String masterKey() {
    String fromEnv = System.getenv("DB_PII_KEY");
    return fromEnv == null || fromEnv.isBlank() ? DEFAULT_KEY : fromEnv;
  }

  /** Encrypt a PII value for storage; {@code null} in, {@code null} out. */
  public static String encrypt(String plaintext) {
    return plaintext == null ? null : CIPHER.encrypt(plaintext);
  }

  /** Decrypt a stored PII value; {@code null} in, {@code null} out. */
  public static String decrypt(String stored) {
    return stored == null ? null : CIPHER.decrypt(stored);
  }

  /** Blind-index token for an already-canonical value; {@code null} in, {@code null} out. */
  public static String blindIndex(String canonicalValue) {
    return canonicalValue == null ? null : BLIND_INDEX.index(canonicalValue);
  }
}
