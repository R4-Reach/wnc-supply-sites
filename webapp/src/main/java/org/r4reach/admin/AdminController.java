package org.r4reach.admin;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

/**
 * Admin landing page. Any admin ({@link UserRole#USER_ADMIN}, {@link UserRole#SITE_ADMIN}, or
 * {@link UserRole#DATA_ADMIN}) may see it; each button is gated on its own role so an admin only
 * sees the sections they can use.
 */
@Controller
@AllArgsConstructor
public class AdminController {

  public static final String PATH_ADMIN = "/admin";

  @GetMapping(PATH_ADMIN)
  ModelAndView adminHome(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    if (!UserRole.canAccessAdminArea(roles)) {
      return new ModelAndView("redirect:/");
    }
    return new ModelAndView(
        "admin/admin",
        Map.of(
            "isUserAdmin", UserRole.isUserAdmin(roles),
            "isSiteAdmin", UserRole.isSiteAdmin(roles),
            "isDataAdmin", UserRole.isDataAdmin(roles),
            // The inventory sub-portal is reachable by user admins (item merge) and data admins
            // (item tagging); each button inside it self-gates further.
            "showInventory", UserRole.isUserAdmin(roles) || UserRole.isDataAdmin(roles)));
  }
}
