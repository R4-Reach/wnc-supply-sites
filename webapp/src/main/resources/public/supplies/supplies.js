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
  removeButton.setAttribute('aria-label', 'Remove ' + value);
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
  const box = button.closest('.selection-box');
  const list = box.parentElement;
  // Removing the focused chip would otherwise drop focus to <body>; move it to a sensible
  // successor first (the next remaining chip's remove button, else the group's dropdown).
  const sibling = box.nextElementSibling || box.previousElementSibling;
  box.remove();
  const category = list.id.replace('-selections', '');
  const successor =
      (sibling && sibling.querySelector('button')) || document.getElementById(category + '-select');
  if (successor) {
    successor.focus();
  }
  htmx.trigger(document.getElementById('filters-form'), 'refresh');
}

function clearSelections(category) {
  const list = document.getElementById(category + '-selections');
  // Keep the empty-state placeholder; only drop the chip boxes.
  list.querySelectorAll('.selection-box').forEach(box => box.remove());
  document.getElementById(category + '-select').focus();
  htmx.trigger(document.getElementById('filters-form'), 'refresh');
}

// Wire the styled-but-previously-inert #error-div to htmx's failure events: a filter request that
// times out, fails to send, or returns an error leaves the last good results in place and shows a
// plain-language message, instead of failing silently. Any successful request clears it.
document.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('filters-form');
  const errorDiv = document.getElementById('error-div');
  if (!form || !errorDiv) {
    return;
  }
  const showError = () => {
    errorDiv.textContent =
        'Could not update results — check your connection and try again. '
        + 'The results below still show your last successful search.';
  };
  const clearError = () => {
    errorDiv.textContent = '';
  };
  // Clear at the start of every request; the error events below fire later in the same request's
  // lifecycle, so a failed request ends up showing the message and a successful one stays cleared.
  form.addEventListener('htmx:beforeRequest', clearError);
  ['htmx:responseError', 'htmx:sendError', 'htmx:timeout'].forEach(
      evt => form.addEventListener(evt, showError));
});
