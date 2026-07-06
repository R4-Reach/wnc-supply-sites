package com.vanatta.helene.supplies.database;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.auth.UserRole;
import com.vanatta.helene.supplies.database.data.HostNameLookup;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.jdbi.v3.core.Jdbi;
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
  private final HostNameLookup hostNameLookup;

  private static final String CONTACT_US_LINK = "https://form.jotform.com/243608573773062";

  @GetMapping("/")
  public ModelAndView home(
      HttpServletRequest request, @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    Map<String, Object> params = new HashMap<>();
    params.put("isAuthenticated", roles.contains(UserRole.AUTHORIZED));
    params.put("isDriver", roles.contains(UserRole.DRIVER));
    params.put("canManageSites", UserRole.canManageSites(roles));
    params.put("siteDescription", "Disaster Relief");
    params.put("contactUsLink", CONTACT_US_LINK);
    return new ModelAndView("home/home", params);
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
