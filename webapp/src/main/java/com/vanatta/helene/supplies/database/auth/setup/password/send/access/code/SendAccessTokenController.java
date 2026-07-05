package com.vanatta.helene.supplies.database.auth.setup.password.send.access.code;

import com.google.gson.Gson;
import com.vanatta.helene.supplies.database.twilio.sms.SmsSender;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@Slf4j
public class SendAccessTokenController {

  private final SmsSender smsSender;
  private final Jdbi jdbi;
  private final Supplier<String> csrfGenerator;
  private final AccessTokenGenerator accessTokenGenerator;

  @Autowired
  SendAccessTokenController(
      SmsSender smsSender, Jdbi jdbi, AccessTokenGenerator accessTokenGenerator) {
    this(smsSender, jdbi, accessTokenGenerator, () -> UUID.randomUUID().toString());
  }

  SendAccessTokenController(
      SmsSender smsSender,
      Jdbi jdbi,
      AccessTokenGenerator accessTokenGenerator,
      Supplier<String> csrfGenerator) {
    this.smsSender = smsSender;
    this.jdbi = jdbi;
    this.accessTokenGenerator = accessTokenGenerator;
    this.csrfGenerator = csrfGenerator;
  }

  @PostMapping("/send-access-code")
  ResponseEntity<SendAccessCodeResponse> sendAccessCode(@RequestBody String request) {
    log.info("Access code request for: {}", request);

    SendAccessCodeRequest sendAccessCodeRequest = SendAccessCodeRequest.parse(request);
    if (!sendAccessCodeRequest.isValid()) {
      log.warn("Invalid phone number received for access code request: {}", request);
      throw new IllegalArgumentException("Invalid phone number");
    }

    // check that the user has a registered phone number
    String phoneNumber = sendAccessCodeRequest.getNumber();

    if (!SendAccessTokenDao.isPhoneNumberRegistered(jdbi, phoneNumber)) {
      log.warn("Access code requested for unregistered phone number: {}", phoneNumber);
      return ResponseEntity.status(401)
          .body(
              SendAccessCodeResponse.invalid(
                  """
                    Phone number is not registered.
                    The phone number associated with the site or that you used during driver sign up is required.
                    Please <a href="/registration/">contact us</a> to get your number registered.
                  """));
    }

    if (!SendAccessTokenDao.userAccountExists(jdbi, phoneNumber)) {
      SendAccessTokenDao.createUser(jdbi, phoneNumber);
    }

    if (SendAccessTokenDao.isThrottled(jdbi, phoneNumber)) {
      log.warn("Too many access code requests for phone number: {}", phoneNumber);
      return ResponseEntity.status(429)
          .body(
              SendAccessCodeResponse.invalid(
                  """
                    Too many access code requests. Please wait a few minutes before trying again.
                  """));
    }

    // generate an access code & CRF token
    String accessCode = accessTokenGenerator.generate();
    String csrf = csrfGenerator.get();

    // store in database
    SendAccessTokenDao.insertSmsPasscode(
        jdbi,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(phoneNumber)
            .csrfToken(csrf)
            .accessCode(accessCode)
            .build());

    // send the passcode via SMS
    boolean success =
        smsSender.send(
            phoneNumber,
            String.format(
                """
            WNC Supply Sites:
            Your one time access code is %s.
            Enter this code to set up your password.
            """,
                accessCode));

    if (success) {
      // return the CRF token
      SendAccessCodeResponse response = SendAccessCodeResponse.valid(csrf);
      return ResponseEntity.ok(response);
    } else {
      return ResponseEntity.status(400)
          .body(
              SendAccessCodeResponse.invalid(
                  """
                    Potentially invalid phone number. Failed to send SMS message.
                    Please double check the phone number. If the phone number is for sure
                    valid, please <a href="/registration/">contact us</a>.
                  """));
    }
  }

  @Value
  static class SendAccessCodeRequest {
    String number;

    static SendAccessCodeRequest parse(String json) {
      return new Gson().fromJson(json, SendAccessCodeRequest.class);
    }

    boolean isValid() {
      // phone number can country code in front, in which case it is 11 digits
      return number != null
          && !number.isEmpty()
          && (number.trim().length() == 10 || number.trim().length() == 11);
    }
  }

  @Value
  static class SendAccessCodeResponse {
    static SendAccessCodeResponse valid(String crf) {
      return new SendAccessCodeResponse(crf, null);
    }

    static SendAccessCodeResponse invalid(String error) {
      return new SendAccessCodeResponse(null, error);
    }

    String csrf;
    String error;
  }
}
