// Client-side behaviour for the "Assign Tags to Items" table. Assignment state lives on the
// server; everything here is presentation, focus management, and the bulk-apply helper.
(function () {
  const search = document.getElementById("tag-item-search");
  const untaggedOnly = document.getElementById("tag-item-untagged-only");
  const body = document.getElementById("tag-item-body");
  const noMatch = document.getElementById("tag-item-no-match");
  const live = document.getElementById("tag-live");
  if (!body) {
    return;
  }

  const FILTER_KEY = "tagItemsFilter";

  function rows() {
    return body.querySelectorAll("tr.tag-item-row");
  }

  function applyFilter() {
    const term = (search ? search.value : "").trim().toLowerCase();
    const onlyUntagged = untaggedOnly && untaggedOnly.checked;
    let anyVisible = false;
    for (const row of rows()) {
      const nameMatch = (row.dataset.itemName || "").toLowerCase().includes(term);
      const untaggedMatch = !onlyUntagged || row.dataset.untagged === "true";
      const visible = nameMatch && untaggedMatch;
      row.hidden = !visible;
      anyVisible = anyVisible || visible;
    }
    if (noMatch) {
      noMatch.hidden = anyVisible;
    }
  }

  // Persist the name filter across the full-page reload that a tag create/rename/delete triggers,
  // so a create-then-assign rhythm doesn't lose the user's place.
  if (search) {
    const saved = sessionStorage.getItem(FILTER_KEY);
    if (saved) {
      search.value = saved;
    }
    search.addEventListener("input", () => {
      sessionStorage.setItem(FILTER_KEY, search.value);
      applyFilter();
    });
  }
  if (untaggedOnly) {
    untaggedOnly.addEventListener("change", applyFilter);
  }
  applyFilter();

  // ---- Toggle: restore focus to the swapped chicklet and announce the new state. ----
  let pendingChickletId = null;

  function isChicklet(el) {
    return el && el.classList && el.classList.contains("chicklet");
  }

  document.body.addEventListener("htmx:beforeRequest", (e) => {
    if (isChicklet(e.detail.elt)) {
      pendingChickletId = e.detail.elt.id;
    }
  });

  document.body.addEventListener("htmx:afterSwap", () => {
    if (!pendingChickletId) {
      return;
    }
    const el = document.getElementById(pendingChickletId);
    pendingChickletId = null;
    if (!el) {
      return;
    }
    el.focus();
    if (live) {
      const [tagName, itemName] = (el.getAttribute("aria-label") || "").split(" — ");
      const verb = el.getAttribute("aria-pressed") === "true" ? "assigned to" : "removed from";
      live.textContent = `${tagName} ${verb} ${itemName}`;
    }
  });

  document.body.addEventListener("htmx:responseError", announceFailure);
  document.body.addEventListener("htmx:sendError", announceFailure);
  function announceFailure(e) {
    if (isChicklet(e.detail.elt) && live) {
      live.textContent = "Couldn't save that change — please try again.";
    }
  }

  // ---- Bulk apply: assign / remove one tag across every item the filter currently shows. ----
  const bulkSelect = document.getElementById("tag-bulk-select");
  for (const btn of document.querySelectorAll(".tag-bulk-btn")) {
    btn.addEventListener("click", () => {
      if (!bulkSelect || !bulkSelect.value) {
        return;
      }
      const assigned = btn.dataset.assigned === "true";
      const option = bulkSelect.options[bulkSelect.selectedIndex];
      const tagName = option ? option.dataset.tagName : "";
      const ids = [...rows()].filter((r) => !r.hidden).map((r) => r.dataset.itemId);
      if (ids.length === 0) {
        window.alert("No items are shown to update. Adjust the filter first.");
        return;
      }
      const verb = assigned ? "Assign" : "Remove";
      const prep = assigned ? "to" : "from";
      const plural = ids.length === 1 ? "item" : "items";
      if (!window.confirm(`${verb} “${tagName}” ${prep} ${ids.length} shown ${plural}?`)) {
        return;
      }
      const params = new URLSearchParams();
      params.set("tagId", bulkSelect.value);
      params.set("assigned", String(assigned));
      for (const id of ids) {
        params.append("itemIds", id);
      }
      fetch("/admin/tag-items/bulk", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: params.toString(),
      }).then((r) => {
        if (r.ok) {
          window.location.reload();
        } else {
          window.alert("Bulk update failed. Please try again.");
        }
      });
    });
  }
})();
