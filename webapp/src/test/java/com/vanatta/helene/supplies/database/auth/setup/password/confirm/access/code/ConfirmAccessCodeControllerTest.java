package com.vanatta.helene.supplies.database.auth.setup.password.confirm.access.code;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.setup.password.SetupPasswordHelper;
import com.vanatta.helene.supplies.database.auth.setup.password.send.access.code.SendAccessTokenDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfirmAccessCodeControllerTest {
  static final String jsonInput =
      """
      {"confirmCode":"557603","csrf":"1a24abcd-029f-49fe-8d76-af1c4b93bc28"}
      """;
  ConfirmAccessCodeController controller =
      new ConfirmAccessCodeController(TestConfiguration.jdbiTest, () -> "validation token");

  @BeforeEach
  void cleanDb() {
    SetupPasswordHelper.setup();
  }

  @Test
  void canParseInput() {
    var request = ConfirmAccessCodeController.ConfirmAccessCodeRequest.parse(jsonInput);
    assertThat(request.getConfirmCode()).isEqualTo("557603");
    assertThat(request.getCsrf()).isEqualTo("1a24abcd-029f-49fe-8d76-af1c4b93bc28");
  }

  @Test
  void validConfirmCodeRequest() {
    assertThat(ConfirmAccessCodeController.ConfirmAccessCodeRequest.builder().build().isValid())
        .isFalse();
    assertThat(
            ConfirmAccessCodeController.ConfirmAccessCodeRequest.builder()
                .csrf("value")
                .build()
                .isValid())
        .isFalse();
    assertThat(
            ConfirmAccessCodeController.ConfirmAccessCodeRequest.builder()
                .confirmCode("123456")
                .build()
                .isValid())
        .isFalse();

    assertThat(
            ConfirmAccessCodeController.ConfirmAccessCodeRequest.builder()
                .csrf("value")
                .confirmCode("123456")
                .build()
                .isValid())
        .isTrue();

    assertThat(
            ConfirmAccessCodeController.ConfirmAccessCodeRequest.builder()
                .csrf("value")
                .confirmCode("1234567")
                .build()
                .isValid())
        .isFalse();

    assertThat(
            ConfirmAccessCodeController.ConfirmAccessCodeRequest.builder()
                .csrf("value")
                .confirmCode("12345")
                .build()
                .isValid())
        .isFalse();
  }

  /**
   * Send a valid access token with CSRF. This should create a validation token in database and that
   * token will be returned to us. The test objct is set up to generate a hardcoded validation
   * token.
   */
  @Test
  void confirmAccessCode() {
    assertThat(
            SetupPasswordHelper.accessTokenExists(
                "557603", "1a24abcd-029f-49fe-8d76-af1c4b93bc28", "validation token"))
        .isFalse();

    String number = "123___4444";
    SetupPasswordHelper.withRegisteredNumber(number);
    SendAccessTokenDao.insertSmsPasscode(
        TestConfiguration.jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .accessCode("557603")
            .csrfToken("1a24abcd-029f-49fe-8d76-af1c4b93bc28")
            .build());

    var response = controller.confirmAccessCode(jsonInput);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().getError()).isNull();
    assertThat(response.getBody().getValidationToken()).isEqualTo("validation token");
    assertThat(
            SetupPasswordHelper.accessTokenExists(
                "557603", "1a24abcd-029f-49fe-8d76-af1c4b93bc28", "validation token"))
        .isTrue();
  }

  @Test
  void confirmAccessBadAccessToken() {
    String number = "123___4444";
    SetupPasswordHelper.withRegisteredNumber(number);
    SendAccessTokenDao.insertSmsPasscode(
        TestConfiguration.jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .accessCode("000000")
            .csrfToken("1a24abcd-029f-49fe-8d76-af1c4b93bc28")
            .build());

    var response = controller.confirmAccessCode(jsonInput);
    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody().getError()).isNotNull();
    assertThat(response.getBody().getValidationToken()).isNull();
  }

  private static final String realCode = "557603";
  private static final String csrf = "1a24abcd-029f-49fe-8d76-af1c4b93bc28";
  private static final String wrongCodeInput =
      String.format("{\"confirmCode\":\"000000\",\"csrf\":\"%s\"}", csrf);

  private void insertRealCode() {
    String number = "123___4444";
    SetupPasswordHelper.withRegisteredNumber(number);
    SendAccessTokenDao.insertSmsPasscode(
        TestConfiguration.jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .accessCode(realCode)
            .csrfToken(csrf)
            .build());
  }

  /** After the max number of failed attempts, even the correct code is rejected. */
  @Test
  void lockedOutAfterMaxAttempts() {
    insertRealCode();

    for (int i = 0; i < ConfirmAccessCodeDao.MAX_ATTEMPTS; i++) {
      assertThat(controller.confirmAccessCode(wrongCodeInput).getStatusCode().value())
          .isEqualTo(401);
    }

    assertThat(controller.confirmAccessCode(jsonInput).getStatusCode().value()).isEqualTo(401);
  }

  /** One failed attempt below the limit still leaves the correct code usable. */
  @Test
  void succeedsJustBelowAttemptLimit() {
    insertRealCode();

    for (int i = 0; i < ConfirmAccessCodeDao.MAX_ATTEMPTS - 1; i++) {
      assertThat(controller.confirmAccessCode(wrongCodeInput).getStatusCode().value())
          .isEqualTo(401);
    }

    assertThat(controller.confirmAccessCode(jsonInput).getStatusCode().value()).isEqualTo(200);
  }

  /** A code older than the expiry window is rejected even when correct. */
  @Test
  void expiredCodeRejected() {
    insertRealCode();
    TestConfiguration.jdbiTest.withHandle(
        handle ->
            handle
                .createUpdate(
                    "update sms_passcode set date_created = now() - interval '20 minutes'")
                .execute());

    assertThat(controller.confirmAccessCode(jsonInput).getStatusCode().value()).isEqualTo(401);
  }

  /** A code that has already been confirmed cannot be confirmed a second time. */
  @Test
  void alreadyConfirmedCodeRejected() {
    insertRealCode();

    assertThat(controller.confirmAccessCode(jsonInput).getStatusCode().value()).isEqualTo(200);
    assertThat(controller.confirmAccessCode(jsonInput).getStatusCode().value()).isEqualTo(401);
  }

  @Test
  void confirmAccessBadCsrfToken() {
    String number = "123___4444";
    SetupPasswordHelper.withRegisteredNumber(number);
    SendAccessTokenDao.insertSmsPasscode(
        TestConfiguration.jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .accessCode("557603")
            .csrfToken("some other value")
            .build());

    var response = controller.confirmAccessCode(jsonInput);
    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody().getError()).isNotNull();
    assertThat(response.getBody().getValidationToken()).isNull();
  }
}
