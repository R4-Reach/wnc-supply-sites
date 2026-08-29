package com.vanatta.helene.supplies.database;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.auth.UserRole;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/** Controller for the various HTML pages that are relatively 'simple' and are mostly static. */
@Controller
@AllArgsConstructor
public class SimpleHtmlController {

  private final Jdbi jdbi;
  private final Environment environment;

  private static final String CONTACT_US_LINK = "https://form.jotform.com/243608573773062";

  /** Cookie that opts a browser in to in-development ("beta") features. */
  private static final String BETA_VOLUNTEER_COOKIE = "beta-volunteer";

  /**
   * All in-development ("beta") feature cookies. Source of truth for the local-only "Enable Beta"
   * button, which sets each of these to "true"; add a new beta cookie here to have that button turn
   * it on too.
   */
  private static final List<String> BETA_COOKIES = List.of(BETA_VOLUNTEER_COOKIE);

  @GetMapping("/")
  public ModelAndView home(
      HttpServletRequest request, @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    Map<String, Object> params = new HashMap<>();
    params.put("isAuthenticated", roles.contains(UserRole.AUTHORIZED));
    params.put("isDriver", roles.contains(UserRole.DRIVER));
    params.put("canManageSites", UserRole.canManageSites(roles));
    params.put("betaVolunteer", hasCookie(request, BETA_VOLUNTEER_COOKIE, "true"));
    params.put("siteDescription", "Disaster Relief");
    params.put("contactUsLink", CONTACT_US_LINK);
    params.put("localProfile", environment.matchesProfiles("local"));
    params.put("enableBetaJs", enableBetaJs());
    return new ModelAndView("home/home", params);
  }

  /**
   * JavaScript (for a button's onclick) that sets every {@link #BETA_COOKIES} cookie and reloads.
   */
  private static String enableBetaJs() {
    return BETA_COOKIES.stream()
            .map(cookie -> String.format("document.cookie='%s=true;path=/';", cookie))
            .collect(Collectors.joining())
        + "location.reload();";
  }

  /** Returns true if the request carries a cookie with the given name and value. */
  private static boolean hasCookie(HttpServletRequest request, String name, String value) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return false;
    }
    return Arrays.stream(cookies)
        .anyMatch(cookie -> name.equals(cookie.getName()) && value.equals(cookie.getValue()));
  }

  @GetMapping("/log-out")
  public RedirectView logout(HttpServletResponse response) {
    Cookie cookie = new Cookie("auth", null);
    cookie.setMaxAge(0);
    cookie.setSecure(true);
    cookie.setHttpOnly(true);
    response.addCookie(cookie);
    return new RedirectView("/");
  }

  @GetMapping("/registration/")
  ModelAndView showRegistrationPage(HttpServletRequest request) {

    Map<String, Object> params = new HashMap<>();
    params.put("contactUsLink", CONTACT_US_LINK);
    return new ModelAndView("registration/registration", params);
  }
}
