package org.r4reach.util;

import java.time.Duration;

public class DurationFormatter {

  public static String formatDuration(Duration duration) {
    long hours = duration.toHours();
    long minutes = duration.toMinutes() % 60;

    StringBuilder sb = new StringBuilder();
    if (hours > 0) {
      sb.append(hours).append(" hr ");
    }
    sb.append(minutes).append(" min");
    return sb.toString();
  }
}
