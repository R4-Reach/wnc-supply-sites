async function saveNewSite() {

  /* Required Fields */
  const siteName = document.getElementById("Site Name");
  const streetAddress = document.getElementById("Street Address");
  const cityField = document.getElementById("City");

  let validData = true;
  validData = checkField(siteName) && validData;
  validData = checkField(streetAddress) && validData;
  validData = checkField(cityField) && validData;

  /* Drop Down Selection Fields */
  const stateField = document.getElementById("State");
  const countyField = document.getElementById("County");

  const website = document.getElementById("Website");
  const facebook = document.getElementById("Facebook");
  if(document.getElementById("distributionCenterRadio").checked) {
      siteType = "Distribution Center";
  } else if(document.getElementById("supplyHubRadio").checked) {
      siteType = "Supply Hub";
  } else {
      siteType = "Food Pantry";
  }
  const siteHours = document.getElementById("Site Hours");

  const maxSupplyLoad = document.getElementById('maxSupplyLoad');
  const receivingNotes = document.getElementById('receivingNotes');

  const contactName = document.getElementById("Contact Name");


  if (!validData) {
    showAddSiteError("Missing required fields")
    return;
  }

  const url = "/manage/add-site";
  fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      siteName: siteName.value.trim(),
      streetAddress: streetAddress.value.trim(),
      city: cityField.value.trim(),
      state: stateField.value.trim(),
      county: countyField.value.trim(),
      website: website.value.trim(),
      facebook: facebook.value.trim(),
      siteType: siteType.trim(),
      siteHours: siteHours.value.trim(),

      maxSupplyLoad: maxSupplyLoad.value,
      receivingNotes: receivingNotes.value.trim(),

      contactName: contactName.value.trim(),
    })
  })
  .then(
      async function (response) {
        if (response.ok) {
          const responseJson = await response.json();
          window.location.href = responseJson.manageSiteUrl;
        } else {
          const responseJson = await response.json();
          showAddSiteError("Failed to save: " + responseJson.error);
        }
        window.scrollTo(0, document.body.scrollHeight);
      },
      function (error) {
        showAddSiteError("Failed to save, server not available.");
        window.scrollTo(0, document.body.scrollHeight);
      });
}

function showAddSiteError(text) {
  const redX = document.getElementById("red-x");
  redX.classList.remove("hidden");

  const errorMessage = document.getElementById("error-message");
  errorMessage.innerHTML = text;
}

/** Returns false if field is not set, true if field is set */
function checkField(fieldElement) {
  if (fieldElement.value.trim() === "") {
    fieldElement.classList.add("missingData");
    return false;
  } else {
    fieldElement.classList.remove("missingData");
    return true;
  }
}
