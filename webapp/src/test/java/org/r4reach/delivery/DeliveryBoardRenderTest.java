package org.r4reach.delivery;

import static org.r4reach.TestConfiguration.jdbiTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.auth.LoginDao;
import org.r4reach.auth.UserRole;
import org.r4reach.auth.setup.password.SetupPasswordHelper;
import org.r4reach.auth.user.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Renders the dispatcher kanban board through the servlet stack (not just the controller model) so
 * the strict-Mustache template is actually executed. The controller unit test cannot catch a
 * missing/null template key, because it never renders the view.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryBoardRenderTest {

  private static final String DISPATCHER_NUMBER = "5553330000";

  @Autowired private MockMvc mockMvc;

  private Cookie authCookie;

  @BeforeEach
  void setup() {
    SetupPasswordHelper.setup();
    TestConfiguration.setupDatabase();
    SetupPasswordHelper.withRegisteredNumber(DISPATCHER_NUMBER);
    UserRoleService.grantRole(jdbiTest, DISPATCHER_NUMBER, UserRole.DISPATCHER);
    authCookie = new Cookie("auth", LoginDao.generateAuthToken(jdbiTest, DISPATCHER_NUMBER));
  }

  /**
   * An early-pipeline delivery has no pickup/drop-off site yet, so {@code fromSite}/{@code toSite}
   * are null. Strict Mustache throws on a null plain tag, so before those tags were null-guarded a
   * single such card 500'd the whole board.
   */
  @Test
  void boardRendersDeliveryWithNoSitesAssigned() throws Exception {
    DeliveryDao.createDelivery(
        jdbiTest,
        DeliveryDao.CreateDeliveryRequest.builder()
            .deliveryStatus(DeliveryStatus.DRIVER_VOLUNTEERED)
            .build());

    mockMvc
        .perform(get("/dispatch/deliveries").header("host", "localhost").cookie(authCookie))
        .andExpect(status().isOk());
  }
}
