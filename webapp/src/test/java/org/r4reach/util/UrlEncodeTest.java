package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UrlEncodeTest {

  @ParameterizedTest
  @CsvSource({"  a ,a", "twin & city,twin+%26+city", "'city, hills!',city%2C+hills%21"})
  void urlEncoding(String input, String expectedOutput) {
    var output = UrlEncode.encode(input);
    assertThat(output).isEqualTo(expectedOutput);
  }

  @Test
  void nullAndEmptyHandling() {
    assertThat(UrlEncode.encode(null)).isNull();
    assertThat(UrlEncode.encode("   ")).isEmpty();
  }
}
