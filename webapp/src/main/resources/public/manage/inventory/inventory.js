/**
 * Client-side inventory page behavior. Item activation and status changes are handled server-side
 * via htmx (each mutation re-renders the affected row); this file only covers the purely
 * client-side pieces htmx doesn't do: filtering the visible rows, coloring the tag chips, and
 * flashing the "Updated" confirmation after an htmx row swap.
 */

/** Briefly reveal the "Updated" confirmation on any row that htmx just swapped in. */
document.addEventListener("htmx:afterSwap", (e) => {
  const scope = e.target;
  if (!scope || !scope.querySelectorAll) return;
  scope.querySelectorAll(".update-confirm-div").forEach((div) => {
    if (div.id === "addItemResult") return;
    div.style.display = "flex";
    setTimeout(() => (div.style.display = "none"), 1000);
  });
});

/**
 * Filter functionality
 */

function instantiateInputEventListener() {
  const input = document.getElementById("filter-text-input");
  input.addEventListener("keyup", () => {
    filterItems();
  });
}

function instantiateTagsEventListener() {
  const tags = document.getElementsByClassName("item-tag-inner");

  // color the tag chips
  for (let i = 0; i < tags.length; i++) {
    const tag = tags[i];
    const tagColor = tag.getAttribute("data-tag-color");
    tag.style.backgroundColor = tagColor;
  }

  const tagsContainer = document.getElementById("tags-container");
  tagsContainer.addEventListener("click", (e) => {
    const classes = Array.from(e.target.classList);
    if (!classes.includes("item-tag-inner")) return;

    e.target.classList.toggle("tag-selected");
    filterItems();
  });
}

function filterItems() {
  const textInputValue = document.getElementById("filter-text-input").value;
  const selectedTags = getListOfSelectedTags();

  hideElementsBasedOnFilters(textInputValue, selectedTags);
}

function hideElementsBasedOnFilters(filterText, filterTags) {
  const inventoryItems = document.getElementsByClassName("inventory-item");
  for (let i = 0; i < inventoryItems.length; i++) {
    const inventoryItem = inventoryItems[i];
    const itemName = inventoryItem
        .getElementsByClassName("inventoryLabel")[0]
        .textContent
        .trim()
        .toLowerCase();
    const itemTags = getTagListFromItem(inventoryItem);

    const filterTagsContainItemTags =
        itemTags.some((tag) => (filterTags.length === 0 ? true : filterTags.includes(tag)));
    const filterContainsItemName = itemName.includes(filterText.toLowerCase());

    if (filterContainsItemName && filterTagsContainItemTags) {
      inventoryItem.classList.remove("hidden");
    } else {
      inventoryItem.classList.add("hidden");
    }
  }
}

function getListOfSelectedTags() {
  const tags = [];
  const selectedTags = document.getElementsByClassName("tag-selected");
  for (let i = 0; i < selectedTags.length; i++) {
    tags.push(selectedTags[i].value);
  }
  return tags;
}

function getTagListFromItem(element) {
  const itemTags = element.getElementsByClassName("item-tags")[0].value.slice(1, -1).split(",");
  return itemTags.map((tag) => tag.trim());
}
