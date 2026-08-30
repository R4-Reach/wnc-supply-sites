package org.r4reach.util;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TruncateStringTest {

  @ParameterizedTest
  @CsvSource(
      value = {
        "1 2 3 4, 1, 1...",
        "1 2 3 4, 2, 1...",
        "1 2 3 4, 3, 1 2...",
        "1 2 3 4, 4, 1 2...",
        "1 2 3 4, 5, 1 2 3...",
        "1 2 3 4, 6, 1 2 3...",
        "1 2 3 4, 7, 1 2 3 4",
        "1 2 3 4, 8, 1 2 3 4",
        "one two three, 0, one...",
        "one two three, 3, one...",
        "one two three, 4, one...",
        "one two three, 5, one two...",
        "one two three, 8, one two...",
        "one two three, 9, one two three",
        "one two three, 20, one two three",
        "nospaces, 0, nospaces",
        "nospaces, 3, nospaces",
        "nospaces, 10, nospaces"
      })
  void testTruncate(String input, int length, String expected) {
    var result = TruncateString.truncate(input, length);
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void testTruncateNullCase() {
    var result = TruncateString.truncate(null, 1);
    assertThat(result).isNull();
  }

  @Test
  void testTruncateEmptyCase() {
    var result = TruncateString.truncate("", 1);
    assertThat(result).isEqualTo("");
  }

  @Test
  void blankStringIsTrimmed() {
    var result = TruncateString.truncate("     ", 1);
    assertThat(result).isEqualTo("");
  }

  @Test
  void inputIsTrimmed() {
    var result = TruncateString.truncate("  1 2   ", 1);
    assertThat(result).isEqualTo("1...");

    result = TruncateString.truncate("  1 2   ", 2);
    assertThat(result).isEqualTo("1...");

    result = TruncateString.truncate("  1 2   ", 3);
    assertThat(result).isEqualTo("1 2");
  }
}
