function updatePhoneNumber(siteId, fieldName) {
  const phoneField = document.getElementById(fieldName);

  const phoneValue = phoneField.value.replace(/\D/g, '');

  if (phoneValue.length !== 10) {
    phoneField.classList.add("missingData");

    document.getElementById("Contact Number-update-confirm")
        .innerHTML = "Check phone number, invalid length."
  } else {
    phoneField.classList.remove("missingData");

    document.getElementById("Contact Number-update-confirm")
        .innerHTML = "";
    showUpdateConfirmation(siteId, fieldName);
  }
}

function addAdditionalManager(siteId) {
  const newRow = `
    <div class="horizontal  additional-contact">
      <input class="managerId" type="hidden" value="">
      Name: <input class="name" type="text" maxlength="32" size="18">
      Phone: <input class="phone" type="text" maxlength="16" size="12">
      <button onclick="updateContact(${siteId})">Update</button>
      <button disabled onclick="removeContact(${siteId})" class="remove-button">Remove</button>
      <div class="errorMessage"></div>
      <div class="message"></div>
      <div class="green-check hidden">&#10003;</div>
    </div>
  `;
  document.getElementById("add-managers-div")
  .insertAdjacentHTML('beforebegin', newRow);
}

function removeContact(siteId) {

  const button = event.target;

  if (button.innerHTML !== "Sure?") {
    button.innerHTML = "Sure?";
    button.classList.add("are-you-sure");
    return;
  }

  const name =
      event.target.parentNode
      .getElementsByClassName("name")[0];
  const phone =
      event.target.parentNode
      .getElementsByClassName("phone")[0];
  const managerId =
      event.target.parentNode
      .getElementsByClassName("managerId")[0];
  const errorDiv =
      event.target.parentNode
      .getElementsByClassName("errorMessage")[0];
  const message =
      event.target.parentNode
      .getElementsByClassName("message")[0];
  const greenCheck =
      event.target.parentNode
      .getElementsByClassName("green-check")[0];

  fetch("/manage/remove-manager", {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      managerId: managerId.value,
      siteId: siteId
    })
  })
  .then(
      async function (response) {
        if (response.ok) {
          button.innerHTML = "Remove";
          name.value = "";
          name.classList.remove("missingData");
          phone.value = "";
          phone.classList.remove("missingData");
          managerId.value = "";

          message.innerHTML = "Removed";
          button.classList.remove("are-you-sure");
          button.disabled = true;
          greenCheck.style.display = 'block';
          setTimeout(function () {
                greenCheck.style.display = 'none';
                message.innerHTML = "";
              },
              1500);
        } else {
          const responseJson = await response.json();
          errorDiv.innerHTML = "Failed to save: " + responseJson.error;
        }
        window.scrollTo(0, document.body.scrollHeight);
      },
      function (error) {
        errorDiv.innerHTML = "Failed to save, server not available. " + error;
      });
}

function updateContact(siteId) {
  const name =
      event.target.parentNode
      .getElementsByClassName("name")[0];
  const phone =
      event.target.parentNode
      .getElementsByClassName("phone")[0];
  const managerId =
      event.target.parentNode
      .getElementsByClassName("managerId")[0];
  const errorDiv =
      event.target.parentNode
      .getElementsByClassName("errorMessage")[0];
  const message =
      event.target.parentNode
      .getElementsByClassName("message")[0];
  const greenCheck =
      event.target.parentNode
      .getElementsByClassName("green-check")[0];
  const removeButton =
      event.target.parentNode
      .getElementsByClassName("remove-button")[0];

  let missing = false;
  if (name.value.trim() === "") {
    name.classList.add("missingData");
    missing = true;
  } else {
    name.classList.remove("missingData");
  }

  if (phone.value.trim() === "") {
    phone.classList.add("missingData");
    missing = true;
    errorDiv.innerHTML = ""
  } else if (phone.value.replace(/\D/g, '').length !== 10) {
    phone.classList.add("missingData");
    missing = true;
    errorDiv.innerHTML = "Invalid phone number length"
  } else {
    phone.classList.remove("missingData");
    errorDiv.innerHTML = ""
  }

  if (missing) {
    return;
  }

  const json = JSON.stringify({
    managerId: managerId.value,
    name: name.value.trim(),
    phone: phone.value.trim(),
    siteId: siteId
  });

  fetch("/manage/add-manager", {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      managerId: managerId.value,
      name: name.value.trim(),
      phone: phone.value.trim(),
      siteId: siteId
    })
  })
  .then(
      async function (response) {
        if (response.ok) {
          const responseJson = await response.json();
          managerId.value = responseJson.id;
          errorDiv.innerHTML = "";

          message.innerHTML = responseJson.message;
          removeButton.disabled = false;
          greenCheck.style.display = 'block';
          errorDiv.innerHTML = "";

          setTimeout(function () {
                greenCheck.style.display = 'none';
                message.innerHTML = "";
              },
              1500);
        } else {
          const responseJson = await response.json();
          errorDiv.innerHTML = "Failed to save: " + responseJson.error;
        }
        window.scrollTo(0, document.body.scrollHeight);
      },
      function (error) {
        errorDiv.innerHTML = "Failed to save, server not available. " + error;
      });
}
