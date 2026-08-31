package org.r4reach.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Optional;

public class CookieUtil {

  public static void setCookie(HttpServletResponse response, String name, String value) {
    Cookie cookie = new Cookie(name, value);
    cookie.setMaxAge(14 * 24 * 60 * 60); // expires in 14 days
    cookie.setSecure(true);
    cookie.setHttpOnly(true);
    // SameSite=Lax so the auth cookie isn't sent on cross-site POSTs, blunting CSRF while full
    // Spring Security CSRF tokens remain deferred. Lax still allows top-level GET navigations.
    cookie.setAttribute("SameSite", "Lax");
    response.addCookie(cookie);
  }

  public static void deleteCookie(HttpServletResponse response, String cookieName) {
    if (response == null) {
      return;
    }
    Cookie cookie = new Cookie(cookieName, null);
    cookie.setMaxAge(0);
    cookie.setSecure(true);
    cookie.setHttpOnly(true);
    cookie.setAttribute("SameSite", "Lax");
    response.addCookie(cookie);
  }

  public static Optional<String> readAuthCookie(HttpServletRequest request) {
    return readCookieValue(request, "auth");
  }

  /**
   * Warnings, do not use this cookie value for anything security related! Just convenience. The
   * user can spoof cookies and set any value they want.
   */
  public static Optional<String> readUserCookie(HttpServletRequest request) {
    return readCookieValue(request, "user");
  }

  public static Optional<String> readCookieValue(HttpServletRequest request, String cookieName) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }

    return Arrays.stream(cookies)
        .filter(c -> c.getName().equals(cookieName))
        .findAny()
        .map(Cookie::getValue)
        .filter(v -> !v.isBlank());
  }
}
