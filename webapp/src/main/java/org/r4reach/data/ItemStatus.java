package org.r4reach.data;

import java.util.Arrays;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.r4reach.util.EnumUtil;

@Getter
@AllArgsConstructor
public enum ItemStatus {
  // glyph is an ordinal shape (▲ peak → ▽ surplus) that carries supply rank on a non-color
  // channel, so state survives colorblindness, greyscale, and print; shortLabel is the
  // redundant text token shown beside it. Both are paired with cssClass on every status display.
  URGENTLY_NEEDED("Urgently Needed", "urgent", "▲", "Urgent", true),
  NEEDED("Needed", "needed", "◆", "Needed", true),
  AVAILABLE("Available", "available", "●", "Available", false),
  OVERSUPPLY("Oversupply", "oversupply", "▽", "Oversupply", false),
  ;
  private final String text;
  private final String cssClass;
  private final String glyph;
  private final String shortLabel;
  private final boolean needed;

  public static List<String> allItemStatus() {
    return Arrays.stream(values()).map(s -> s.text).toList();
  }

  public static ItemStatus fromTextValue(String textValue) {
    return EnumUtil.mapText(values(), ItemStatus::getText, textValue)
        .orElseThrow(() -> new IllegalArgumentException("Invalid item status text: " + textValue));
  }
}
