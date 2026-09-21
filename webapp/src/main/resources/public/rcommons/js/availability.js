/**
 * availability.js — Shared weekly-availability grid model.
 *
 * Used by onboarding (auth.js), the Profile page (profile.js), and read by
 * calendar.js for the org/volunteer calendars. Availability is one flat
 * weekly pattern — a plain object keyed "day_HH" (e.g. "sat_09" = free
 * 9-10am on Saturdays) — not per-date. Replaces the old 3-block
 * Morning/Afternoon/Evening model with actual hours.
 */

var AVAIL_DAYS = ['sun', 'mon', 'tue', 'wed', 'thu', 'fri', 'sat'];

var AVAIL_HOURS = []; // 6am through 9pm start-of-hour (covers 6am-10pm)
(function() { for (var h = 6; h <= 21; h++) AVAIL_HOURS.push(h); })();

function availKey(day, hour) { return day + '_' + String(hour).padStart(2, '0'); }

function availHourLabel(hour) {
  var ampm = hour >= 12 ? 'PM' : 'AM';
  var h12  = hour % 12 === 0 ? 12 : hour % 12;
  return h12 + ' ' + ampm;
}

/** Groups a day's available hours into contiguous ranges for compact
 * display — e.g. hours [18,19,20,21] reads as "6–10 PM" instead of
 * listing every hour. */
function summarizeAvailHours(hours) {
  if (!hours.length) return '';
  var sorted = hours.slice().sort(function(a, b) { return a - b; });
  var ranges = [];
  var start = sorted[0], prev = sorted[0];
  sorted.slice(1).forEach(function(h) {
    if (h === prev + 1) { prev = h; return; }
    ranges.push([start, prev]);
    start = h; prev = h;
  });
  ranges.push([start, prev]);
  return ranges.map(function(r) {
    return r[0] === r[1] ? availHourLabel(r[0]) : availHourLabel(r[0]) + '–' + availHourLabel(r[1] + 1);
  }).join(', ');
}

function availableHoursForDay(availability, dayKey) {
  return AVAIL_HOURS.filter(function(hour) { return availability[availKey(dayKey, hour)]; });
}

/**
 * Click-and-drag ("paint") support for the availability grid.
 * Pressing down on a cell flips it and starts a drag; every other cell the
 * pointer moves over while the button/finger stays down is set to match
 * the same on/off state, so a whole block of hours can be marked in one
 * stroke instead of clicking each cell individually.
 *
 * State is kept in one shared object (not per-grid) and the document-level
 * listeners are bound exactly once, since renderAvailGrid may be called
 * many times (onboarding step re-entry, profile availability edits, preset
 * buttons re-rendering the grid, etc.) and we don't want listeners piling up.
 */
var _availDrag = { active: false, mode: null, lastKey: null, availability: null, onToggle: null };
var _availDragBound = false;

function _bindAvailDragListenersOnce() {
  if (_availDragBound) return;
  _availDragBound = true;

  function endDrag() {
    if (!_availDrag.active) return;
    _availDrag.active  = false;
    _availDrag.lastKey = null;
    document.body.classList.remove('avail-dragging');
  }

  // elementFromPoint (rather than pointer capture / pointerenter) is used so
  // this works the same way for mouse drag and touch drag: capturing the
  // pointer on the first cell would stop other cells from ever receiving
  // enter/move events.
  document.addEventListener('pointermove', function(e) {
    if (!_availDrag.active) return;
    var el = document.elementFromPoint(e.clientX, e.clientY);
    if (!el || !el.classList || !el.classList.contains('avail-cell')) return;
    var key = el.dataset.key;
    if (!key || key === _availDrag.lastKey) return;
    _availDrag.lastKey = key;
    el.classList.toggle('on', _availDrag.mode);
    _availDrag.availability[key] = _availDrag.mode;
    if (_availDrag.onToggle) _availDrag.onToggle(_availDrag.availability);
  });

  document.addEventListener('pointerup', endDrag);
  document.addEventListener('pointercancel', endDrag);
  // Safety net: if the pointer leaves the window entirely while dragging
  // (e.g. released over a scrollbar or outside the viewport).
  window.addEventListener('blur', endDrag);
}

/** (Re)builds the hourly grid inside containerEl. The header row (empty
 * corner + 7 day labels) is expected to already be in the markup — this
 * only manages the row-label + hour cells beneath it. */
function renderAvailGrid(containerEl, availability, onToggle) {
  if (!containerEl) return;
  containerEl.querySelectorAll('.avail-row-label, .avail-cell').forEach(function(e) { e.remove(); });
  _bindAvailDragListenersOnce();

  AVAIL_HOURS.forEach(function(hour) {
    var label = document.createElement('div');
    label.className = 'avail-row-label';
    label.textContent = availHourLabel(hour);
    containerEl.appendChild(label);

    AVAIL_DAYS.forEach(function(day) {
      var key = availKey(day, hour);
      var cell = document.createElement('div');
      cell.className = 'avail-cell' + (availability[key] ? ' on' : '');
      cell.dataset.key = key;
      cell.addEventListener('pointerdown', function(e) {
        // Only the primary mouse button / a touch/pen contact starts a drag.
        if (e.button !== undefined && e.button !== 0) return;
        e.preventDefault(); // stop touch-scroll and text-selection while painting

        var turnOn = !cell.classList.contains('on');
        cell.classList.toggle('on', turnOn);
        availability[key] = turnOn;

        _availDrag.active       = true;
        _availDrag.mode         = turnOn;
        _availDrag.lastKey      = key;
        _availDrag.availability = availability;
        _availDrag.onToggle     = onToggle;
        document.body.classList.add('avail-dragging');

        if (onToggle) onToggle(availability);
      });
      containerEl.appendChild(cell);
    });
  });
}

var AVAIL_PRESETS = {
  mornings: { label: 'Mornings', hours: [6, 7, 8, 9] },
  evenings: { label: 'Evenings', hours: [18, 19, 20, 21] },
  weekends: { label: 'Weekends', days: ['sat', 'sun'] },
  clear:    { label: 'Clear all', clear: true }
};

/** Applies a named quick-action preset to an availability object in place. */
function applyAvailPreset(availability, presetKey) {
  var preset = AVAIL_PRESETS[presetKey];
  if (!preset) return;
  if (preset.clear) {
    Object.keys(availability).forEach(function(k) { delete availability[k]; });
    return;
  }
  var days  = preset.days  || AVAIL_DAYS;
  var hours = preset.hours || AVAIL_HOURS;
  days.forEach(function(day) {
    hours.forEach(function(hour) { availability[availKey(day, hour)] = true; });
  });
}

/** Renders preset quick-action buttons into containerEl; onApply(presetKey)
 * is called on click so the caller can re-render its own grid afterward. */
function renderAvailPresetButtons(containerEl, onApply) {
  if (!containerEl) return;
  containerEl.innerHTML = Object.keys(AVAIL_PRESETS).map(function(key) {
    return '<button type="button" class="btn btn-secondary btn-sm" data-preset="' + key + '">' + AVAIL_PRESETS[key].label + '</button>';
  }).join('');
  containerEl.querySelectorAll('button[data-preset]').forEach(function(btn) {
    btn.addEventListener('click', function() { onApply(btn.dataset.preset); });
  });
}