package org.r4reach.util;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EnumUtil {
  public static <T> Optional<T> mapText(
      T[] values, Function<T, String> mapper, String textToMatch) {
    if (textToMatch == null) {
      log.warn(
          "Null value requested to be mapped in values: {}",
          Arrays.toString(values),
          new IllegalStateException());
      return Optional.empty();
    }

    Optional<T> matchedValue =
        Arrays.stream(values).filter(v -> mapper.apply(v).equalsIgnoreCase(textToMatch)).findAny();
    if (matchedValue.isEmpty()) {
      log.warn(
          "Unmapped value: {}, requested to be mapped in values: {}",
          textToMatch,
          Arrays.toString(values),
          new IllegalStateException());
    }
    return matchedValue;
  }
}
