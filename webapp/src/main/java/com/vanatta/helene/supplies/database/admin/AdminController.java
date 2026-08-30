package com.vanatta.helene.supplies.database.admin;

import com.vanatta.helene.supplies.database.auth.LoggedInAdvice;
import com.vanatta.helene.supplies.database.auth.UserRole;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;

/** Admin landing page. Only {@link UserRole#USER_ADMIN}s may see it. */
@Controller
@AllArgsConstructor
public class AdminController {

  public static final String PATH_ADMIN = "/admin";

  @GetMapping(PATH_ADMIN)
  ModelAndView adminHome(@ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles) {
    if (!UserRole.isUserAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    return new ModelAndView("admin/admin", Map.of());
  }
}
