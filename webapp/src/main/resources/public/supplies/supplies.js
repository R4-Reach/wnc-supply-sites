
function getFilterItems() {
    const url = "/supplies/filter-data";
      fetch(url, {
        method: 'GET',
        headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
        }
      })
      .then(response => {
            if (!response.ok) {
              throw new Error(`Server ${response.status} error: ${response.statusText}`);
            }
            return response.json()
          }
      )
      .then(filterData => {
        const emptyOption = "<option value=''></option>\n";
        document.getElementById('site-select').innerHTML =
            emptyOption +
            filterData.sites.map(v =>
                `<option value="${v}">${v}</option>`).join("\n");
        document.getElementById('county-select').innerHTML =
            emptyOption +
            filterData.counties.map(v =>
                `<option value="${v}">${v}</option>`).join("\n");
        document.getElementById('state-select').innerHTML =
                    emptyOption +
                    filterData.states.map(v =>
                        `<option value="${v}">${v}</option>`).join("\n");
        document.getElementById('item-select').innerHTML =
            emptyOption +
            filterData.items.map(v =>
                `<option value="${v}">${v}</option>`).join("\n");

        prePopulateCheckboxValuesIntoSession();
        addFiltersFromSession();
        updateData();
      }).catch(error => {
        showSuppliesError(error);
      });
}

function prePopulateCheckboxValuesIntoSession() {
  const fieldsets = document.querySelectorAll("fieldset");
  fieldsets.forEach((fieldset) => {
    const currentValueInSession = sessionStorage.getItem(fieldset.id);
    if (!currentValueInSession) {
      const fieldsetCheckboxes = fieldset.querySelectorAll("input");
      const fieldsetValue = []
      fieldsetCheckboxes.forEach((checkbox) => {
        if (checkbox.checked) fieldsetValue.push(checkbox.id);
      });
      sessionStorage.setItem(fieldset.id, JSON.stringify(fieldsetValue));
    }
  });
};

function addFiltersFromSession() {
  const filters = {
    selections: ["site", "county", "item", "state"],
    checkboxes: ["item-status", "site-status", "site-type"],
  }

  addSelectionFiltersFromSession(filters.selections);
  addCheckboxFiltersFromSession(filters.checkboxes);
  setSortByFromSession();
}

function setSortByFromSession() {

  const sortByValueInSession = JSON.parse(sessionStorage.getItem("sort-by"));
  const sortByOptions = document.getElementById("sort-results").children;

  for (let i = 0; i < sortByOptions.length; i++) {
    const sortByOption = sortByOptions[i];
    if (sortByOption.value === sortByValueInSession) sortByOption.setAttribute("selected", "selected");
  }

}

function addSelectionFiltersFromSession(selections){
  selections.forEach((filter) => {
    const currentFilterSet = readSelectionsFromSession(filter);
    currentFilterSet.forEach((filterValue) => {
      addSelection(filter, filterValue)
    });
  });
}

function addCheckboxFiltersFromSession(fieldsets){
  fieldsets.forEach((fieldset) => {
    const fieldsetElement = document.querySelector(`#${fieldset}`);
    const inputs = fieldsetElement.querySelectorAll("input");
    const currentSessionValue = JSON.parse(sessionStorage.getItem(fieldset));
    inputs.forEach((input) => input.checked = currentSessionValue.includes(input.id))
  })
}

function readSelectionsFromSession(filterCategory) {
  const currentFilterSet = JSON.parse(sessionStorage.getItem(`${filterCategory}-filter`));
  return currentFilterSet ? currentFilterSet : [];
}

function handleCheckboxSelection(element) {
  const isChecked = element.checked;
  const fieldset = element.closest("fieldset").id;
  saveCheckboxToSession(fieldset, element.id, isChecked);
  updateData();
}

function handleSelection(elementSelected) {
  const selectionBox = document.getElementById(elementSelected + "-select");
  const selection = selectionBox.value;

  if (selection === "") {
    return;
  }

  saveToSession(elementSelected, selection);
  addSelection(elementSelected, selection);
  selectionBox.selectedIndex = 0;
  updateData();
}

function handleSortSelection() {
   const sortDataBy = document.getElementById("sort-results").value;
   sessionStorage.setItem("sort-by", JSON.stringify(sortDataBy));
  updateData();
}

function saveCheckboxToSession(fieldsetName, name, wasChecked) {
  const fieldsetInSession = getFieldsetValueFromSession(fieldsetName);

  const itemExistsInFieldset = fieldsetInSession.includes(name);

  if (wasChecked && !itemExistsInFieldset) {
    fieldsetInSession.push(name);
    sessionStorage.setItem(fieldsetName, JSON.stringify(fieldsetInSession));
    return;
  } else if (!wasChecked && itemExistsInFieldset ) {
    const newFieldsetValue = JSON.stringify(fieldsetInSession.reduce((acc, cur) => {
      if (cur !== name) acc.push(cur);
      return acc;
    }, []))
    sessionStorage.setItem(fieldsetName, newFieldsetValue);
  }
}

function getFieldsetValueFromSession(fieldsetName) {
  const fieldsetInSession = sessionStorage.getItem(fieldsetName);
  if (!fieldsetInSession) {
    sessionStorage.setItem(fieldsetName, JSON.stringify([]));
    return JSON.parse(sessionStorage.getItem(fieldsetName));
  }
  return JSON.parse(fieldsetInSession);
}

function saveToSession(elementSelected, value) {
  const currentFilterSet = JSON.parse(sessionStorage.getItem(`${elementSelected}-filter`));

  if (currentFilterSet) {
    if (currentFilterSet.includes(value)) return;
    return sessionStorage.setItem(`${elementSelected}-filter`, JSON.stringify([...currentFilterSet, value]));
  } else {
    return sessionStorage.setItem(`${elementSelected}-filter`, JSON.stringify([value]));
  }
};

function addSelection(elementSelected, value) {
  if (value === "") {
    return;
  }
  const currentSelections = readSelections(elementSelected);
  if (currentSelections.includes(value)) {
    return;
  }
  const selectionDiv = document.getElementById(elementSelected + "-selections");
  selectionDiv.innerHTML +=
      `<div class='box horizontal selection-box' onclick="removeSelection(this, '${elementSelected}', '${value}')">
                    <div style="margin-right: 5px"><button type="button">X</button></div>
                    <div class="selected-value">${value}</div>\
                </div>`
}

function removeFromSession(filterCategory, filterValue) {
  const currentFilterSet = JSON.parse(sessionStorage.getItem(`${filterCategory}-filter`));
  const newFilterSet = currentFilterSet.reduce((acc, cur) => {
    if (cur !== filterValue) acc.push(cur);
    return acc;
  }, [])
  sessionStorage.setItem(`${filterCategory}-filter`, JSON.stringify(newFilterSet));
}

function clearSelectionFromSession(filterCategory) {
  const currentValue = readSelectionsFromSession(filterCategory);
  if (currentValue.length === 0) return;
  sessionStorage.setItem(`${filterCategory}-filter`, JSON.stringify([]));
}

function removeSelection(div, filterCategory, filterValue) {
  removeFromSession(filterCategory, filterValue);
  div.parentNode.removeChild(div);
  updateData();
}

function readSelections(selectionElement) {
  const selectionsDiv = document.getElementById(selectionElement + "-selections");
  return [...selectionsDiv.querySelectorAll(".selected-value")].map(v => v.innerHTML);
}

function clearSelections(selectionElement) {
  clearSelectionFromSession(selectionElement);
  document.getElementById(selectionElement + "-selections").innerHTML = "";
  document.getElementById(selectionElement + "-select").selectedIndex = 0;
  updateData();
}

async function updateData() {
  try {
    document.getElementById("error-div").innerHTML = "";
    startLoaderAnimation();

    // Grab The Site Data
    let data = await fetchSupplyData();

    // Update the data based on Results filter data
    data = sortDataResults(data);

    const supplyHubHtml = "<br><span class='supply-hub'>(Supply Warehouse)</span>";
    const notAcceptingDonationsHtml = "<br><span class='not-accepting-donations'>(Not Accepting Donations)</span>";

    // write data to the results table
    document.getElementById('results-table').querySelector("tbody").innerHTML =
        data.results.map(r => `
              <tr>
                  <td>
                    <div class="vertical site-name-column">
                      <span>
                        <a href='site-detail?id=${r.id}'><span class="site-name">${r.site}</span></a>
                      </span>
                      <span>${r.county} County, ${r.state}</span>
                      <span>${r.siteType === "Supply Hub" ? supplyHubHtml : ''}</span>
                      <span>${r.acceptingDonations ? '' : notAcceptingDonationsHtml}</span>
                      <div class="vertical">
                        <span>Last Updated</span>
                        <span>${r.inventoryLastUpdated}</span>
                      </div>
                      ${r.lastDelivery  ?
                        "<div class='vertical'><span>Last Delivery</span><span>" + r.lastDelivery + "</span></div>" : ""
                      }
                    </div>
                  </td>
                  <td>${formatItems(r.neededItems)}</td>
                  <td>${formatItems(r.availableItems)}</td>
              </tr>`)
        .join("\n");

    // Redisplay results ct and results filter
    document.getElementById('result-count').innerHTML = `${data.resultCount} results`;
    stopLoaderAnimation();
  } catch (error) {
    document.getElementById('result-count').innerHTML = "";
    stopLoaderAnimation();
    showSuppliesError(error);
  }
}

function sortDataResults (data) {
    const sortType = document.getElementById("sort-results").value;

    switch(sortType) {
        case "last-updated":
            data.results.sort((siteA, siteB) => {
                const siteADate = new Date(siteA.inventoryLastUpdated).getTime();
                const siteBDate = new Date(siteB.inventoryLastUpdated).getTime();
                return siteBDate - siteADate;
            })
            break;
        case "last-updated-reverse":
            data.results.sort((siteA, siteB) => {
                const siteADate = new Date(siteA.inventoryLastUpdated).getTime();
                const siteBDate = new Date(siteB.inventoryLastUpdated).getTime();
                return siteADate - siteBDate;
            })
            break;
        case "alphabetical":
            data.results.sort((siteA, siteB) => {
                return siteA.site.toLowerCase().localeCompare(siteB.site.toLowerCase())
            })
    }
    return data;
}

function showSuppliesError(error) {
  console.error(error, error.stack);
  document.getElementById("error-div").innerHTML =
      `Error fetching data. Server is not available. Error: ${error.message}`;
}

function startLoaderAnimation() {
  // Clear out results count
  document.getElementById('result-count').innerHTML = "";
  document.getElementById('loader-div').style.animationPlayState = 'running';
  document.getElementById('loader-div').style.display = 'block';
}

function stopLoaderAnimation() {
  document.getElementById('loader-div').style.animationPlayState = 'paused';
  document.getElementById('loader-div').style.display = 'none';
}

async function fetchSupplyData() {
  const url = "/supplies/site-data";

  // Drop-down filters values
  const sites = readSelections('site');
  const counties = readSelections('county');
  const items = readSelections('item');
  const states = readSelections('state');

  // Toggle filters values
  const itemStatus = [...document.getElementById('item-status').querySelectorAll("input:checked")].map(c => c.value);
  const siteType = [...document.getElementById('site-type').querySelectorAll("input:checked")].map(c => c.value);
  const acceptingDonations = document.getElementById('accepting-donations').checked;
  const notAcceptingDonations = document.getElementById('not-accepting-donations').checked;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      sites: sites,
      items: items,
      counties: counties,
      states: states,
      itemStatus: itemStatus,
      siteType: siteType,
      acceptingDonations: acceptingDonations,
      notAcceptingDonations: notAcceptingDonations
    })
  });

  if (!response.ok) {
    throw new Error(`Response status: ${response.status}, ${response}`);
  }
  return await response.json();

  //    Example response JSON
  // var data = JSON.parse(`{
  //   "resultCount": 2,
  //   "results": [
  //     {"site": "site1", "county": "Ashe", "neededItems": [
  //         {"name": "heater", "status": "urgent"}
  //         {"name": "bread", "status": "need"}
  //       ], "availableItems": [
  //         {"name": "butter", "status": "available"},
  //         {"name": "water", "status": "oversupply"},
  //       ]},
  //     {"site": "site2", "county": "Watauga", "items": [
  //         {"name": "water", "status": "need"}
  //       ], "availableItems": [
  //         {"name": "bread", "status": "available"}
  //       ]}
  //   ]
  // }`);
}

function formatItems(items) {

  let list1 = "";
  let list2 = "";

  if(items.length < 5) {
    list1 = formatAsList(items);
  } else {
    list1 = formatAsList(items.slice(0, items.length / 2));
    list2 = formatAsList(items.slice(items.length / 2));
  }
  return `<div class="horizontal">${list1} ${list2}</div>`
}

function formatAsList(items) {

  return "<ul>\n" +
      items.map(i =>
          `<li><input type="hidden" value="${i.tags}"><div class="${i.displayClass}">${i.name}</div></li>\n`
      ).join("\n")
      + "\n</ul>";
}

function initializeModal() {
    if (!sessionStorage.getItem("calledToAction")) {
        const modal = document.getElementById("getInvolvedModal");
        modal.showModal();
        sessionStorage.setItem("calledToAction", "true");
    }
};
