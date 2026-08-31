package org.r4reach.auth.user.whitelist;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.UserRole;
import org.r4reach.util.PhoneNumberUtil;
import org.r4reach.util.PiiCrypto;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Webhook that receives JSON payloads that adds users to the registration white list. Only users on
 * this white list can register & create a password. Without being on the white list, we will not
 * send them a SMS code to register.
 */
@Controller
@Slf4j
@AllArgsConstructor
public class UserWhiteListWebhook {
  private final Jdbi jdbi;

  @Builder(toBuilder = true)
  @Value
  public static class UserWhiteListRequest {
    String phoneNumber;
    List<String> roles;
    Boolean removed;

    public String getPhoneNumber() {
      return PhoneNumberUtil.toCanonical(phoneNumber);
    }

    static UserWhiteListRequest parse(String input) {
      return new Gson().fromJson(input, UserWhiteListRequest.class);
    }

    boolean isValid() {
      return !roles.isEmpty()
          && new HashSet<>(Arrays.stream(UserRole.values()).map(Enum::name).toList())
              .containsAll(roles)
          && UserRole.values().length >= roles.size()
          && PhoneNumberUtil.isValid(phoneNumber);
    }

    boolean getRemoved() {
      return Optional.ofNullable(removed).orElse(Boolean.FALSE);
    }
  }

  @PostMapping("/webhook/whitelist-user")
  ResponseEntity<String> whiteListUser(@RequestBody String input) {
    log.info("white list user request received: {}", input);

    UserWhiteListRequest request = UserWhiteListRequest.parse(input);
    if (!request.isValid()) {
      return ResponseEntity.badRequest().build();
    }

    updateUserAndRoles(jdbi, request);

    return ResponseEntity.ok().build();
  }

  public static void updateUserAndRoles(Jdbi jdbi, UserWhiteListRequest request) {
    upsertUser(jdbi, request);
    updateRoles(jdbi, request.getPhoneNumber(), request.getRoles());
  }

  /** Adds a user to wss_user table, does *not* update roles. */
  public static void upsertUser(Jdbi jdbi, UserWhiteListRequest request) {
    String phone = request.getPhoneNumber();
    String script =
        """
        insert into wss_user(phone_enc, phone_hmac)
        values (:phoneEnc, :phoneHmac)
        on conflict(phone_hmac) do update set removed = :removed
        """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(script)
                .bind("phoneEnc", PiiCrypto.encrypt(phone))
                .bind("phoneHmac", PiiCrypto.blindIndex(phone))
                .bind("removed", request.getRemoved())
                .execute());
  }

  private static void updateRoles(Jdbi jdbi, String phoneNumber, List<String> roles) {
    String removeOldRoles =
        """
      delete from wss_user_roles where wss_user_id = (select id from wss_user where phone_hmac = :phoneHmac);
    """;
    jdbi.withHandle(
        handle ->
            handle
                .createUpdate(removeOldRoles)
                .bind("phoneHmac", PiiCrypto.blindIndex(phoneNumber))
                .execute());

    for (String role : roles) {
      String insert =
          """
      insert into wss_user_roles(wss_user_id, wss_user_role_id)
      values(
        (select id from wss_user where phone_hmac = :phoneHmac),
        (select id from wss_user_role where name = :role)

      )
      """;
      jdbi.withHandle(
          handle ->
              handle
                  .createUpdate(insert)
                  .bind("phoneHmac", PiiCrypto.blindIndex(phoneNumber))
                  .bind("role", role)
                  .execute());
    }
  }

  @PostMapping("/webhook/whitelist-update")
  ResponseEntity<String> updateUser(@RequestBody String input) {
    log.info("update white list user request received: {}", input);

    UserWhiteListRequest request = UserWhiteListRequest.parse(input);
    if (!request.isValid()) {
      return ResponseEntity.badRequest().build();
    }

    updateUserAndRoles(
        jdbi, request.getRemoved() ? request.toBuilder().roles(List.of()).build() : request);
    return ResponseEntity.ok().build();
  }
}
