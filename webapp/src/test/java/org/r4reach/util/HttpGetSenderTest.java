package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpGetSenderTest {

  @Test
  void buildUrl() {
    String result = HttpGetSender.buildUrl("http://url", Map.of());
    assertThat(result).isEqualTo("http://url");

    result = HttpGetSender.buildUrl("http://url", Map.of("a", "c&d"));
    assertThat(result).isEqualTo("http://url?a=c%26d");

    result = HttpGetSender.buildUrl("http://url", Map.of("a", "b", "1", "2"));
    assertThat(result).isEqualTo("http://url?1=2&a=b");
  }
}
