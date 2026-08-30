package com.vanatta.helene.supplies.database.auth;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.auth.user.UserRoleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.servlet.ModelAndView;

class LoginControllerTest {

  /**
   * If someone tries to login with a registered phone number, but has not yet set up their
   * password, and attemps login - then redirect them to the setup password flow.
   */
  @Test
  void registeredPhoneNumbersAreRedirectedToCreatePassword() {
    LoginController loginController = new LoginController(TestConfiguration.jdbiTest);

    UserRoleService.grantRole(jdbiTest, "987 345 6789", UserRole.DRIVER);

    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    params.put("user", List.of("987 345 6789"));
    params.put("password", List.of("a guess"));
    ModelAndView modelAndView = loginController.doLogin(params, new MockHttpServletResponse());

    assertThat(modelAndView.getViewName()).isEqualTo("redirect:/login/setup-password");
  }
}
