package org.r4reach.crawl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.springframework.test.web.servlet.MvcResult;

/**
 * Smoke crawler: signed in as a full-admin user, walks every in-app link reachable from the known
 * entry points and fails if any page returns a 404 or a server error (5xx, or an unhandled handler
 * exception, which in production is a 500). This is the net that catches broken links and dead
 * routes across the whole authed surface without anyone hand-listing every URL.
 *
 * <p>GET-only by design: submitting forms here would write the database and fire SMS. Form flows
 * are covered by the per-feature tests, not this crawl.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SiteCrawlTest {

  // A full-admin user (every assignable role) so the crawl reaches admin-gated pages too.
  private static final String NUMBER = "5556667788";

  // Where the walk starts. Deeper URLs are discovered by parsing each page's links; these are the
  // routes with no discovering link (the landing pages a signed-in user is dropped onto).
  private static final List<String> SEED_URLS =
      List.of(
          "/",
          "/registration/",
          "/supplies/needs",
          "/rcommons/dashboard",
          "/rcommons/onboarding",
          "/driver/portal",
          "/manage/new-site/add-site",
          "/admin/merge-items",
          "/volunteer/delivery");
  // Deliberately not seeded: routes that require a query param (siteId, urlKey, site, ...). Bare,
  // they only 400; they get crawled properly when a page links to them with the param filled in.

  // Logging out drops the auth cookie and would blind the rest of the crawl.
  private static final Set<String> SKIP_PATHS = Set.of("/log-out");

  private static final int MAX_PAGES = 500;
  // Both links (href) and asset references (src: scripts, images), so a missing JS/image 404s too.
  private static final Pattern LINK_REF = Pattern.compile("(?:href|src)\\s*=\\s*\"([^\"]*)\"");

  @Autowired private MockMvc mockMvc;

  private Cookie authCookie;

  @BeforeEach
  void setup() {
    SetupPasswordHelper.setup();
    TestConfiguration.setupDatabase();
    SetupPasswordHelper.withRegisteredNumber(NUMBER);
    UserRole.assignableRoles().forEach(role -> UserRoleService.grantRole(jdbiTest, NUMBER, role));
    authCookie = new Cookie("auth", LoginDao.generateAuthToken(jdbiTest, NUMBER));
  }

  @Test
  void noBrokenLinksAcrossTheAuthedSurface() throws Exception {
    Deque<String> queue = new ArrayDeque<>(SEED_URLS);
    Set<String> visited = new HashSet<>();
    // url -> what went wrong; sorted so the failure message is stable and easy to scan.
    TreeMap<String, String> failures = new TreeMap<>();

    while (!queue.isEmpty() && visited.size() < MAX_PAGES) {
      String url = queue.poll();
      if (!visited.add(url)) {
        continue;
      }

      MvcResult result;
      try {
        result =
            mockMvc.perform(get(url).header("host", "localhost").cookie(authCookie)).andReturn();
      } catch (Exception e) {
        // A view-render error (e.g. a template referencing a key the model never set) escapes
        // perform() rather than becoming a status. In production it is a 500; record and move on.
        failures.put(
            url,
            "threw " + rootCause(e).getClass().getSimpleName() + ": " + rootCause(e).getMessage());
        continue;
      }
      // Judge by the final status. Handler exceptions Spring resolves (e.g. a missing required
      // param -> 400) are the app behaving correctly, not a broken page; only 404/5xx count.
      int status = result.getResponse().getStatus();

      if (status == 404 || status >= 500) {
        failures.put(url, "status " + status);
      } else if (status >= 300 && status < 400) {
        // Not a failure, but follow in-app redirects so the crawl reaches what they point at.
        enqueue(url, result.getResponse().getHeader("Location"), queue, visited);
      } else if (isHtml(result.getResponse().getContentType())) {
        // Only HTML carries links worth following; scanning CSS/JS/JSON would invent bogus URLs.
        extractLinks(result.getResponse().getContentAsString())
            .forEach(link -> enqueue(url, link, queue, visited));
      }
    }

    assertThat(failures)
        .withFailMessage(
            "Crawled %d pages; found %d broken:%n%s",
            visited.size(),
            failures.size(),
            failures.entrySet().stream()
                .map(e -> "  " + e.getKey() + " -> " + e.getValue())
                .reduce("", (a, b) -> a + b + "\n"))
        .isEmpty();
  }

  private Throwable rootCause(Throwable t) {
    Throwable cause = t;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause;
  }

  private boolean isHtml(String contentType) {
    return contentType != null && contentType.contains("text/html");
  }

  private List<String> extractLinks(String html) {
    List<String> links = new ArrayList<>();
    Matcher m = LINK_REF.matcher(html);
    while (m.find()) {
      links.add(m.group(1));
    }
    return links;
  }

  /**
   * Resolves a reference (link or asset, absolute or relative) against the page it was found on and
   * enqueues it if it is a same-app path not already seen. Skips external URLs (a scheme like
   * http:/mailto:/javascript:), protocol-relative {@code //host} references, and fragments.
   */
  private void enqueue(String fromPath, String ref, Deque<String> queue, Set<String> visited) {
    if (ref == null || ref.isBlank()) {
      return;
    }
    URI resolved;
    try {
      resolved = URI.create(fromPath).resolve(ref.trim());
    } catch (IllegalArgumentException malformed) {
      return;
    }
    // A scheme (http:, mailto:, javascript:) or an authority (//cdn) means it leaves the app.
    if (resolved.isAbsolute() || resolved.getAuthority() != null) {
      return;
    }
    String path = resolved.getRawPath();
    if (path == null || !path.startsWith("/") || SKIP_PATHS.contains(path)) {
      return;
    }
    String target = resolved.getRawQuery() == null ? path : path + "?" + resolved.getRawQuery();
    if (!visited.contains(target)) {
      queue.add(target);
    }
  }
}
