package com.vanatta.helene.supplies.database.admin.user;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.auth.UserRole;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.ModelAndView;

/** User-management UI: whitelist phones, edit names, toggle roles, activate/deactivate. */
@Controller
@AllArgsConstructor
@Slf4j
public class UserAdminController {

  public static final String PATH_ADMIN_USERS = "/admin/users";

  private final Jdbi jdbi;

  @GetMapping(PATH_ADMIN_USERS)
  ModelAndView usersPage(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    if (!UserRole.isUserAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    return new ModelAndView("admin/users", buildPageParams());
  }

  private Map<String, Object> buildPageParams() {
    List<UserAdminDao.UserData> users = UserAdminDao.fetchAllUsers(jdbi);
    Map<Long, Set<String>> rolesByUser =
        UserAdminDao.fetchAllUserRoles(jdbi).stream()
            .collect(
                Collectors.groupingBy(
                    UserAdminDao.UserRoleRow::getUserId,
                    Collectors.mapping(UserAdminDao.UserRoleRow::getRoleName, Collectors.toSet())));

    List<UserRole> assignable = UserRole.assignableRoles();

    List<Map<String, Object>> userRows =
        users.stream()
            .map(
                user -> {
                  Set<String> held = rolesByUser.getOrDefault(user.getId(), Set.of());
                  List<Map<String, Object>> cells =
                      assignable.stream()
                          .map(
                              role ->
                                  Map.<String, Object>of(
                                      "role", role.name(),
                                      "active", held.contains(role.name())))
                          .toList();
                  return Map.<String, Object>of(
                      "id", user.getId(),
                      "phone", user.getPhone(),
                      "name", Optional.ofNullable(user.getName()).orElse(""),
                      "removed", user.isRemoved(),
                      "active", !user.isRemoved(),
                      "roles", cells);
                })
            .toList();

    return Map.of("users", userRows, "roleHeaders", assignable.stream().map(Enum::name).toList());
  }

  @PostMapping(PATH_ADMIN_USERS + "/whitelist")
  ResponseEntity<String> whitelist(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestBody Map<String, String> params) {
    if (!UserRole.isUserAdmin(roles)) {
      return forbidden();
    }
    boolean added = UserAdminDao.whitelistUser(jdbi, params.get("phone"), params.get("name"));
    if (!added) {
      return ResponseEntity.badRequest().body("{\"error\": \"Invalid phone number\"}");
    }
    return ok();
  }

  @PostMapping(PATH_ADMIN_USERS + "/set-name")
  ResponseEntity<String> setName(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestBody Map<String, String> params) {
    if (!UserRole.isUserAdmin(roles)) {
      return forbidden();
    }
    UserAdminDao.setName(jdbi, Long.parseLong(params.get("userId")), params.get("name"));
    return ok();
  }

  @PostMapping(PATH_ADMIN_USERS + "/set-removed")
  ResponseEntity<String> setRemoved(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestBody Map<String, String> params) {
    if (!UserRole.isUserAdmin(roles)) {
      return forbidden();
    }
    UserAdminDao.setRemoved(
        jdbi, Long.parseLong(params.get("userId")), Boolean.parseBoolean(params.get("removed")));
    return ok();
  }

  @PostMapping(PATH_ADMIN_USERS + "/toggle-role")
  ResponseEntity<String> toggleRole(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestBody Map<String, String> params) {
    if (!UserRole.isUserAdmin(roles)) {
      return forbidden();
    }
    UserRole role;
    try {
      role = UserRole.valueOf(params.get("role"));
    } catch (IllegalArgumentException | NullPointerException e) {
      return ResponseEntity.badRequest().body("{\"error\": \"Unknown role\"}");
    }
    if (!UserRole.assignableRoles().contains(role)) {
      return ResponseEntity.badRequest().body("{\"error\": \"Role is not assignable\"}");
    }
    long userId = Long.parseLong(params.get("userId"));
    if (Boolean.parseBoolean(params.get("enabled"))) {
      UserAdminDao.addRole(jdbi, userId, role);
    } else {
      UserAdminDao.removeRole(jdbi, userId, role);
    }
    return ok();
  }

  private static ResponseEntity<String> ok() {
    return ResponseEntity.ok("{\"message\": \"Saved\"}");
  }

  private static ResponseEntity<String> forbidden() {
    return ResponseEntity.status(403).body("{\"error\": \"Not authorized\"}");
  }
}
