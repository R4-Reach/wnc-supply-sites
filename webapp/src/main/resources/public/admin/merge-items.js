function selectRadio() {

  enableAllCheckboxes();

  disableCheckboxOnCurrentRow(event);

  removeGreenHighlightingOnAllRows();

  greenHighlightCurrentRow(event);

  handleMergeButtonEnableDisable();
}


function selectCheckbox() {
  const row =
      event.target.parentNode.parentNode;
  if(event.target.checked) {
    row.classList.add("merge-delete-row");
  } else {
    row.classList.remove("merge-delete-row");
  }
  handleMergeButtonEnableDisable();
}

/**
 * Enables or disables the 'merge' button as appropriate.
 */
function handleMergeButtonEnableDisable() {
  // need a radio button to be checked and at least one checkbox
  const checkboxCheckedCount = [...document.getElementById("itemTable")
  .querySelectorAll("tbody tr input[type=checkbox]:checked")]
  .reduce((sum, value) => sum + 1, 0);

  const radioSelected = [...document.getElementById("itemTable")
  .querySelectorAll("tbody tr input[type=radio]:checked")]
  .reduce((sum, value) => sum + 1, 0);

  document.getElementById("mergeButton").disabled = (checkboxCheckedCount === 0 || radioSelected === 0);
}


function enableAllCheckboxes() {
  [...document.getElementById("itemTable")
    .querySelectorAll("tbody tr input[type=checkbox]")]
    .forEach(checkBox => checkBox.disabled = false)
}

function disableCheckboxOnCurrentRow(event) {
  event.target.parentNode.parentNode
    .querySelector("td input[type=checkbox]")
    .disabled = true

  event.target.parentNode.parentNode
  .querySelector("td input[type=checkbox]")
      .checked = false
}

function removeGreenHighlightingOnAllRows() {
  [...document.getElementById("itemTable")
    .querySelectorAll("tr")]
    .forEach(tr => tr.classList.remove("merge-keep-row"));
}

function greenHighlightCurrentRow(event) {
  event.target.parentNode.parentNode
    .classList.add("merge-keep-row");
  event.target.parentNode.parentNode
    .classList.remove("merge-delete-row");
}

//--------------
// do merge functions

function doMerge() {
  // determine the name of the selected radio button
  // do this by grabbing the label of the selected radio button
  const radioButtonId =
      document.getElementById("itemTable")
      .querySelector("tbody tr input[type=radio]:checked")
          .id;
  const radioButtonItem = document.getElementById(radioButtonId + "-label").innerHTML;

  // determine the name of the checked checkboxes
  // do this by grabbing the label of the checked checkboxes
  const checkedItems = [...document.getElementById("itemTable")
  .querySelectorAll("tbody tr input[type=checkbox]:checked")]
  .map(checkBox => checkBox.id)
  .map(id => document.getElementById(id + "-label").innerHTML);


  const confirmed = window.confirm(`Are you sure?\n
      Merge into: ${radioButtonItem}
      Deleting items: ${checkedItems}`);

  if(confirmed) {
    const radioButtonItemId =
        document.getElementById("itemTable")
        .querySelector("tbody tr input[type=radio]:checked")
            .value;

    const checkBoxItemIds =
        [...document.getElementById("itemTable")
        .querySelectorAll("tbody tr input[type=checkbox]:checked")]
        .map(checkBox => checkBox.value)

    fetch("/admin/merge-items/do-merge", {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        mergeInto: radioButtonItemId,
        mergeItems: checkBoxItemIds
      })
    })
    .then(
        async function (response) {
          const responseJson = await response.json();

          if (response.ok) {
            document.getElementById("message").classList.remove("errorMessage");
            document.getElementById("message").classList.add("confirm-message");
            document.getElementById("message").innerHTML =
                `Success - ${checkBoxItemIds.length} items merged into ${radioButtonItem}`;

            [...document.getElementById("itemTable")
            .querySelectorAll("tbody tr input[type=checkbox]:checked")]
            .map(checkBox => checkBox.parentNode.parentNode)
            .forEach(tableRow => tableRow.remove());
          } else {
            document.getElementById("message").classList.add("errorMessage");
            document.getElementById("message").classList.remove("confirm-message");
            document.getElementById("message").innerHTML =
                "Failed, server message: " +  JSON.stringify(responseJson);
          }
        },
        function (error) {
          console.log("error")
          document.getElementById("message").classList.add("errorMessage");
          document.getElementById("message").classList.remove("confirm-message");
          document.getElementById("message").innerHTML =
              "Failed, server error (server not available):" + error.message;
        });

  }
}
