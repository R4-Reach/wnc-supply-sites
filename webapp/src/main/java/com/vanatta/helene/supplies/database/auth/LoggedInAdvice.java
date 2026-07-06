package com.vanatta.helene.supplies.database.auth;

import com.vanatta.helene.supplies.database.util.CookieUtil;
import com.vanatta.helene.supplies.database.util.HashingUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@AllArgsConstructor
public class LoggedInAdvice {

  public static final String USER_ROLES = "userRoles";
  public static final String USER_PHONE = "userPhone";
  public static final String USER_SITES = "userSites";
  public static final String LOGGED_IN = "loggedIn";

  private final CookieAuthenticator cookieAuthenticator;
  private final Jdbi jdbi;

  @ModelAttribute(LOGGED_IN)
  public boolean loggedIn(HttpServletRequest request) {
    return cookieAuthenticator.isAuthenticated(request);
  }

  @ModelAttribute(USER_PHONE)
  public String userPhone(HttpServletRequest request) {
    String auth = CookieUtil.readAuthCookie(request).orElse(null);
    if (auth == null) {
      return null;
    } else {
      return fetchPhoneNumberFromAuth(jdbi, auth).orElse(null);
    }
  }

  @ModelAttribute(USER_SITES)
  public List<Long> userSites(
      @ModelAttribute(USER_ROLES) List<UserRole> roles, HttpServletRequest request) {
    String auth = CookieUtil.readAuthCookie(request).orElse(null);
    if (auth == null) {
      return List.of();
    } else {
      return computeUserSites(jdbi, auth, roles);
    }
  }

  public static List<Long> computeUserSites(Jdbi jdbi, String auth, List<UserRole> roles) {
    String number = fetchPhoneNumberFromAuth(jdbi, auth).orElse(null);
    if (number == null) {
      return List.of();
    } else {
      if (UserRole.hasGodMode(roles)) {
        // get list of all sites
        return jdbi
            .withHandle(h -> h.createQuery("select id from site").mapTo(Long.class).list())
            .stream()
            .sorted()
            .toList();
      } else {
        // get list of sites that user is primary or secondary
        return jdbi
            .withHandle(
                h ->
                    h.createQuery(
                            """
                                select id siteId
                                from site
                                where regexp_replace(contact_number, '[^0-9]+', '', 'g') = :number
                                union
                                select id siteId
                                from site
                                where regexp_replace(og_contact_number, '[^0-9]+', '', 'g') = :number
                                union
                                select site_id siteId
                                from additional_site_manager
                                where regexp_replace(phone, '[^0-9]+', '', 'g') = :number;
                                """)
                        .bind("number", number)
                        .mapTo(Long.class)
                        .list())
            .stream()
            .sorted()
            .distinct()
            .toList();
      }
    }
  }

  @ModelAttribute(LoggedInAdvice.USER_ROLES)
  public List<UserRole> userRoles(HttpServletRequest request) {
    String auth = CookieUtil.readAuthCookie(request).orElse(null);
    if (auth == null) {
      return List.of();
    } else {
      return computeUserRoles(jdbi, auth);
    }
  }

  static List<UserRole> computeUserRoles(Jdbi jdbi, String auth) {
    String userPhone = fetchPhoneNumberFromAuth(jdbi, auth).orElse(null);
    if (userPhone == null) {
      return List.of();
    }

    List<UserRole> userRoles = new ArrayList<>();
    userRoles.add(UserRole.AUTHORIZED);

    // check if they have dispatcher or data admin from wss_user_role
    List<UserRole> whiteListedRoles =
        jdbi
            .withHandle(
                h ->
                    h.createQuery(
                            """
      select
        role.name
      from wss_user wu
      join wss_user_roles wur on wur.wss_user_id = wu.id
      join wss_user_role role on role.id = wur.wss_user_role_id
      where wu.phone = :phone
      """)
                        .bind("phone", userPhone)
                        .mapTo(String.class)
                        .list())
            .stream()
            .map(UserRole::valueOf)
            .toList();

    if (whiteListedRoles.contains(UserRole.DISPATCHER)) {
      userRoles.add(UserRole.DISPATCHER);
    }
    if (whiteListedRoles.contains(UserRole.DATA_ADMIN)) {
      userRoles.add(UserRole.DATA_ADMIN);
    }
    if (whiteListedRoles.contains(UserRole.SITE_MANAGER)) {
      userRoles.add(UserRole.SITE_MANAGER);
    }

    // check if they are a driver (exist in the driver table)
    boolean isDriver =
        jdbi.withHandle(
                h ->
                    h.createQuery(
                            """
                            select 1 from driver where regexp_replace(phone, '[^0-9]+', '', 'g') = :phone
                          """)
                        .bind("phone", userPhone)
                        .mapTo(Long.class)
                        .findOne())
            .isPresent();
    if (isDriver) {
      userRoles.add(UserRole.DRIVER);
    }

    // check if they are a site manager (secondary or primary)
    boolean isSiteAdmin =
        jdbi.withHandle(
                h ->
                    h.createQuery(
                            """
                          select 1 from site where regexp_replace(contact_number, '[^0-9]+', '', 'g')  = :phone
                          union
                          select 1 from site where regexp_replace(og_contact_number, '[^0-9]+', '', 'g')  = :phone
                          union
                          select 1 from additional_site_manager where regexp_replace(phone, '[^0-9]+', '', 'g') = :phone
                        """)
                        .bind("phone", userPhone)
                        .mapTo(Long.class)
                        .findFirst())
            .isPresent();
    if (isSiteAdmin) {
      userRoles.add(UserRole.SITE_MANAGER);
    }

    return userRoles;
  }

  static Optional<String> fetchPhoneNumberFromAuth(Jdbi jdbi, String authKey) {
    return jdbi.withHandle(
        h ->
            h.createQuery(
                    """
                  select
                    wu.phone
                  from wss_user_auth_key wuak
                  join wss_user wu on wuak.wss_user_id = wu.id
                  where wu.removed = false and wuak.token_sha256 = :hashedToken
                  """)
                .bind("hashedToken", HashingUtil.sha256(authKey))
                .mapTo(String.class)
                .findOne());
  }
}
