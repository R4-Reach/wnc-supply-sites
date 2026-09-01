package org.r4reach.auth.setup.password.set.pass;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.r4reach.util.CookieUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Part of the setup password flow, the last part. Accepts the users new password and then redirects
 * the user.
 */
@Slf4j
@Controller
@AllArgsConstructor
public class SetPasswordController {

  private final Jdbi jdbi;

  @PostMapping("/set-password")
  ResponseEntity<SetPasswordResponse> setPassword(
      @RequestBody String request, HttpServletResponse response) {
    SetPasswordRequest setPasswordRequest = SetPasswordRequest.parse(request);
    return doSetPassword(
        setPasswordRequest.getValidationToken(), setPasswordRequest.getPassword(), response);
  }

  /**
   * Posted by the setup-password wizard's htmx set-password form. Shows the success fragment on
   * success, or re-renders the set-password fragment with an error message on failure.
   */
  @PostMapping("/setup-password/set")
  ModelAndView setPasswordHtmx(
      @RequestParam String validationToken,
      @RequestParam String password,
      HttpServletResponse response) {
    ResponseEntity<SetPasswordResponse> result = doSetPassword(validationToken, password, response);
    if (result.getStatusCode().is2xxSuccessful()) {
      return new ModelAndView("login/fragments/success");
    }
    Map<String, Object> model = new HashMap<>();
    model.put("validationToken", validationToken);
    model.put("errorMessage", result.getBody().getError());
    return new ModelAndView("login/fragments/set-password", model);
  }

  /** Minimum length for a newly set password. */
  static final int MIN_PASSWORD_LENGTH = 8;

  /** Core set-password logic, shared by the JSON endpoint and the htmx wizard endpoint. */
  private ResponseEntity<SetPasswordResponse> doSetPassword(
      String validationToken, String password, HttpServletResponse response) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      return ResponseEntity.badRequest()
          .body(
              new SetPasswordResponse(
                  "Password must be at least " + MIN_PASSWORD_LENGTH + " characters"));
    } else if (EasyPasswordList.isEasyPassword(password)) {
      return ResponseEntity.badRequest()
          .body(new SetPasswordResponse("Password is too easy to guess"));
    }

    boolean success = SetPasswordDao.updatePassword(jdbi, validationToken, password);

    if (success) {
      CookieUtil.deleteCookie(response, "auth");
      return ResponseEntity.ok(SetPasswordResponse.OK);
    } else {
      return ResponseEntity.status(401).body(new SetPasswordResponse("Failed to set password"));
    }
  }

  @Value
  static class SetPasswordRequest {
    String password;
    String validationToken;

    static SetPasswordRequest parse(String json) {
      return new Gson().fromJson(json, SetPasswordRequest.class);
    }
  }

  @Value
  static class SetPasswordResponse {
    static final SetPasswordResponse OK = new SetPasswordResponse(null);
    String error;
  }
}
