package org.r4reach.auth;

import static org.r4reach.TestConfiguration.jdbiTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.auth.setup.password.SetupPasswordHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the security filter chain end to end: gated URLs demand a valid {@code auth} cookie and
 * bounce unauthenticated visitors to the login page, while public URLs stay open. This is the one
 * place the URL-gating rules in {@link SecurityConfig} are covered, since the controller unit tests
 * invoke handlers directly and never touch the filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityGatingTest {

  private static final String GATED_URL = "/dispatch/deliveries";
  private static final String NUMBER = "5556667788";

  @Autowired private MockMvc mockMvc;

  private String authToken;

  @BeforeEach
  void setup() {
    SetupPasswordHelper.setup();
    TestConfiguration.setupDatabase();
    SetupPasswordHelper.withRegisteredNumber(NUMBER);
    authToken = LoginDao.generateAuthToken(jdbiTest, NUMBER);
  }

  @Test
  void gatedUrlWithoutAuthRedirectsToLogin() throws Exception {
    mockMvc
        .perform(get(GATED_URL).header("host", "localhost"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login/login?redirectUri=" + GATED_URL));
  }

  @Test
  void gatedUrlWithValidAuthPassesTheGate() throws Exception {
    // A logged-in-but-not-dispatcher user clears the login gate; the controller's own role check
    // then redirects home. The point here is only that we got past the security layer, not to
    // /login.
    mockMvc
        .perform(get(GATED_URL).header("host", "localhost").cookie(new Cookie("auth", authToken)))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/"));
  }

  @Test
  void publicUrlIsOpenWithoutAuth() throws Exception {
    mockMvc.perform(get("/").header("host", "localhost")).andExpect(status().isOk());
  }
}
