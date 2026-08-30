package org.r4reach.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeFormat {

  private static final DateTimeFormatter dateTimeFormatter =
      DateTimeFormatter.ofPattern("yyyy-MMM-d");

  private static final DateTimeFormatter localTimeFormatter = DateTimeFormatter.ofPattern("HH:mm");

  public static String format(LocalDateTime dateTime) {
    return dateTimeFormatter.format(dateTime);
  }

  public static String formatTime(LocalDateTime dateTime) {
    return localTimeFormatter.format(dateTime);
  }
}
