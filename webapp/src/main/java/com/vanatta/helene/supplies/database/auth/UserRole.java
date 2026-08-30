package com.vanatta.helene.supplies.database.auth;

import java.util.Arrays;
import java.util.List;

public enum UserRole {
  /** Implicit role held by every logged-in user; never stored in wss_user_roles. */
  AUTHORIZED,
  DRIVER,
  DISPATCHER,
  SITE_MANAGER,
  DATA_ADMIN,
  USER_ADMIN,
  SITE_ADMIN,
  ;

  static boolean hasGodMode(List<UserRole> userRoles) {
    return userRoles.contains(DISPATCHER) || userRoles.contains(DATA_ADMIN);
  }

  public static boolean canManageSites(List<UserRole> roles) {
    return roles.contains(DISPATCHER) || roles.contains(DATA_ADMIN) || roles.contains(SITE_MANAGER);
  }

  public static boolean isUserAdmin(List<UserRole> roles) {
    return roles.contains(USER_ADMIN);
  }

  /** Grants access to the site-wide DB configuration (API keys, Twilio settings) UI. */
  public static boolean isSiteAdmin(List<UserRole> roles) {
    return roles.contains(SITE_ADMIN);
  }

  /**
   * Whether the user may reach the /admin area at all. The landing page and its home-page button
   * are shown to any admin; each sub-page still gates on its own specific role.
   */
  public static boolean canAccessAdminArea(List<UserRole> roles) {
    return isUserAdmin(roles) || isSiteAdmin(roles);
  }

  /**
   * Roles an admin can grant/revoke in the user-management UI (everything but {@link #AUTHORIZED}).
   */
  public static List<UserRole> assignableRoles() {
    return Arrays.stream(values()).filter(r -> r != AUTHORIZED).toList();
  }
}
