package org.r4reach.auth.setup.password.set.pass;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.auth.PasswordDao;
import org.r4reach.auth.setup.password.SetupPasswordHelper;
import org.r4reach.auth.setup.password.confirm.access.code.ConfirmAccessCodeController;
import org.r4reach.auth.setup.password.confirm.access.code.ConfirmAccessCodeDao;
import org.r4reach.auth.setup.password.send.access.code.SendAccessTokenDao;

class SetPasswordControllerTest {
  SetPasswordController controller = new SetPasswordController(TestConfiguration.jdbiTest);

  String number = "1230004422";
  String csrf = "csrf token";
  String accessCode = "333666";
  String validation = "validation auth token";
  String newPassword = "new password";

  String input =
      String.format(
          """
          {"validationToken":"%s","password":"%s"}
          """,
          validation, newPassword);

  /**
   * First set up the whole flow so we have a validation token and are able to update password..<br>
   * Then send a request to update password.
   */
  @BeforeEach
  void cleanDb() {
    SetupPasswordHelper.setup();

    // arrange - go through the reset password flow
    // register a number & do the access code confirmation
    SetupPasswordHelper.withRegisteredNumber(number);
    SendAccessTokenDao.insertSmsPasscode(
        TestConfiguration.jdbiTest,
        SendAccessTokenDao.InsertAccessCodeParams.builder()
            .phoneNumber(number)
            .csrfToken(csrf)
            .accessCode(accessCode)
            .build());
    ConfirmAccessCodeDao.confirmAccessCode(
        TestConfiguration.jdbiTest,
        ConfirmAccessCodeController.ConfirmAccessCodeRequest.builder()
            .confirmCode(accessCode)
            .csrf(csrf)
            .build(),
        validation);
  }

  @Test
  void canParseInput() {
    var result = SetPasswordController.SetPasswordRequest.parse(input);
    assertThat(result.getValidationToken()).isEqualTo(validation);
    assertThat(result.getPassword()).isEqualTo(newPassword);
  }

  /** Happy case - send a request to update password. */
  @Test
  void setPassword() {
    // set password
    var response = controller.setPassword(input, null);

    // validate password is set
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody().getError()).isNull();
    assertThat(PasswordDao.confirmPassword(TestConfiguration.jdbiTest, number, newPassword))
        .isTrue();
  }

  /** Attempt password update with a bad validation token */
  @Test
  void setPasswordWithBadValidationToken() {
    String input =
        String.format(
            """
            {"validationToken":"%s","password":"%s"}
            """,
            "bad token value, this is incorrect", newPassword);

    var response = controller.setPassword(input, null);

    assertThat(response.getStatusCode().value()).isEqualTo(401);
    assertThat(response.getBody().getError()).isNotNull();
    assertThat(PasswordDao.confirmPassword(TestConfiguration.jdbiTest, number, newPassword))
        .isFalse();
  }
}
