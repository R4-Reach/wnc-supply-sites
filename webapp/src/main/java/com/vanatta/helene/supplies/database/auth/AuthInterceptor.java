package com.vanatta.helene.supplies.database.auth;

import com.vanatta.helene.supplies.database.browse.routes.BrowseRoutesController;
import com.vanatta.helene.supplies.database.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Intercepts requests, checks if user is accessing /manage URL, if so, checks 'auth' cookie for
 * valid auth.
 */
@Configuration
public class AuthInterceptor implements WebMvcConfigurer {

  private final CookieAuthenticator cookieAuthenticator;
  private final WebhookAuthenticator webhookAuthenticator;

  public AuthInterceptor(
      @Value("${webhook.auth.secret}") String webhookAuthSecret,
      CookieAuthenticator cookieAuthenticator) {
    this.cookieAuthenticator = cookieAuthenticator;
    this.webhookAuthenticator = new WebhookAuthenticator(webhookAuthSecret);
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(new AuthIntercept(cookieAuthenticator, webhookAuthenticator));
  }

  @AllArgsConstructor
  static class AuthIntercept implements HandlerInterceptor {
    private final CookieAuthenticator cookieAuthenticator;
    private final WebhookAuthenticator webhookAuthenticator;

    @Override
    public boolean preHandle(
        HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

      String requestUri = request.getRequestURI();
      if (requestUri.startsWith("/manage/")
          || requestUri.startsWith("/admin")
          || requestUri.startsWith("/driver")
          || requestUri.startsWith(BrowseRoutesController.BROWSE_ROUTES_PATH)) {
        String queryString = request.getQueryString();
        if (queryString != null) {
          requestUri += URLEncoder.encode("?" + queryString, StandardCharsets.UTF_8);
        }

        if (cookieAuthenticator.isAuthenticated(request)) {
          return true;
        } else {
          // auth failed, delete cookie if present
          CookieUtil.deleteCookie(response, "auth");
          response.sendRedirect("/login/login?redirectUri=" + requestUri);
          return false;
        }
      } else if (requestUri.startsWith("/webhook/")) {
        if (webhookAuthenticator.hasCorrectSecret(request)) {
          return true;
        } else {
          response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
          return false;
        }
      } else {
        return true;
      }
    }
  }
}
