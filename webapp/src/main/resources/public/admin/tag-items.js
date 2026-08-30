// Client-side filter for the "Assign Tags to Items" table: hides item rows whose name does not
// contain the typed text. Purely presentational — assignment state lives on the server.
(function () {
  const search = document.getElementById("tag-item-search");
  const body = document.getElementById("tag-item-body");
  const noMatch = document.getElementById("tag-item-no-match");
  if (!search || !body) {
    return;
  }

  function applyFilter() {
    const term = search.value.trim().toLowerCase();
    let anyVisible = false;
    for (const row of body.querySelectorAll("tr.tag-item-row")) {
      const name = (row.dataset.itemName || "").toLowerCase();
      const match = name.includes(term);
      row.hidden = !match;
      anyVisible = anyVisible || match;
    }
    if (noMatch) {
      noMatch.hidden = anyVisible;
    }
  }

  search.addEventListener("input", applyFilter);
})();
