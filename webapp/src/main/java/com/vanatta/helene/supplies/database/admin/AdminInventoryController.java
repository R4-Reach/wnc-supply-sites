package com.vanatta.helene.supplies.database.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

/** Inventory-admin sub-portal (item merging). Reached from the admin landing. */
@Controller
public class AdminInventoryController {

  public static final String PATH_ADMIN_INVENTORY = "/admin/inventory";

  @GetMapping(PATH_ADMIN_INVENTORY)
  ModelAndView adminInventory() {
    return new ModelAndView("admin/inventory");
  }
}
