package org.r4reach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.auth.user.UserRoleService;
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

  @Test
  void safeRedirect_allowsLocalPaths() {
    assertThat(LoginController.safeRedirect("/manage/select-site"))
        .isEqualTo("/manage/select-site");
    assertThat(LoginController.safeRedirect("/")).isEqualTo("/");
  }

  @Test
  void safeRedirect_rejectsOffSiteTargets() {
    // Protocol-relative, backslash-normalized, absolute-URL, and empty inputs all fall back to "/".
    assertThat(LoginController.safeRedirect("//evil.com")).isEqualTo("/");
    assertThat(LoginController.safeRedirect("/\\evil.com")).isEqualTo("/");
    assertThat(LoginController.safeRedirect("https://evil.com")).isEqualTo("/");
    assertThat(LoginController.safeRedirect("evil.com")).isEqualTo("/");
    assertThat(LoginController.safeRedirect(null)).isEqualTo("/");
    assertThat(LoginController.safeRedirect("")).isEqualTo("/");
  }
}
