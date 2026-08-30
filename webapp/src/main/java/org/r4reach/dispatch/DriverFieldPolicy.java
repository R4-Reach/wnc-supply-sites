package org.r4reach.dispatch;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.r4reach.auth.UserRole;

/**
 * Single source of truth for who may see and edit each field on the dispatch drivers page. The same
 * policy drives both the rendered controls (editable / read-only / hidden) and the write endpoints,
 * so the two can't drift apart.
 *
 * <p>The endpoint checks are the real security boundary — a caller can craft a POST regardless of
 * what the page rendered — so every mutating handler consults {@link #writable}. The render side is
 * only a convenience: it spares users controls they would be refused.
 */
public final class DriverFieldPolicy {

  private DriverFieldPolicy() {}

  public static Map<DriverField, FieldAccess> forRoles(List<UserRole> roles) {
    EnumMap<DriverField, FieldAccess> policy = new EnumMap<>(DriverField.class);

    if (UserRole.canManageDrivers(roles)) {
      // DRIVER_ADMIN edits every field.
      fill(policy, FieldAccess.READ_WRITE);
      return policy;
    }
    if (roles.contains(UserRole.DISPATCHER)) {
      // DISPATCHER reads every field, edits only notes, and doesn't see the admin-only active or
      // blacklist toggles.
      fill(policy, FieldAccess.READ_ONLY);
      policy.put(DriverField.NOTES, FieldAccess.READ_WRITE);
      policy.put(DriverField.ACTIVE, FieldAccess.HIDDEN);
      policy.put(DriverField.BLACK_LISTED, FieldAccess.HIDDEN);
      return policy;
    }
    // Anyone else can't reach the page; nothing is visible or writable.
    fill(policy, FieldAccess.HIDDEN);
    return policy;
  }

  public static boolean writable(List<UserRole> roles, DriverField field) {
    return forRoles(roles).get(field).isWritable();
  }

  private static void fill(Map<DriverField, FieldAccess> policy, FieldAccess access) {
    for (DriverField field : DriverField.values()) {
      policy.put(field, access);
    }
  }
}
