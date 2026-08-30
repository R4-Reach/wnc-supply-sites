package org.r4reach.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DonationStatus {
  CLOSED("Closed"),
  ACCEPTING_DONATIONS("Accepting Donations"),
  NOT_ACCEPTING_DONATIONS("Not Accepting Donations"),
  ;
  private final String textValue;
}
