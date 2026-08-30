package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TrimUtilTest {

  @Nested
  class StringInput {
    @Test
    void nullInput() {
      String input = null;

      String output = TrimUtil.trim(input);

      assertThat(output).isNull();
    }

    @Test
    void notTrimmed() {
      String input = "a";

      String output = TrimUtil.trim(input);

      assertThat(output).isEqualTo("a");
    }

    @Test
    void needsTrim() {
      String input = "  a ";

      String output = TrimUtil.trim(input);

      assertThat(output).isEqualTo("a");
    }
  }

  @Nested
  class ListInput {

    /** Null input returns an empty list instead of null */
    @Test
    void nullInput() {
      var output = TrimUtil.trim((List<String>) null);
      assertThat(output).isEqualTo(List.of());
    }

    @Test
    void listNeedsTrim() {
      List<String> input = List.of("  a ", " b", "c", "d  ");

      var output = TrimUtil.trim(input);

      assertThat(output).isEqualTo(List.of("a", "b", "c", "d"));
    }
  }
}
