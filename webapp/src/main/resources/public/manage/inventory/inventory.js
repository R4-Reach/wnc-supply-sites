/** Fires when a user clicks the table cell containing an
 * inventory checkbox. This is a convenience to make the select
 * area very large, easy to click, without having to actually
 * click the checkbox or label precisely.
 */

async function checkAndToggleInventory(event, siteId, itemName) {

  // check that the event target was a tag
  const eventTargetClassList = Array.from(event.target.classList);
  if (eventTargetClassList.includes("item-tag-inner")) return;

  // toggle the checkbox
  const checkbox = document.getElementById(`${itemName}Checkbox`);
  checkbox.checked = !checkbox.checked;

  // handle the toggle inventory event as normal
  await toggleInventory(siteId, itemName);
}

/**
 * Fires when inventory checkbox is checked or unchecked.
 */
async function toggleInventory(siteId, itemName) {
  const checked = document.getElementById(`${itemName}Checkbox`).checked;

  const oversupplyChecked = document.getElementById(`${itemName}Oversupply`)
      .checked;
  const neededChecked = document.getElementById(`${itemName}Needed`)
      .checked;
  const availableChecked = document.getElementById(`${itemName}Available`)
      .checked;
  const urgentChecked = document.getElementById(`${itemName}Urgent`)
      .checked;

  if (checked) {
    try {
      let labelClass = "available";
      let itemStatus = "Available";

      if (urgentChecked) {
        labelClass = "urgent";
        itemStatus = "Urgently Needed";
      } else if (neededChecked) {
        labelClass = "needed";
        itemStatus = "Needed";
      } else if (oversupplyChecked) {
        labelClass = "oversupply";
        itemStatus = "Oversupply";
      }

      await sendActivateItem(siteId, itemName, itemStatus);

      document.getElementById(`${itemName}Label`).classList.value = "inventoryLabel " + labelClass;

      document.getElementById(`${itemName}UrgentLabel`)
      .classList.remove("disabled");
      document.getElementById(`${itemName}NeededLabel`)
      .classList.remove("disabled");
      document.getElementById(`${itemName}AvailableLabel`)
      .classList.remove("disabled");
      document.getElementById(`${itemName}OversupplyLabel`)
      .classList.remove("disabled");
      showUpdateConfirmation(itemName);

      document.getElementById(`${itemName}Urgent`).disabled = !checked;
      document.getElementById(`${itemName}Needed`).disabled = !checked;
      document.getElementById(`${itemName}Available`).disabled = !checked;
      document.getElementById(`${itemName}Oversupply`).disabled = !checked;
    } catch (error) {
      showInventoryError(error);
      // revert checkbox update
      document.getElementById(`${itemName}Checkbox`).checked = false;
    }
  } else {
    try {
      await sendDeactivateItem(siteId, itemName);

      document.getElementById(`${itemName}Label`).classList.value = "inventoryLabel disabled";

      document.getElementById(`${itemName}UrgentLabel`)
      .classList.add("disabled");
      document.getElementById(`${itemName}NeededLabel`)
      .classList.add("disabled");
      document.getElementById(`${itemName}AvailableLabel`)
      .classList.add("disabled");
      document.getElementById(`${itemName}OversupplyLabel`)
      .classList.add("disabled");
      showUpdateConfirmation(itemName);

      document.getElementById(`${itemName}Urgent`).disabled = !checked;
      document.getElementById(`${itemName}Needed`).disabled = !checked;
      document.getElementById(`${itemName}Available`).disabled = !checked;
      document.getElementById(`${itemName}Oversupply`).disabled = !checked;
    } catch (error) {
      showInventoryError(error);
      // revert checkbox update
      document.getElementById(`${itemName}Checkbox`).checked = true;
    }
  }
}

async function sendActivateItem(siteId, itemName, itemStatus) {
  const url = "/manage/activate-site-item";

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      siteId: siteId,
      itemName: itemName,
      itemStatus: itemStatus
    })
  });

  if (!response.ok) {
    throw new Error(`Response status: ${response.status}, ${response.body}`);
  }
  return await response.text();
}

async function sendDeactivateItem(siteId, itemName) {
  const url = "/manage/deactivate-site-item";

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      siteId: siteId,
      itemName: itemName
    })
  });

  if (!response.ok) {
    throw new Error(`Response status: ${response.status}, ${response.body}`);
  }
  return await response.text();
}


/**
 * Fires when the item status radio buttons are toggled.
 */
async function changeItemStatus(siteId, itemName) {
  const urgentChecked = document.getElementById(`${itemName}Urgent`)
      .checked ? "checked" : "";
  const neededChecked = document.getElementById(`${itemName}Needed`)
      .checked ? "checked" : "";
  const availableChecked = document.getElementById(`${itemName}Available`)
      .checked ? "checked" : "";
  const oversupplyChecked = document.getElementById(`${itemName}Oversupply`)
      .checked ? "checked" : "";

  document.getElementById(`${itemName}Label`)
  .classList.remove("urgent", "needed", "available", "oversupply");

  let newStatus = "";

  if (urgentChecked) {
    newStatus = "Urgently Needed";
    document.getElementById(`${itemName}Label`)
    .classList.add("urgent");
  } else if (neededChecked) {
    newStatus = "Needed";
    document.getElementById(`${itemName}Label`)
    .classList.add("needed");
  } else if (availableChecked) {
    newStatus = "Available";
    document.getElementById(`${itemName}Label`)
    .classList.add("available");
  } else {
    newStatus = "Oversupply";
    document.getElementById(`${itemName}Label`)
    .classList.add("oversupply");
  }

  try {
    await sendItemStatusChange(siteId, itemName, newStatus);
    showUpdateConfirmation(itemName);
  } catch (error) {
    showInventoryError(error);
  }
}

async function sendItemStatusChange(siteId, itemName, newStatus) {
  const url = "/manage/update-site-item-status";

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      siteId: siteId,
      itemName: itemName,
      newStatus: newStatus
    })
  });

  if (!response.ok) {
    throw new Error(`Response status: ${response.status}, ${response.body}`);
  }
  return await response.text();
}

async function addItem(siteId) {
  const itemName = document.getElementById("newItemText").value.trim();
  if (itemName === "") {
    return;
  }

  const itemNameEncoded = htmlEncode(itemName);

  const urgentChecked = document.getElementById("urgentlyNeededRadioNew")
      .checked ? "checked" : "";
  const neededChecked = document.getElementById("neededRadioNew")
      .checked ? "checked" : "";
  const availableChecked = document.getElementById("availableRadioNew")
      .checked ? "checked" : "";
  const oversupplyChecked = document.getElementById("oversupplyRadioNew")
      .checked ? "checked" : "";

  let labelStyle = "available";
  let status = "Available";


  if (urgentChecked) {
    labelStyle = "urgent";
    status = "Urgently Needed";
  } else if (neededChecked) {
    labelStyle = "needed";
    status = "Needed";
  } else if (availableChecked) {
    labelStyle = "available";
    status = "Available";
  } else if (oversupplyChecked) {
    labelStyle = "oversupply";
    status = "Oversupply";
  }

  let result = await sendAddNewItem(siteId, itemName, status);
  if (!result) {
    return;
  }

  const newItemRowHtml = `
        <tr>
          <!-- Item Checkbox -->
          <td>
            <input type="checkbox" id="${itemNameEncoded}Checkbox"
                   onclick="toggleInventory('${siteId}', '${itemNameEncoded}')" checked />
          </td>

           <!-- Item Label -->
          <td onclick="checkAndToggleInventory('${siteId}', '${itemNameEncoded}')">
           <div class="inventoryLabel">
              <span class="${labelStyle}" id="${itemNameEncoded}Label">${itemNameEncoded}</span>
            </div>
            <div class="horizontal update-confirm-div" id="${itemNameEncoded}UpdateConfirm">
              <span class="green-check" id="${itemNameEncoded}update-confirm">&#10003;</span>
              <span>Updated</span>
            </div>
          </td>

          <!-- Item Status Radio buttons -->
          <td>
            <div class="horizontal">

              <!-- Urgently Needed -->
              <fieldset class="vertical">
                <div class="horizontal item-status-div">
                  <input
                      type="radio"
                      id="${itemNameEncoded}Urgent"
                      name="${itemNameEncoded}Status"
                      onclick="changeItemStatus(${siteId}, '${itemNameEncoded}')"
                      ${urgentChecked}/>
                  <label
                      for="${itemNameEncoded}Urgent"
                      class="urgent"
                      id="${itemNameEncoded}UrgentLabel">
                    Urgently Needed
                  </label>
                </div>


                <!-- Needed -->
                <div class="horizontal item-status-div">
                  <input
                      type="radio"
                      id="${itemNameEncoded}Needed"
                      name="${itemNameEncoded}Status"
                      onclick="changeItemStatus(${siteId}, '${itemNameEncoded}')"
                      ${neededChecked}/>
                  <label
                      for="${itemNameEncoded}Needed"
                      class="needed"
                      id="${itemNameEncoded}NeededLabel">
                    Needed
                  </label>
                </div>

                <!-- Available -->
                <div class="horizontal item-status-div">
                  <input
                      type="radio"
                      id="${itemNameEncoded}Available"
                      name="${itemNameEncoded}Status"
                      onclick="changeItemStatus(${siteId}, '${itemNameEncoded}')"
                      ${availableChecked}/>
                  <label
                      for="${itemNameEncoded}Available"
                      class="available"
                      id="${itemNameEncoded}AvailableLabel">
                    Available
                  </label>
                </div>

                <!-- Oversupply -->
                <div class="horizontal item-status-div">
                  <input
                      type="radio"
                      id="${itemNameEncoded}Oversupply"
                      name="${itemNameEncoded}Status"
                      onclick="changeItemStatus(${siteId}, '${itemNameEncoded}')"
                      ${oversupplyChecked}/>
                  <label
                      for="${itemNameEncoded}Oversupply"
                      class="oversupply"
                      id="${itemNameEncoded}OversupplyLabel">
                    Oversupply (too much)
                  </label>
                </div>
              </fieldset>
            </div>
          </td>
        </tr>
  `;

  document.getElementById("inventoryTableBody").insertAdjacentHTML('beforeend', newItemRowHtml);

  document.getElementById("newItemText").value = "";

  showUpdateConfirmation(itemNameEncoded);
}

async function sendAddNewItem(siteId, itemName, itemStatus) {
  const url = "/manage/add-site-item";

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        siteId: siteId,
        itemName: itemName,
        itemStatus: itemStatus
      })
    });

    if (!response.ok) {
      if (response.status === 400) {
        document.getElementById("addItemResult").innerHTML = "Item not added, already exists."
        document.getElementById("addItemResult").style.display = 'block';
        return false;
      } else {
        showInventoryError(new Error(`Response status: ${response.status}, ${response.text}`));
        return false;
      }
    }
    document.getElementById("addItemResult").style.display = 'none';
    return true;
  } catch (error) {
    showInventoryError(error);
    return false;
  }
}

const timeouts = [];


/**
 * Shows 'update confirm' checkmark. If the indicator is already visible, then we hide it briefly
 * before showing it again. If there was a previous timer that was going to hide it, we cancel
 * that so we can renew the hide timer.
 */
function showUpdateConfirmation(itemName) {

  // remove any preceding timeouts which have yet to fire.
  clearTimeout(timeouts[itemName]);

  // if update confirmation is visible, hide it very briefly and then show it again.
  if (document.getElementById(`${itemName}UpdateConfirm`).style.display === "block") {
    // hide the element for 200ms
    document.getElementById(`${itemName}UpdateConfirm`).style.display = "none";
    timeouts[itemName] = setTimeout(function () {
          document.getElementById(`${itemName}UpdateConfirm`).style.display = 'block';
        },
        100);

  } else {
    // if update confirm is not visible, then make it visible.
    document.getElementById(`${itemName}UpdateConfirm`).style.display = "block";
  }
  // document.getElementById(`${itemName}UpdateConfirm`).style.display = "block";
  timeouts[itemName] = setTimeout(function () {
        document.getElementById(`${itemName}UpdateConfirm`).style.display = 'none';
      },
      1000);
}

function showInventoryError(error) {
  console.log(error);
  let errorDiv = document.getElementById("error-div");
  errorDiv.innerHTML = "Update failed. Error contacting server. " + error;
  errorDiv.style.display = 'block';

  console.log("Error: " + error);
  clearTimeout(timeouts['error']);
  timeouts['error'] = setTimeout(function () {
        errorDiv.style.display = 'none';
      },
      3000);

}

function htmlEncode(input) {
  input = input.replace(/&/g, '&amp;');
  input = input.replace(/</g, '&lt;');
  input = input.replace(/>/g, '&gt;');
  return input;
}

/**
 * Filter functionality
 */

function instantiateInputEventListener() {
  const input = document.getElementById("filter-text-input");
  input.addEventListener("keyup", (e) => {
    filterItems();
  })
}

function instantiateTagsEventListener() {
  const tags = document.getElementsByClassName("item-tag-inner");

  // color the tag
  for (let i = 0; i < tags.length; i++) {
    const tag = tags[i];
    const tagColor = tag.getAttribute("data-tag-color");
    tag.style.backgroundColor = tagColor;
  }

  // Add event listener
  const tagsContainer = document.getElementById("tags-container");
  tagsContainer.addEventListener("click", (e) => {
    const classes = Array.from(e.target.classList);

    // check if the clicked item was a tag
    if (!classes.includes("item-tag-inner")) return;

    e.target.classList.toggle("tag-selected");
    filterItems();
  })
}

function filterItems() {
  const textInputValue = document.getElementById("filter-text-input").value;
  const selectedTags = getListOfSelectedTags();

  hideElementsBasedOnFilters(textInputValue, selectedTags);
}

function hideElementsBasedOnFilters(filterText, filterTags) {
  const inventoryItems = document.getElementsByClassName("inventory-item");
  for(let i = 0; i < inventoryItems.length; i++) {
    const inventoryItem = inventoryItems[i];
    const itemName = inventoryItem
        .getElementsByClassName("inventoryLabel")[0]
        .textContent
        .trim()
        .toLowerCase();
    const itemTags = getTagListFromItem(inventoryItem);

    const filterTagsContainItemTags = itemTags.some((tag) => filterTags.length === 0 ? true : filterTags.includes(tag));

    const filterContainsItemName = itemName.includes(filterText.toLowerCase());
    if (filterContainsItemName && filterTagsContainItemTags){
      inventoryItem.classList.remove("hidden")
    } else {
      inventoryItem.classList.add("hidden")
    }
  }
}

function arrayElementsAreInFilterArray(array, filterArray) {
  return array.some((element) => {
    filterArray.contains(element)
  })
}

function getListOfSelectedTags() {
  const tags = []
  const selectedTags = document.getElementsByClassName("tag-selected");
  for(let i = 0; i < selectedTags.length; i++) {
    const tag = selectedTags[i];
    tags.push(tag.value);
  }
  return tags;
}

function getTagListFromItem(element) {
  const itemTags = element.getElementsByClassName("item-tags")[0].value.slice(1, -1).split(",");
  const cleanedItemTags = itemTags.map((tag) => {return tag.trim()});
  return cleanedItemTags;
}

// Remove the quotes from tag string value
function formatTagElementValue(tag){
  const tagText = tag.textContent;
  const formattedString = tagText.trim().slice(1,-1);
  tag.textContent = formattedString;
  tag.value = formattedString;
  return tag;
}
