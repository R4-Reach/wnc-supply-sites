package org.r4reach.volunteer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.*;

import java.util.List;
import java.util.Objects;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.data.ItemStatus;
import org.r4reach.util.URLKeyGenerator;

public class VolunteerDaoTest {

  @BeforeAll
  static void setup() {
    TestConfiguration.setupDatabase();
  }

  /** fetchSiteSelect */
  @Test
  void retrievesSiteSelect() {
    List<VolunteerService.SiteSelect> results =
        VolunteerDao.fetchSiteSelect(jdbiTest, List.of("NC", "KY", "VA", "TN"));

    // Retrieves sites that are active and accepting donations
    assertThat(results.size() == 1).isTrue();

    // Add a site without items
    String inactiveSiteName = addSite("inactiveSite");
    Long inactiveSiteId = TestConfiguration.getSiteId(inactiveSiteName);

    List<VolunteerService.SiteSelect> updatedResults =
        VolunteerDao.fetchSiteSelect(jdbiTest, List.of("NC", "KY", "VA", "TN"));

    // Validate that updated result and results are the same size
    assertThat(results.size() == updatedResults.size()).isTrue();
  }

  /** fetchSiteItems */
  @Test
  void correctlyRetrievesNeededItems() {
    long siteId = TestConfiguration.getSiteId("site1");

    VolunteerService.Site result = VolunteerDao.fetchSiteItems(jdbiTest, siteId);

    // Retrieved site name is correct
    assertThat(result.getName()).isEqualTo("site1");

    // Retrieved item count is one
    assertThat(result.getItems().size() == 1).isTrue();

    addItemToSite(siteId, ItemStatus.NEEDED, "heater", -80);

    // Update site item
    VolunteerService.Site resultAfterUpdate = VolunteerDao.fetchSiteItems(jdbiTest, siteId);

    // Check site item count was increased
    assertThat(resultAfterUpdate.getItems().size() == 2).isTrue();
  }

  @Test
  void neededItemsRequestReturnsNull() {
    // Returns null if item id does not exist
    VolunteerService.Site result = VolunteerDao.fetchSiteItems(jdbiTest, 567L);
    assertThat(result).isNull();
  }

  /** createVolunteerDelivery */
  @Test
  void createsVolunteerDelivery() {
    long siteId = TestConfiguration.getSiteId("site1");

    addItemToSite(siteId, ItemStatus.NEEDED, "batteries", -66);
    addItemToSite(siteId, ItemStatus.URGENTLY_NEEDED, "soap", -65);

    VolunteerService.Site site = VolunteerDao.fetchSiteItems(jdbiTest, siteId);

    List<Long> itemIds =
        site.getItems().stream().map(VolunteerService.Item::getId).toList().subList(0, 1);

    String urlKey = URLKeyGenerator.generateUrlKey();

    VolunteerService.DeliveryForm form =
        VolunteerService.DeliveryForm.builder()
            .site(String.valueOf(siteId))
            .neededItems(itemIds)
            .volunteerContact("1231231234")
            .volunteerName("John Test")
            .urlKey(urlKey)
            .build();

    Long volunteerDeliveryId = VolunteerDao.createVolunteerDelivery(jdbiTest, form);

    assertThat(volunteerDeliveryId).isNotNull();

    // Create volunteer Items
    VolunteerDao.createVolunteerDeliveryItems(jdbiTest, volunteerDeliveryId, itemIds);

    // Check that the created volunteer delivery row has the correct information
    VolunteerService.VolunteerDelivery volunteerDelivery =
        jdbiTest.withHandle(
            handle ->
                handle
                    .createQuery(
                        """
        SELECT id, volunteer_name, volunteer_phone, site_id, url_key
        FROM volunteer_delivery
        WHERE id = :id
        LIMIT 1
        """)
                    .bind("id", volunteerDeliveryId)
                    .mapToBean(VolunteerService.VolunteerDelivery.class)
                    .one());

    assertThat(
            Objects.equals(
                volunteerDelivery,
                VolunteerService.VolunteerDelivery.builder()
                    .id(volunteerDeliveryId)
                    .volunteerPhone("1231231234")
                    .volunteerName("John Test")
                    .siteId(siteId)
                    .urlKey(urlKey)
                    .build()))
        .isTrue();

    // Check that the correct amount of volunteer_delivery_id was created
    List<VolunteerService.VolunteerDeliveryItem> deliveryItems =
        jdbiTest.withHandle(
            handle ->
                handle
                    .createQuery(
                        """
            SELECT id, site_item_id, volunteer_delivery_id
            FROM volunteer_delivery_item
            WHERE volunteer_delivery_item.volunteer_delivery_id = :deliveryId
            """)
                    .bind("deliveryId", volunteerDeliveryId)
                    .mapToBean(VolunteerService.VolunteerDeliveryItem.class)
                    .list());

    assertThat(deliveryItems.size() == itemIds.size()).isTrue();
  }

  @Test
  void errorIfURLKeyIsNotUnique() {
    long siteId = TestConfiguration.getSiteId("site1");

    VolunteerService.Site site = VolunteerDao.fetchSiteItems(jdbiTest, siteId);

    List<Long> itemIds = site.getItems().stream().map(VolunteerService.Item::getId).toList();

    String urlKey = URLKeyGenerator.generateUrlKey();

    VolunteerService.DeliveryForm form1 =
        VolunteerService.DeliveryForm.builder()
            .site(String.valueOf(siteId))
            .neededItems(itemIds)
            .volunteerContact("1231231234")
            .volunteerName("John Test")
            .urlKey(urlKey)
            .build();

    VolunteerService.DeliveryForm form2 =
        VolunteerService.DeliveryForm.builder()
            .site(String.valueOf(siteId))
            .neededItems(itemIds)
            .volunteerContact("2223334444")
            .volunteerName("Sally Test")
            .urlKey(urlKey)
            .build();

    Long volunteerDeliveryId = VolunteerDao.createVolunteerDelivery(jdbiTest, form1);

    Exception exception =
        Assertions.assertThrows(
            UnableToExecuteStatementException.class,
            () -> VolunteerDao.createVolunteerDelivery(jdbiTest, form2),
            "Expected an exception due to duplicate URL Key.");
  }
}
