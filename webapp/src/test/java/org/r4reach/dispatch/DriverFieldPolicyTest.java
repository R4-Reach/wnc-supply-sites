package org.r4reach.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.r4reach.auth.UserRole;

class DriverFieldPolicyTest {

  @Test
  void driverAdminMayEditEveryField() {
    for (DriverField field : DriverField.values()) {
      assertThat(DriverFieldPolicy.writable(List.of(UserRole.DRIVER_ADMIN), field))
          .as("DRIVER_ADMIN writable: %s", field)
          .isTrue();
    }
  }

  @Test
  void dispatcherMayEditOnlyNotes() {
    for (DriverField field : DriverField.values()) {
      boolean writable = DriverFieldPolicy.writable(List.of(UserRole.DISPATCHER), field);
      assertThat(writable)
          .as("DISPATCHER writable: %s", field)
          .isEqualTo(field == DriverField.NOTES);
    }
  }

  @Test
  void dispatcherSeesEveryFieldButTheAdminToggles() {
    var policy = DriverFieldPolicy.forRoles(List.of(UserRole.DISPATCHER));
    assertThat(policy.get(DriverField.ACTIVE)).isEqualTo(FieldAccess.HIDDEN);
    assertThat(policy.get(DriverField.BLACK_LISTED)).isEqualTo(FieldAccess.HIDDEN);
    assertThat(policy.get(DriverField.FULL_NAME)).isEqualTo(FieldAccess.READ_ONLY);
    assertThat(policy.get(DriverField.LICENSE_PLATES)).isEqualTo(FieldAccess.READ_ONLY);
    assertThat(policy.get(DriverField.CAN_LIFT_50LBS)).isEqualTo(FieldAccess.READ_ONLY);
    assertThat(policy.get(DriverField.PALLET_CAPACITY)).isEqualTo(FieldAccess.READ_ONLY);
    assertThat(policy.get(DriverField.NOTES)).isEqualTo(FieldAccess.READ_WRITE);
  }

  @Test
  void unprivilegedUserSeesAndWritesNothing() {
    var policy = DriverFieldPolicy.forRoles(List.of(UserRole.SITE_MANAGER));
    for (DriverField field : DriverField.values()) {
      assertThat(policy.get(field)).as("no-access: %s", field).isEqualTo(FieldAccess.HIDDEN);
    }
  }

  @Test
  void driverAdminBeatsAReadOnlyDispatcherRoleWhenBothHeld() {
    // Holding both roles grants the more permissive DRIVER_ADMIN access.
    assertThat(
            DriverFieldPolicy.writable(
                List.of(UserRole.DISPATCHER, UserRole.DRIVER_ADMIN), DriverField.FULL_NAME))
        .isTrue();
  }
}
