package org.r4reach.util;

import java.util.List;
import java.util.Optional;

public class TrimUtil {

  /** Null safe trim operation, returns null if the input is null otherwise trims */
  public static String trim(String input) {
    return Optional.ofNullable(input).map(String::trim).orElse(null);
  }

  /**
   * Applies a trim to each element of the input list. Returns an empty list if null input is
   * received.
   */
  public static List<String> trim(List<String> input) {
    return input == null ? List.of() : input.stream().map(String::trim).toList();
  }
}
