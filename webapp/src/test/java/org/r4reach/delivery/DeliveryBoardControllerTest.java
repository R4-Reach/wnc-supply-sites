package org.r4reach.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.r4reach.TestConfiguration.jdbiTest;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.r4reach.TestConfiguration;
import org.r4reach.auth.UserRole;
import org.r4reach.dispatch.DispatchDao;

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
            "Water\nBlankets");

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
    // GET must supply them all — blank — or rendering 500s. The dispatcher and driver dropdowns
    // need their option lists too.
    var view = controller.newDelivery(DISPATCHER, NO_PHONE, "DRIVER_VOLUNTEERED");

    assertThat(view.getViewName()).isEqualTo("delivery/delivery-create");
    assertThat(view.getModel())
        .containsEntry("targetDeliveryDate", "")
        .containsEntry("dispatcherNotes", "")
        .containsEntry("items", "")
        .containsKey("dispatchers")
        .containsKey("drivers");
  }

  @Test
  void createWithoutSitesReturnsFormWithError() {
    var view =
        controller.create(
            DISPATCHER, null, null, "DRIVER_VOLUNTEERED", null, null, null, null, null);

    assertThat(view.getViewName()).isEqualTo("delivery/delivery-create");
    assertThat(view.getModel().get("errorMessage")).isNotNull();
    assertThat(view.getModel().get("sites")).isNotNull();
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
