package org.r4reach.vehicletype;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.auth.setup.password.SetupPasswordHelper;
import org.r4reach.dispatch.DispatchDao;

class VehicleTypeDaoTest {

  @BeforeEach
  void cleanDb() {
    SetupPasswordHelper.setup();
    jdbiTest.withHandle(handle -> handle.createUpdate("delete from vehicle_type").execute());
  }

  @Test
  void addInsertsNewType() {
    assertThat(VehicleTypeDao.add(jdbiTest, "Van")).isTrue();
    assertThat(names()).containsExactly("Van");
  }

  @Test
  void addTrimsAndRejectsBlank() {
    assertThat(VehicleTypeDao.add(jdbiTest, "   ")).isFalse();
    assertThat(VehicleTypeDao.add(jdbiTest, null)).isFalse();
    assertThat(VehicleTypeDao.add(jdbiTest, "  SUV  ")).isTrue();
    assertThat(names()).containsExactly("SUV");
  }

  @Test
  void addDuplicateIsNoOp() {
    assertThat(VehicleTypeDao.add(jdbiTest, "Car")).isTrue();
    assertThat(VehicleTypeDao.add(jdbiTest, "Car")).isFalse();
    assertThat(names()).containsExactly("Car");
  }

  @Test
  void removeDeletesUnusedType() {
    VehicleTypeDao.add(jdbiTest, "Trailer");
    long id = VehicleTypeDao.fetchAll(jdbiTest).get(0).getId();

    assertThat(VehicleTypeDao.remove(jdbiTest, id)).isTrue();
    assertThat(names()).isEmpty();
  }

  @Test
  void removeBlockedWhileDriverUsesType() {
    VehicleTypeDao.add(jdbiTest, "Van");
    long typeId = VehicleTypeDao.fetchAll(jdbiTest).get(0).getId();
    DispatchDao.createDriver(jdbiTest, "555-000-1234", "Alice");
    long wssId = DispatchDao.fetchAll(jdbiTest).get(0).getWssId();
    DispatchDao.setVehicleType(jdbiTest, wssId, (int) typeId);

    assertThat(VehicleTypeDao.countDriversUsing(jdbiTest, typeId)).isEqualTo(1);
    assertThat(VehicleTypeDao.remove(jdbiTest, typeId)).isFalse();
    assertThat(names()).containsExactly("Van");
  }

  private static List<String> names() {
    return VehicleTypeDao.fetchAll(jdbiTest).stream().map(VehicleType::getName).toList();
  }
}
