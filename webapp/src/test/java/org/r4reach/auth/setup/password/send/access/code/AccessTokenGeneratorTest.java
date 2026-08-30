package org.r4reach.auth.setup.password.send.access.code;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class AccessTokenGeneratorTest {
  AccessTokenGenerator accessTokenGenerator = new AccessTokenGenerator();

  @RepeatedTest(300)
  void generate() {
    String value = accessTokenGenerator.generate();
    assertThat(value).hasSize(6);
  }

  @Test
  void generateValuesChange() {
    String value = accessTokenGenerator.generate();
    String nextValue = accessTokenGenerator.generate();
    assertThat(value).isNotEqualTo(nextValue);
  }
}
