package org.r4reach.admin.user;

import java.util.Comparator;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.UserRole;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.PiiCrypto;

/** CRUD over wss_user and its roles, backing the user-management UI. */
public class UserAdminDao {

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserData {
    long id;
    String publicId;
    String phone;
    String name;
    boolean removed;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class UserRoleRow {
    long userId;
    String roleName;
  }

  // name/phone are encrypted, so the old SQL `order by removed, lower(name) nulls last, phone`
  // can't run in the query; sort the decrypted rows here to match it.
  private static final Comparator<UserData> USER_ORDER =
      Comparator.comparing(UserData::isRemoved)
          .thenComparing(u -> u.getName() == null)
          .thenComparing(u -> u.getName() == null ? "" : u.getName().toLowerCase())
          .thenComparing(UserData::getPhone);

  public static List<UserData> fetchAllUsers(Jdbi jdbi) {
    return jdbi
        .withHandle(
            handle ->
                handle
                    .createQuery(
                        """
                        select id, public_id publicId, phone_enc phone, name_enc name, removed
                        from wss_user
                        """)
                    .mapToBean(UserData.class)
                    .list())
        .stream()
        .map(UserAdminDao::decryptIdentity)
        .sorted(USER_ORDER)
        .toList();
  }

  // phone/name are fetched as phone_enc/name_enc ciphertext; decrypt in place.
  private static UserData decryptIdentity(UserData user) {
    user.setPhone(PiiCrypto.decrypt(user.getPhone()));
    user.setName(PiiCrypto.decrypt(user.getName()));
    return user;
  }

  public static List<UserRoleRow> fetchAllUserRoles(Jdbi jdbi) {
    return jdbi.withHandle(
        handle ->
            handle
                .createQuery(
                    """
                    select wur.wss_user_id userId, role.name roleName
                    from wss_user_roles wur
                    join wss_user_role role on role.id = wur.wss_user_role_id
                    """)
                .mapToBean(UserRoleRow.class)
                .list());
  }

  /**
   * Adds a phone to the whitelist, or reactivates it if already present. Returns false for an
   * invalid (non 10-digit) phone number.
   */
  public static boolean whitelistUser(Jdbi jdbi, String phoneInput, String name) {
    if (!PhoneNumberUtil.isValid(phoneInput)) {
      return false;
    }
    String phone = PhoneNumberUtil.toCanonical(phoneInput);
    String trimmedName = name == null || name.isBlank() ? null : name.trim();
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into wss_user(phone_enc, phone_hmac, name_enc)
                    values (:phoneEnc, :phoneHmac, :nameEnc)
                    on conflict(phone_hmac) do update
                      set removed = false,
                          name_enc = coalesce(:nameEnc, wss_user.name_enc)
                    """)
                .bind("phoneEnc", PiiCrypto.encrypt(phone))
                .bind("phoneHmac", PiiCrypto.blindIndex(phone))
                .bind("nameEnc", PiiCrypto.encrypt(trimmedName))
                .execute());
    return true;
  }

  public static void setName(Jdbi jdbi, long userId, String name) {
    String trimmedName = name == null || name.isBlank() ? null : name.trim();
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate("update wss_user set name_enc = :nameEnc where id = :id")
                .bind("nameEnc", PiiCrypto.encrypt(trimmedName))
                .bind("id", userId)
                .execute());
  }

  public static void setRemoved(Jdbi jdbi, long userId, boolean removed) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate("update wss_user set removed = :removed where id = :id")
                .bind("removed", removed)
                .bind("id", userId)
                .execute());
  }

  public static void addRole(Jdbi jdbi, long userId, UserRole role) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    insert into wss_user_roles(wss_user_id, wss_user_role_id)
                    values(:userId, (select id from wss_user_role where name = :role))
                    on conflict (wss_user_id, wss_user_role_id) do nothing
                    """)
                .bind("userId", userId)
                .bind("role", role.name())
                .execute());
  }

  public static void removeRole(Jdbi jdbi, long userId, UserRole role) {
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(
                    """
                    delete from wss_user_roles
                    where wss_user_id = :userId
                      and wss_user_role_id = (select id from wss_user_role where name = :role)
                    """)
                .bind("userId", userId)
                .bind("role", role.name())
                .execute());
  }
}
