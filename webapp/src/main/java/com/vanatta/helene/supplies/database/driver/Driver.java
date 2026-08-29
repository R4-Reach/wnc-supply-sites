package com.vanatta.helene.supplies.database.driver;

import com.vanatta.helene.supplies.database.util.TruncateString;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Driver {
  Long wssId;
  private String fullName;
  private String phone;
  private boolean active;
  private boolean blacklisted;
  private String location;
  private String availability;
  private String comments;
  private String licensePlates;
  private boolean can_lift_50lbs;
  private int pallet_capacity;

  public String getComments() {
    return TruncateString.truncate(comments, 1000);
  }

  public String getAvailability() {
    return TruncateString.truncate(availability, 1000);
  }
}
