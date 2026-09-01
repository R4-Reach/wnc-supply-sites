package org.r4reach.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.auth.UserRole;
import org.r4reach.data.ItemStatus;
import org.r4reach.data.SiteType;
import org.r4reach.dispatch.DispatchDao;
import org.springframework.web.servlet.ModelAndView;

class DeliveryBoardControllerTest {

  private static final List<UserRole> DISPATCHER = List.of(UserRole.DISPATCHER);
  private static final List<UserRole> NOT_DISPATCHER = List.of(UserRole.DRIVER_ADMIN);
  private static final String NO_PHONE = null;

  private final DeliveryBoardController controller = new DeliveryBoardController(jdbiTest);

  @BeforeAll
  static void setupDatabase() {
    TestConfiguration.setupDatabase();
  }

  @Test
  void boardGroupsDeliveriesByStatusAndExcludesCancelled() {
    var view = controller.board(DISPATCHER);

    assertThat(view.getViewName()).isEqualTo("delivery/deliveries-board");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> columns = (List<Map<String, Object>>) view.getModel().get("columns");

    // The seven forward columns, in enum order; cancelled is not a column.
    assertThat(columns)
        .extracting(column -> column.get("label"))
        .containsExactly(
            "Driver Volunteered",
            "Creating Dispatch",
            "Assigning Driver",
            "Confirming",
            "Confirmed",
            "Delivery In Progress",
            "Delivery Completed");

    Map<String, Object> creatingDispatch =
        columns.stream()
            .filter(column -> "Creating Dispatch".equals(column.get("label")))
            .findFirst()
            .orElseThrow();

    @SuppressWarnings("unchecked")
    List<Delivery> deliveries = (List<Delivery>) creatingDispatch.get("deliveries");
    assertThat(deliveries).extracting(Delivery::getPublicKey).contains("BETA");
  }

  @Test
  void boardRedirectsWhenNotDispatcher() {
    assertThat(controller.board(NOT_DISPATCHER).getViewName()).isEqualTo("redirect:/");
  }

  @Test
  void setStatusMovesDeliveryToNewColumn() {
    var delivery = DeliveryHelper.withNewDelivery();

    var response = controller.setStatus(DISPATCHER, delivery.getPublicKey(), "ASSIGNING_DRIVER");

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(
            DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey())
                .orElseThrow()
                .getDeliveryStatus())
        .isEqualTo(DeliveryStatus.ASSIGNING_DRIVER.getAirtableName());
  }

  @Test
  void setStatusRejectsCancelledAsADropTarget() {
    var delivery = DeliveryHelper.withNewDelivery();

    var response = controller.setStatus(DISPATCHER, delivery.getPublicKey(), "DELIVERY_CANCELLED");

    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void setStatusRejectsUnknownStatus() {
    var delivery = DeliveryHelper.withNewDelivery();

    var response = controller.setStatus(DISPATCHER, delivery.getPublicKey(), "NONSENSE");

    assertThat(response.getStatusCode().value()).isEqualTo(400);
  }

  @Test
  void setStatusForbiddenWhenNotDispatcher() {
    var delivery = DeliveryHelper.withNewDelivery();

    var response =
        controller.setStatus(NOT_DISPATCHER, delivery.getPublicKey(), "ASSIGNING_DRIVER");

    assertThat(response.getStatusCode().value()).isEqualTo(403);
    // The status must be unchanged.
    assertThat(
            DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, delivery.getPublicKey())
                .orElseThrow()
                .getDeliveryStatus())
        .isEqualTo(delivery.getDeliveryStatus());
  }

  @Test
  void createPersistsDeliveryAndRedirectsToBoard() {
    long siteId = siteIdByName("site2");
    long driverId = insertDriver("ControllerTestDriver", "444-333-7022");

    var view =
        controller.create(
            DISPATCHER,
            siteId,
            siteId,
            "DRIVER_VOLUNTEERED",
            "2026-05-15",
            null,
            driverId,
            "handle with care",
            List.of("Water", "Blankets"));

    assertThat(view.getViewName()).isEqualTo("redirect:/dispatch/deliveries");

    Delivery created =
        DeliveryDao.fetchAllDeliveries(jdbiTest).stream()
            .filter(delivery -> "ControllerTestDriver".equals(delivery.getDriverName()))
            .findFirst()
            .orElseThrow();
    assertThat(created.getDeliveryStatus())
        .isEqualTo(DeliveryStatus.DRIVER_VOLUNTEERED.getAirtableName());
    assertThat(created.getItemList()).containsExactlyInAnyOrder("Water", "Blankets");
  }

  @Test
  void newDeliveryFormSeedsBlankFieldsAndDropdownOptions() {
    // The template echoes every text field via {{field}} and Mustache is strict, so the initial
    // GET must supply them all — blank — or rendering 500s. The dropdowns and item picker need
    // their option lists too.
    var view = controller.newDelivery(DISPATCHER, NO_PHONE, "DRIVER_VOLUNTEERED");

    assertThat(view.getViewName()).isEqualTo("delivery/delivery-create");
    assertThat(view.getModel())
        .containsEntry("targetDeliveryDate", "")
        .containsEntry("dispatcherNotes", "")
        .containsKey("sitesFrom")
        .containsKey("sitesTo")
        .containsKey("dispatchers")
        .containsKey("drivers")
        .containsKey("selectedItems")
        .containsKey("availableItems")
        .containsKey("catalogItems");
  }

  @Test
  void createWithoutSitesReturnsFormWithError() {
    var view =
        controller.create(
            DISPATCHER, null, null, "DRIVER_VOLUNTEERED", null, null, null, null, null);

    assertThat(view.getViewName()).isEqualTo("delivery/delivery-create");
    assertThat(view.getModel().get("errorMessage")).isNotNull();
    assertThat(view.getModel().get("sitesFrom")).isNotNull();
  }

  @Test
  void detailRendersEditFormForExistingDelivery() {
    var delivery = DeliveryHelper.withNewDelivery();

    var view = controller.detail(DISPATCHER, delivery.getPublicKey(), null, null);

    assertThat(view.getViewName()).isEqualTo("delivery/delivery-detail");
    assertThat(view.getModel())
        .containsEntry("publicKey", delivery.getPublicKey())
        .containsKey("sitesFrom")
        .containsKey("statuses")
        .containsKey("catalogItems")
        .containsKey("selectedItems");
  }

  @Test
  void detailRedirectsWhenNotDispatcher() {
    var delivery = DeliveryHelper.withNewDelivery();
    assertThat(controller.detail(NOT_DISPATCHER, delivery.getPublicKey(), null, null).getViewName())
        .isEqualTo("redirect:/");
  }

  @Test
  void detailRedirectsToBoardWhenDeliveryMissing() {
    assertThat(controller.detail(DISPATCHER, "does-not-exist", null, null).getViewName())
        .isEqualTo("redirect:/dispatch/deliveries");
  }

  @Test
  void updatePersistsEditsAndReplacesItemList() {
    long siteId = siteIdByName("site2");
    long driverId = insertDriver("UpdateTestDriver", "444-333-9001");
    String publicKey =
        DeliveryDao.createDelivery(
            jdbiTest,
            DeliveryDao.CreateDeliveryRequest.builder()
                .fromSiteId(siteId)
                .toSiteId(siteId)
                .deliveryStatus(DeliveryStatus.DRIVER_VOLUNTEERED)
                .items(List.of("Water", "Blankets"))
                .build());

    var view =
        controller.update(
            DISPATCHER,
            publicKey,
            siteId,
            siteId,
            "ASSIGNING_DRIVER",
            "2026-06-01",
            null,
            driverId,
            "updated notes",
            List.of("Blankets", "Diapers"));

    assertThat(view.getViewName()).isEqualTo("redirect:/dispatch/deliveries/" + publicKey);

    Delivery updated = DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, publicKey).orElseThrow();
    assertThat(updated.getDeliveryStatus())
        .isEqualTo(DeliveryStatus.ASSIGNING_DRIVER.getAirtableName());
    assertThat(updated.getDriverName()).isEqualTo("UpdateTestDriver");
    // The submitted picker set is authoritative: Water is dropped, Diapers added.
    assertThat(updated.getItemList()).containsExactlyInAnyOrder("Blankets", "Diapers");
  }

  @Test
  void updateWithoutSitesReturnsDetailFormWithErrorPreservingItems() {
    var delivery = DeliveryHelper.withNewDelivery();

    var view =
        controller.update(
            DISPATCHER,
            delivery.getPublicKey(),
            null,
            null,
            "DRIVER_VOLUNTEERED",
            null,
            null,
            null,
            null,
            List.of("Water"));

    assertThat(view.getViewName()).isEqualTo("delivery/delivery-detail");
    assertThat(view.getModel().get("errorMessage")).isNotNull();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> selected =
        (List<Map<String, Object>>) view.getModel().get("selectedItems");
    assertThat(selected).extracting(chip -> chip.get("name")).containsExactly("Water");
  }

  @Test
  void updateForbiddenWhenNotDispatcher() {
    var delivery = DeliveryHelper.withNewDelivery();
    var view =
        controller.update(
            NOT_DISPATCHER,
            delivery.getPublicKey(),
            1L,
            1L,
            "DRIVER_VOLUNTEERED",
            null,
            null,
            null,
            null,
            List.of());
    assertThat(view.getViewName()).isEqualTo("redirect:/");
  }

  @Test
  void availableItemsReturnsGiveableGoodsForPickup() {
    long siteId = TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.SUPPLY_HUB));
    TestConfiguration.addItemToSite(siteId, ItemStatus.AVAILABLE, "water", -931);
    TestConfiguration.addItemToSite(siteId, ItemStatus.OVERSUPPLY, "batteries", -932);
    TestConfiguration.addItemToSite(siteId, ItemStatus.NEEDED, "soap", -933);

    var response = controller.availableItems(DISPATCHER, siteId);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    // A supply hub can give its available and oversupply goods, but not the things it needs.
    assertThat(response.getBody()).containsExactlyInAnyOrder("water", "batteries");
  }

  @Test
  void availableItemsForbiddenWhenNotDispatcher() {
    assertThat(controller.availableItems(NOT_DISPATCHER, 1L).getStatusCode().value())
        .isEqualTo(403);
  }

  /**
   * The dispatcher "match goods" action: it reuses the needs match (pickup's available supply
   * intersected with the drop-off's needs) and adds the matched goods to the delivery, skipping
   * goods already on it, then reloads the detail page.
   */
  @Nested
  class MatchGoods {

    @Test
    void addsMatchedGoodsWithoutDuplicates() {
      long fromSiteId = TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.SUPPLY_HUB));
      TestConfiguration.addItemToSite(fromSiteId, ItemStatus.AVAILABLE, "water", -901);
      TestConfiguration.addItemToSite(fromSiteId, ItemStatus.AVAILABLE, "gloves", -902);
      TestConfiguration.addItemToSite(fromSiteId, ItemStatus.OVERSUPPLY, "batteries", -903);

      long toSiteId =
          TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.DISTRIBUTION_CENTER));
      TestConfiguration.addItemToSite(toSiteId, ItemStatus.NEEDED, "gloves", -904);
      TestConfiguration.addItemToSite(toSiteId, ItemStatus.URGENTLY_NEEDED, "batteries", -905);
      TestConfiguration.addItemToSite(toSiteId, ItemStatus.NEEDED, "soap", -906);

      String publicKey =
          DeliveryDao.createDelivery(
              jdbiTest,
              DeliveryDao.CreateDeliveryRequest.builder()
                  .fromSiteId(fromSiteId)
                  .toSiteId(toSiteId)
                  .deliveryStatus(DeliveryStatus.CREATING_DISPATCH)
                  .items(List.of("gloves"))
                  .build());

      ModelAndView result = controller.matchGoods(DISPATCHER, publicKey);

      assertThat(result.getViewName())
          .isEqualTo(
              "redirect:/dispatch/deliveries/" + publicKey + "?matchAdded=1&matchCandidates=2");
      assertThat(fetchItems(publicKey)).containsExactlyInAnyOrder("gloves", "batteries");

      // Re-running is idempotent: the already-added goods are not duplicated.
      controller.matchGoods(DISPATCHER, publicKey);
      assertThat(fetchItems(publicKey)).containsExactlyInAnyOrder("gloves", "batteries");
    }

    @Test
    void noMatchesAddsNothing() {
      long fromSiteId = TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.SUPPLY_HUB));
      TestConfiguration.addItemToSite(fromSiteId, ItemStatus.AVAILABLE, "water", -911);

      long toSiteId =
          TestConfiguration.getSiteId(TestConfiguration.addSite(SiteType.DISTRIBUTION_CENTER));
      TestConfiguration.addItemToSite(toSiteId, ItemStatus.NEEDED, "soap", -912);

      String publicKey =
          DeliveryDao.createDelivery(
              jdbiTest,
              DeliveryDao.CreateDeliveryRequest.builder()
                  .fromSiteId(fromSiteId)
                  .toSiteId(toSiteId)
                  .deliveryStatus(DeliveryStatus.CREATING_DISPATCH)
                  .build());

      controller.matchGoods(DISPATCHER, publicKey);

      assertThat(fetchItems(publicKey)).isEmpty();
    }

    @Test
    void forbiddenWhenNotDispatcher() {
      var delivery = DeliveryHelper.withNewDelivery();
      assertThat(controller.matchGoods(NOT_DISPATCHER, delivery.getPublicKey()).getViewName())
          .isEqualTo("redirect:/");
    }

    private List<String> fetchItems(String publicKey) {
      return DeliveryDao.fetchDeliveryByPublicKey(jdbiTest, publicKey).orElseThrow().getItemList();
    }
  }

  private static long insertDriver(String name, String phone) {
    DispatchDao.createDriver(jdbiTest, phone, name);
    return DispatchDao.fetchAll(jdbiTest).stream()
        .filter(row -> name.equals(row.getFullName()))
        .findFirst()
        .orElseThrow()
        .getWssUserId();
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
