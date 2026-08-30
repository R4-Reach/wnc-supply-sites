package org.r4reach.util;

import java.util.UUID;

public class SecretCodeGenerator {

  /** Generates a random 4 character code. Code is upper-case alpha characters only. */
  public static String generateCode() {
    return UUID.randomUUID()
        .toString()
        .substring(0, 4)
        .replace("0", "A")
        .replace("1", "B")
        .replace("2", "C")
        .replace("3", "D")
        .replace("4", "E")
        .replace("5", "F")
        .replace("6", "G")
        .replace("7", "H")
        .replace("8", "I")
        .replace("9", "J")
        .toUpperCase();
  }
}
