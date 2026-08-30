package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;

class SecretCodeGeneratorTest {

  @RepeatedTest(100)
  void generateSecretCode() {
    String result = SecretCodeGenerator.generateCode();
    assertThat(result).hasSize(4);
    assertThat(result).matches("[A-Z]+");
  }
}
