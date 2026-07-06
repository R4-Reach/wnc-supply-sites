package com.vanatta.helene.supplies.database.auth;

import com.vanatta.helene.supplies.database.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.jdbi.v3.core.Jdbi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stores & generates valid auth keys, auth key needs to be present as a cookie value, and is
 * inspected when accessing /manage URLs to validate user is logged in.
 */
@Component
public class CookieAuthenticator {

  private final Jdbi jdbi;

  @Autowired
  public CookieAuthenticator(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  public boolean isAuthenticated(HttpServletRequest request) {
    return CookieUtil.readAuthCookie(request)
        .map(auth -> LoginDao.isLoggedIn(jdbi, auth))
        .orElse(false);
  }
}
