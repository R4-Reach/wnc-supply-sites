package org.r4reach.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Application security. Login-gating that used to live in a hand-rolled URL-prefix interceptor now
 * runs through Spring Security's filter chain: {@link #GATED_PATHS} require an authenticated
 * request, everything else is public. Authentication itself still rides the app's opaque {@code
 * auth} cookie — {@link AuthTokenFilter} translates that cookie into the security context.
 *
 * <p>Fine-grained authorization (role and per-site checks) is deliberately <em>not</em> expressed
 * here; it stays inline in the controllers via {@link LoggedInAdvice} model attributes so its
 * behavior and unit tests are unchanged.
 *
 * <p>CSRF is disabled, matching the app's prior behavior (forms carry no CSRF token). Enabling it
 * is a separate effort because the Mustache views have no CSRF-token integration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  /**
   * URL prefixes that require login. Kept faithful to the previous interceptor: anything under
   * {@code /manage/}, and the {@code /admin}, {@code /dispatch}, and {@code /driver} areas.
   */
  static final String[] GATED_PATHS = {
    "/manage/**", "/admin", "/admin/**", "/dispatch", "/dispatch/**", "/driver", "/driver/**"
  };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, AuthTokenFilter authTokenFilter, LoginRedirectEntryPoint entryPoint)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            auth -> auth.requestMatchers(GATED_PATHS).authenticated().anyRequest().permitAll())
        .addFilterBefore(authTokenFilter, AnonymousAuthenticationFilter.class)
        .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
        .build();
  }
}
