package org.r4reach;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.r4reach.util.CookieUtil;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Supplies the role-gated flags and links the site-wide left-hand nav ({@code header.html} → {@code
 * sidebar.html}) reads, so the sidebar renders correctly on every page rather than only on the home
 * page. Derived from the {@link UserRole}s that {@link LoggedInAdvice} already resolves per
 * request; a handler that sets any of these itself still wins, since advice populates the model
 * before the handler runs.
 */
@ControllerAdvice
public class NavModelAdvice {

  @ModelAttribute("isAuthenticated")
  public boolean isAuthenticated(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    return roles.contains(UserRole.AUTHORIZED);
  }

  @ModelAttribute("isDriver")
  public boolean isDriver(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    return roles.contains(UserRole.DRIVER);
  }

  @ModelAttribute("canViewDrivers")
  public boolean canViewDrivers(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    return UserRole.canViewDrivers(roles);
  }

  @ModelAttribute("canManageSites")
  public boolean canManageSites(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    return UserRole.canManageSites(roles);
  }

  @ModelAttribute("canAccessAdminArea")
  public boolean canAccessAdminArea(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    return UserRole.canAccessAdminArea(roles);
  }

  /**
   * Whether any staff portal (driver, dispatch, manager, admin) is reachable — gates the section.
   */
  @ModelAttribute("hasStaffNav")
  public boolean hasStaffNav(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    return roles.contains(UserRole.DRIVER)
        || UserRole.canViewDrivers(roles)
        || UserRole.canManageSites(roles)
        || UserRole.canAccessAdminArea(roles);
  }

  @ModelAttribute("betaVolunteer")
  public boolean betaVolunteer(HttpServletRequest request) {
    return CookieUtil.readCookieValue(request, SimpleHtmlController.BETA_VOLUNTEER_COOKIE)
        .map("true"::equals)
        .orElse(false);
  }

  @ModelAttribute("contactUsLink")
  public String contactUsLink() {
    return SimpleHtmlController.CONTACT_US_LINK;
  }
}
