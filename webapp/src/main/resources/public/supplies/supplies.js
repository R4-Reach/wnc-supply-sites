// Minimal client-side glue for the supplies filter chips. Everything else (fetching results,
// sorting, and persisting selections) is handled server-side via htmx; this only manages the
// add/remove of the multi-value selection chips, which htmx cannot express natively.
//
// Each chip carries a hidden <input> whose name is the request param the server binds. Adding a
// chip from a dropdown lets the dropdown's own change event bubble to #filters-form, which htmx
// posts (with the freshly-added hidden input included). Removing or clearing chips is a click, so
// those fire htmx's "refresh" trigger explicitly.

const FILTER_PARAM = {site: 'sites', state: 'states', county: 'counties', item: 'items'};

function handleSelection(category) {
  const select = document.getElementById(category + '-select');
  const value = select.value;
  if (!value) {
    return;
  }
  const list = document.getElementById(category + '-selections');
  const existing = [...list.querySelectorAll('.selected-value')].map(e => e.textContent.trim());
  if (!existing.includes(value)) {
    list.appendChild(buildChip(FILTER_PARAM[category], value));
  }
  // Reset the dropdown; the change event keeps bubbling to the form, which htmx posts.
  select.selectedIndex = 0;
}

function buildChip(paramName, value) {
  const chip = document.createElement('div');
  chip.className = 'box horizontal selection-box';

  const removeCell = document.createElement('div');
  removeCell.style.marginRight = '5px';
  const removeButton = document.createElement('button');
  removeButton.type = 'button';
  removeButton.textContent = 'X';
  removeButton.onclick = () => removeSelection(removeButton);
  removeCell.appendChild(removeButton);

  const label = document.createElement('div');
  label.className = 'selected-value';
  label.textContent = value;

  const hidden = document.createElement('input');
  hidden.type = 'hidden';
  hidden.name = paramName;
  hidden.value = value;

  chip.append(removeCell, label, hidden);
  return chip;
}

function removeSelection(button) {
  button.closest('.selection-box').remove();
  htmx.trigger(document.getElementById('filters-form'), 'refresh');
}

function clearSelections(category) {
  document.getElementById(category + '-selections').innerHTML = '';
  htmx.trigger(document.getElementById('filters-form'), 'refresh');
}
