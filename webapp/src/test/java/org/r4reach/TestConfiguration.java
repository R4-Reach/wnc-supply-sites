package org.r4reach;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.data.ItemStatus;
import org.r4reach.data.SiteType;
import org.r4reach.driver.Driver;
import org.r4reach.manage.add.site.AddSiteDao;
import org.r4reach.manage.add.site.AddSiteData;
import org.r4reach.test.util.TestDataFile;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.PiiCrypto;

public class TestConfiguration {

  // these values come from TestData.sql
  public static final long SITE1_WSS_ID = -10;
  public static final long SITE2_WSS_ID = -20;
  public static final long WATER_WSS_ID = -40;
  public static final long SOAP_WSS_ID = -30;
  public static final long GLOVES_WSS_ID = -50;
  public static final long USED_CLOTHES_WSS_ID = -60;
  public static final long NEW_CLOTHES_WSS_ID = -70;
  public static final long RANDOM_STUFF_WSS_ID = -80;
  public static final long HEATER_WSS_ID = -90;
  public static final long BATTERIES_WSS_ID = -95;

  public static final Jdbi jdbiTest;

  static {
    HikariConfig config = new HikariConfig();
    // Host and port are injected by the docker-compose gradle plugin (exposeAsEnvironment) so the
    // tests connect to the dynamically-published port of the 'database' service. Falls back to
    // localhost:5432 for IDE runs against a manually-started database.
    String host = Optional.ofNullable(System.getenv("DATABASE_HOST")).orElse("localhost");
    String port = Optional.ofNullable(System.getenv("DATABASE_TCP_5432")).orElse("5432");
    config.setJdbcUrl(String.format("jdbc:postgresql://%s:%s/wnc_helene", host, port));
    config.setUsername("wnc_helene");
    config.setPassword("wnc_helene");
    config.addDataSourceProperty("maximumPoolSize", "16");
    HikariDataSource ds = new HikariDataSource(config);
    jdbiTest = Jdbi.create(ds);
  }

  public static void setupDatabase() {
    try {
      var sql = TestDataFile.TEST_DATA_SCHEMA.readData();
      TestConfiguration.jdbiTest.withHandle(handle -> handle.createScript(sql).execute());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Adds a new site with a random name, returns the name of the site. */
  public static String addSite() {
    return addSite(SiteType.DISTRIBUTION_CENTER);
  }

  public static String addSite(String namePrefix) {
    return addSite(namePrefix, SiteType.DISTRIBUTION_CENTER);
  }

  public static String addSite(SiteType siteType) {
    return addSite("" + SiteType.DISTRIBUTION_CENTER, siteType);
  }

  public static String addSite(String namePrefix, SiteType siteType) {
    String name = (namePrefix + " site " + UUID.randomUUID().toString()).trim();
    AddSiteDao.addSite(
        jdbiTest,
        AddSiteData.builder()
            .siteName(name)
            .county("Watauga")
            .state("NC")
            .city("city " + name)
            .streetAddress("address of " + name)
            .siteType(siteType)
            .maxSupplyLoad("Car")
            .contactNumber("000")
            .build());
    return name;
  }

  public static void addCounty(String county, String state) {
    String insert = "insert into county(name, state) values (:name, :state)";
    jdbiTest.withHandle(
        handle -> handle.createUpdate(insert).bind("name", county).bind("state", state).execute());
  }

  public static long getSiteId() {
    return getSiteId("site1");
  }

  public static long getSiteId(String siteName) {
    return TestConfiguration.jdbiTest.withHandle(
        handle ->
            handle
                .createQuery("select id from site where name = :siteName")
                .bind("siteName", siteName)
                .mapTo(Long.class)
                .one());
  }

  @Value
  @Builder
  public static class ItemResult {
    String name;
    long id;
    long wssId;
  }

  /** Creates a random item and returns the ID of the created item. */
  public static ItemResult addItem(String prefix) {
    String name = prefix + " item " + UUID.randomUUID().toString();
    String insert =
        """
      insert into item(name)
      values(:name)
      """;
    long id =
        jdbiTest.withHandle(
            handle ->
                handle
                    .createUpdate(insert)
                    .bind("name", name)
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long.class)
                    .one());
    long wssId =
        jdbiTest.withHandle(
            h ->
                h.createQuery("select wss_id from item where id = :id")
                    .bind("id", id)
                    .mapTo(Long.class)
                    .one());

    return ItemResult.builder().name(name).id(id).wssId(wssId).build();
  }

  public static void addItemToSite(
      long siteId, ItemStatus itemStatus, String itemName, long wssId) {
    String insert =
        """
        insert into site_item(site_id, item_id, item_status_id, wss_id)
        values(
          :siteId,
          (select id from item where name = :itemName),
          (select id from item_status where name = :itemStatus),
          :wssId
        )
        """;
    TestConfiguration.jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate(insert)
                .bind("siteId", siteId)
                .bind("itemStatus", itemStatus.getText())
                .bind("itemName", itemName)
                .bind("wssId", wssId)
                .execute());
  }

  public static Driver buildDriver(long wssId, String phoneNumber) {
    return Driver.builder()
        .location("city")
        .active(true)
        .wssId(wssId)
        .licensePlates("WXC444")
        .fullName("driver")
        .phone(phoneNumber)
        .availability("availability test driver")
        .comments("comments test driver")
        .build();
  }

  /**
   * Inserts a driver, creating the backing wss_user for its phone/name (identity now lives on
   * wss_user; the driver row only holds the portal fields and a foreign key).
   */
  public static void insertDriver(Driver driver) {
    String canonicalPhone = PhoneNumberUtil.toCanonical(driver.getPhone());
    jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate(
                    "insert into wss_user(phone_enc, phone_hmac, name_enc)"
                        + " values(:phoneEnc, :phoneHmac, :nameEnc)"
                        + " on conflict(phone_hmac) do nothing")
                .bind("phoneEnc", PiiCrypto.encrypt(canonicalPhone))
                .bind("phoneHmac", PiiCrypto.blindIndex(canonicalPhone))
                .bind("nameEnc", PiiCrypto.encrypt(driver.getFullName()))
                .execute());
    jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into driver(
                      wss_id, wss_user_id, location, active, black_listed,
                      license_plates, comments, availability, can_lift_50lbs, pallet_capacity)
                    values(
                      :wssId,
                      (select id from wss_user where phone_hmac = :phoneHmac),
                      :location, :active, :blacklisted,
                      :licensePlates, :comments, :availability, :canLift, :pallet)
                    """)
                .bind("wssId", driver.getWssId())
                .bind("phoneHmac", PiiCrypto.blindIndex(canonicalPhone))
                .bind("location", driver.getLocation())
                .bind("active", driver.isActive())
                .bind("blacklisted", driver.isBlacklisted())
                .bind("licensePlates", driver.getLicensePlates())
                .bind("comments", driver.getComments())
                .bind("availability", driver.getAvailability())
                .bind("canLift", driver.isCan_lift_50lbs())
                .bind("pallet", driver.getPallet_capacity())
                .execute());
  }
}
