package org.r4reach.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnumUtilTest {

  enum TestEnum {
    ONE("one"),
    TWO("two");

    private final String textValue;

    TestEnum(String textValue) {
      this.textValue = textValue;
    }

    String getTextValue() {
      return textValue;
    }

    static Optional<TestEnum> mapText(String displayText) {
      return EnumUtil.mapText(TestEnum.values(), TestEnum::getTextValue, displayText);
    }
  }

  @Test
  void mapping() {
    var result = TestEnum.mapText("one");
    assertThat(result.orElseThrow()).isEqualTo(TestEnum.ONE);

    result = TestEnum.mapText("two");
    assertThat(result.orElseThrow()).isEqualTo(TestEnum.TWO);
  }

  @Test
  void emptyMapping() {
    var result = TestEnum.mapText("DNE");
    assertThat(result).isEmpty();

    result = TestEnum.mapText("");
    assertThat(result).isEmpty();

    result = TestEnum.mapText(null);
    assertThat(result).isEmpty();
  }
}
