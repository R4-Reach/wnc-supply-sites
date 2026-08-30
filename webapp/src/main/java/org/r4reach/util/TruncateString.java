package org.r4reach.util;

public class TruncateString {

  /**
   * Truncates on whole words, find the first word past the max length and replaces the rest of the
   * string with ellipses.
   *
   * <p>In other words, we return the first 'maxlength' characters of the input string, then on the
   * next space, we will truncate.
   *
   * <p>If input is null, returns an empty string.
   *
   * <pre>
   *   EG:
   *   truncate("one two three", 0) -> "one..."
   *   truncate("one two three", 4) -> "one..."
   *   truncate("one two three", 5) -> "one two..."
   *   truncate("one two", 7) -> "one two"
   * </pre>
   */
  public static String truncate(String input, int maxLength) {
    if (input == null) {
      return null;
    }

    input = input.trim();

    if (input.length() <= maxLength) {
      return input;
    }
    if (!input.contains(" ")) {
      return input;
    }

    if (maxLength == 0) {
      return input.substring(0, input.indexOf(" ")).trim() + "...";
    }

    // if our max length character is a space, then we can truncate from that location.
    if (input.charAt(maxLength - 1) == ' ') {
      return input.substring(0, maxLength - 1).trim() + "...";
    } else {
      String searchString = input.substring(maxLength);
      int spaceIndex = searchString.indexOf(' ');
      if (spaceIndex == -1) {
        // no more spaces in the string, so return the whole thing.
        return input;
      } else {
        return input.substring(0, maxLength + spaceIndex).trim() + "...";
      }
    }

    //    String searchString = input.substring(maxLength - 1);
    //
    //    if (searchString.endsWith(" ")) {
    //      return searchString.substring(0, searchString.length() - 1).trim() + "...";
    //    }

  }
}
