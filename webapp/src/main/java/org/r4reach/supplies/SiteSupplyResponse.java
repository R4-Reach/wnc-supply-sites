package org.r4reach.supplies;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
public class SiteSupplyResponse {
  int resultCount;
  List<SiteSupplyData> results;

  @Value
  @Builder
  static class SiteSupplyData {
    Long id;
    String site;
    String siteType;
    String county;
    String state;
    // includes all needed items, whether urgently needed or just needed
    @Builder.Default List<SiteItem> neededItems = new ArrayList<>();
    // includes all available items, whether just available or oversupply
    @Builder.Default List<SiteItem> availableItems = new ArrayList<>();
    boolean acceptingDonations;
    String inventoryLastUpdated;
    String lastDelivery;
  }

  @Value
  @Builder
  static class SiteItem {
    String name;
    String displayClass;
    String statusGlyph;
    String statusLabel;
    List<String> tags;

    public String getDisplayClass() {
      if (List.of("urgent", "needed", "available", "oversupply").contains(displayClass)) {
        return displayClass;
      } else {
        log.error("Invalid display class: {}", displayClass, new Exception());
        throw new IllegalStateException("Illegal display class: " + displayClass);
      }
    }
  }
}
