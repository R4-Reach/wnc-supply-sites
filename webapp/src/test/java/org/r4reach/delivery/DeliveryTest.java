package org.r4reach.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DeliveryTest {

  @Test
  void itemListTruncated() {
    // item list should display just three items in sorted order.
    // If there are more, then dots ...
    var result = Delivery.getItemListTruncated(null);
    assertThat(result).isEqualTo("");

    result = Delivery.getItemListTruncated(List.of());
    assertThat(result).isEqualTo("");

    result = Delivery.getItemListTruncated(List.of("one", "water", "gloves"));
    assertThat(result).isEqualTo("gloves, one, water");

    result = Delivery.getItemListTruncated(List.of("one", "water", "gloves", "four"));
    assertThat(result).isEqualTo("four, gloves, one...");
  }

  @Test
  void deliveryIsConfirmed() {
    assertThat(buildDeliveryWithConfirmations(true, true, true).isConfirmed()).isTrue();
    assertThat(buildDeliveryWithConfirmations(true, true, true).hasCancellation()).isFalse();
  }

  @Test
  void emptyDeliveryNotConfirmed_and_notCancelled() {
    var delivery = Delivery.builder().build();
    assertThat(delivery.isConfirmed()).isFalse();
    assertThat(delivery.hasCancellation()).isFalse();
  }

  @ParameterizedTest
  @MethodSource
  void isNotConfirmed_and_notCancelled(Delivery delivery) {
    assertThat(delivery.isConfirmed()).isFalse();
    assertThat(delivery.hasCancellation()).isFalse();
  }

  static List<Delivery> isNotConfirmed_and_notCancelled() {
    return List.of(
        Delivery.builder().build(),
        buildDeliveryWithConfirmations(null, null, null),
        buildDeliveryWithConfirmations(true, null, null),
        buildDeliveryWithConfirmations(null, true, null),
        buildDeliveryWithConfirmations(null, null, true),
        buildDeliveryWithConfirmations(true, true, null),
        buildDeliveryWithConfirmations(true, null, true),
        buildDeliveryWithConfirmations(null, null, true));
  }

  @ParameterizedTest
  @MethodSource
  void isCancelled(Delivery delivery) {
    assertThat(delivery.hasCancellation()).isTrue();
  }

  static List<Delivery> isCancelled() {
    return List.of(
        buildDeliveryWithConfirmations(false, null, null),
        buildDeliveryWithConfirmations(null, false, null),
        buildDeliveryWithConfirmations(null, null, false),
        buildDeliveryWithConfirmations(false, false, null),
        buildDeliveryWithConfirmations(false, null, false),
        buildDeliveryWithConfirmations(false, false, false),
        buildDeliveryWithConfirmations(false, null, true),
        buildDeliveryWithConfirmations(true, false, null),
        buildDeliveryWithConfirmations(true, true, false),
        buildDeliveryWithConfirmations(false, true, true),
        buildDeliveryWithConfirmations(false, false, false));
  }

  private static Delivery buildDeliveryWithConfirmations(
      Boolean driver, Boolean pickup, Boolean dropOff) {
    return Delivery.builder()
        .confirmations(
            List.of(
                DeliveryConfirmation.builder()
                    .confirmed(driver)
                    .confirmRole(DeliveryConfirmation.ConfirmRole.DROPOFF_SITE.name())
                    .code("C")
                    .build(),
                DeliveryConfirmation.builder()
                    .confirmed(pickup)
                    .confirmRole(DeliveryConfirmation.ConfirmRole.PICKUP_SITE.name())
                    .code("C")
                    .build(),
                DeliveryConfirmation.builder()
                    .confirmed(dropOff)
                    .confirmRole(DeliveryConfirmation.ConfirmRole.DROPOFF_SITE.name())
                    .code("C")
                    .build()))
        .build();
  }
}
