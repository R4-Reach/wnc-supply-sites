package org.r4reach.siteconfig;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Symmetric encryption for secret values kept in the database, using AES-GCM with a single master
 * key supplied via the {@code DB_ENCRYPTION_KEY} environment variable (base64-encoded 16/24/32
 * bytes). GCM gives us authenticated encryption, so a tampered ciphertext fails to decrypt rather
 * than yielding garbage.
 *
 * <p>Stored form is {@code base64(iv || ciphertext-with-tag)}: a fresh random 12-byte IV is
 * prepended to each ciphertext, so encrypting the same plaintext twice yields different output.
 */
@Component
public class DbSecretCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKey key;
  private final SecureRandom random = new SecureRandom();

  public DbSecretCipher(@Value("${db.encryption.key}") String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
      throw new IllegalStateException(
          "DB_ENCRYPTION_KEY must base64-decode to 16, 24, or 32 bytes; got " + keyBytes.length);
    }
    this.key = new SecretKeySpec(keyBytes, "AES");
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] combined =
          ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
      return Base64.getEncoder().encodeToString(combined);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt site config secret", e);
    }
  }

  public String decrypt(String stored) {
    try {
      ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(stored));
      byte[] iv = new byte[IV_LENGTH_BYTES];
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to decrypt site config secret", e);
    }
  }
}
