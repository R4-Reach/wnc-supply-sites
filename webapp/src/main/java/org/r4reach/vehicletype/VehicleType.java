package org.r4reach.vehicletype;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One selectable value in the configurable driver vehicle-type list. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VehicleType {
  long id;
  String name;
}
