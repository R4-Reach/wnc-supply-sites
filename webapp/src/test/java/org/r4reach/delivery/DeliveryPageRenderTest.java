package org.r4reach.delivery;

import static org.r4reach.TestConfiguration.jdbiTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
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
 * Renders the dispatcher delivery pages end to end. Because Mustache is strict — a missing plain
 * variable throws at render time, invisible to the controller unit tests that only inspect the
 * model map — this is the one place the create form, the read/write detail page, and the public
 * manifest are actually compiled and executed through the real view resolver and the shared
 * item-picker partial.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeliveryPageRenderTest {

  private static final String NUMBER = "5556660001";

  @Autowired private MockMvc mockMvc;

  private Cookie authCookie;

  @BeforeEach
  void setup() {
    SetupPasswordHelper.setup();
    TestConfiguration.setupDatabase();
    SetupPasswordHelper.withRegisteredNumber(NUMBER);
    UserRoleService.grantRole(jdbiTest, NUMBER, UserRole.DISPATCHER);
    authCookie = new Cookie("auth", LoginDao.generateAuthToken(jdbiTest, NUMBER));
  }

  @Test
  void createFormRenders() throws Exception {
    mockMvc
        .perform(get("/dispatch/deliveries/new").header("host", "localhost").cookie(authCookie))
        .andExpect(status().isOk());
  }

  @Test
  void detailPageRenders() throws Exception {
    long siteId = siteIdByName("site2");
    String publicKey =
        DeliveryDao.createDelivery(
            jdbiTest,
            DeliveryDao.CreateDeliveryRequest.builder()
                .fromSiteId(siteId)
                .toSiteId(siteId)
                .deliveryStatus(DeliveryStatus.CREATING_DISPATCH)
                .items(List.of("Water"))
                .build());

    mockMvc
        .perform(
            get("/dispatch/deliveries/" + publicKey).header("host", "localhost").cookie(authCookie))
        .andExpect(status().isOk());
  }

  @Test
  void publicManifestRenders() throws Exception {
    long siteId = siteIdByName("site2");
    String publicKey =
        DeliveryDao.createDelivery(
            jdbiTest,
            DeliveryDao.CreateDeliveryRequest.builder()
                .fromSiteId(siteId)
                .toSiteId(siteId)
                .deliveryStatus(DeliveryStatus.CREATING_DISPATCH)
                .build());

    // The manifest is public — no auth cookie needed.
    mockMvc
        .perform(get("/delivery/" + publicKey).header("host", "localhost"))
        .andExpect(status().isOk());
  }

  private static long siteIdByName(String name) {
    return jdbiTest.withHandle(
        handle ->
            handle
                .createQuery("select id from site where name = :name")
                .bind("name", name)
                .mapTo(Long.class)
                .one());
  }
}
