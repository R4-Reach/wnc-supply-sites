package org.r4reach.data;

import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.r4reach.util.EnumUtil;

@Getter
@AllArgsConstructor
public enum ItemStatus {
  URGENTLY_NEEDED("Urgently Needed", "urgent", true),
  NEEDED("Needed", "needed", true),
  AVAILABLE("Available", "available", false),
  OVERSUPPLY("Oversupply", "oversupply", false),
  ;
  private final String text;
  private final String cssClass;
  private final boolean needed;

  public static List<String> allItemStatus() {
    return Arrays.stream(values()).map(s -> s.text).toList();
  }

  public static ItemStatus fromTextValue(String textValue) {
    return EnumUtil.mapText(values(), ItemStatus::getText, textValue)
        .orElseThrow(() -> new IllegalArgumentException("Invalid item status text: " + textValue));
  }
}
