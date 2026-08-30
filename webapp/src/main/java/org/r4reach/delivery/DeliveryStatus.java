package org.r4reach.delivery;

import java.util.Arrays;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DeliveryStatus {
  // driverFacingLabel/driverFacingBucketId are the single source of truth for how a status
  // reads to a driver -- used both for the driver-portal route-status filter and the per-card
  // "Status:" line, so the two can never drift into different vocabularies (see driver-portal
  // UX review F6). The four bucket ids match the driver portal's workflow-ordered filter tabs;
  // statuses that don't map onto the driver's own four-stage view (e.g. "Assigning Driver")
  // fold into the nearest bucket a driver would recognize.
  DRIVER_VOLUNTEERED("Driver Volunteered", "Volunteered", "volunteered"),
  CREATING_DISPATCH("Creating Dispatch", "Being scheduled", "pending"),
  ASSIGNING_DRIVER("Assigning Driver", "Being scheduled", "pending"),
  CONFIRMING("Confirming", "Being scheduled", "pending"),
  CONFIRMED("Confirmed", "Confirmed", "confirmed"),
  DELIVERY_IN_PROGRESS("Delivery In Progress", "Confirmed", "confirmed"),
  DELIVERY_COMPLETED("Delivery Completed", "Completed", "completed"),
  DELIVERY_CANCELLED("Delivery Cancelled", "Completed", "completed"),
  ;

  final String airtableName;
  final String driverFacingLabel;
  final String driverFacingBucketId;

  public static Optional<DeliveryStatus> fromAirtableName(String airtableName) {
    return Arrays.stream(values()).filter(s -> s.airtableName.equals(airtableName)).findFirst();
  }
}
