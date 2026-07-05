
async function handleVerificationSubmission(e) {
    const phoneNumber = document.getElementById("phone-verification").value.replaceAll("-", "");
    const urlKey = document.getElementById("delivery-details").dataset.urlkey;
    submitVerification(phoneNumber, urlKey);
};

async function submitVerification (phoneNumber, urlKey) {
    const response = await fetch("/volunteer/verify-delivery", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                "phoneNumber": phoneNumber,
                "urlKey": urlKey.toUpperCase(),
                "section": "delivery"
                })
    });
    if (response.ok) {
        hideVerificationForm();
        const data = await response.json();
        return loadDeliveryData(data);
    } else {
        return displayVerificationError();
    }
}

// fills and displays delivery data
function loadDeliveryData(data) {
    // Load request status
    if(data.request.status) loadStatus(data.request.status);

    // If request is cancelled or declined hide data
    if (data.request.status == "CANCELLED" || data.request.status == "DECLINED") {
        hideDeliveryData();
    } else {
        // Load site Name
        if (data.request.siteName) loadSiteDetail("site-name", data.request.siteName);

        // Load site Address and map
        if (data.request.address) {
            const siteAddress = `${data.request.address}, ${data.request.city}`;
            loadSiteDetail("site-address", siteAddress);
            loadMap(siteAddress);
        };

        // Load Volunteer Name
        if (data.request.volunteerName) loadSiteDetail("volunteer-name", data.request.volunteerName);

        // Load items
        if (data.request.items) loadDeliveryItems(data.request.items);

        // Load contacts
        if (data.request.volunteerPhone) loadSiteDetail("volunteer-contact", data.request.volunteerPhone)
        if (data.request.siteContactName) loadSiteDetail("site-contact-name", data.request.siteContactName);
        if (data.request.siteContactNumber) loadSiteDetail("site-contact-number", data.request.siteContactNumber);
    }

    // Load status change buttons
    loadStatusChangeButtons(data.request.status, data.userPhoneNumber, data.access);

    const deliveryDetails = document.getElementById("delivery-details");
    deliveryDetails.classList.remove("hidden");

};


function hideDeliveryData() {
    // Hide site info
    const siteDetailContainers = document.getElementsByClassName("site-detail-container");
    for (let container of siteDetailContainers) {
        container.classList.add("hidden");
    };

    // Hide site map
    document.getElementById("map").classList.add("hidden");

    // Hide site items
    document.getElementById("items").classList.add("hidden");
}

// Creates a new <li> element with item name as it's textContent
function createDeliveryItemElement(itemName) {
    const deliveryItem = document.createElement("li");
    deliveryItem.textContent = itemName;
    return deliveryItem;
}

// loadsDeliveryItem
function loadDeliveryItems(items) {
    const itemsContainer = document.getElementById("items");
    itemsContainer.classList.remove("hidden");
    // Clear all items
    const itemsList = document.getElementById("delivery-items");

    // Remove all items
    while (itemsList.firstChild) {
        itemsList.removeChild(itemsList.lastChild);
    };

    // Load new items
    for (let item of items) {
        const deliveryItem = createDeliveryItemElement(item.name);
        itemsList.appendChild(deliveryItem);
    };
};

function hideVerificationForm(){
    removeVerificationError();
    const verificationContainer = document.getElementById("verification-container");
    verificationContainer.classList.add("hidden");
};

// Creates a new iframe element using
// google maps as the source and the site address as query
function loadMap(address) {
    const mapContainer = document.getElementById("map");

    // Empty current map
    while (mapContainer.firstChild) mapContainer.removeChild(mapContainer.lastChild);

    const mapElement = document.createElement("div");
    mapElement.innerHTML = `
    <div class="gmap_canvas">
        <iframe
            class="gmap_iframe"
            frameborder="0"
            scrolling="no"
            marginheight="0"
            marginwidth="0"
            src="https://maps.google.com/maps?width=600&amp;height=500&amp;hl=en&amp;q=${address}&amp;t=&amp;z=10&amp;ie=UTF8&amp;iwloc=B&amp;output=embed"
        >
        </iframe>
    </div>`.trim();
    mapContainer.appendChild(mapElement.firstChild);
}

// unselect all statuses except for the current status
function loadStatus(status){

    const statusElements = document.getElementsByClassName("status");
    const statusText = `status-${status.toLowerCase()}`;

    for (const element of statusElements) {
        const statusIcon = element.querySelector(".request-status-icon");
        if (element.id === statusText) {
            statusIcon.classList.add("status-selected");
            statusIcon.classList.remove("status-unselected");
        } else {
            statusIcon.classList.remove("status-selected");
            statusIcon.classList.add("status-unselected");
        }
    }
}

// Reads the status and displays the appropriate buttons or an inactive message
function loadStatusChangeButtons(status, userPhoneNumber, access) {

    // Hide All buttons
    const buttonGroups = document.getElementsByClassName("button-group");
    for (let buttonGroup of buttonGroups) {
        buttonGroup.classList.add("hidden")
    }

    switch(status){
        case "PENDING":
            loadPendingStatusButtons(access, userPhoneNumber);
            break;
        case "ACCEPTED":
             loadCancelButton(userPhoneNumber);
             break;
        default:
            loadDeclinedOrCancelledMessage();
    }
}

// Displays corresponding delivery update buttons based on user access
// Also set up the event listeners for the appropriate button group
function loadPendingStatusButtons(access, userPhoneNumber) {
    if (access.hasManagerAccess) {
        const acceptDeclineButtonGroup = document.getElementById("acceptDeclineButtonGroup")
        initializeButtonGroupEventListener(acceptDeclineButtonGroup, userPhoneNumber);
        acceptDeclineButtonGroup.classList.remove("hidden");
    }

    if (access.hasVolunteerAccess) {
        const cancelButtonGroup = document.getElementById("cancelButtonGroup");
        initializeButtonGroupEventListener(cancelButtonGroup, userPhoneNumber);
        cancelButtonGroup.classList.remove("hidden");
    }
}

// Loads the cancel button
function loadCancelButton(userPhoneNumber) {
    const cancelButtonGroup = document.getElementById("cancelButtonGroup");
    initializeButtonGroupEventListener(cancelButtonGroup, userPhoneNumber);
    cancelButtonGroup.classList.remove("hidden");
};

// displays the declined or cancelled message
function loadDeclinedOrCancelledMessage() {
    const message = document.getElementById("requestInactiveMessage");
    message.classList.remove("hidden");
};


// Initialize event listener for provided status change button group
function initializeButtonGroupEventListener(buttonGroup , userPhoneNumber) {
    buttonGroup.addEventListener("click", (event) => {
        const isButton = event.target.nodeName === 'BUTTON';
        if(isButton){
           const urlKey = document.getElementById("delivery-details").dataset.urlkey;
           const newStatus = event.target.dataset.request;

           const modalMessage = buildConfirmationMessage(newStatus);

           // Open confirmation modal with appropriate data and callback;
           displayConfirmationModal(modalMessage, () => {
                handleStatusUpdate(urlKey, userPhoneNumber, newStatus);
           });
        }
    });
};


function buildConfirmationMessage(newStatus) {
    switch (newStatus) {
        case "ACCEPTED":
            return "You are about to ACCEPT the delivery request. This action cannot be undone. We will share you phone number and send a text message notification upon confirmation.";
        case "DECLINED":
            return "You are about to DECLINE the delivery request. This action cannot be undone. We will hide delivery information and send a text message upon confirmation.";
        case "CANCELLED":
            return "You are about to CANCEL the delivery request. This action cannot be undone. We will hide delivery information and send a text message upon confirmation.";
        default:
            return "";
    }
}

// Displays update status modal
function displayConfirmationModal(message, sendUpdate) {
    // Get modal
    const confirmationModal = document.getElementById("update-confirmation");
    const confirmationMessage = document.getElementById("confirmation-message");
    const confirmationButton = document.getElementById("confirm-update");

    // Updates the message
    confirmationMessage.textContent = message;

    // Remove previous onClick
    const newButton = confirmationButton.cloneNode(true);
    confirmationButton.parentNode.replaceChild(newButton, confirmationButton);

    // Add event listener for onClick. Runs the update sendUpdate and then closes the modal
    newButton.addEventListener("click", () => {
        sendUpdate();
        closeConfirmationModal();
    });

    confirmationModal.showModal();
}

function closeConfirmationModal() {
    // Remove any previous callbacks
    const confirmationButton = document.getElementById("confirm-update");
    const newButton = confirmationButton.cloneNode(true);
    confirmationButton.parentNode.replaceChild(newButton, confirmationButton);
    // Close modal
    const confirmationModal = document.getElementById("update-confirmation").close();
}

// Send update request and fills the data
// If error display error message
async function handleStatusUpdate(urlKey, phoneNumber, status) {
    try {
        updatedDeliveryData = await updateStatus(urlKey, phoneNumber, status);
        // todo: Load update success message

        // Load the updated data
        loadDeliveryData(updatedDeliveryData);
    } catch (e) {
        console.log(e)
        // todo: Display error message
    }
}

// Sends update request and returns updated data
async function updateStatus(urlKey, phoneNumber, status) {
    // send the urlKey ,update text and the user phone number as the request body
    const response = await fetch("/volunteer/delivery/update", {
        method: "POST",
        headers: {'Content-Type': "application/json"},
        body: JSON.stringify({
            "phoneNumber": phoneNumber,
            "urlKey": urlKey.toUpperCase(),
            "status": status.toUpperCase()
        })
    });

    if (response.ok) {
        return await response.json();
    } else {
        // todo: Handle Error
        console.log("An error occurred!");
        throw Error(e);
    }
}


// Finds the element by the provided elementId.
// Changes the textContent and then removes hidden element
function loadSiteDetail(elementId, value) {
    const detailElement = document.getElementById(elementId);
    detailElement.textContent = value;
    detailElement.parentElement.classList.remove("hidden");
}

function displayVerificationError() {
    const errorMsgElement = document.getElementById("verification-error-msg");
    errorMsgElement.classList.remove("hidden");
}

function removeVerificationError() {
    const errorMsgElement = document.getElementById("verification-error-msg");
    errorMsgElement.classList.add("hidden");
}

window.addEventListener("load", () => {
    const verificationForm = document.getElementById("verification-form");

    // Event Listener for Phone number verification
    verificationForm.addEventListener("submit", (e) => {
        e.preventDefault();
        handleVerificationSubmission(e);
    });
})
