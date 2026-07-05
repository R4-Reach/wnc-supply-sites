package com.vanatta.helene.supplies.database;

import com.vanatta.helene.supplies.database.data.ItemStatus;
import com.vanatta.helene.supplies.database.data.SiteType;
import com.vanatta.helene.supplies.database.driver.Driver;
import com.vanatta.helene.supplies.database.manage.add.site.AddSiteDao;
import com.vanatta.helene.supplies.database.manage.add.site.AddSiteData;
import com.vanatta.helene.supplies.database.test.util.TestDataFile;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.UUID;
import lombok.Builder;
import lombok.Value;
import org.jdbi.v3.core.Jdbi;

public class TestConfiguration {

  // these values come from TestData.sql
  public static final long SITE1_AIRTABLE_ID = -200;
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
    String dbUrl = "database:5432";
    // Optional.ofNullable(System.getenv("DB_URL")).orElse("localhost:5432");
    config.setJdbcUrl(String.format("jdbc:postgresql://%s/wnc_helene_test", dbUrl));
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

  public static Driver buildDriver(long airtableId, String phoneNumber) {
    return Driver.builder()
        .location("city")
        .active(true)
        .airtableId(airtableId)
        .licensePlates("WXC444")
        .fullName("driver")
        .phone(phoneNumber)
        .availability("availability test driver")
        .comments("comments test driver")
        .build();
  }
}
