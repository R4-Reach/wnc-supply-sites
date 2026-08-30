package org.r4reach.data;

import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SiteType {
  DISTRIBUTION_CENTER("Distribution Center"),
  SUPPLY_HUB("Supply Hub"),
  FOOD_PANTRY("Food Pantry"),
  ;
  private final String text;

  public static List<String> allSiteTypes() {
    return Arrays.stream(values()).map(s -> s.text).toList();
  }

  public static SiteType parseSiteType(String text) {
    return Arrays.stream(values())
        .filter(s -> s.text.equals(text))
        .findAny()
        .orElseThrow(() -> new IllegalArgumentException("Invalid site type: " + text));
  }
}
