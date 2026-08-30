package org.r4reach.admin.user;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    Map<Long, Set<String>> rolesByUser = rolesByUser();

    List<Map<String, Object>> userRows =
        users.stream()
            .map(user -> toRow(user, rolesByUser.getOrDefault(user.getId(), Set.of())))
            .toList();

    return Map.of(
        "users",
        userRows,
        "roleHeaders",
        UserRole.assignableRoles().stream().map(Enum::name).toList());
  }

  private Map<Long, Set<String>> rolesByUser() {
    return UserAdminDao.fetchAllUserRoles(jdbi).stream()
        .collect(
            Collectors.groupingBy(
                UserAdminDao.UserRoleRow::getUserId,
                Collectors.mapping(UserAdminDao.UserRoleRow::getRoleName, Collectors.toSet())));
  }

  /** Builds the template model for a single user's table row (also used to swap a row via htmx). */
  private Map<String, Object> buildUserRow(long userId) {
    UserAdminDao.UserData user =
        UserAdminDao.fetchAllUsers(jdbi).stream()
            .filter(u -> u.getId() == userId)
            .findFirst()
            .orElseThrow();
    return toRow(user, rolesByUser().getOrDefault(userId, Set.of()));
  }

  private static Map<String, Object> toRow(UserAdminDao.UserData user, Set<String> held) {
    // Each role cell carries the userId so the row fragment can post without a parent-path lookup.
    List<Map<String, Object>> cells =
        UserRole.assignableRoles().stream()
            .map(
                role ->
                    Map.<String, Object>of(
                        "userId", user.getId(),
                        "role", role.name(),
                        "active", held.contains(role.name())))
            .toList();
    return Map.of(
        "id", user.getId(),
        "phone", user.getPhone(),
        "name", Optional.ofNullable(user.getName()).orElse(""),
        "removed", user.isRemoved(),
        "active", !user.isRemoved(),
        "roles", cells);
  }

  @PostMapping(PATH_ADMIN_USERS + "/whitelist")
  ResponseEntity<String> whitelist(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam String phone,
      @RequestParam(required = false) String name) {
    if (!UserRole.isUserAdmin(roles)) {
      return htmlBadRequest("Not authorized");
    }
    boolean added = UserAdminDao.whitelistUser(jdbi, phone, name);
    if (!added) {
      return htmlBadRequest("Invalid phone number");
    }
    // Re-render the whole page so the new user appears in the grid.
    return ResponseEntity.ok().header("HX-Refresh", "true").build();
  }

  @PostMapping(PATH_ADMIN_USERS + "/set-name")
  ModelAndView setName(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long userId,
      @RequestParam(required = false) String name) {
    if (!UserRole.isUserAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    UserAdminDao.setName(jdbi, userId, name);
    return userRowView(userId);
  }

  @PostMapping(PATH_ADMIN_USERS + "/set-removed")
  ModelAndView setRemoved(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long userId,
      @RequestParam boolean removed) {
    if (!UserRole.isUserAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    UserAdminDao.setRemoved(jdbi, userId, removed);
    return userRowView(userId);
  }

  @PostMapping(PATH_ADMIN_USERS + "/toggle-role")
  ModelAndView toggleRole(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam long userId,
      @RequestParam String role,
      @RequestParam boolean enabled) {
    if (!UserRole.isUserAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    UserRole target;
    try {
      target = UserRole.valueOf(role);
    } catch (IllegalArgumentException | NullPointerException e) {
      log.warn("Ignoring toggle-role for unknown role: {}", role);
      return userRowView(userId);
    }
    if (UserRole.assignableRoles().contains(target)) {
      if (enabled) {
        UserAdminDao.addRole(jdbi, userId, target);
      } else {
        UserAdminDao.removeRole(jdbi, userId, target);
      }
    }
    return userRowView(userId);
  }

  private ModelAndView userRowView(long userId) {
    return new ModelAndView("admin/user-row", buildUserRow(userId));
  }

  private static ResponseEntity<String> htmlBadRequest(String message) {
    // 200 so htmx swaps the message into place (it ignores non-2xx bodies by default).
    return ResponseEntity.ok()
        .header("Content-Type", "text/html; charset=UTF-8")
        .body("<span class=\"errorMessage\">" + message + "</span>");
  }
}
