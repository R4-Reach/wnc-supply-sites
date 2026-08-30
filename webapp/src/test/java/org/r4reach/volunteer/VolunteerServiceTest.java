package org.r4reach.volunteer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.util.URLKeyGenerator;

public class VolunteerServiceTest {

  private VolunteerService volunteerService;

  @BeforeAll
  static void setup() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void errorIfItemIdDoesNotExist() {
    long siteId = TestConfiguration.getSiteId("site1");

    VolunteerService.Site site = VolunteerDao.fetchSiteItems(jdbiTest, siteId);

    List<Long> itemIds =
        new java.util.ArrayList<>(
            site.getItems().stream().map(VolunteerService.Item::getId).toList());

    // Adding fake item id to itemIds
    itemIds.add(-66L);

    String urlKey = URLKeyGenerator.generateUrlKey();

    VolunteerService.DeliveryForm form =
        VolunteerService.DeliveryForm.builder()
            .site(String.valueOf(siteId))
            .neededItems(itemIds)
            .volunteerContact("1231231234")
            .volunteerName("John Test")
            .urlKey(urlKey)
            .build();

    Exception exception =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> volunteerService.createVolunteerDelivery(jdbiTest, form),
            "Expected an exception due to Invalid item id URL Key.");

    // Check that the created delivery was removed after error
    List<VolunteerService.VolunteerDeliveryItem> deliveryItems =
        jdbiTest.withHandle(
            handle ->
                handle
                    .createQuery(
                        """
            SELECT *
            FROM volunteer_delivery
            WHERE volunteer_delivery.site_id = :siteId
            """)
                    .bind("siteId", siteId)
                    .mapToBean(VolunteerService.VolunteerDeliveryItem.class)
                    .list());
    assertThat(deliveryItems.isEmpty()).isTrue();
  }

  // todo: getVolunteerDeliveryRequest -> returns correct delivery request

  // todo: getVolunteerDeliveryRequest -> returns empty optional if urlKey does not exist

  // todo: verifyVolunteerPortalAccess -> returns correct access

  // todo: verifyVolunteerPortalAccess -> Returns an empty hashmap if no access

  // todo: VolunteerService.VolunteerDeliveryRequest.scrubDataBasedOnStatus -> returns correct
  // information based on each status

}
