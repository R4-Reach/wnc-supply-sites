/**
 * org.js — Org-side event/task/shift management (view switcher, create
 * event, shifts & signups)
 *
 * Hierarchy: an Event optionally groups Tasks (what needs doing); a Task
 * has one or more Shifts (a time-boxed slot with a capacity); a Shift has
 * signups. The simple case ("trash pickup next Saturday") still only takes
 * one form: one event, one task defaulted to the event's own name, one
 * shift spanning the whole event. Nothing forces the extra structure to
 * be visible unless you ask for it.
 */

// ─── DEMO DATA ──────────────────────────────────────────────────────────
// Same 3 shifts shown on the volunteer Opportunities page, now properly
// split: title/description/category live on the task, time/capacity/
// location on the shift. Shift ids are unchanged so DEMO_SIGNUPS still
// lines up without any remapping.

var DEMO_EVENTS = [
  { id: 'event-1', title: 'Meal prep & distribution shift', siteId: null, siteName: 'His Humble Hands', county: 'Caldwell, NC', startTime: '2026-08-09T09:00', endTime: '2026-08-09T13:00', timezone: 'America/New_York', status: 'PENDING' },
  { id: 'event-2', title: 'Sorting & inventory — supply hub', siteId: null, siteName: 'Civilian Disaster Response Org', county: 'Buncombe, NC', startTime: '2026-08-10T10:00', endTime: '2026-08-10T14:00', timezone: 'America/New_York', status: 'PENDING' },
  { id: 'event-3', title: 'Weekend food pantry — stocking & intake', siteId: null, siteName: 'Old Fort Church of God', county: 'McDowell, NC', startTime: '2026-08-13T08:00', endTime: '2026-08-13T12:00', timezone: 'America/New_York', status: 'PENDING' }
];

var DEMO_TASKS = [
  { id: 'task-1', eventId: 'event-1', title: 'Meal prep & distribution shift', description: 'Help sort and hand out boxes at the weekly distribution. No experience needed — we\'ll train you on site.', category: 'food' },
  { id: 'task-2', eventId: 'event-2', title: 'Sorting & inventory — supply hub', description: 'Sort incoming donations and update shelf inventory at the CDRO supply hub.', category: 'disaster' },
  { id: 'task-3', eventId: 'event-3', title: 'Weekend food pantry — stocking & intake', description: 'Stock shelves and check in donations before Saturday distribution opens.', category: 'food' }
];

var DEMO_SHIFTS = [
  { id: 'shift-1', taskId: 'task-1', siteId: null, siteName: null, county: null, startTime: '2026-08-09T09:00', endTime: '2026-08-09T13:00', timezone: 'America/New_York', capacity: 8, minStaffing: 2, status: 'CONFIRMED' },
  { id: 'shift-2', taskId: 'task-2', siteId: null, siteName: null, county: null, startTime: '2026-08-10T10:00', endTime: '2026-08-10T14:00', timezone: 'America/New_York', capacity: 4, minStaffing: 2, status: 'PENDING' },
  { id: 'shift-3', taskId: 'task-3', siteId: null, siteName: null, county: null, startTime: '2026-08-13T08:00', endTime: '2026-08-13T12:00', timezone: 'America/New_York', capacity: 8, minStaffing: 3, status: 'PENDING' }
];

var DEMO_SIGNUPS = [
  { id: 1, shiftId: 'shift-1', volunteerName: 'Maria Gonzalez', notes: 'Bringing my own gloves', status: 'CONFIRMED' },
  { id: 2, shiftId: 'shift-1', volunteerName: 'Devon Price',    notes: '',                        status: 'CONFIRMED' },
  { id: 3, shiftId: 'shift-2', volunteerName: 'Aaliyah Ferris', notes: 'Can only stay until noon', status: 'CANCELED' }
];

var CATEGORY_META = {
  food:       { label: 'Food Distribution', tagClass: 'tag-cat-food' },
  disaster:   { label: 'Disaster Response', tagClass: 'tag-cat-disaster' },
  repair:     { label: 'Home Repair',       tagClass: 'tag-cat-repair' },
  unassigned: { label: 'Community',         tagClass: 'tag-cat-unassigned' }
};

var TZ_OPTIONS = [
  { v: 'America/New_York',    l: 'Eastern — America/New_York' },
  { v: 'America/Chicago',     l: 'Central — America/Chicago' },
  { v: 'America/Denver',      l: 'Mountain — America/Denver' },
  { v: 'America/Los_Angeles', l: 'Pacific — America/Los_Angeles' },
  { v: 'America/Anchorage',   l: 'Alaska — America/Anchorage' },
  { v: 'Pacific/Honolulu',    l: 'Hawaii — Pacific/Honolulu' }
];
var TZ_ABBR = {
  'America/New_York': 'ET', 'America/Chicago': 'CT', 'America/Denver': 'MT',
  'America/Los_Angeles': 'PT', 'America/Anchorage': 'AKT', 'Pacific/Honolulu': 'HT'
};

var CAL_ICON = '<svg viewBox="0 0 16 16" fill="none" stroke-width="1.5"><rect x="2" y="3" width="12" height="11" rx="1.5"/><path d="M2 6h12M5.5 2v2.5M10.5 2v2.5"/></svg>';

var currentOrgShiftId = null;

// ─── JOIN HELPERS ───────────────────────────────────────────────────────
// A shift only stores time/capacity/an optional location override — its
// name, description, and category always come from its task; its default
// location comes from the task's event unless the shift overrides it.

function getTask(taskId)   { return DEMO_TASKS.find(function(t) { return t.id === taskId; }); }
function getEvent(eventId) { return DEMO_EVENTS.find(function(e) { return e.id === eventId; }); }
function shiftTask(shift)  { return getTask(shift.taskId); }
function shiftEventOf(shift) { var t = shiftTask(shift); return t ? getEvent(t.eventId) : null; }
function shiftTitle(shift) { var t = shiftTask(shift); return t ? t.title : 'Shift'; }
function shiftCategoryKey(shift) { var t = shiftTask(shift); return t ? t.category : 'unassigned'; }

function shiftLocation(shift) {
  if (shift.siteName) return { siteName: shift.siteName, county: shift.county };
  var ev = shiftEventOf(shift);
  return ev ? { siteName: ev.siteName, county: ev.county } : { siteName: 'TBD', county: '' };
}

/** Rolls up shift-level confirmation into an event-level "is this fully
 * staffed" signal — no separate event.status to keep in sync, it's always
 * derived from its shifts. */
function eventStaffingSummary(eventId) {
  var tasks  = DEMO_TASKS.filter(function(t) { return t.eventId === eventId; });
  var taskIds = tasks.map(function(t) { return t.id; });
  var shifts = DEMO_SHIFTS.filter(function(s) { return taskIds.indexOf(s.taskId) !== -1 && s.status !== 'CANCELED'; });
  var confirmed = shifts.filter(function(s) { return s.status === 'CONFIRMED'; }).length;
  return { total: shifts.length, confirmed: confirmed, allConfirmed: shifts.length > 0 && confirmed === shifts.length };
}

// ─── VIEW SWITCHER ──────────────────────────────────────────────────────

function switchView(view) {
  document.querySelectorAll('.view-switcher-group .tab').forEach(function(t) {
    t.classList.toggle('active', t.dataset.viewBtn === view);
  });

  var isOrg = view === 'org';
  document.getElementById('nav-view-volunteer').style.display = isOrg ? 'none' : '';
  document.getElementById('nav-view-org').style.display       = isOrg ? '' : 'none';

  // Mobile bottom nav + "More" sheet swap the same way the sidebar does —
  // the sidebar itself is hidden entirely below the 768px breakpoint.
  document.getElementById('mob-nav-volunteer').style.display  = isOrg ? 'none' : 'contents';
  document.getElementById('mob-nav-org').style.display        = isOrg ? 'contents' : 'none';
  document.getElementById('mob-more-volunteer').style.display = isOrg ? 'none' : 'contents';
  document.getElementById('mob-more-org').style.display       = isOrg ? 'contents' : 'none';

  navTo(isOrg ? 'org-shifts' : 'dashboard');
}

function goToCreateEvent() {
  navTo('org-create');
  populateOrgLocationSelect();
  populateOrgTimezoneSelect();
  if (draftTasks.length === 0) resetEventDraft();
}

// ─── FORMATTING ─────────────────────────────────────────────────────────

var DOW = ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'];
var MON = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];

/** Parses a "YYYY-MM-DDTHH:MM" value without any timezone conversion — the
 * value already represents wall-clock time at the shift's own timezone. */
function parseLocalDT(v) {
  var parts = (v || '').split('T');
  var d = (parts[0] || '').split('-');
  var t = (parts[1] || '00:00').split(':');
  return { y: +d[0] || 2026, mo: +d[1] || 1, da: +d[2] || 1, h: +t[0] || 0, mi: +t[1] || 0 };
}

/** Same calendar date/time, shifted by whole days — used to generate
 * recurring event/shift instances without any timezone conversion. */
function shiftDateTimeStr(v, days) {
  if (!v || !days) return v;
  var p = parseLocalDT(v);
  var d = new Date(p.y, p.mo - 1, p.da, p.h, p.mi);
  d.setDate(d.getDate() + days);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') +
    'T' + String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
}

function formatTime12(h, mi) {
  var ampm = h >= 12 ? 'PM' : 'AM';
  var h12  = h % 12 === 0 ? 12 : h % 12;
  return h12 + ':' + String(mi).padStart(2, '0') + ' ' + ampm;
}

function formatShiftTime(shift) {
  var s = parseLocalDT(shift.startTime);
  var e = parseLocalDT(shift.endTime);
  var dow = DOW[new Date(s.y, s.mo - 1, s.da).getDay()];
  var abbr = TZ_ABBR[shift.timezone] || shift.timezone;
  return dow + ', ' + MON[s.mo - 1] + ' ' + s.da + ' · ' + formatTime12(s.h, s.mi) + '–' + formatTime12(e.h, e.mi) + ' ' + abbr;
}

/** Staffing status is about whether the shift can run at all, not just how
 * full it is — 'understaffed' below minStaffing, 'minimum-met' once enough
 * people are in to run it, 'full' at capacity. */
function shiftFillInfo(shift) {
  var confirmed = DEMO_SIGNUPS.filter(function(s) { return s.shiftId === shift.id && s.status === 'CONFIRMED'; }).length;
  var minStaffing = shift.minStaffing || 1;
  var remaining = Math.max(0, shift.capacity - confirmed);
  var pct = shift.capacity > 0 ? Math.round((confirmed / shift.capacity) * 100) : 0;
  var status = confirmed >= shift.capacity ? 'full' : (confirmed >= minStaffing ? 'minimum-met' : 'understaffed');
  var statusLabel = status === 'full' ? 'Full'
    : status === 'minimum-met' ? 'Minimum staffing met'
    : 'Needs ' + (minStaffing - confirmed) + ' more to run';
  return { confirmed: confirmed, remaining: remaining, pct: pct, minStaffing: minStaffing, status: status, statusLabel: statusLabel };
}

// ─── SHIFTS LIST ────────────────────────────────────────────────────────

function renderOrgShifts() {
  var list = document.getElementById('org-shifts-list');
  var count = document.getElementById('org-shifts-count');
  if (!list) return;

  if (!currentOrgShiftId || !DEMO_SHIFTS.some(function(s) { return s.id === currentOrgShiftId; })) {
    currentOrgShiftId = DEMO_SHIFTS.length ? DEMO_SHIFTS[0].id : null;
  }

  if (count) count.textContent = DEMO_SHIFTS.length + ' shift' + (DEMO_SHIFTS.length === 1 ? '' : 's');

  if (DEMO_SHIFTS.length === 0) {
    list.innerHTML = '<div class="empty-state">No shifts posted yet.</div>';
    renderOrgSignups(null);
    return;
  }

  list.innerHTML = DEMO_SHIFTS.map(function(shift) {
    var cat = CATEGORY_META[shiftCategoryKey(shift)] || CATEGORY_META.unassigned;
    var loc = shiftLocation(shift);
    var fill = shiftFillInfo(shift);
    var ev = shiftEventOf(shift);
    var title = shiftTitle(shift);
    var eventLine = (ev && ev.title !== title) ? ev.title + ' · ' : '';
    var selected = shift.id === currentOrgShiftId ? ' selected' : '';
    var staffingTagClass = shift.status === 'CANCELED' ? 'tag-dist'
      : fill.status === 'full' ? 'tag-done'
      : fill.status === 'minimum-met' ? 'tag-done'
      : 'tag-urgent';
    var staffingLabel = shift.status === 'CANCELED' ? 'Canceled' : fill.statusLabel;
    var confirmedTag = shift.status === 'CONFIRMED' ? '<span class="tag tag-done">Shift confirmed ✓</span>' : '';
    return '<div class="opp-item' + selected + '" onclick="selectOrgShift(\'' + shift.id + '\')">' +
      '<div class="opp-org-mark mark-amber">' + CAL_ICON + '</div>' +
      '<div class="opp-info">' +
        '<div class="opp-title">' + title + '</div>' +
        '<div class="opp-org">' + eventLine + loc.siteName + (loc.county ? ' · ' + loc.county : '') + '</div>' +
        '<div class="opp-meta" style="margin-bottom:6px;">' +
          '<span class="tag ' + cat.tagClass + '">' + cat.label + '</span>' +
          '<span class="tag tag-dist">' + formatShiftTime(shift) + '</span>' +
          '<span class="tag ' + staffingTagClass + '">' + staffingLabel + '</span>' +
          confirmedTag +
        '</div>' +
        '<div class="item-progress">' +
          '<div class="item-progress-meta"><span>' + fill.confirmed + ' of ' + shift.capacity + ' filled · min ' + fill.minStaffing + '</span><span>' + fill.remaining + ' spots open</span></div>' +
          '<div class="item-progress-bar"><div class="item-progress-fill' + (fill.pct < 40 ? ' low' : '') + '" style="width:' + fill.pct + '%"></div></div>' +
        '</div>' +
      '</div>' +
    '</div>';
  }).join('');

  renderOrgSignups(currentOrgShiftId);
}

function selectOrgShift(shiftId) {
  currentOrgShiftId = shiftId;
  renderOrgShifts();
}

// ─── SIGNUPS PANEL ──────────────────────────────────────────────────────

function renderOrgSignups(shiftId) {
  var titleEl = document.getElementById('org-signups-title');
  var countEl = document.getElementById('org-signups-count');
  var listEl  = document.getElementById('org-signups-list');
  var metaEl  = document.getElementById('org-signups-meta');
  if (!listEl) return;

  var shift = DEMO_SHIFTS.find(function(s) { return s.id === shiftId; });
  if (!shift) {
    if (titleEl) titleEl.textContent = 'Signups';
    if (countEl) countEl.textContent = '';
    if (metaEl) metaEl.innerHTML = '';
    listEl.innerHTML = '<div style="padding:16px 18px;font-size:13px;color:var(--text-3);">Select a shift above to see who\'s signed up.</div>';
    return;
  }

  var signups = DEMO_SIGNUPS.filter(function(s) { return s.shiftId === shiftId; });
  if (titleEl) titleEl.textContent = 'Signups — ' + shiftTitle(shift);
  if (countEl) countEl.textContent = signups.length + ' signup' + (signups.length === 1 ? '' : 's');

  if (metaEl) {
    var task = shiftTask(shift);
    var ev   = shiftEventOf(shift);
    var fill = shiftFillInfo(shift);
    var evSummary = ev ? eventStaffingSummary(ev.id) : null;

    var confirmBtn = shift.status === 'CONFIRMED'
      ? '<button class="btn btn-secondary btn-sm" onclick="unconfirmShiftStaffing(\'' + shift.id + '\')">Confirmed ✓ — undo</button>'
      : '<button class="btn btn-primary btn-sm" onclick="confirmShiftStaffing(\'' + shift.id + '\')"' + (fill.status === 'understaffed' ? ' disabled title="Needs minimum staffing to confirm"' : '') + '>Confirm shift</button>';

    metaEl.innerHTML =
      '<span style="font-size:12px;color:var(--text-3);">Task: <strong style="color:var(--text-1);">' + (task ? task.title : '—') + '</strong>' +
      (ev ? ' · Event: <strong style="color:var(--text-1);">' + ev.title + '</strong>' +
        (evSummary ? ' <span style="color:var(--text-3);">(' + evSummary.confirmed + '/' + evSummary.total + ' shifts confirmed' + (evSummary.allConfirmed ? ' — fully staffed ✓' : '') + ')</span>' : '')
        : '') + '</span>' +
      confirmBtn +
      '<button class="btn btn-secondary btn-sm" onclick="openEditShift(\'' + shift.id + '\')">Edit shift</button>' +
      (task ? '<button class="btn btn-secondary btn-sm" onclick="openEditTask(\'' + task.id + '\')">Edit task</button>' : '') +
      (ev ? '<button class="btn btn-secondary btn-sm" onclick="openEditEvent(\'' + ev.id + '\')">Edit event</button>' : '');
  }

  if (signups.length === 0) {
    listEl.innerHTML = '<div style="padding:16px 18px;font-size:13px;color:var(--text-3);">No one has signed up for this shift yet.</div>';
    return;
  }

  listEl.innerHTML = signups.map(function(s) {
    var confirmed = s.status === 'CONFIRMED';
    return '<div class="hours-item">' +
      '<div class="hours-dot ' + (confirmed ? 'confirmed' : 'logged') + '"></div>' +
      '<div class="hours-info">' +
        '<div class="hours-title">' + s.volunteerName + '</div>' +
        '<div class="hours-org">' + (s.notes ? s.notes : 'No notes') + '</div>' +
      '</div>' +
      '<div style="display:flex;align-items:center;gap:8px;">' +
        '<span class="tag ' + (confirmed ? 'tag-done' : 'tag-dist') + '">' + (confirmed ? 'Confirmed' : 'Canceled') + '</span>' +
        '<button class="btn btn-secondary btn-sm" onclick="' + (confirmed ? 'cancelOrgSignup(' + s.id + ')' : 'confirmOrgSignup(' + s.id + ')') + '">' + (confirmed ? 'Cancel' : 'Confirm') + '</button>' +
      '</div>' +
    '</div>';
  }).join('');
}

function cancelOrgSignup(signupId) {
  var s = DEMO_SIGNUPS.find(function(x) { return x.id === signupId; });
  if (!s) return;
  s.status = 'CANCELED';
  showToast('Signup canceled.');
  renderOrgShifts();
}

function confirmOrgSignup(signupId) {
  var s = DEMO_SIGNUPS.find(function(x) { return x.id === signupId; });
  if (!s) return;
  s.status = 'CONFIRMED';
  showToast('Signup confirmed.');
  renderOrgShifts();
}

// TODO: confirming/un-confirming a shift here should notify signed-up
// volunteers (SMS/email — same delivery mechanism as the existing
// volunteer_delivery SMS notifications) once a real backend exists.

function confirmShiftStaffing(shiftId) {
  var shift = DEMO_SHIFTS.find(function(s) { return s.id === shiftId; });
  if (!shift) return;
  var fill = shiftFillInfo(shift);
  if (fill.status === 'understaffed') {
    showToast('Needs at least ' + fill.minStaffing + ' confirmed volunteers before this shift can be confirmed.');
    return;
  }
  shift.status = 'CONFIRMED';
  showToast('Shift confirmed — staffing is good to go.');
  renderOrgShifts();
  renderOrgCalendar();
}

function unconfirmShiftStaffing(shiftId) {
  var shift = DEMO_SHIFTS.find(function(s) { return s.id === shiftId; });
  if (!shift) return;
  shift.status = 'PENDING';
  showToast('Shift confirmation undone.');
  renderOrgShifts();
  renderOrgCalendar();
}

// ─── CREATE EVENT — draft state ────────────────────────────────────────
// The form is built from this in-memory draft, then translated into
// DEMO_EVENTS/DEMO_TASKS/DEMO_SHIFTS rows on publish. Re-rendered on
// structural changes (add/remove task or shift, mode toggle); plain text
// fields update the draft directly without a re-render so typing doesn't
// lose focus.

// A small library of pre-built tasks an org can start a new task from
// instead of writing title/description/category from scratch every time.
// Picking one just pre-fills the fields below — still fully editable.
var DEMO_TASK_TEMPLATES = [
  { id: 'tmpl-food-sort',  title: 'Food sorting & packing', description: 'Sort donated food items and pack them into distribution boxes.', category: 'food' },
  { id: 'tmpl-warehouse',  title: 'Warehouse stocking',     description: 'Unload deliveries and organize shelf inventory.', category: 'disaster' },
  { id: 'tmpl-cleanup',    title: 'Community cleanup',      description: 'Pick up litter and debris in the assigned area.', category: 'unassigned' },
  { id: 'tmpl-repair',     title: 'Minor home repair',      description: 'Basic repairs — patching, painting, yard work — for elderly or disabled residents.', category: 'repair' }
];

function applyTaskTemplate(idx, templateId) {
  var t = DEMO_TASK_TEMPLATES.find(function(x) { return x.id === templateId; });
  if (!t || !draftTasks[idx]) return;
  draftTasks[idx].title = t.title;
  draftTasks[idx].description = t.description;
  draftTasks[idx].category = t.category;
  if (idx === 0) draftTasks[0].syncTitle = false; // picking a template is a deliberate choice, stop auto-syncing from the event name
  renderDraftTasks();
}

var draftTasks = [];

function newDraftTask(syncTitle) {
  return { title: '', description: '', category: 'food', mode: 'single', singleCapacity: '', singleMinStaffing: '', shifts: [], syncTitle: !!syncTitle };
}

function resetEventDraft() {
  draftTasks = [newDraftTask(true)];
  renderDraftTasks();
}

function updateEventTitleSync(val) {
  if (draftTasks[0] && draftTasks[0].syncTitle) {
    draftTasks[0].title = val;
    var el = document.getElementById('ce-task-title-0');
    if (el) el.value = val;
  }
}

function updateDraftTask(idx, field, val) {
  if (!draftTasks[idx]) return;
  draftTasks[idx][field] = val;
  if (idx === 0 && field === 'title') draftTasks[0].syncTitle = false;
}

function updateDraftShift(taskIdx, shiftIdx, field, val) {
  if (draftTasks[taskIdx] && draftTasks[taskIdx].shifts[shiftIdx]) {
    draftTasks[taskIdx].shifts[shiftIdx][field] = val;
  }
}

function setTaskMode(idx, mode) {
  var t = draftTasks[idx];
  if (!t) return;
  t.mode = mode;
  if (mode === 'multi' && t.shifts.length === 0) {
    t.shifts.push({
      start: document.getElementById('ce-start').value || '',
      end: document.getElementById('ce-end').value || '',
      capacity: t.singleCapacity || '',
      minStaffing: t.singleMinStaffing || ''
    });
  }
  renderDraftTasks();
}

function addDraftTask() {
  draftTasks.push(newDraftTask(false));
  renderDraftTasks();
}

function removeDraftTask(idx) {
  draftTasks.splice(idx, 1);
  renderDraftTasks();
}

function addDraftShift(taskIdx) {
  draftTasks[taskIdx].shifts.push({
    start: document.getElementById('ce-start').value || '',
    end: document.getElementById('ce-end').value || '',
    capacity: '',
    minStaffing: ''
  });
  renderDraftTasks();
}

function removeDraftShift(taskIdx, shiftIdx) {
  draftTasks[taskIdx].shifts.splice(shiftIdx, 1);
  renderDraftTasks();
}

function renderDraftTasks() {
  var container = document.getElementById('ce-tasks-list');
  if (!container) return;

  container.innerHTML = draftTasks.map(function(t, idx) {
    var categoryOptions = Object.keys(CATEGORY_META).map(function(k) {
      return '<option value="' + k + '"' + (t.category === k ? ' selected' : '') + '>' + CATEGORY_META[k].label + '</option>';
    }).join('');

    var shiftBlock = t.mode === 'single'
      ? '<div class="two-fields form-group">' +
          '<div><div class="form-label">Min to run</div>' +
            '<input class="form-input" type="number" min="1" step="1" value="' + escHtml(t.singleMinStaffing) + '" ' +
            'oninput="updateDraftTask(' + idx + ',\'singleMinStaffing\',this.value)" placeholder="e.g. 3"></div>' +
          '<div><div class="form-label">Max volunteers</div>' +
            '<input class="form-input" type="number" min="1" step="1" value="' + escHtml(t.singleCapacity) + '" ' +
            'oninput="updateDraftTask(' + idx + ',\'singleCapacity\',this.value)" placeholder="e.g. 8"></div>' +
        '</div>'
      : renderDraftShiftRows(idx, t);

    var templateOptions = '<option value="">Start from a saved task…</option>' +
      DEMO_TASK_TEMPLATES.map(function(tp) { return '<option value="' + tp.id + '">' + tp.title + '</option>'; }).join('');

    return '<div class="card" style="margin-bottom:12px;">' +
      '<div class="card-body">' +
        '<div class="form-group"><select class="form-select" onchange="applyTaskTemplate(' + idx + ',this.value)">' + templateOptions + '</select></div>' +
        '<div class="form-group"><div class="form-label">Task name</div>' +
          '<input class="form-input" id="ce-task-title-' + idx + '" value="' + escHtml(t.title) + '" ' +
          'oninput="updateDraftTask(' + idx + ',\'title\',this.value)" placeholder="What needs to be done?"></div>' +
        '<div class="form-group"><div class="form-label">Description</div>' +
          '<textarea class="form-textarea" oninput="updateDraftTask(' + idx + ',\'description\',this.value)" ' +
          'placeholder="Anything volunteers should know beforehand">' + escHtml(t.description) + '</textarea></div>' +
        '<div class="form-group"><div class="form-label">Category</div>' +
          '<select class="form-select" onchange="updateDraftTask(' + idx + ',\'category\',this.value);renderDraftTasks();">' + categoryOptions + '</select></div>' +
        '<div class="tabs" style="width:max-content;margin-bottom:12px;">' +
          '<button type="button" class="tab' + (t.mode === 'single' ? ' active' : '') + '" onclick="setTaskMode(' + idx + ',\'single\')">One shift for the whole event</button>' +
          '<button type="button" class="tab' + (t.mode === 'multi' ? ' active' : '') + '" onclick="setTaskMode(' + idx + ',\'multi\')">Break into shifts</button>' +
        '</div>' +
        shiftBlock +
        (draftTasks.length > 1 ? '<button type="button" class="btn btn-secondary btn-sm" onclick="removeDraftTask(' + idx + ')">Remove task</button>' : '') +
      '</div>' +
    '</div>';
  }).join('');
}

function renderDraftShiftRows(taskIdx, t) {
  var rows = t.shifts.map(function(s, si) {
    return '<div style="display:grid;grid-template-columns:1fr 1fr 80px 80px auto;gap:8px;align-items:end;margin-bottom:8px;">' +
      '<div><div class="form-label">Start</div><input class="form-input" type="datetime-local" value="' + s.start + '" ' +
        'onchange="updateDraftShift(' + taskIdx + ',' + si + ',\'start\',this.value)"></div>' +
      '<div><div class="form-label">End</div><input class="form-input" type="datetime-local" value="' + s.end + '" ' +
        'onchange="updateDraftShift(' + taskIdx + ',' + si + ',\'end\',this.value)"></div>' +
      '<div><div class="form-label">Min</div><input class="form-input" type="number" min="1" step="1" value="' + escHtml(s.minStaffing) + '" ' +
        'oninput="updateDraftShift(' + taskIdx + ',' + si + ',\'minStaffing\',this.value)"></div>' +
      '<div><div class="form-label">Max</div><input class="form-input" type="number" min="1" step="1" value="' + escHtml(s.capacity) + '" ' +
        'oninput="updateDraftShift(' + taskIdx + ',' + si + ',\'capacity\',this.value)"></div>' +
      '<button type="button" class="btn btn-secondary btn-sm" onclick="removeDraftShift(' + taskIdx + ',' + si + ')" title="Remove shift">&#10005;</button>' +
    '</div>';
  }).join('');
  return '<div class="form-group">' + rows +
    '<button type="button" class="btn btn-secondary btn-sm" onclick="addDraftShift(' + taskIdx + ')">+ Add shift</button>' +
  '</div>';
}

// ─── CREATE EVENT — location/timezone/recurrence controls ─────────────

function populateOrgLocationSelect() {
  var sel = document.getElementById('ce-location');
  if (!sel) return;
  sel.innerHTML = '<option>Loading sites…</option>';
  apiGetLocations()
    .then(function(sites) {
      if (!sites.length) { sel.innerHTML = '<option>No sites available</option>'; return; }
      sel.innerHTML = sites.map(function(s) {
        return '<option value="' + s.dbId + '">' + s.site + ' · ' + s.county + ', ' + s.state + '</option>';
      }).join('');
    })
    .catch(function() { sel.innerHTML = '<option>Could not load sites — refresh and try again</option>'; });
}

function populateOrgTimezoneSelect() {
  var sel = document.getElementById('ce-timezone');
  if (!sel || sel.options.length) return; // already populated
  var detected = (Intl.DateTimeFormat().resolvedOptions().timeZone) || 'America/New_York';
  var hasDetected = TZ_OPTIONS.some(function(o) { return o.v === detected; });
  sel.innerHTML = TZ_OPTIONS.map(function(o) {
    return '<option value="' + o.v + '"' + (o.v === detected ? ' selected' : '') + '>' + o.l + '</option>';
  }).join('');
  if (!hasDetected) sel.value = 'America/New_York';
}

function updateRecurrenceVisibility() {
  var rec = document.getElementById('ce-recurrence').value;
  var group = document.getElementById('ce-recurrence-until-group');
  if (group) group.style.display = rec === 'none' ? 'none' : '';
}

var MAX_RECURRENCE_INSTANCES = 12;

/** Returns a list of day-offsets (starting with 0) for each occurrence of
 * a recurring event, capped so a typo in the end date can't generate an
 * unbounded number of rows. */
function computeRecurrenceOffsets(recurrence, untilStr, startStr) {
  if (recurrence === 'none' || !untilStr) return [0];
  var stepDays = recurrence === 'weekly' ? 7 : 1;
  var s = parseLocalDT(startStr);
  var startDate = new Date(s.y, s.mo - 1, s.da);
  var untilParts = untilStr.split('-');
  var untilDate = new Date(+untilParts[0], +untilParts[1] - 1, +untilParts[2]);

  var offsets = [0];
  for (var n = 1; n < MAX_RECURRENCE_INSTANCES; n++) {
    var candidate = new Date(startDate);
    candidate.setDate(candidate.getDate() + stepDays * n);
    if (candidate > untilDate) break;
    offsets.push(stepDays * n);
  }
  return offsets;
}

function resetCreateEventForm() {
  ['ce-title', 'ce-start', 'ce-end'].forEach(function(id) {
    var el = document.getElementById(id);
    if (el) el.value = '';
  });
  var recEl = document.getElementById('ce-recurrence');
  if (recEl) recEl.value = 'none';
  var untilEl = document.getElementById('ce-recurrence-until');
  if (untilEl) untilEl.value = '';
  updateRecurrenceVisibility();
  var errEl = document.getElementById('ce-error');
  if (errEl) errEl.classList.remove('show');
  resetEventDraft();
}

function submitCreateEvent() {
  var title       = document.getElementById('ce-title').value.trim();
  var locationSel = document.getElementById('ce-location');
  var locationOpt = locationSel.options[locationSel.selectedIndex];
  var start       = document.getElementById('ce-start').value;
  var end         = document.getElementById('ce-end').value;
  var timezone    = document.getElementById('ce-timezone').value || 'America/New_York';
  var recurrence  = document.getElementById('ce-recurrence').value;
  var recurUntil  = document.getElementById('ce-recurrence-until').value;
  var errEl       = document.getElementById('ce-error');

  if (!title || !start || !end || !locationOpt || !locationOpt.value) {
    errEl.textContent = 'Please fill in the event name, location, and start/end time.';
    errEl.classList.add('show');
    return;
  }
  if (end <= start) {
    errEl.textContent = 'End time must be after the start time.';
    errEl.classList.add('show');
    return;
  }
  if (draftTasks.length === 0 || draftTasks.some(function(t) { return !t.title.trim(); })) {
    errEl.textContent = 'Every task needs a name.';
    errEl.classList.add('show');
    return;
  }
  for (var i = 0; i < draftTasks.length; i++) {
    var t = draftTasks[i];
    if (t.mode === 'single' && !parseInt(t.singleCapacity, 10)) {
      errEl.textContent = '"' + t.title + '" needs a max volunteers number.';
      errEl.classList.add('show');
      return;
    }
    if (t.mode === 'multi' && t.shifts.length === 0) {
      errEl.textContent = '"' + t.title + '" needs at least one shift.';
      errEl.classList.add('show');
      return;
    }
  }
  errEl.classList.remove('show');

  var locationParts = locationOpt.textContent.split('·');
  var siteId   = locationOpt.value;
  var siteName = (locationParts[0] || '').trim();
  var county   = (locationParts[1] || '').trim();

  var offsets = computeRecurrenceOffsets(recurrence, recurUntil, start);
  var stamp = Date.now();
  var lastEventId = null;

  offsets.forEach(function(offsetDays, occurrenceIdx) {
    var evStart = shiftDateTimeStr(start, offsetDays);
    var evEnd   = shiftDateTimeStr(end, offsetDays);
    var eventId = 'event-' + stamp + '-' + occurrenceIdx;

    DEMO_EVENTS.unshift({ id: eventId, title: title, siteId: siteId, siteName: siteName, county: county, startTime: evStart, endTime: evEnd, timezone: timezone, status: 'PENDING' });
    lastEventId = eventId;

    draftTasks.forEach(function(t, taskIdx) {
      var taskId = eventId + '-task-' + taskIdx;
      DEMO_TASKS.unshift({ id: taskId, eventId: eventId, title: t.title, description: t.description, category: t.category });

      if (t.mode === 'single') {
        var singleCapacity = parseInt(t.singleCapacity, 10);
        DEMO_SHIFTS.unshift({
          id: taskId + '-shift-0', taskId: taskId, siteId: null, siteName: null, county: null,
          startTime: evStart, endTime: evEnd, timezone: timezone, capacity: singleCapacity,
          minStaffing: parseInt(t.singleMinStaffing, 10) || 1, status: 'PENDING'
        });
      } else {
        t.shifts.forEach(function(s, shiftIdx) {
          DEMO_SHIFTS.unshift({
            id: taskId + '-shift-' + shiftIdx, taskId: taskId, siteId: null, siteName: null, county: null,
            startTime: shiftDateTimeStr(s.start, offsetDays), endTime: shiftDateTimeStr(s.end, offsetDays),
            timezone: timezone, capacity: parseInt(s.capacity, 10) || 1,
            minStaffing: parseInt(s.minStaffing, 10) || 1, status: 'PENDING'
          });
        });
      }
    });
  });

  resetCreateEventForm();
  showToast(offsets.length > 1
    ? 'Published ' + offsets.length + ' events — volunteers can now sign up.'
    : 'Event published — volunteers can now sign up.');

  currentOrgShiftId = null; // let renderOrgShifts pick the first shift again
  navTo('org-shifts');
  renderOrgShifts();
}

// ─── EDIT MODAL (shift / task / event) ─────────────────────────────────
// One modal, three field sets, swapped by editModalContext.type. Saving
// writes straight back into the matching DEMO_ array and re-renders every
// view that could show the change (shift list, both calendars).

var editModalContext = null; // { type: 'shift'|'task'|'event', id: string }

function closeEditModal() {
  document.getElementById('edit-panel').classList.remove('open');
  editModalContext = null;
}

function populateEditLocationSelect(currentSiteId, includeDefaultOption) {
  var sel = document.getElementById('em-location');
  if (!sel) return;
  sel.innerHTML = '<option>Loading sites…</option>';
  apiGetLocations()
    .then(function(sites) {
      var options = sites.map(function(s) {
        return '<option value="' + s.dbId + '"' + (String(s.dbId) === String(currentSiteId) ? ' selected' : '') + '>' + s.site + ' · ' + s.county + ', ' + s.state + '</option>';
      }).join('');
      if (includeDefaultOption) options = '<option value="">Use event default</option>' + options;
      sel.innerHTML = options;
      if (includeDefaultOption && !currentSiteId) sel.value = '';
    })
    .catch(function() { sel.innerHTML = includeDefaultOption ? '<option value="">Use event default</option>' : '<option>Could not load sites</option>'; });
}

function openEditShift(shiftId) {
  var shift = DEMO_SHIFTS.find(function(s) { return s.id === shiftId; });
  if (!shift) return;
  editModalContext = { type: 'shift', id: shiftId };
  document.getElementById('edit-modal-title').textContent = 'Edit shift';

  var loc = shiftLocation(shift);

  document.getElementById('edit-modal-body').innerHTML =
    '<div class="two-fields form-group">' +
      '<div><div class="form-label">Start</div><input class="form-input" id="em-start" type="datetime-local" value="' + shift.startTime + '"></div>' +
      '<div><div class="form-label">End</div><input class="form-input" id="em-end" type="datetime-local" value="' + shift.endTime + '"></div>' +
    '</div>' +
    '<div class="form-group"><div class="form-label">Timezone</div><select class="form-select" id="em-timezone">' +
      TZ_OPTIONS.map(function(o) { return '<option value="' + o.v + '"' + (o.v === shift.timezone ? ' selected' : '') + '>' + o.l + '</option>'; }).join('') +
    '</select></div>' +
    '<div class="two-fields form-group">' +
      '<div><div class="form-label">Min to run</div><input class="form-input" id="em-min-staffing" type="number" min="1" step="1" value="' + (shift.minStaffing || 1) + '"></div>' +
      '<div><div class="form-label">Max volunteers</div><input class="form-input" id="em-capacity" type="number" min="1" step="1" value="' + shift.capacity + '"></div>' +
    '</div>' +
    '<div class="form-group">' +
      '<div class="form-label">Location</div>' +
      '<select class="form-select" id="em-location"><option>Loading sites…</option></select>' +
      '<div style="font-size:11px;color:var(--text-3);margin-top:5px;">Defaults to the event\'s location (' + loc.siteName + ') unless you pick a different site here.</div>' +
    '</div>';

  populateEditLocationSelect(shift.siteId, true);
  document.getElementById('edit-panel').classList.add('open');
}

function openEditTask(taskId) {
  var task = getTask(taskId);
  if (!task) return;
  editModalContext = { type: 'task', id: taskId };
  document.getElementById('edit-modal-title').textContent = 'Edit task';

  var categoryOptions = Object.keys(CATEGORY_META).map(function(k) {
    return '<option value="' + k + '"' + (task.category === k ? ' selected' : '') + '>' + CATEGORY_META[k].label + '</option>';
  }).join('');

  document.getElementById('edit-modal-body').innerHTML =
    '<div class="form-group"><div class="form-label">Task name</div><input class="form-input" id="em-title" value="' + escHtml(task.title) + '"></div>' +
    '<div class="form-group"><div class="form-label">Description</div><textarea class="form-textarea" id="em-description">' + escHtml(task.description) + '</textarea></div>' +
    '<div class="form-group"><div class="form-label">Category</div><select class="form-select" id="em-category">' + categoryOptions + '</select></div>';

  document.getElementById('edit-panel').classList.add('open');
}

function openEditEvent(eventId) {
  var ev = getEvent(eventId);
  if (!ev) return;
  editModalContext = { type: 'event', id: eventId };
  document.getElementById('edit-modal-title').textContent = 'Edit event';

  var canceled = ev.status === 'CANCELED';

  document.getElementById('edit-modal-body').innerHTML =
    (canceled ? '<div style="background:var(--urgent-lt);color:var(--urgent);border-radius:var(--radius-md);padding:10px 14px;font-size:12px;font-weight:600;margin-bottom:4px;">This event is canceled.</div>' : '') +
    '<div class="form-group"><div class="form-label">Event name</div><input class="form-input" id="em-title" value="' + escHtml(ev.title) + '"></div>' +
    '<div class="two-fields form-group">' +
      '<div><div class="form-label">Default location</div><select class="form-select" id="em-location"><option>Loading sites…</option></select></div>' +
      '<div><div class="form-label">Timezone</div><select class="form-select" id="em-timezone">' +
        TZ_OPTIONS.map(function(o) { return '<option value="' + o.v + '"' + (o.v === ev.timezone ? ' selected' : '') + '>' + o.l + '</option>'; }).join('') +
      '</select></div>' +
    '</div>' +
    '<div class="two-fields form-group">' +
      '<div><div class="form-label">Start</div><input class="form-input" id="em-start" type="datetime-local" value="' + ev.startTime + '"></div>' +
      '<div><div class="form-label">End</div><input class="form-input" id="em-end" type="datetime-local" value="' + ev.endTime + '"></div>' +
    '</div>' +
    '<div style="font-size:11px;color:var(--text-3);margin-bottom:14px;">Editing an already-published event only changes the event\'s own record — tasks and shifts under it keep whatever times they were given.</div>' +
    (canceled
      ? '<button type="button" class="btn btn-secondary" onclick="uncancelEvent(\'' + ev.id + '\')">Un-cancel event</button>'
      : '<button type="button" class="btn btn-secondary" style="color:var(--urgent);border-color:#FFBBBB;" onclick="cancelEvent(\'' + ev.id + '\')">Cancel event</button>');

  populateEditLocationSelect(ev.siteId, false);
  document.getElementById('edit-panel').classList.add('open');
}

// TODO: canceling an event should notify every volunteer signed up for any
// of its shifts (SMS/email) once a real notification backend exists — for
// now this only updates what the org and volunteer UIs show.

function cancelEvent(eventId) {
  var ev = getEvent(eventId);
  if (!ev) return;
  ev.status = 'CANCELED';
  DEMO_TASKS.filter(function(t) { return t.eventId === eventId; }).forEach(function(t) {
    DEMO_SHIFTS.filter(function(s) { return s.taskId === t.id; }).forEach(function(s) { s.status = 'CANCELED'; });
  });
  showToast('Event canceled.');
  closeEditModal();
  renderOrgShifts();
  renderOrgCalendar();
  renderVolCalendar();
}

function uncancelEvent(eventId) {
  var ev = getEvent(eventId);
  if (!ev) return;
  ev.status = 'PENDING';
  DEMO_TASKS.filter(function(t) { return t.eventId === eventId; }).forEach(function(t) {
    DEMO_SHIFTS.filter(function(s) { return s.taskId === t.id; }).forEach(function(s) { s.status = 'PENDING'; });
  });
  showToast('Event un-canceled — shifts are open for signups again.');
  closeEditModal();
  renderOrgShifts();
  renderOrgCalendar();
  renderVolCalendar();
}

function saveEditModal() {
  if (!editModalContext) return;

  if (editModalContext.type === 'shift') {
    var shift = DEMO_SHIFTS.find(function(s) { return s.id === editModalContext.id; });
    if (!shift) return;
    var start = document.getElementById('em-start').value;
    var end   = document.getElementById('em-end').value;
    if (!start || !end || end <= start) { showToast('End time must be after the start time.'); return; }
    shift.startTime = start;
    shift.endTime   = end;
    shift.timezone  = document.getElementById('em-timezone').value;
    shift.capacity    = parseInt(document.getElementById('em-capacity').value, 10) || shift.capacity;
    shift.minStaffing = parseInt(document.getElementById('em-min-staffing').value, 10) || shift.minStaffing;

    var locSel = document.getElementById('em-location');
    var locOpt = locSel.options[locSel.selectedIndex];
    if (locOpt && locOpt.value) {
      var parts = locOpt.textContent.split('·');
      shift.siteId   = locOpt.value;
      shift.siteName = (parts[0] || '').trim();
      shift.county   = (parts[1] || '').trim();
    } else {
      shift.siteId = null; shift.siteName = null; shift.county = null;
    }

  } else if (editModalContext.type === 'task') {
    var task = getTask(editModalContext.id);
    if (!task) return;
    var title = document.getElementById('em-title').value.trim();
    if (!title) { showToast('Task needs a name.'); return; }
    task.title       = title;
    task.description = document.getElementById('em-description').value.trim();
    task.category    = document.getElementById('em-category').value;

  } else if (editModalContext.type === 'event') {
    var ev = getEvent(editModalContext.id);
    if (!ev) return;
    var evTitle = document.getElementById('em-title').value.trim();
    var evStart = document.getElementById('em-start').value;
    var evEnd   = document.getElementById('em-end').value;
    if (!evTitle || !evStart || !evEnd || evEnd <= evStart) { showToast('Please check the event name and times.'); return; }
    ev.title    = evTitle;
    ev.startTime = evStart;
    ev.endTime   = evEnd;
    ev.timezone  = document.getElementById('em-timezone').value;

    var evLocSel = document.getElementById('em-location');
    var evLocOpt = evLocSel.options[evLocSel.selectedIndex];
    if (evLocOpt && evLocOpt.value) {
      var evParts = evLocOpt.textContent.split('·');
      ev.siteId   = evLocOpt.value;
      ev.siteName = (evParts[0] || '').trim();
      ev.county   = (evParts[1] || '').trim();
    }
  }

  showToast('Changes saved.');
  closeEditModal();
  renderOrgShifts();
  renderOrgCalendar();
  renderVolCalendar();
}

// ─── BOOT ───────────────────────────────────────────────────────────────

renderOrgShifts();
