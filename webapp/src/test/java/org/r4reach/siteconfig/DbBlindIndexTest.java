package org.r4reach.siteconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class DbBlindIndexTest {

  private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
  private final DbBlindIndex index = new DbBlindIndex(KEY);

  @Test
  void sameValueYieldsSameToken() {
    // Determinism is the whole point: a value must hash identically every time so lookups match.
    assertThat(index.index("15551234567")).isEqualTo(index.index("15551234567"));
  }

  @Test
  void differentValuesYieldDifferentTokens() {
    assertThat(index.index("15551234567")).isNotEqualTo(index.index("15559999999"));
  }

  @Test
  void tokenIsHexSha256() {
    assertThat(index.index("15551234567")).matches("[0-9a-f]{64}");
  }

  @Test
  void differentKeyYieldsDifferentToken() {
    // The key is what makes the hash non-reversible; a different master key must give a different
    // token for the same value, or the secret would be doing nothing.
    byte[] otherKeyBytes = new byte[32];
    otherKeyBytes[0] = 1;
    DbBlindIndex other = new DbBlindIndex(Base64.getEncoder().encodeToString(otherKeyBytes));
    assertThat(index.index("15551234567")).isNotEqualTo(other.index("15551234567"));
  }

  @Test
  void rejectsKeyOfWrongLength() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[10]);
    assertThatThrownBy(() -> new DbBlindIndex(shortKey)).isInstanceOf(IllegalStateException.class);
  }
}
