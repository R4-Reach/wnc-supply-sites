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
 * Inventory-admin sub-portal. Reached from the admin landing by a {@link UserRole#USER_ADMIN} (item
 * merging) or a {@link UserRole#DATA_ADMIN} (item tagging); each button self-gates on its own role.
 */
@Controller
@AllArgsConstructor
public class AdminInventoryController {

  public static final String PATH_ADMIN_INVENTORY = "/admin/inventory";

  @GetMapping(PATH_ADMIN_INVENTORY)
  ModelAndView adminInventory(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    if (!UserRole.isUserAdmin(roles) && !UserRole.isDataAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    return new ModelAndView(
        "admin/inventory",
        Map.of(
            "isUserAdmin", UserRole.isUserAdmin(roles),
            "isDataAdmin", UserRole.isDataAdmin(roles)));
  }
}
