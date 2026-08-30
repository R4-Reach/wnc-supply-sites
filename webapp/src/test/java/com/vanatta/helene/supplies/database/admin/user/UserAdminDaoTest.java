package com.vanatta.helene.supplies.database.admin.user;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.auth.UserRole;
import com.vanatta.helene.supplies.database.auth.setup.password.SetupPasswordHelper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserAdminDaoTest {

  private static final String PHONE = "5550001234";

  @BeforeEach
  void cleanDb() {
    SetupPasswordHelper.setup();
  }

  @Test
  void whitelistAddsActiveUserWithName() {
    boolean added = UserAdminDao.whitelistUser(jdbiTest, "555-000-1234", "Alice");

    assertThat(added).isTrue();
    UserAdminDao.UserData user = fetchUser(PHONE);
    assertThat(user.getName()).isEqualTo("Alice");
    assertThat(user.isRemoved()).isFalse();
  }

  @Test
  void whitelistRejectsInvalidPhone() {
    assertThat(UserAdminDao.whitelistUser(jdbiTest, "123", "Bob")).isFalse();
    assertThat(UserAdminDao.fetchAllUsers(jdbiTest)).isEmpty();
  }

  @Test
  void whitelistExistingReactivatesAndKeepsName() {
    UserAdminDao.whitelistUser(jdbiTest, PHONE, "Alice");
    long id = fetchUser(PHONE).getId();
    UserAdminDao.setRemoved(jdbiTest, id, true);

    UserAdminDao.whitelistUser(jdbiTest, PHONE, null);

    UserAdminDao.UserData user = fetchUser(PHONE);
    assertThat(user.isRemoved()).isFalse();
    assertThat(user.getName()).isEqualTo("Alice");
  }

  @Test
  void addAndRemoveRole() {
    UserAdminDao.whitelistUser(jdbiTest, PHONE, null);
    long id = fetchUser(PHONE).getId();

    UserAdminDao.addRole(jdbiTest, id, UserRole.DISPATCHER);
    assertThat(rolesOf(id)).containsExactly(UserRole.DISPATCHER.name());

    // adding again is idempotent
    UserAdminDao.addRole(jdbiTest, id, UserRole.DISPATCHER);
    assertThat(rolesOf(id)).containsExactly(UserRole.DISPATCHER.name());

    UserAdminDao.removeRole(jdbiTest, id, UserRole.DISPATCHER);
    assertThat(rolesOf(id)).isEmpty();
  }

  @Test
  void setNameAndRemoved() {
    UserAdminDao.whitelistUser(jdbiTest, PHONE, "Alice");
    long id = fetchUser(PHONE).getId();

    UserAdminDao.setName(jdbiTest, id, "Alice Smith");
    UserAdminDao.setRemoved(jdbiTest, id, true);

    UserAdminDao.UserData user = fetchUser(PHONE);
    assertThat(user.getName()).isEqualTo("Alice Smith");
    assertThat(user.isRemoved()).isTrue();
  }

  private static UserAdminDao.UserData fetchUser(String phone) {
    return UserAdminDao.fetchAllUsers(jdbiTest).stream()
        .filter(u -> u.getPhone().equals(phone))
        .findFirst()
        .orElseThrow();
  }

  private static Set<String> rolesOf(long userId) {
    List<UserAdminDao.UserRoleRow> rows = UserAdminDao.fetchAllUserRoles(jdbiTest);
    return rows.stream()
        .filter(r -> r.getUserId() == userId)
        .map(UserAdminDao.UserRoleRow::getRoleName)
        .collect(Collectors.toSet());
  }
}
