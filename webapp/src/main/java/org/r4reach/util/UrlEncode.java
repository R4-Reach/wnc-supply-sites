package org.r4reach.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UrlEncode {

  public static String encode(String input) {
    if (input == null) {
      return null;
    } else {
      return URLEncoder.encode(input.trim(), StandardCharsets.UTF_8);
    }
  }
}
