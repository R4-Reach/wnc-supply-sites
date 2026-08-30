package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DateTimeFormatTest {

  @Test
  void formatTime() {
    LocalDateTime localDateTime = LocalDateTime.of(2020, 1, 1, 23, 59);
    var result = DateTimeFormat.formatTime(localDateTime);
    assertThat(result).isEqualTo("23:59");
  }
}
