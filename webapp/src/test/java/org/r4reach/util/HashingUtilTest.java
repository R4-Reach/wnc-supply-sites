package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HashingUtilTest {

  @Test
  void sha256() {
    var result = HashingUtil.sha256("input");
    assertThat(result)
        .isEqualTo("c96c6d5be8d08a12e7b5cdc1b207fa6b2430974c86803d8891675e76fd992c20");
    assertThat(result).hasSize(64);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "123",
        "test",
        "input value",
        "another value that is for sha256 hashing consistency check that is more than 64 characters long!"
      })
  void sha256ConsistentLength(String input) {
    var result = HashingUtil.sha256("input");
    assertThat(result).hasSize(64);
  }

  @Test
  void bcryptUsage() {
    var hash = HashingUtil.bcrypt("hopping rabbit");

    assertThat(HashingUtil.verifyBCryptHash("hopping rabbit", hash)).isTrue();
    assertThat(HashingUtil.verifyBCryptHash("bad pass", hash)).isFalse();
  }
}
