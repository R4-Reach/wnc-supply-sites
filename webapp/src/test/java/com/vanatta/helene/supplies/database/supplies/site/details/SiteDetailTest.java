package com.vanatta.helene.supplies.database.supplies.site.details;

import static com.vanatta.helene.supplies.database.TestConfiguration.jdbiTest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.manage.contact.ContactDao;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class SiteDetailTest {

  /**
   * Validate that website link properly strips 'http://' from readable display, and prepends 'http'
   * properly for href.
   */
  @Nested
  class WebsiteLink {

    @Test
    void simpleWebsiteLink() {
      String input = "link.com";
      var output = new SiteDetailController.WebsiteLink(input);
      assertThat(output.getHref()).isEqualTo("http://link.com");
      assertThat(output.getTitle()).isEqualTo("link.com");
    }

    @Test
    void websiteLink() {
      String input = "www.link.com";
      var output = new SiteDetailController.WebsiteLink(input);
      assertThat(output.getHref()).isEqualTo("http://www.link.com");
      assertThat(output.getTitle()).isEqualTo("www.link.com");
    }

    @Test
    void websiteLinkWithHttp() {
      String input = "http://www.link.com";
      var output = new SiteDetailController.WebsiteLink(input);
      assertThat(output.getHref()).isEqualTo("http://www.link.com");
      assertThat(output.getTitle()).isEqualTo("www.link.com");
    }

    @Test
    void websiteLinkWithHttps() {
      String input = "https://www.link.com";
      var output = new SiteDetailController.WebsiteLink(input);
      assertThat(output.getHref()).isEqualTo("https://www.link.com");
      assertThat(output.getTitle()).isEqualTo("www.link.com");
    }

    @Test
    void stripsTrailingSlash() {
      String input = "www.link.com/";
      var output = new SiteDetailController.WebsiteLink(input);
      assertThat(output.getHref()).isEqualTo("http://www.link.com");
      assertThat(output.getTitle()).isEqualTo("www.link.com");
    }
  }

  @Test
  void fetchAdditionalContacts() {
    String siteName = TestConfiguration.addSite();
    long siteId = TestConfiguration.getSiteId(siteName);

    ContactDao.addAdditionalSiteManager(jdbiTest, siteId, "name", "123-333-4444");

    List<SiteDetailDao.SiteContact> contacts =
        SiteDetailDao.lookupAdditionalSiteContacts(jdbiTest, siteId);
    assertThat(contacts).containsExactly(new SiteDetailDao.SiteContact("name", "11233334444"));
  }
}
