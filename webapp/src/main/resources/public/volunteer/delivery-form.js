

/** When A new site is selected
*    - Change address
*    - Update Items list
*    - Remove selected items from selected items list
*/

async function updateSelectedSiteData (siteId) {
    const siteData = await fetchSiteData(siteId);
    updateSiteAddress(siteData.site.address, siteData.site.county, siteData.site.state);
    updateNeededItemsList(siteData.site.items);
    clearSelectedItems();
}

function updateNeededItemsList (items) {
    const neededItemsList = document.getElementById("needed-items");

    // Remove current needs
    while (neededItemsList.firstChild) neededItemsList.removeChild(neededItemsList.firstChild);

    // Add new needs
    for(const item of items) {
        let itemContainer = document.createElement("div");
        itemContainer.classList.add("checkbox-container");

        let listItem = document.createElement("input");
        listItem.type = "checkbox"
        listItem.name = "neededItems"
        listItem.value = item.id;
        listItem.id = item.id;
        listItem.dataset.name = item.name;

        let itemLabel = document.createElement("label");
        itemLabel.for = item.id;
        itemLabel.textContent = item.name;

        itemContainer.appendChild(listItem);
        itemContainer.appendChild(itemLabel);
        neededItemsList.appendChild(itemContainer);
    }
}

function updateSiteAddress (address, county, state) {
    const siteAddress = document.getElementById("selected-site-address");
    siteAddress.textContent = `Site Address: ${address}, ${county}, ${state}`;
}


function addSelectedItem(name, id) {
    const itemsContainer = document.getElementById("selected-items");

    // Get the existing selected items from the DOM
    const selectedItems = Array.from(itemsContainer.getElementsByClassName("selected-item"));

    // Create a new element and add it to the DOM before sorting
    const selectedItem = document.createElement("div");
    selectedItem.classList.add("selected-item");
    selectedItem.id = `selected-${id}`;
    selectedItem.dataset.itemName = name;

    // Build the remove button and item name
    const removeSelectionButton = document.createElement("button");
    removeSelectionButton.type = "button";
    removeSelectionButton.dataset.itemId = id;
    removeSelectionButton.textContent = `X`;
    removeSelectionButton.addEventListener("click", (event) => {
        event.preventDefault();
        const item = document.getElementById(id);
        if (item.checked) item.checked = false;
        removeSelectedItem(id);
    })

    const itemName = document.createElement("span");
    itemName.textContent = name;

    selectedItem.appendChild(removeSelectionButton);
    selectedItem.appendChild(itemName);
    itemsContainer.appendChild(selectedItem);

    // Re-fetch the updated list (including the new item)
    const updatedItems = Array.from(itemsContainer.getElementsByClassName("selected-item"));

    // Sort elements alphabetically by text content
    updatedItems.sort((a, b) => a.dataset.itemName.localeCompare(b.dataset.itemName));

    // Re-append sorted elements to reorder them
    updatedItems.forEach(item => itemsContainer.appendChild(item));
}

function handleItemSelect(element) {
    // If item was checked add selected item, if it was unchecked remove
    if (element.checked) {
        hideNeededItemsErrorMsg();
        addSelectedItem(element.dataset.name, element.value);
    } else {
        removeSelectedItem(element.value);
    };
};

function removeSelectedItem(id) {
    const selectedItem = document.getElementById(`selected-${id}`);
    selectedItem.remove();
};

function clearSelectedItems() {
    const selectedItems = document.getElementById(`selected-items`);
    while (selectedItems.firstChild) selectedItems.removeChild(selectedItems.firstChild)
}

/** Form Submission */
async function handleFormSubmission(event) {
    event.preventDefault();

    // Creating form data
    const formData = new FormData(event.target);
    const requestBody = {"neededItems": [], "site": "", "volunteerContacts":"", "volunteerName": ""};
    for (const [key, value] of formData.entries()) {
        if (key === "neededItems") {
          // Adds the current needed item into the existing needed items array in requestBody
          requestBody["neededItems"] = requestBody[key].concat(parseInt(value));
        } else {
            requestBody[key] = value;
        }
    }

    // Validates formData
    const isValid = validateData(requestBody);

    if (!isValid) {
        alert("Whoops, please make sure you select some items to bring!");
        showNeededItemsErrorMsg();
    };

    if (isValid) {
        try {
            sendForm(requestBody)
        } catch (e) {
            handleSubmissionError()
        }
    }
}

async function sendForm(requestBody) {
    startLoaderAnimation();

    const response = await fetch("/volunteer/delivery", {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
          },
        body: JSON.stringify(requestBody)
    });

    stopLoaderAnimation();
    if (response.ok) {
        const urlKey = await response.text();
        updateRedirectButton(urlKey);
        showSuccessModal();
        showSuccessModal(urlKey);
    } else {
        handleSubmissionError();
    }
}

// Displays a modal that contains the redirect url to the volunteer page
function showSuccessModal() {
    const modal = document.getElementById("success-modal");
    modal.showModal();
}

function updateRedirectButton(urlKey) {
    // Change the href of the redirect button to point to the volunteer delivery portal
    const redirectButton = document.getElementById("request-redirect");
    redirectButton.href = `/volunteer/delivery/request?urlKey=${urlKey}`;
}

function handleSubmissionError() {
    const errorText = document.getElementById("submission-error-text");
    errorText.classList.remove("hidden");
    document.documentElement.scrollTop = 0;
}

// Checks if data has needed items selected
function validateData(data) {
  return (data["neededItems"].length > 0);
}

// Removes hidden tag to error message div
function showNeededItemsErrorMsg() {
    const errorMessage = document.getElementById("items-error-msg");
    errorMessage.classList.remove("hidden");
}

// Add hidden tag to error message div
function hideNeededItemsErrorMsg() {
    const errorMessage = document.getElementById("items-error-msg");
    errorMessage.classList.add("hidden");
}

// Loader animation
function startLoaderAnimation() {
  // Clear out results count
  document.getElementById('loader-div').style.animationPlayState = 'running';
  document.getElementById('loader-div').style.display = 'block';
}

function stopLoaderAnimation() {
  document.getElementById('loader-div').style.animationPlayState = 'paused';
  document.getElementById('loader-div').style.display = 'none';
}


/** API requests */
async function fetchSiteData (siteId) {
    const response = await fetch(`/volunteer/site-items?siteId=${siteId}`);
    return await response.json()
}


/** On Load */
function instantiateEventListeners() {

    // Updates site data when site is selected
    const siteSelect = document.getElementById("site-select");
    siteSelect.addEventListener("change", (e) => {
        updateSelectedSiteData(e.target.value)
    });

    // Updates the selected item list when a needed item is selected
    const neededItemsList = document.getElementById("needed-items");
    neededItemsList.addEventListener("change", (e) => {
        handleItemSelect(e.target);
    });


    // Handle form submission event listener
    const form = document.getElementById("volunteer-delivery-form");
    form.addEventListener("submit", handleFormSubmission)
}

window.addEventListener("load", () => {
    instantiateEventListeners();
    const siteSelect = document.getElementById("site-select");
    updateSelectedSiteData(siteSelect.value);
})
