package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiCryptoTest {

  @Test
  void encryptDecryptRoundTrips() {
    assertThat(PiiCrypto.decrypt(PiiCrypto.encrypt("Jane Doe"))).isEqualTo("Jane Doe");
  }

  @Test
  void blindIndexIsDeterministic() {
    // Determinism is what lets an encrypted phone still be looked up by exact value.
    assertThat(PiiCrypto.blindIndex("15551234567")).isEqualTo(PiiCrypto.blindIndex("15551234567"));
  }

  @Test
  void nullsPassThrough() {
    assertThat(PiiCrypto.encrypt(null)).isNull();
    assertThat(PiiCrypto.decrypt(null)).isNull();
    assertThat(PiiCrypto.blindIndex(null)).isNull();
  }
}
