package org.r4reach.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
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

  @Test
  void fetchAllDeliveriesIncludesSeededDeliveries() {
    var all = DeliveryDao.fetchAllDeliveries(jdbiTest);
    assertThat(all).extracting(Delivery::getPublicKey).contains("BETA", "XKCD", "ABCD");
  }

  @Test
  void createDeliveryStartsInGivenStatusWithItemsAndSites() {
    long fromSiteId = siteIdByName("site2");
    long toSiteId = siteIdByName("site3");

    String publicKey =
        DeliveryDao.createDelivery(
            jdbiTest,
            DeliveryDao.CreateDeliveryRequest.builder()
                .fromSiteId(fromSiteId)
                .toSiteId(toSiteId)
                .deliveryStatus(DeliveryStatus.DRIVER_VOLUNTEERED)
                .targetDeliveryDate("2026-05-15")
                .dispatcherName("Jessi")
                .driverName("Ian Foster")
                .items(List.of("Water", "Blankets"))
                .build());

    Delivery created = DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, publicKey).orElseThrow();
    assertThat(created.getDeliveryStatus())
        .isEqualTo(DeliveryStatus.DRIVER_VOLUNTEERED.getAirtableName());
    assertThat(created.getFromSite()).isEqualTo("site2");
    assertThat(created.getToSite()).isEqualTo("site3");
    assertThat(created.getDeliveryDate()).isEqualTo("2026-05-15");
    assertThat(created.getDispatcherName()).isEqualTo("Jessi");
    assertThat(created.getDriverName()).isEqualTo("Ian Foster");
    assertThat(created.getItemList()).containsExactlyInAnyOrder("Water", "Blankets");
  }

  @Test
  void createDeliveryOmitsBlankItems() {
    long siteId = siteIdByName("site2");

    String publicKey =
        DeliveryDao.createDelivery(
            jdbiTest,
            DeliveryDao.CreateDeliveryRequest.builder()
                .fromSiteId(siteId)
                .toSiteId(siteId)
                .deliveryStatus(DeliveryStatus.CREATING_DISPATCH)
                .items(List.of("Water", "  ", ""))
                .build());

    Delivery created = DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, publicKey).orElseThrow();
    assertThat(created.getItemList()).containsExactly("Water");
  }

  private static long siteIdByName(String name) {
    return jdbiTest.withHandle(
        handle ->
            handle
                .createQuery("select id from site where name = :name")
                .bind("name", name)
                .mapTo(Long.class)
                .one());
  }
}
