package org.r4reach.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.r4reach.TestConfiguration;
import org.r4reach.dispatch.DispatchDao;
import org.r4reach.util.PiiCrypto;

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
    long dispatcherId = insertUser("Jessi", "15559990000");
    long driverId = insertDriver("Ian Foster", "555-222-3333");

    String publicKey =
        DeliveryDao.createDelivery(
            jdbiTest,
            DeliveryDao.CreateDeliveryRequest.builder()
                .fromSiteId(fromSiteId)
                .toSiteId(toSiteId)
                .deliveryStatus(DeliveryStatus.DRIVER_VOLUNTEERED)
                .targetDeliveryDate("2026-05-15")
                .dispatcherWssUserId(dispatcherId)
                .driverWssUserId(driverId)
                .items(List.of("Water", "Blankets"))
                .build());

    Delivery created = DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, publicKey).orElseThrow();
    assertThat(created.getDeliveryStatus())
        .isEqualTo(DeliveryStatus.DRIVER_VOLUNTEERED.getAirtableName());
    assertThat(created.getFromSite()).isEqualTo("site2");
    assertThat(created.getToSite()).isEqualTo("site3");
    assertThat(created.getDeliveryDate()).isEqualTo("2026-05-15");
    // Name and phone are derived from the referenced wss_user records, not stored on the delivery.
    assertThat(created.getDispatcherName()).isEqualTo("Jessi");
    assertThat(created.getDispatcherPhoneNumber()).isEqualTo("15559990000");
    assertThat(created.getDriverName()).isEqualTo("Ian Foster");
    assertThat(created.getDriverPhoneNumber()).isEqualTo("15552223333");
    assertThat(created.getItemList()).containsExactlyInAnyOrder("Water", "Blankets");
  }

  @Test
  void driverPortalLookupMatchesReferencedDriverPhone() {
    long siteId = siteIdByName("site2");
    long driverId = insertDriver("Portal Driver", "555-777-8888");

    DeliveryDao.createDelivery(
        jdbiTest,
        DeliveryDao.CreateDeliveryRequest.builder()
            .fromSiteId(siteId)
            .toSiteId(siteId)
            .deliveryStatus(DeliveryStatus.DRIVER_VOLUNTEERED)
            .driverWssUserId(driverId)
            .build());

    assertThat(DeliveryDao.fetchDeliveriesByDriverPhoneNumber(jdbiTest, "555-777-8888"))
        .extracting(Delivery::getDriverName)
        .contains("Portal Driver");
  }

  @Test
  void dispatcherOptionsPreselectGivenUser() {
    long dispatcherId = insertDispatcher("Dana", "15550100200");
    long otherId = insertDispatcher("Ola", "15550100300");

    List<DeliveryDao.PersonOption> options =
        DeliveryDao.fetchDispatcherOptions(jdbiTest, dispatcherId);

    assertThat(options).extracting(DeliveryDao.PersonOption::getId).contains(dispatcherId, otherId);
    assertThat(options)
        .filteredOn(DeliveryDao.PersonOption::isSelected)
        .extracting(DeliveryDao.PersonOption::getId)
        .containsExactly(dispatcherId);
  }

  @Test
  void driverOptionsExcludeBlacklisted() {
    long activeId = insertDriver("Active Driver", "555-444-1111");
    long barredId = insertDriver("Barred Driver", "555-444-2222");
    long barredWssId =
        DispatchDao.fetchAll(jdbiTest).stream()
            .filter(row -> "Barred Driver".equals(row.getFullName()))
            .findFirst()
            .orElseThrow()
            .getWssId();
    DispatchDao.setBlackListed(jdbiTest, barredWssId, true);

    assertThat(DeliveryDao.fetchDriverOptions(jdbiTest, null))
        .extracting(DeliveryDao.PersonOption::getId)
        .contains(activeId)
        .doesNotContain(barredId);
  }

  private static long insertUser(String name, String phone) {
    return jdbiTest.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    insert into wss_user(phone_enc, phone_hmac, name_enc)
                    values (:phoneEnc, :phoneHmac, :nameEnc)
                    on conflict(phone_hmac) do update set name_enc = excluded.name_enc, removed = false
                    returning id
                    """)
                .bind("phoneEnc", PiiCrypto.encrypt(phone))
                .bind("phoneHmac", PiiCrypto.blindIndex(phone))
                .bind("nameEnc", PiiCrypto.encrypt(name))
                .mapTo(Long.class)
                .one());
  }

  private static long insertDispatcher(String name, String phone) {
    long userId = insertUser(name, phone);
    jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into wss_user_roles(wss_user_id, wss_user_role_id)
                    select :userId, r.id
                    from wss_user_role r
                    where r.name = 'DISPATCHER'
                      and not exists (
                        select 1 from wss_user_roles x
                        where x.wss_user_id = :userId and x.wss_user_role_id = r.id)
                    """)
                .bind("userId", userId)
                .execute());
    return userId;
  }

  private static long insertDriver(String name, String phone) {
    DispatchDao.createDriver(jdbiTest, phone, name);
    return DispatchDao.fetchAll(jdbiTest).stream()
        .filter(row -> name.equals(row.getFullName()))
        .findFirst()
        .orElseThrow()
        .getWssUserId();
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
