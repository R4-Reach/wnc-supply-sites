package org.r4reach.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.auth.setup.password.SetupPasswordHelper;
import org.r4reach.vehicletype.VehicleType;
import org.r4reach.vehicletype.VehicleTypeDao;

class DispatchDaoTest {

  // Canonical 11-digit form (see PhoneNumberUtil.toCanonical).
  private static final String PHONE = "15550001234";

  @BeforeEach
  void cleanDb() {
    SetupPasswordHelper.setup();
    jdbiTest.withHandle(handle -> handle.createUpdate("delete from vehicle_type").execute());
  }

  @Test
  void createDriverMintsUserAndDriverRow() {
    assertThat(DispatchDao.createDriver(jdbiTest, "555-000-1234", "Alice")).isTrue();

    List<DispatchDao.DriverRow> rows = DispatchDao.fetchAll(jdbiTest);
    assertThat(rows).hasSize(1);
    DispatchDao.DriverRow row = rows.get(0);
    assertThat(row.getFullName()).isEqualTo("Alice");
    assertThat(row.getPhone()).isEqualTo(PHONE);
    assertThat(row.isActive()).isTrue();
    assertThat(row.getVehicleTypeId()).isNull();
  }

  @Test
  void createDriverRejectsInvalidPhone() {
    assertThat(DispatchDao.createDriver(jdbiTest, "123", "Bob")).isFalse();
    assertThat(DispatchDao.fetchAll(jdbiTest)).isEmpty();
  }

  @Test
  void createDriverIsIdempotentForSamePhone() {
    DispatchDao.createDriver(jdbiTest, PHONE, "Alice");
    DispatchDao.createDriver(jdbiTest, PHONE, "Alice");
    assertThat(DispatchDao.fetchAll(jdbiTest)).hasSize(1);
  }

  @Test
  void editsDriverFields() {
    VehicleTypeDao.add(jdbiTest, "Van");
    VehicleType van = VehicleTypeDao.fetchAll(jdbiTest).get(0);
    DispatchDao.createDriver(jdbiTest, PHONE, "Alice");
    long wssId = DispatchDao.fetchAll(jdbiTest).get(0).getWssId();

    DispatchDao.setLocation(jdbiTest, wssId, "Asheville");
    DispatchDao.setAvailability(jdbiTest, wssId, "Weekends");
    DispatchDao.setNotes(jdbiTest, wssId, "Prefers short routes");
    DispatchDao.setVehicleType(jdbiTest, wssId, (int) van.getId());
    DispatchDao.setActive(jdbiTest, wssId, false);
    DispatchDao.setLicensePlates(jdbiTest, wssId, "ABC-1234");
    DispatchDao.setCanLift50lbs(jdbiTest, wssId, true);
    DispatchDao.setPalletCapacity(jdbiTest, wssId, 5);

    DispatchDao.DriverRow row = DispatchDao.fetch(jdbiTest, wssId).orElseThrow();
    assertThat(row.getLocation()).isEqualTo("Asheville");
    assertThat(row.getAvailability()).isEqualTo("Weekends");
    assertThat(row.getComments()).isEqualTo("Prefers short routes");
    assertThat(row.getVehicleTypeName()).isEqualTo("Van");
    assertThat(row.isActive()).isFalse();
    assertThat(row.getLicensePlates()).isEqualTo("ABC-1234");
    assertThat(row.isCanLift50lbs()).isTrue();
    assertThat(row.getPalletCapacity()).isEqualTo(5);
  }

  @Test
  void newDriverHasEmptyOperationalDefaults() {
    DispatchDao.createDriver(jdbiTest, PHONE, "Alice");
    DispatchDao.DriverRow row = DispatchDao.fetchAll(jdbiTest).get(0);

    assertThat(row.getLicensePlates()).isEmpty();
    assertThat(row.isCanLift50lbs()).isFalse();
    assertThat(row.getPalletCapacity()).isZero();
  }

  @Test
  void setBlackListedFlagsAndClearsDriver() {
    DispatchDao.createDriver(jdbiTest, PHONE, "Alice");
    long wssId = DispatchDao.fetchAll(jdbiTest).get(0).getWssId();
    // A freshly created driver is not blacklisted.
    assertThat(DispatchDao.fetch(jdbiTest, wssId).orElseThrow().isBlackListed()).isFalse();

    DispatchDao.setBlackListed(jdbiTest, wssId, true);
    assertThat(DispatchDao.fetch(jdbiTest, wssId).orElseThrow().isBlackListed()).isTrue();

    DispatchDao.setBlackListed(jdbiTest, wssId, false);
    assertThat(DispatchDao.fetch(jdbiTest, wssId).orElseThrow().isBlackListed()).isFalse();
  }

  @Test
  void clearingVehicleTypeSetsItNull() {
    VehicleTypeDao.add(jdbiTest, "Van");
    VehicleType van = VehicleTypeDao.fetchAll(jdbiTest).get(0);
    DispatchDao.createDriver(jdbiTest, PHONE, "Alice");
    long wssId = DispatchDao.fetchAll(jdbiTest).get(0).getWssId();
    DispatchDao.setVehicleType(jdbiTest, wssId, (int) van.getId());

    DispatchDao.setVehicleType(jdbiTest, wssId, null);

    assertThat(DispatchDao.fetch(jdbiTest, wssId).orElseThrow().getVehicleTypeId()).isNull();
  }

  @Test
  void setFullNameUpdatesIdentity() {
    DispatchDao.createDriver(jdbiTest, PHONE, "Alice");
    DispatchDao.DriverRow row = DispatchDao.fetchAll(jdbiTest).get(0);

    DispatchDao.setFullName(jdbiTest, row.getWssUserId(), "Alice Smith");

    assertThat(DispatchDao.fetch(jdbiTest, row.getWssId()).orElseThrow().getFullName())
        .isEqualTo("Alice Smith");
  }

  @Test
  void setPhoneRejectsNumberHeldByAnother() {
    DispatchDao.createDriver(jdbiTest, "555-000-1111", "Alice");
    DispatchDao.createDriver(jdbiTest, "555-000-2222", "Bob");
    DispatchDao.DriverRow alice =
        DispatchDao.fetchAll(jdbiTest).stream()
            .filter(r -> "Alice".equals(r.getFullName()))
            .findFirst()
            .orElseThrow();

    // Bob already holds this number: rejected, Alice keeps hers.
    assertThat(DispatchDao.setPhone(jdbiTest, alice.getWssUserId(), "555-000-2222")).isFalse();
    assertThat(DispatchDao.fetch(jdbiTest, alice.getWssId()).orElseThrow().getPhone())
        .isEqualTo("15550001111");

    // A free number is accepted.
    assertThat(DispatchDao.setPhone(jdbiTest, alice.getWssUserId(), "555-000-3333")).isTrue();
    assertThat(DispatchDao.fetch(jdbiTest, alice.getWssId()).orElseThrow().getPhone())
        .isEqualTo("15550003333");
  }

  @Test
  void setPhoneRejectsInvalidNumber() {
    DispatchDao.createDriver(jdbiTest, PHONE, "Alice");
    DispatchDao.DriverRow row = DispatchDao.fetchAll(jdbiTest).get(0);

    assertThat(DispatchDao.setPhone(jdbiTest, row.getWssUserId(), "123")).isFalse();
    assertThat(DispatchDao.fetch(jdbiTest, row.getWssId()).orElseThrow().getPhone())
        .isEqualTo(PHONE);
  }
}
