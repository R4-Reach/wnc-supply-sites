package org.r4reach.siteconfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.r4reach.auth.LoggedInAdvice;
import org.r4reach.auth.UserRole;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Site Config page: lets a {@link UserRole#SITE_ADMIN} edit the DB-backed configuration (Google
 * Maps and Twilio credentials). Secret fields are write-only — their current value is never
 * rendered; the form only reports whether one is set, and a blank submission leaves the stored
 * secret alone.
 */
@Controller
@AllArgsConstructor
public class SiteConfigController {

  public static final String PATH_SITE_CONFIG = "/admin/site-config";

  private final SiteConfigService siteConfigService;

  @GetMapping(PATH_SITE_CONFIG)
  ModelAndView siteConfigPage(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam(value = "saved", required = false) boolean saved) {
    if (!UserRole.isSiteAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    Map<String, Object> params = new HashMap<>();
    params.put("saved", saved);
    params.put("googleMapsApiKeySet", siteConfigService.isSet(SiteConfigKey.GOOGLE_MAPS_API_KEY));
    params.put("twilioAccountSid", siteConfigService.getOrEmpty(SiteConfigKey.TWILIO_ACCOUNT_SID));
    params.put("twilioAuthTokenSet", siteConfigService.isSet(SiteConfigKey.TWILIO_AUTH_TOKEN));
    params.put("twilioFromNumber", siteConfigService.getOrEmpty(SiteConfigKey.TWILIO_FROM_NUMBER));
    return new ModelAndView("admin/site-config", params);
  }

  @PostMapping(PATH_SITE_CONFIG)
  ModelAndView saveSiteConfig(
      @ModelAttribute(LoggedInAdvice.USER_ROLES) List<UserRole> roles,
      @RequestParam("GOOGLE_MAPS_API_KEY") String googleMapsApiKey,
      @RequestParam("TWILIO_ACCOUNT_SID") String twilioAccountSid,
      @RequestParam("TWILIO_AUTH_TOKEN") String twilioAuthToken,
      @RequestParam("TWILIO_FROM_NUMBER") String twilioFromNumber) {
    if (!UserRole.isSiteAdmin(roles)) {
      return new ModelAndView("redirect:/");
    }
    // Plaintext fields are written verbatim (blank clears them). Secret fields are written only
    // when a new value is supplied, so a blank submission keeps the existing secret.
    siteConfigService.set(SiteConfigKey.TWILIO_ACCOUNT_SID, twilioAccountSid.trim());
    siteConfigService.set(SiteConfigKey.TWILIO_FROM_NUMBER, twilioFromNumber.trim());
    setSecretIfPresent(SiteConfigKey.GOOGLE_MAPS_API_KEY, googleMapsApiKey);
    setSecretIfPresent(SiteConfigKey.TWILIO_AUTH_TOKEN, twilioAuthToken);
    return new ModelAndView("redirect:" + PATH_SITE_CONFIG + "?saved=true");
  }

  private void setSecretIfPresent(SiteConfigKey key, String value) {
    if (value != null && !value.isBlank()) {
      siteConfigService.set(key, value.trim());
    }
  }
}
