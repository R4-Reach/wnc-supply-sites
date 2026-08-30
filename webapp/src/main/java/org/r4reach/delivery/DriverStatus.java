package org.r4reach.delivery;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum DriverStatus {
  PENDING(""),
  DRIVER_EN_ROUTE("Start Delivery"),
  ARRIVED_AT_PICKUP("Arrived at Pickup"),
  DEPARTED_PICKUP("Leaving Pickup"),
  ARRIVED_AT_DROP_OFF("Arrived at Drop Off"),
  ;

  /** The text on the button to trigger the next driver status */
  final String buttonText;

  public static DriverStatus nextStatus(String statusName) {
    return nextStatus(DriverStatus.valueOf(statusName));
  }

  public static DriverStatus nextStatus(DriverStatus status) {
    return switch (status) {
      case PENDING -> DRIVER_EN_ROUTE;
      case DRIVER_EN_ROUTE -> ARRIVED_AT_PICKUP;
      case ARRIVED_AT_PICKUP -> DEPARTED_PICKUP;
      case DEPARTED_PICKUP, ARRIVED_AT_DROP_OFF -> ARRIVED_AT_DROP_OFF;
    };
  }
}
