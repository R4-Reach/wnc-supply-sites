package org.r4reach.siteconfig;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Deterministic keyed hash (HMAC-SHA256) that lets encrypted PII columns still be looked up by
 * exact value. {@link DbSecretCipher} randomizes its output on purpose, so a ciphertext can't be
 * matched in a {@code WHERE} clause; the blind index gives each value one stable, non-reversible
 * token to store alongside the ciphertext and query on instead.
 *
 * <p>The HMAC key is derived from the same {@code DB_ENCRYPTION_KEY} master key as the cipher, via
 * a one-block HKDF-style expansion (an HMAC over a fixed label). Deriving a distinct key means the
 * raw master key is never used directly for two different primitives.
 *
 * <p>Plain SHA-256 would be unsafe here: phone numbers span a small, brute-forceable space, so an
 * unkeyed hash is reversible by anyone willing to guess-and-hash. The secret key defeats that.
 */
@Component
public class DbBlindIndex {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final byte[] KEY_DERIVATION_LABEL =
      "wss-pii-blind-index-v1".getBytes(StandardCharsets.UTF_8);

  private final SecretKeySpec indexKey;

  public DbBlindIndex(@Value("${db.encryption.key}") String base64MasterKey) {
    byte[] masterKey = Base64.getDecoder().decode(base64MasterKey);
    if (masterKey.length != 16 && masterKey.length != 24 && masterKey.length != 32) {
      throw new IllegalStateException(
          "DB_ENCRYPTION_KEY must base64-decode to 16, 24, or 32 bytes; got " + masterKey.length);
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
