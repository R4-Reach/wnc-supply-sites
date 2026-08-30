package org.r4reach.auth.setup.password.confirm.access.code;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Receives the challenge access code that we send to a user via SMS, validates the access code. If
 * the access code is valid, then we advance the user to password reset. For apss
 */
@Controller
@Slf4j
public class ConfirmAccessCodeController {
  private final Jdbi jdbi;
  private final Supplier<String> validationTokenGenerator;

  @Autowired
  ConfirmAccessCodeController(Jdbi jdbi) {
    this(jdbi, () -> UUID.randomUUID().toString());
  }

  ConfirmAccessCodeController(Jdbi jdbi, Supplier<String> validationTokenGenerator) {
    this.jdbi = jdbi;
    this.validationTokenGenerator = validationTokenGenerator;
  }

  @PostMapping("/confirm-access-code")
  ResponseEntity<ConfirmAccessCodeResponse> confirmAccessCode(@RequestBody String input) {
    log.info("Confirm access code: {}", input);

    ConfirmAccessCodeRequest confirmAccessCodeRequest = ConfirmAccessCodeRequest.parse(input);
    if (!confirmAccessCodeRequest.isValid()) {
      log.warn("Invalid confirm access code request: {}", input);
      throw new IllegalArgumentException("Invalid confirm access code request");
    }

    return doConfirm(confirmAccessCodeRequest);
  }

  /**
   * Posted by the setup-password wizard's htmx confirm-code form. Advances to the set-password
   * fragment on success, or re-renders the confirm-code fragment with an error message on failure.
   */
  @PostMapping("/setup-password/confirm-code")
  ModelAndView confirmCodeHtmx(@RequestParam String csrf, @RequestParam String confirmCode) {
    String cleaned = confirmCode == null ? "" : confirmCode.trim().replaceAll("\\D", "");
    ConfirmAccessCodeRequest request =
        ConfirmAccessCodeRequest.builder().csrf(csrf).confirmCode(cleaned).build();
    if (!request.isValid()) {
      return confirmFragment(csrf, "Confirm code not valid");
    }

    ResponseEntity<ConfirmAccessCodeResponse> result = doConfirm(request);
    if (result.getStatusCode().is2xxSuccessful()) {
      Map<String, Object> model = new HashMap<>();
      model.put("validationToken", result.getBody().getValidationToken());
      model.put("errorMessage", "");
      return new ModelAndView("login/fragments/set-password", model);
    }
    return confirmFragment(csrf, result.getBody().getError());
  }

  private ModelAndView confirmFragment(String csrf, String error) {
    Map<String, Object> model = new HashMap<>();
    model.put("csrf", csrf == null ? "" : csrf);
    model.put("errorMessage", error == null ? "" : error);
    return new ModelAndView("login/fragments/confirm-code", model);
  }

  /** Core confirm logic, shared by the JSON endpoint and the htmx wizard endpoint. */
  private ResponseEntity<ConfirmAccessCodeResponse> doConfirm(ConfirmAccessCodeRequest request) {
    String validationToken = validationTokenGenerator.get();

    int updateCount = ConfirmAccessCodeDao.confirmAccessCode(jdbi, request, validationToken);

    if (updateCount == 1) {
      return ResponseEntity.ok(
          ConfirmAccessCodeResponse.builder().validationToken(validationToken).build());
    } else {
      return ResponseEntity.status(401)
          .body(ConfirmAccessCodeResponse.builder().error("Invalid access token").build());
    }
  }

  @Builder
  @Value
  public static class ConfirmAccessCodeRequest {
    String csrf;
    String confirmCode;

    static ConfirmAccessCodeRequest parse(String json) {
      return new Gson().fromJson(json, ConfirmAccessCodeRequest.class);
    }

    boolean isValid() {
      return csrf != null //
          && !csrf.isEmpty()
          && confirmCode != null
          && confirmCode.length() == 6;
    }
  }

  @Builder
  @Value
  static class ConfirmAccessCodeResponse {
    String validationToken;
    String error;
  }
}
