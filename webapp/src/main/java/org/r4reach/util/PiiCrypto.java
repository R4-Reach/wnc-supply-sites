package org.r4reach.util;

import org.r4reach.siteconfig.DbBlindIndex;
import org.r4reach.siteconfig.DbSecretCipher;

/**
 * Static access to the app's PII encryption and blind index for the static DAO layer, which --
 * being plain utility classes that take a {@code Jdbi}, not Spring beans -- can't be injected with
 * the Spring-managed {@link DbSecretCipher}. This keys off the same {@code DB_ENCRYPTION_KEY} the
 * Spring bean uses, so site-config secrets and user PII share one master key.
 *
 * <p>Encrypt/blind-index a value on write; decrypt on read. {@code phone} values passed to {@link
 * #blindIndex} must already be canonical (see {@link PhoneNumberUtil#toCanonical}) so formatting
 * variants collide to one lookup token.
 */
public final class PiiCrypto {

  // Matches the db.encryption.key default in application.properties, so local/test runs with no env
  // var behave identically to the Spring-configured cipher.
  private static final String DEFAULT_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  private static final DbSecretCipher CIPHER = new DbSecretCipher(masterKey());
  private static final DbBlindIndex BLIND_INDEX = new DbBlindIndex(masterKey());

  private PiiCrypto() {}

  private static String masterKey() {
    String fromEnv = System.getenv("DB_ENCRYPTION_KEY");
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
