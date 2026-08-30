package org.r4reach.siteconfig;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Deterministic keyed hash (HMAC-SHA256) that lets encrypted PII columns still be looked up by
 * exact value. {@link DbSecretCipher} randomizes its output on purpose, so a ciphertext can't be
 * matched in a {@code WHERE} clause; the blind index gives each value one stable, non-reversible
 * token to store alongside the ciphertext and query on instead.
 *
 * <p>Constructed with the caller's master key (see {@link org.r4reach.util.PiiCrypto}, which passes
 * the PII key); the actual HMAC key is derived from it via a one-block HKDF-style expansion (an
 * HMAC over a fixed label), so the raw master key is never used directly for two different
 * primitives.
 *
 * <p>Plain SHA-256 would be unsafe here: phone numbers span a small, brute-forceable space, so an
 * unkeyed hash is reversible by anyone willing to guess-and-hash. The secret key defeats that.
 */
public class DbBlindIndex {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final byte[] KEY_DERIVATION_LABEL =
      "wss-pii-blind-index-v1".getBytes(StandardCharsets.UTF_8);

  private final SecretKeySpec indexKey;

  public DbBlindIndex(String base64MasterKey) {
    byte[] masterKey = Base64.getDecoder().decode(base64MasterKey);
    if (masterKey.length != 16 && masterKey.length != 24 && masterKey.length != 32) {
      throw new IllegalStateException(
          "blind index master key must base64-decode to 16, 24, or 32 bytes; got "
              + masterKey.length);
    }
    this.indexKey = new SecretKeySpec(deriveKey(masterKey), HMAC_ALGORITHM);
  }

  /**
   * The stable lookup token for {@code value}: lowercase hex of its HMAC-SHA256. Callers pass the
   * already-canonicalized value (e.g. {@link org.r4reach.util.PhoneNumberUtil#toCanonical}) so that
   * formatting variants of one input collide to a single token.
   */
  public String index(String value) {
    return HexFormat.of().formatHex(hmac(indexKey, value.getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] deriveKey(byte[] masterKey) {
    return hmac(new SecretKeySpec(masterKey, HMAC_ALGORITHM), KEY_DERIVATION_LABEL);
  }

  private static byte[] hmac(SecretKeySpec key, byte[] data) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(key);
      return mac.doFinal(data);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to compute blind index", e);
    }
  }
}
