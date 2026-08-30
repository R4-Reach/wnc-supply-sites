package org.r4reach.dev;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.UserRole;
import org.r4reach.auth.user.UserRoleService;
import org.r4reach.data.SiteType;
import org.r4reach.delivery.DeliveryDao;
import org.r4reach.delivery.DeliveryStatus;
import org.r4reach.driver.Driver;
import org.r4reach.driver.DriverDao;
import org.r4reach.manage.add.site.AddSiteDao;
import org.r4reach.manage.add.site.AddSiteData;
import org.r4reach.manage.inventory.InventoryDao;
import org.r4reach.util.HashingUtil;
import org.r4reach.util.PhoneNumberUtil;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds local-only data on startup so a developer running the full stack has a working portal, not
 * just an empty database: a known admin login plus a representative slice of the domain -- sites
 * with inventory and needs, drivers, and deliveries between sites.
 *
 * <p>Everything is built by calling the same DAOs the real UI calls (so it stays correct as the
 * schema evolves and exercises the true write paths). The representative slice is inserted only
 * once, on an empty database; see {@link #seedRepresentativeData}.
 */
@Slf4j
@Component
@Profile("local")
public class LocalDevUserSeeder implements ApplicationRunner {

  private static final String PHONE = "11111111111";
  private static final String PASSWORD = "wncstrong";

  /**
   * Every grantable role, taken straight from {@link UserRole} so that a newly added role is seeded
   * automatically without touching this class. Excludes {@link UserRole#AUTHORIZED}, the implicit
   * role that is never stored in wss_user_roles. This makes the local admin a full admin.
   */
  private static final List<String> ROLES =
      UserRole.assignableRoles().stream().map(Enum::name).toList();

  // Item-status names, straight from the item_status reference table (see V01/V07/V26 migrations).
  // The two "need" statuses carry is_need = true; the other two are on-hand / surplus.
  private static final String URGENTLY_NEEDED = "Urgently Needed";
  private static final String NEEDED = "Needed";
  private static final String AVAILABLE = "Available";
  private static final String OVERSUPPLY = "Oversupply";

  // The full catalog of items the seeded sites and deliveries reference. A site_item row can only
  // point at an item that already exists, so this list is inserted before any of them.
  private static final List<String> ITEMS =
      List.of(
          "Bottled Water",
          "Baby Formula",
          "Diapers",
          "Canned Food",
          "Non-Perishable Snacks",
          "Blankets",
          "Cleaning Supplies",
          "Hygiene Kits",
          "Batteries",
          "Flashlights",
          "First Aid Kits",
          "Work Gloves",
          "Tarps",
          "Propane",
          "Pet Food");

  /** A site plus the inventory it holds and the supplies it needs, keyed by item name. */
  private record SeedSite(
      String name,
      String address,
      String city,
      String county,
      SiteType type,
      String hours,
      String contactName,
      String contactPhone,
      List<String> urgentlyNeeded,
      List<String> needed,
      List<String> available,
      List<String> oversupply) {}

  // Sites spread across the WNC counties that ship with the county reference table (V01), in a mix
  // of the three site types, each with a named primary contact and a small stock of needs/surplus.
  private static final List<SeedSite> SITES =
      List.of(
          new SeedSite(
              "Asheville Community Distribution Center",
              "45 Riverside Dr",
              "Asheville",
              "Buncombe",
              SiteType.DISTRIBUTION_CENTER,
              "Mon-Sat 8am-6pm",
              "Maria Gonzalez",
              "8285550101",
              List.of("Bottled Water", "Baby Formula"),
              List.of("Diapers", "Canned Food"),
              List.of("Work Gloves"),
              List.of("Blankets")),
          new SeedSite(
              "Swannanoa Supply Hub",
              "2100 US-70",
              "Swannanoa",
              "Buncombe",
              SiteType.SUPPLY_HUB,
              "Daily 7am-7pm",
              "James Carter",
              "8285550102",
              List.of("Cleaning Supplies"),
              List.of("Tarps", "Batteries"),
              List.of("Canned Food", "Flashlights"),
              List.of("Non-Perishable Snacks")),
          new SeedSite(
              "Hendersonville Relief Depot",
              "300 N Main St",
              "Hendersonville",
              "Henderson",
              SiteType.DISTRIBUTION_CENTER,
              "Mon-Fri 9am-5pm",
              "Aisha Bello",
              "8285550103",
              List.of("First Aid Kits", "Baby Formula"),
              List.of("Hygiene Kits"),
              List.of("Blankets"),
              List.of("Work Gloves")),
          new SeedSite(
              "Marshall Food Pantry",
              "12 Bridge St",
              "Marshall",
              "Madison",
              SiteType.FOOD_PANTRY,
              "Tue/Thu 10am-2pm",
              "Tom Rivera",
              "8285550104",
              List.of("Canned Food", "Non-Perishable Snacks"),
              List.of("Baby Formula"),
              List.of("Pet Food"),
              List.of()),
          new SeedSite(
              "Old Fort Recovery Center",
              "25 Catawba Ave",
              "Old Fort",
              "McDowell",
              SiteType.SUPPLY_HUB,
              "Daily 8am-8pm",
              "Nina Alvarez",
              "8285550105",
              List.of("Tarps", "Propane"),
              List.of("Batteries", "Flashlights"),
              List.of("Cleaning Supplies"),
              List.of("Bottled Water")),
          new SeedSite(
              "Burnsville Aid Station",
              "8 Town Square",
              "Burnsville",
              "Yancey",
              SiteType.FOOD_PANTRY,
              "Mon/Wed/Fri 9am-3pm",
              "David Kim",
              "8285550106",
              List.of("Diapers"),
              List.of("Hygiene Kits", "Blankets"),
              List.of("Canned Food"),
              List.of("Non-Perishable Snacks")),
          new SeedSite(
              "Boone Mountain Relief",
              "500 Blowing Rock Rd",
              "Boone",
              "Watauga",
              SiteType.DISTRIBUTION_CENTER,
              "Daily 7am-9pm",
              "Sarah Feld",
              "8285550107",
              List.of("Propane", "Batteries"),
              List.of("Flashlights", "Work Gloves"),
              List.of("Tarps"),
              List.of("Pet Food")));

  /** A driver's identity (name/phone live on wss_user) plus their vehicle and hauling details. */
  private record SeedDriver(
      String fullName,
      String phone,
      String vehicleType,
      String licensePlate,
      String location,
      boolean canLift,
      int palletCapacity,
      String availability,
      String comments) {}

  // vehicleType values come from the vehicle_type reference table (V94).
  private static final List<SeedDriver> DRIVERS =
      List.of(
          new SeedDriver(
              "Carlos Mendez",
              "8285551201",
              "Van",
              "NC-CM1234",
              "Asheville, NC",
              true,
              3,
              "Weekdays and Saturdays",
              "Prefers Buncombe/Madison routes"),
          new SeedDriver(
              "Priya Nair",
              "3365551202",
              "Pickup Truck",
              "NC-PN5678",
              "Hickory, NC",
              true,
              5,
              "Any day with 24h notice",
              "Has ratchet straps and a dolly"),
          new SeedDriver(
              "Greg Olsen",
              "8285551203",
              "SUV",
              "NC-GO9012",
              "Marion, NC",
              false,
              1,
              "Weekends only",
              "Small loads only, no heavy lifting"),
          new SeedDriver(
              "Lena Fox",
              "7045551204",
              "Trailer",
              "NC-LF3456",
              "Charlotte, NC",
              true,
              5,
              "Flexible",
              "Can haul full pallets long distance"));

  /** A delivery between two seeded sites. A blank driver models a run still awaiting assignment. */
  private record SeedDelivery(
      String fromSite,
      String toSite,
      DeliveryStatus status,
      String targetDate,
      String driverName,
      String driverPhone,
      String licensePlate,
      List<String> items) {}

  private static final String DISPATCHER_NAME = "Dispatch Desk";
  private static final String DISPATCHER_NUMBER = "8285550100";

  private static final List<SeedDelivery> DELIVERIES =
      List.of(
          new SeedDelivery(
              "Asheville Community Distribution Center",
              "Marshall Food Pantry",
              DeliveryStatus.DELIVERY_COMPLETED,
              "2026-08-25",
              "Carlos Mendez",
              "8285551201",
              "NC-CM1234",
              List.of("Bottled Water", "Canned Food", "Diapers")),
          new SeedDelivery(
              "Hendersonville Relief Depot",
              "Burnsville Aid Station",
              DeliveryStatus.DELIVERY_IN_PROGRESS,
              "2026-08-30",
              "Priya Nair",
              "3365551202",
              "NC-PN5678",
              List.of("Baby Formula", "Blankets")),
          new SeedDelivery(
              "Swannanoa Supply Hub",
              "Old Fort Recovery Center",
              DeliveryStatus.CONFIRMED,
              "2026-09-02",
              "Greg Olsen",
              "8285551203",
              "NC-GO9012",
              List.of("Cleaning Supplies", "Tarps")),
          new SeedDelivery(
              "Boone Mountain Relief",
              "Asheville Community Distribution Center",
              DeliveryStatus.CREATING_DISPATCH,
              "2026-09-05",
              "Lena Fox",
              "7045551204",
              "NC-LF3456",
              List.of("Pet Food", "Batteries", "Flashlights")),
          new SeedDelivery(
              "Old Fort Recovery Center",
              "Boone Mountain Relief",
              DeliveryStatus.ASSIGNING_DRIVER,
              "2026-09-06",
              null,
              null,
              null,
              List.of("First Aid Kits", "Hygiene Kits")));

  private final Jdbi jdbi;

  LocalDevUserSeeder(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  @Override
  public void run(ApplicationArguments args) {
    jdbi.useTransaction(
        handle -> {
          // insert a seed admin user
          handle
              .createUpdate(
                  "insert into wss_user(phone) values(:phone)"
                      + " on conflict(phone) do update set removed = false")
              .bind("phone", PHONE)
              .execute();
          handle
              .createUpdate("update wss_user set password_bcrypt = :hash where phone = :phone")
              .bind("hash", HashingUtil.bcrypt(PASSWORD))
              .bind("phone", PHONE)
              .execute();
          for (String role : ROLES) {
            handle
                .createUpdate(
                    """
                    insert into wss_user_roles(wss_user_id, wss_user_role_id)
                    values(
                      (select id from wss_user where phone = :phone),
                      (select id from wss_user_role where name = :role)
                    )
                    on conflict(wss_user_id, wss_user_role_id) do nothing
                    """)
                .bind("phone", PHONE)
                .bind("role", role)
                .execute();
          }
        });
    log.warn(
        "LOCAL PROFILE: seeded admin login -> phone={} / password={} (roles {})",
        PHONE,
        PASSWORD,
        ROLES);

    seedRepresentativeData();
  }

  /**
   * Fills an otherwise-empty local database with a representative slice of the domain. Some of the
   * write paths used here are not idempotent -- {@link DeliveryDao#createDelivery} mints a fresh
   * random url key on every call, so re-running would pile up duplicate deliveries -- so the whole
   * slice is inserted only when it is absent. The first seed site standing in as the marker: if it
   * already exists the data has been seeded before and nothing is touched.
   */
  private void seedRepresentativeData() {
    if (siteExists(SITES.get(0).name())) {
      log.warn("LOCAL PROFILE: representative data already present, skipping");
      return;
    }

    ITEMS.forEach(item -> InventoryDao.addNewItem(jdbi, item));

    Map<String, Long> siteIds =
        SITES.stream().collect(Collectors.toMap(SeedSite::name, this::seedSite));
    SITES.forEach(site -> seedStock(site, siteIds.get(site.name())));
    DRIVERS.forEach(this::seedDriver);
    DELIVERIES.forEach(delivery -> seedDelivery(delivery, siteIds));

    log.warn(
        "LOCAL PROFILE: seeded {} sites, {} items, {} drivers, {} deliveries",
        SITES.size(),
        ITEMS.size(),
        DRIVERS.size(),
        DELIVERIES.size());
  }

  private boolean siteExists(String name) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery("select exists(select 1 from site where name = :name)")
                .bind("name", name)
                .mapTo(Boolean.class)
                .one());
  }

  /** Adds the site (which also creates its primary-contact site manager) and returns its id. */
  private long seedSite(SeedSite site) {
    return AddSiteDao.addSite(
        jdbi,
        AddSiteData.builder()
            .siteName(site.name())
            .streetAddress(site.address())
            .city(site.city())
            .state("NC")
            .county(site.county())
            .siteType(site.type())
            .siteHours(site.hours())
            .maxSupplyLoad("Pickup Truck")
            .receivingNotes("Call ahead for large loads.")
            .contactName(site.contactName())
            .contactNumber(site.contactPhone())
            .build());
  }

  private void seedStock(SeedSite site, long siteId) {
    setItemStatuses(siteId, site.urgentlyNeeded(), URGENTLY_NEEDED);
    setItemStatuses(siteId, site.needed(), NEEDED);
    setItemStatuses(siteId, site.available(), AVAILABLE);
    setItemStatuses(siteId, site.oversupply(), OVERSUPPLY);
  }

  private void setItemStatuses(long siteId, List<String> items, String status) {
    items.forEach(item -> InventoryDao.updateSiteItemActive(jdbi, siteId, item, status));
  }

  /**
   * Seeds one driver: grant the DRIVER role (which also creates the wss_user identity), record
   * their display name, insert the driver row via the real upsert, then point it at a vehicle type
   * -- neither the name nor the vehicle type is set by {@link DriverDao#upsert}.
   */
  private void seedDriver(SeedDriver driver) {
    UserRoleService.grantRole(jdbi, driver.phone(), UserRole.DRIVER);
    String canonicalPhone = PhoneNumberUtil.toCanonical(driver.phone());
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate("update wss_user set name = :name where phone = :phone")
                .bind("name", driver.fullName())
                .bind("phone", canonicalPhone)
                .execute());
    DriverDao.upsert(
        jdbi,
        Driver.builder()
            .phone(driver.phone())
            .location(driver.location())
            .licensePlates(driver.licensePlate())
            .availability(driver.availability())
            .comments(driver.comments())
            .can_lift_50lbs(driver.canLift())
            .pallet_capacity(driver.palletCapacity())
            .build());
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    update driver set vehicle_type_id =
                      (select id from vehicle_type where name = :vehicleType)
                    where wss_user_id = (select id from wss_user where phone = :phone)
                    """)
                .bind("vehicleType", driver.vehicleType())
                .bind("phone", canonicalPhone)
                .execute());
  }

  private void seedDelivery(SeedDelivery delivery, Map<String, Long> siteIds) {
    DeliveryDao.createDelivery(
        jdbi,
        DeliveryDao.CreateDeliveryRequest.builder()
            .fromSiteId(siteIds.get(delivery.fromSite()))
            .toSiteId(siteIds.get(delivery.toSite()))
            .deliveryStatus(delivery.status())
            .targetDeliveryDate(delivery.targetDate())
            .dispatcherName(DISPATCHER_NAME)
            .dispatcherNumber(DISPATCHER_NUMBER)
            .driverName(delivery.driverName())
            .driverNumber(delivery.driverPhone())
            .dispatcherNotes(
                delivery.licensePlate() == null
                    ? null
                    : "Driver vehicle: " + delivery.licensePlate())
            .items(delivery.items())
            .build());
  }
}
