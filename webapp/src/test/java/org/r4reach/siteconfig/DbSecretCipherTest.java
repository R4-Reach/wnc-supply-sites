package org.r4reach.siteconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class DbSecretCipherTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
  private final DbSecretCipher cipher = new DbSecretCipher(KEY);

  @Test
  void roundTrips() {
    String plaintext = "some-super-secret-api-key";
    assertThat(cipher.decrypt(cipher.encrypt(plaintext))).isEqualTo(plaintext);
  }

  @Test
  void encryptingTwiceYieldsDifferentCiphertext() {
    // A fresh random IV per call means the same plaintext never encrypts to the same bytes.
    assertThat(cipher.encrypt("value")).isNotEqualTo(cipher.encrypt("value"));
  }

  @Test
  void tamperedCiphertextFailsToDecrypt() {
    byte[] combined = Base64.getDecoder().decode(cipher.encrypt("value"));
    combined[combined.length - 1] ^= 0x01;
    String tampered = Base64.getEncoder().encodeToString(combined);
    assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsKeyOfWrongLength() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[10]);
    assertThatThrownBy(() -> new DbSecretCipher(shortKey))
        .isInstanceOf(IllegalStateException.class);
  }
}
