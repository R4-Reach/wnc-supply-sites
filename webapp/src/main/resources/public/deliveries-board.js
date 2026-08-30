/* Drag-and-drop for the deliveries kanban board. Dragging a card to another
   column POSTs the new status to the server, then moves the card in place and
   updates the per-column counts. On any server error the page reloads so the UI
   never drifts from the persisted state. */
(function () {
  "use strict";

  var board = document.querySelector(".kanban-board");
  if (!board) {
    return;
  }

  board.addEventListener("dragstart", function (event) {
    var card = event.target.closest(".kanban-card");
    if (!card) {
      return;
    }
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", card.dataset.publicKey);
    card.classList.add("dragging");
  });

  board.addEventListener("dragend", function (event) {
    var card = event.target.closest(".kanban-card");
    if (card) {
      card.classList.remove("dragging");
    }
  });

  board.querySelectorAll(".kanban-cards").forEach(function (zone) {
    zone.addEventListener("dragover", function (event) {
      event.preventDefault();
      event.dataTransfer.dropEffect = "move";
      zone.classList.add("drag-over");
    });

    zone.addEventListener("dragleave", function (event) {
      if (!zone.contains(event.relatedTarget)) {
        zone.classList.remove("drag-over");
      }
    });

    zone.addEventListener("drop", function (event) {
      event.preventDefault();
      zone.classList.remove("drag-over");

      var key = event.dataTransfer.getData("text/plain");
      if (!key) {
        return;
      }
      var card = board.querySelector(
        '.kanban-card[data-public-key="' + CSS.escape(key) + '"]'
      );
      if (!card) {
        return;
      }
      var sourceZone = card.parentElement;
      if (sourceZone === zone) {
        return;
      }
      moveCard(card, sourceZone, zone);
    });
  });

  function moveCard(card, sourceZone, targetZone) {
    var body =
      "publicUrlKey=" +
      encodeURIComponent(card.dataset.publicKey) +
      "&status=" +
      encodeURIComponent(targetZone.dataset.status);

    fetch("/dispatch/deliveries/set-status", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: body,
    })
      .then(function (response) {
        if (!response.ok) {
          throw new Error("set-status failed: " + response.status);
        }
        targetZone.appendChild(card);
        updateCount(sourceZone);
        updateCount(targetZone);
      })
      .catch(function () {
        window.location.reload();
      });
  }

  function updateCount(zone) {
    var count = zone.querySelectorAll(".kanban-card").length;
    zone.closest(".kanban-column").querySelector(".kanban-count").textContent = count;
  }
})();
