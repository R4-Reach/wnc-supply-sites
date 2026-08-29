package com.vanatta.helene.supplies.database.delivery;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;

import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import com.vanatta.helene.supplies.database.test.util.TestDataFile;
import java.util.List;

public class DeliveryHelper {

  static Delivery withDispatcherConfirmedDelivery() {
    var fixture = DeliveryFixture.parseJson(TestDataFile.DELIVERY_DATA_JSON.readData());
    fixture.store(jdbiTest);
    ConfirmationDao.dispatcherConfirm(jdbiTest, fixture.getPublicUrlKey());
    return DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, fixture.getPublicUrlKey()).orElseThrow();
  }

  static Delivery withConfirmedDelivery() {
    var fixture = DeliveryFixture.parseJson(TestDataFile.DELIVERY_DATA_JSON.readData());
    fixture.store(jdbiTest);
    ConfirmationDao.dispatcherConfirm(jdbiTest, fixture.getPublicUrlKey());

    Delivery delivery =
        DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, fixture.getPublicUrlKey()).orElseThrow();

    delivery
        .getConfirmations()
        .forEach(
            confirmartion ->
                ConfirmationDao.confirmDelivery(
                    jdbiTest,
                    delivery.getPublicKey(),
                    DeliveryConfirmation.ConfirmRole.valueOf(confirmartion.getConfirmRole())));

    return delivery;
  }

  public static Delivery withNewDelivery() {
    var fixture = DeliveryFixture.parseJson(TestDataFile.DELIVERY_DATA_JSON.readData());
    fixture.store(jdbiTest);
    return DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, fixture.getPublicUrlKey()).orElseThrow();
  }

  public static Delivery withNewDelivery(long fromSiteId, long toSiteId) {
    long fromSiteWssId = SiteDetailDao.lookupSiteById(jdbiTest, fromSiteId).getWssId();
    long toSiteWssId = SiteDetailDao.lookupSiteById(jdbiTest, toSiteId).getWssId();

    var fixture =
        DeliveryFixture.parseJson(TestDataFile.DELIVERY_DATA_JSON.readData()).toBuilder()
            .dropOffSiteWssId(List.of(toSiteWssId))
            .pickupSiteWssId(List.of(fromSiteWssId))
            .build();
    fixture.store(jdbiTest);
    return DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, fixture.getPublicUrlKey()).orElseThrow();
  }
}
