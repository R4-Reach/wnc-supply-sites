package org.r4reach.manage.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;

class ContactDaoTest {

  @BeforeEach
  void setUp() {
    TestConfiguration.setupDatabase();
  }

  final ContactDao.SiteManager manager =
      ContactDao.SiteManager.builder()
          .name("my new name") //
          .phone("11234567890")
          .build();

  @Test
  void insertAndRead() {
    long siteId = TestConfiguration.getSiteId("site1");

    ContactDao.addAdditionalSiteManager(jdbiTest, siteId, manager.getName(), manager.getPhone());

    List<ContactDao.SiteManager> managers = ContactDao.getManagers(jdbiTest, siteId);

    assertThat(managers).hasSize(1);
    assertThat(managers.getFirst().getName()).isEqualTo(manager.getName());
    assertThat(managers.getFirst().getPhone()).isEqualTo(manager.getPhone());
  }

  @Test
  void updateManager() {
    long siteId = TestConfiguration.getSiteId("site1");

    ContactDao.addAdditionalSiteManager(jdbiTest, siteId, manager.getName(), manager.getPhone());

    long newId = ContactDao.getManagers(jdbiTest, siteId).getFirst().getId();

    var update = manager.toBuilder().id(newId).name("update name").phone("10000000000").build();

    ContactDao.updateAdditionalSiteManager(jdbiTest, siteId, update);

    List<ContactDao.SiteManager> managers = ContactDao.getManagers(jdbiTest, siteId);

    assertThat(managers).hasSize(1);
    assertThat(managers.getFirst()).isEqualTo(update);
  }

  @Test
  void removeManager() {
    long siteId = TestConfiguration.getSiteId("site1");

    long managerId =
        ContactDao.addAdditionalSiteManager(
            jdbiTest, siteId, manager.getName(), manager.getPhone());
    ContactDao.removeAdditionalSiteManager(jdbiTest, siteId, managerId);

    List<ContactDao.SiteManager> managers = ContactDao.getManagers(jdbiTest, siteId);

    assertThat(managers).isEmpty();
  }
}
