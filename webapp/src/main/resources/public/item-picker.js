// Delivery item picker: turns the server-rendered checkbox/datalist controls into an
// Airtable-style chip tray. The tray's hidden inputs are the authoritative submission; the raw
// controls' name is stripped once JS is in charge so nothing is submitted twice. Selection is
// constrained to real inventory items (the catalog datalist); the pickup-available checkboxes
// refresh live when the dispatcher changes the pickup site.
(function () {
  const picker = document.querySelector(".item-picker");
  if (!picker) {
    return;
  }

  const tray = picker.querySelector("#item-tray");
  const trayEmpty = picker.querySelector("#item-tray-empty");
  const availableList = picker.querySelector("#item-available-list");
  const availableEmpty = picker.querySelector("#item-available-empty");
  const selectAllButton = picker.querySelector("#item-select-all");
  const catalogInput = picker.querySelector("#item-catalog-input");
  const catalogList = picker.querySelector("#item-catalog-list");
  const message = picker.querySelector("#item-picker-message");
  const availableItemsUrl = picker.dataset.availableItemsUrl;

  // Lower-cased set of names currently on the delivery, for dedupe.
  const selected = new Set();
  tray.querySelectorAll(".item-chip").forEach((chip) => selected.add(chip.dataset.name.toLowerCase()));

  // Canonical (correctly-cased) catalog names, keyed by lower-case, for match validation.
  const catalog = new Map();
  catalogList.querySelectorAll("option").forEach((option) => {
    catalog.set(option.value.toLowerCase(), option.value);
  });

  function announce(text) {
    message.textContent = text;
  }

  function refreshEmptyState() {
    trayEmpty.classList.toggle("hidden", selected.size > 0);
  }

  function checkboxFor(lowerName) {
    return Array.from(availableList.querySelectorAll("input[type=checkbox]")).find(
      (box) => box.value.toLowerCase() === lowerName
    );
  }

  // Adds an item to the tray. Returns false (with a notice) if it is already present.
  function addItem(name, legacy) {
    const lower = name.toLowerCase();
    if (selected.has(lower)) {
      announce(name + " is already on this delivery");
      return false;
    }
    selected.add(lower);

    const chip = document.createElement("span");
    chip.className = "item-chip" + (legacy ? " item-chip-legacy" : "");
    chip.setAttribute("role", "listitem");
    chip.dataset.name = name;

    const label = document.createElement("span");
    label.className = "item-chip-name";
    label.textContent = name;
    chip.appendChild(label);

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "item-chip-remove";
    remove.setAttribute("aria-label", "Remove " + name);
    remove.innerHTML = "&times;";
    chip.appendChild(remove);

    const hidden = document.createElement("input");
    hidden.type = "hidden";
    hidden.name = "items";
    hidden.value = name;
    chip.appendChild(hidden);

    tray.appendChild(chip);
    refreshEmptyState();

    const box = checkboxFor(lower);
    if (box) {
      box.checked = true;
    }
    announce("");
    return true;
  }

  function removeItem(chip) {
    const name = chip.dataset.name;
    const lower = name.toLowerCase();
    // Move focus somewhere predictable before the node disappears.
    const removeButtons = Array.from(tray.querySelectorAll(".item-chip-remove"));
    const index = removeButtons.indexOf(chip.querySelector(".item-chip-remove"));
    const next = removeButtons[index + 1] || removeButtons[index - 1];

    selected.delete(lower);
    const box = checkboxFor(lower);
    if (box) {
      box.checked = false;
    }
    chip.remove();
    refreshEmptyState();

    if (next) {
      next.focus();
    } else {
      catalogInput.focus();
    }
  }

  // Chip removal (event-delegated so it survives re-render).
  tray.addEventListener("click", (event) => {
    const button = event.target.closest(".item-chip-remove");
    if (button) {
      removeItem(button.closest(".item-chip"));
    }
  });

  // Checking an available item adds it; unchecking removes it. Keep the checkbox out of the raw
  // submission (the chip's hidden input carries it instead).
  function wireCheckbox(box) {
    box.removeAttribute("name");
    box.addEventListener("change", () => {
      if (box.checked) {
        addItem(box.value, false);
      } else {
        const chip = tray.querySelector('.item-chip[data-name="' + cssEscape(box.value) + '"]');
        if (chip) {
          removeItem(chip);
        }
      }
    });
  }

  function cssEscape(value) {
    return value.replace(/["\\]/g, "\\$&");
  }

  availableList.querySelectorAll("input[type=checkbox]").forEach(wireCheckbox);

  if (selectAllButton) {
    selectAllButton.addEventListener("click", () => {
      availableList
        .querySelectorAll("input[type=checkbox]")
        .forEach((box) => addItem(box.value, false));
    });
  }

  // Catalog search: only commit values that resolve to a real inventory item.
  catalogInput.removeAttribute("name");
  function commitCatalog() {
    const raw = catalogInput.value.trim();
    if (!raw) {
      return;
    }
    const canonical = catalog.get(raw.toLowerCase());
    if (!canonical) {
      announce('No inventory item matches "' + raw + '"');
      catalogInput.select();
      return;
    }
    if (addItem(canonical, false)) {
      catalogInput.value = "";
      catalogInput.focus();
    }
  }
  catalogInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      event.preventDefault();
      commitCatalog();
    }
  });
  // Selecting a datalist suggestion fires 'change'; only auto-commit an exact catalog match so
  // partial typing that happens to blur does not spuriously reject.
  catalogInput.addEventListener("change", () => {
    if (catalog.has(catalogInput.value.trim().toLowerCase())) {
      commitCatalog();
    }
  });

  // Refresh the pickup-available checkboxes when the pickup site changes (create/edit forms).
  const fromSite = document.querySelector("#fromSiteId");
  if (fromSite && availableItemsUrl) {
    fromSite.addEventListener("change", () => {
      const siteId = fromSite.value;
      if (!siteId) {
        renderAvailable([], false);
        return;
      }
      fetch(availableItemsUrl + "?fromSiteId=" + encodeURIComponent(siteId))
        .then((response) => (response.ok ? response.json() : []))
        .then((names) => renderAvailable(names, true))
        .catch(() => renderAvailable([], true));
    });
  }

  function renderAvailable(names, siteChosen) {
    availableList.innerHTML = "";
    names.forEach((name) => {
      const label = document.createElement("label");
      label.className = "item-available-option";
      const box = document.createElement("input");
      box.type = "checkbox";
      box.value = name;
      box.checked = selected.has(name.toLowerCase());
      const text = document.createElement("span");
      text.textContent = name;
      label.appendChild(box);
      label.appendChild(text);
      availableList.appendChild(label);
      wireCheckbox(box);
    });
    const hasItems = names.length > 0;
    availableEmpty.classList.toggle("hidden", hasItems);
    availableEmpty.textContent = siteChosen
      ? "No items recorded at this site — use catalog search below."
      : "Choose a pickup site to see its available items.";
    if (selectAllButton) {
      selectAllButton.classList.toggle("hidden", !hasItems);
    }
  }
})();
