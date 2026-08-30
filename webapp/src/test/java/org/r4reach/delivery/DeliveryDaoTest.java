package org.r4reach.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.r4reach.TestConfiguration;

class DeliveryDaoTest {

  @BeforeAll
  static void setupDatabase() {
    TestConfiguration.setupDatabase();
  }

  /** Make sure we can do a lookup of a delivery by public URL key */
  @ParameterizedTest
  @ValueSource(strings = {"BETA", "XKCD", "ABCD"})
  void fetchDeliveryByPublicUrl(String urlKey) {
    var result = DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, urlKey).orElseThrow();
    assertThat(result).isNotNull();
  }

  @Test
  void updateDeliveryStatus() {
    var delivery = DeliveryHelper.withNewDelivery();

    DeliveryDao.updateDeliveryStatus(
        jdbiTest, delivery.getPublicKey(), DeliveryStatus.DELIVERY_IN_PROGRESS);
    var status =
        DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey())
            .orElseThrow()
            .getDeliveryStatus();
    assertThat(status).isEqualTo(DeliveryStatus.DELIVERY_IN_PROGRESS.getAirtableName());

    DeliveryDao.updateDeliveryStatus(
        jdbiTest, delivery.getPublicKey(), DeliveryStatus.DELIVERY_CANCELLED);
    status =
        DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey())
            .orElseThrow()
            .getDeliveryStatus();
    assertThat(status).isEqualTo(DeliveryStatus.DELIVERY_CANCELLED.getAirtableName());
  }
}
