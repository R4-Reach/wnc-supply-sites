package org.r4reach.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.r4reach.util.CookieUtil;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Sends an unauthenticated request for a gated URL back to the login page, preserving the original
 * URL (and query string) as {@code redirectUri} so login can return the user there. A stale {@code
 * auth} cookie is cleared on the way out.
 */
@Component
public class LoginRedirectEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws java.io.IOException {

    CookieUtil.deleteCookie(response, "auth");

    String redirectUri = request.getRequestURI();
    String queryString = request.getQueryString();
    if (queryString != null) {
      redirectUri += URLEncoder.encode("?" + queryString, StandardCharsets.UTF_8);
    }

    response.sendRedirect("/login/login?redirectUri=" + redirectUri);
  }
}
