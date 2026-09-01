package org.r4reach.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.auth.setup.password.send.access.code.SendAccessTokenDao;
import org.r4reach.util.CookieUtil;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Controller
public class LoginController {

  private final Jdbi jdbi;

  LoginController(Jdbi jdbi) {
    this.jdbi = jdbi;
  }

  @GetMapping("/login/login")
  public ModelAndView login(@RequestParam(required = false) String redirectUri) {
    Map<String, String> pageParams = new HashMap<>();
    pageParams.put("redirectUri", safeRedirect(redirectUri));
    pageParams.put("errorMessage", "");
    return new ModelAndView("login/login", pageParams);
  }

  /**
   * Restricts post-login redirects to same-site absolute paths so a crafted {@code redirectUri}
   * can't send the user to an external host. Rejects protocol-relative ({@code //host}) and
   * backslash ({@code /\host}) forms that browsers normalize to an off-site URL.
   */
  static String safeRedirect(String redirectUri) {
    if (redirectUri == null
        || redirectUri.isBlank()
        || !redirectUri.startsWith("/")
        || redirectUri.startsWith("//")
        || redirectUri.startsWith("/\\")) {
      return "/";
    }
    return redirectUri;
  }

  @GetMapping("/login/setup-password")
  public ModelAndView passwordSetup(@RequestParam(required = false) String redirectUri) {
    Map<String, String> pageParams = new HashMap<>();
    pageParams.put("number", "");
    pageParams.put("errorMessage", "");
    return new ModelAndView("login/setup-password", pageParams);
  }

  @PostMapping(
      path = "/doLogin",
      consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
  public ModelAndView doLogin(
      @RequestParam MultiValueMap<String, String> params, HttpServletResponse response) {
    String user = params.get("user").getFirst();
    String password = params.get("password").getFirst();
    String redirectUri =
        safeRedirect(
            Optional.ofNullable(params.get("redirectUri")).map(List::getFirst).orElse("/"));

    if (user == null || user.isEmpty() || password == null || password.isEmpty()) {
      Map<String, String> pageParams = new HashMap<>();
      pageParams.put("redirectUri", redirectUri);
      pageParams.put("errorMessage", "Invalid Login");
      return new ModelAndView("login/login", pageParams);
    } else if (LoginDao.isLoginThrottled(jdbi, user)) {
      // Too many recent failures for this number — lock out to blunt online password guessing.
      log.warn("Login throttled after too many failed attempts");
      Map<String, String> pageParams = new HashMap<>();
      pageParams.put("redirectUri", redirectUri);
      pageParams.put(
          "errorMessage", "Too many failed attempts. Please wait a few minutes and try again.");
      return new ModelAndView("login/login", pageParams);
    } else if (PasswordDao.confirmPassword(jdbi, user, password)) {
      LoginDao.recordLoginSuccess(jdbi, user);
      String authToken = LoginDao.generateAuthToken(jdbi, user);
      CookieUtil.setCookie(response, "auth", authToken);
      CookieUtil.setCookie(response, "user", user);
      return new ModelAndView("redirect:" + redirectUri);
    } else if (!PasswordDao.passwordIsSet(jdbi, user)
        && SendAccessTokenDao.isPhoneNumberRegistered(jdbi, user)) {
      // Registered (whitelisted / a site contact / a driver) but hasn't set a password yet.
      return new ModelAndView("redirect:/login/setup-password");
    } else {
      LoginDao.recordLoginFailure(jdbi, user);
      log.info("User login failed");
      Map<String, String> pageParams = new HashMap<>();
      pageParams.put("redirectUri", redirectUri);
      pageParams.put("errorMessage", "Invalid Login");
      return new ModelAndView("login/login", pageParams);
    }
  }
}
