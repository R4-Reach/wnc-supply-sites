package org.r4reach.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import lombok.Builder;
import lombok.Data;

public class HtmlSelectOptionsUtil {
  public static List<ItemListing> createItemListing(
      String selectedValue, Collection<String> allValues) {
    return allValues.stream()
        .map(
            value ->
                ItemListing.builder()
                    .name(value)
                    .selected(value.equals(selectedValue) ? "selected" : "")
                    .build())
        .sorted(Comparator.comparing(ItemListing::getName))
        .toList();
  }

  public static List<ItemListing> createItemListingWithFuzzyStartsWith(
      String selectedValue, Collection<String> allValues) {
    return allValues.stream()
        .map(
            value ->
                ItemListing.builder()
                    .name(value)
                    .selected(
                        ((selectedValue == null || selectedValue.isBlank()) && value.isBlank())
                                || (selectedValue != null
                                    && !selectedValue.isBlank()
                                    && value.startsWith(selectedValue))
                            ? "selected"
                            : null)
                    .build())
        .toList();
  }

  @Builder
  @Data
  public static class ItemListing {
    String name;

    /** Should either be blank, or "selected" */
    String selected;
  }
}
