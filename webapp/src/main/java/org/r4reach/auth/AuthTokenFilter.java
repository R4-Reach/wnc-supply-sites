package org.r4reach.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bridges the app's opaque {@code auth} cookie into Spring Security: when the cookie holds a valid
 * login token, an authenticated {@link Authentication} is placed in the {@link
 * SecurityContextHolder security context} so {@code SecurityConfig}'s URL rules can gate access.
 * Fine-grained role and per-site checks remain inline in the controllers (via {@link
 * LoggedInAdvice} model attributes); this filter only establishes <em>whether</em> the request is
 * logged in.
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

  /** Marker authority granted to every logged-in request; role checks stay in the controllers. */
  static final String ROLE_AUTHORIZED = "ROLE_AUTHORIZED";

  private final CookieAuthenticator cookieAuthenticator;

  public AuthTokenFilter(CookieAuthenticator cookieAuthenticator) {
    this.cookieAuthenticator = cookieAuthenticator;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    Authentication existing = SecurityContextHolder.getContext().getAuthentication();
    boolean unauthenticated =
        existing == null
            || !existing.isAuthenticated()
            || existing instanceof AnonymousAuthenticationToken;

    if (unauthenticated && cookieAuthenticator.isAuthenticated(request)) {
      var authentication =
          new PreAuthenticatedAuthenticationToken(
              "wss-user", "n/a", List.of(new SimpleGrantedAuthority(ROLE_AUTHORIZED)));
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    filterChain.doFilter(request, response);
  }
}
