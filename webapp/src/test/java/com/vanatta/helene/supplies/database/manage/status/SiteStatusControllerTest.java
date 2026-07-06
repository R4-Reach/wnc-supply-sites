package com.vanatta.helene.supplies.database.manage.status;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanatta.helene.supplies.database.TestConfiguration;
import com.vanatta.helene.supplies.database.data.SiteType;
import com.vanatta.helene.supplies.database.supplies.site.details.SiteDetailDao;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SiteStatusControllerTest {
  SiteStatusController selectSiteController = new SiteStatusController(TestConfiguration.jdbiTest);

  @Nested
  class UpdateStatus {
    long siteId = TestConfiguration.getSiteId("site1");

    private void toggleFlag(SiteStatusController.EnumStatusUpdateFlag flag, String value) {
      selectSiteController.updateStatus(
          Map.of(
              "siteId",
              String.valueOf(siteId), //
              "statusFlag",
              flag.getText(),
              "newValue",
              String.valueOf(value)));
    }

    private void toggleFlag(SiteStatusController.EnumStatusUpdateFlag flag, boolean value) {
      toggleFlag(flag, String.valueOf(value));
    }

    @Test
    void siteAcceptingSupplies() {
      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.ACCEPTING_SUPPLIES, false);
      var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.isAcceptingDonations()).isFalse();

      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.ACCEPTING_SUPPLIES, true);
      details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.isAcceptingDonations()).isTrue();
    }

    @Test
    void siteDistributingSupplies() {
      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.DISTRIBUTING_SUPPLIES, false);
      var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.isDistributingSupplies()).isFalse();

      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.DISTRIBUTING_SUPPLIES, true);
      details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.isDistributingSupplies()).isTrue();
    }

    @Test
    void siteType() {
      toggleFlag(
          SiteStatusController.EnumStatusUpdateFlag.SITE_TYPE, SiteType.SUPPLY_HUB.getText());
      var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.getSiteType()).isEqualTo(SiteType.SUPPLY_HUB.getText());

      toggleFlag(
          SiteStatusController.EnumStatusUpdateFlag.SITE_TYPE,
          SiteType.DISTRIBUTION_CENTER.getText());
      details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.getSiteType()).isEqualTo(SiteType.DISTRIBUTION_CENTER.getText());

      toggleFlag(
          SiteStatusController.EnumStatusUpdateFlag.SITE_TYPE, SiteType.FOOD_PANTRY.getText());
      details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.getSiteType()).isEqualTo(SiteType.FOOD_PANTRY.getText());
    }

    @Test
    void siteVisibleToPublic() {
      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.PUBLICLY_VISIBLE, false);
      var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.isPubliclyVisible()).isFalse();

      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.PUBLICLY_VISIBLE, true);
      details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.isPubliclyVisible()).isTrue();
    }

    @Test
    void siteActive() {
      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.ACTIVE, false);
      var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.isActive()).isFalse();

      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.ACTIVE, true);
      details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.isActive()).isTrue();
    }

    @Test
    void updateSiteInactiveReasons() {
      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.INACTIVE_REASON, "site is inactive");
      var details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.getInactiveReason()).isEqualTo("site is inactive");

      toggleFlag(SiteStatusController.EnumStatusUpdateFlag.INACTIVE_REASON, "");
      details = SiteDetailDao.lookupSiteById(TestConfiguration.jdbiTest, siteId);
      assertThat(details.getInactiveReason()).isEqualTo("");
    }
  }
}
