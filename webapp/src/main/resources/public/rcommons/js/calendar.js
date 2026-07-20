/**
 * calendar.js — Month-view calendars for both the org and volunteer sides.
 *
 * Reads the same DEMO_SHIFTS / DEMO_SIGNUPS as org.js — one shift
 * list, two views, same as the rest of R-Commons. Availability (day/hour
 * keys, grid rendering, presets) is shared with onboarding and the Profile
 * page via availability.js. Availability is one weekly pattern, projected
 * onto each date by day-of-week — there's no per-date override yet.
 */

var MONTH_NAMES = ['January','February','March','April','May','June','July','August','September','October','November','December'];
var DOW_FULL = ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];

// Matches the default pattern the Profile page's avail-grid used to show
// hardcoded, now expressed in hours: weekend mornings + weekday evenings.
var DEFAULT_AVAILABILITY = {};
['sun', 'sat'].forEach(function(d) { [6, 7, 8, 9].forEach(function(h) { DEFAULT_AVAILABILITY[availKey(d, h)] = true; }); });
['mon', 'tue', 'thu', 'fri'].forEach(function(d) { [18, 19, 20].forEach(function(h) { DEFAULT_AVAILABILITY[availKey(d, h)] = true; }); });

// ─── DEMO VOLUNTEER ROSTER (org-side "who's available" aggregate) ────────
// In production this is every volunteer's saved availability, not a fixed
// list — this roster exists only so the org calendar has more than one
// person's schedule to aggregate against.

function rosterAvail(entries) {
  // entries: [[day, [hours...]], ...] — shorthand so the roster below stays readable.
  var avail = {};
  entries.forEach(function(e) { e[1].forEach(function(h) { avail[availKey(e[0], h)] = true; }); });
  return avail;
}

var DEMO_VOLUNTEER_ROSTER = [
  { name: 'Maria Gonzalez', phone: '(828) 555-0143', availability: rosterAvail([['sat', [8, 9, 10, 11]], ['sun', [8, 9, 10, 11]], ['wed', [18, 19, 20]]]) },
  { name: 'Devon Price', phone: '(704) 555-0177', availability: rosterAvail([['sat', [8, 9, 10, 11, 12, 13]], ['mon', [18, 19]], ['tue', [18, 19]]]) },
  { name: 'Aaliyah Ferris', phone: '(828) 555-0190', availability: rosterAvail([['sun', [13, 14, 15]], ['thu', [18, 19, 20]], ['fri', [18, 19, 20]]]) },
  { name: 'Sam Whitaker', phone: '(828) 555-0165', availability: rosterAvail([['sat', [8, 9, 10, 11]], ['sun', [8, 9, 10, 11, 13, 14]], ['fri', [18, 19, 20]]]) },
  { name: 'Priya Natarajan', phone: '(828) 555-0136', availability: rosterAvail([['mon', [18, 19]], ['wed', [18, 19]], ['sat', [13, 14, 15]]]) }
];

function getMyProfile() {
  var raw = localStorage.getItem('rcommons_demo_profile');
  if (raw) { try { return JSON.parse(raw); } catch (e) {} }
  return null;
}

function getMyName() {
  var p = getMyProfile();
  return (p && p.full_name) || 'Jordan Wilson';
}

function getMyAvailability() {
  var p = getMyProfile();
  return (p && p.availability && Object.keys(p.availability).length) ? p.availability : DEFAULT_AVAILABILITY;
}

// ─── SHARED MONTH GRID HELPERS ────────────────────────────────────────────

function ymd(date) {
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0');
}

function shiftsOnDate(dateStr) {
  return DEMO_SHIFTS.filter(function(s) { return s.startTime.slice(0, 10) === dateStr; });
}

/** Full 6-week grid (42 days) for the given year/month, including the
 * leading/trailing days from adjacent months needed to fill it. */
function buildMonthCells(year, month) {
  var first = new Date(year, month, 1);
  var gridStart = new Date(year, month, 1 - first.getDay());
  var cells = [];
  for (var i = 0; i < 42; i++) {
    var d = new Date(gridStart);
    d.setDate(gridStart.getDate() + i);
    cells.push(d);
  }
  return cells;
}

function renderDowRow() {
  return ['Sun','Mon','Tue','Wed','Thu','Fri','Sat'].map(function(d) {
    return '<div class="cal-dow">' + d + '</div>';
  }).join('');
}

// ─── ORG CALENDAR ──────────────────────────────────────────────────────────

var orgCalMonthOffset = 0;
var orgCalSelectedDate = null;

function shiftOrgCalMonth(delta) { orgCalMonthOffset += delta; renderOrgCalendar(); }
function orgCalToday() { orgCalMonthOffset = 0; orgCalSelectedDate = null; renderOrgCalendar(); }

function renderOrgCalendar() {
  var grid = document.getElementById('org-cal-grid');
  if (!grid) return;

  var base  = new Date();
  var view  = new Date(base.getFullYear(), base.getMonth() + orgCalMonthOffset, 1);
  var year  = view.getFullYear(), month = view.getMonth();
  var todayStr = ymd(new Date());

  document.getElementById('org-cal-title').textContent = MONTH_NAMES[month] + ' ' + year;

  var cellsHtml = buildMonthCells(year, month).map(function(d) {
    var dateStr = ymd(d);
    var inMonth = d.getMonth() === month;
    var dayKey  = AVAIL_DAYS[d.getDay()];
    var shifts  = shiftsOnDate(dateStr);
    var availableCount = DEMO_VOLUNTEER_ROSTER.filter(function(v) {
      return availableHoursForDay(v.availability, dayKey).length > 0;
    }).length;

    var shiftChips = shifts.slice(0, 2).map(function(s) {
      if (s.status === 'CANCELED') return '<span class="tag tag-dist" style="font-size:9.5px;padding:1px 6px;">Canceled</span>';
      var fill = shiftFillInfo(s);
      var cls  = fill.status === 'understaffed' ? 'tag-urgent' : 'tag-done';
      return '<span class="tag ' + cls + '" style="font-size:9.5px;padding:1px 6px;">' + fill.confirmed + '/' + s.capacity + '</span>';
    }).join('');
    var moreTag = shifts.length > 2
      ? '<span class="tag tag-dist" style="font-size:9.5px;padding:1px 6px;">+' + (shifts.length - 2) + '</span>' : '';

    var classes = 'cal-day' + (inMonth ? '' : ' other-month') + (dateStr === todayStr ? ' today' : '') + (dateStr === orgCalSelectedDate ? ' selected' : '');

    return '<div class="' + classes + '" onclick="selectOrgCalDay(\'' + dateStr + '\')">' +
      '<div class="cal-day-num">' + d.getDate() + '</div>' +
      '<div style="display:flex;flex-wrap:wrap;gap:3px;">' + shiftChips + moreTag + '</div>' +
      (availableCount > 0 ? '<div class="cal-avail-count">' + availableCount + ' available</div>' : '') +
    '</div>';
  }).join('');

  grid.innerHTML = renderDowRow() + cellsHtml;

  if (!orgCalSelectedDate) orgCalSelectedDate = todayStr;
  renderOrgCalDetail(orgCalSelectedDate);
}

function selectOrgCalDay(dateStr) {
  orgCalSelectedDate = dateStr;
  renderOrgCalendar();
}

function renderOrgCalDetail(dateStr) {
  var el = document.getElementById('org-cal-detail');
  if (!el) return;

  var d = new Date(dateStr + 'T00:00:00');
  var dayKey = AVAIL_DAYS[d.getDay()];
  var label  = DOW_FULL[d.getDay()] + ', ' + MONTH_NAMES[d.getMonth()] + ' ' + d.getDate();

  var shifts = shiftsOnDate(dateStr);
  var shiftsHtml = shifts.length === 0
    ? '<div style="padding:8px 0 4px;font-size:12px;color:var(--text-3);">No shifts scheduled this day.</div>'
    : shifts.map(function(s) {
        var fill = shiftFillInfo(s);
        var cat  = CATEGORY_META[shiftCategoryKey(s)] || CATEGORY_META.unassigned;
        var loc  = shiftLocation(s);
        return '<div class="opp-item" onclick="goToShiftFromCalendar(\'' + s.id + '\')">' +
          '<div class="opp-org-mark mark-amber">' + CAL_ICON + '</div>' +
          '<div class="opp-info">' +
            '<div class="opp-title">' + shiftTitle(s) + '</div>' +
            '<div class="opp-org">' + loc.siteName + ' · ' + formatShiftTime(s) + '</div>' +
            '<div class="opp-meta"><span class="tag ' + cat.tagClass + '">' + cat.label + '</span><span class="tag tag-dist">' + fill.confirmed + ' of ' + s.capacity + ' filled</span></div>' +
          '</div>' +
        '</div>';
      }).join('');

  var available = DEMO_VOLUNTEER_ROSTER
    .map(function(v) { return { v: v, slots: availableHoursForDay(v.availability, dayKey) }; })
    .filter(function(e) { return e.slots.length > 0; });

  var availHtml = available.length === 0
    ? '<div style="padding:8px 0 4px;font-size:12px;color:var(--text-3);">No one has marked themselves available this day.</div>'
    : available.map(function(e) {
        var slotLabels = summarizeAvailHours(e.slots);
        return '<div class="hours-item">' +
          '<div class="hours-dot logged"></div>' +
          '<div class="hours-info"><div class="hours-title">' + e.v.name + '</div><div class="hours-org">' + slotLabels + '</div></div>' +
          '<div class="hours-val" style="font-size:12px;font-weight:600;">' + e.v.phone + '</div>' +
        '</div>';
      }).join('');

  el.innerHTML =
    '<div class="card-header"><div class="card-title">' + label + '</div></div>' +
    '<div class="card-body">' +
      '<div style="font-size:11px;font-weight:700;letter-spacing:.4px;text-transform:uppercase;color:var(--text-3);margin-bottom:2px;">Shifts</div>' +
      shiftsHtml +
      '<div style="font-size:11px;font-weight:700;letter-spacing:.4px;text-transform:uppercase;color:var(--text-3);margin:16px 0 2px;">Available to reach out to</div>' +
      availHtml +
    '</div>';
}

function goToShiftFromCalendar(shiftId) {
  currentOrgShiftId = shiftId;
  navTo('org-shifts');
  renderOrgShifts();
}

// ─── VOLUNTEER CALENDAR ────────────────────────────────────────────────────

var volCalViewMode = 'month'; // 'month' | 'week'
var volCalOffset = 0;         // months in month mode, weeks in week mode
var volCalSelectedDate = null;

function setVolCalViewMode(mode) {
  if (volCalViewMode === mode) return;
  volCalViewMode = mode;
  volCalOffset = 0;
  document.querySelectorAll('#vol-cal-view-toggle .tab').forEach(function(t) {
    t.classList.toggle('active', t.dataset.calView === mode);
  });
  renderVolCalendar();
}

function shiftVolCal(delta) { volCalOffset += delta; renderVolCalendar(); }
function volCalToday() { volCalOffset = 0; volCalSelectedDate = null; renderVolCalendar(); }

function startOfWeek(date) {
  var d = new Date(date);
  d.setDate(d.getDate() - d.getDay());
  return d;
}

function mySignupsOnDate(dateStr) {
  var myName = getMyName();
  return DEMO_SIGNUPS
    .filter(function(s) { return s.volunteerName === myName; })
    .map(function(s) { return { signup: s, shift: DEMO_SHIFTS.find(function(x) { return x.id === s.shiftId; }) }; })
    .filter(function(e) { return e.shift && e.shift.startTime.slice(0, 10) === dateStr; });
}

function openShiftsOnDate(dateStr) {
  var myName = getMyName();
  return shiftsOnDate(dateStr).filter(function(s) {
    var fill = shiftFillInfo(s);
    var alreadyIn = DEMO_SIGNUPS.some(function(su) {
      return su.shiftId === s.id && su.volunteerName === myName && su.status !== 'CANCELED';
    });
    return s.status !== 'CANCELED' && fill.remaining > 0 && !alreadyIn;
  });
}

function renderVolCalendar() {
  var grid = document.getElementById('vol-cal-grid');
  if (!grid) return;

  var todayStr = ymd(new Date());
  var myAvail  = getMyAvailability();
  var cells, targetMonth, titleText;

  if (volCalViewMode === 'week') {
    var weekStart = startOfWeek(new Date());
    weekStart.setDate(weekStart.getDate() + volCalOffset * 7);
    cells = [];
    for (var i = 0; i < 7; i++) { var d = new Date(weekStart); d.setDate(weekStart.getDate() + i); cells.push(d); }
    var last = cells[6];
    titleText = MONTH_NAMES[cells[0].getMonth()].slice(0, 3) + ' ' + cells[0].getDate() + '–' +
      (last.getMonth() !== cells[0].getMonth() ? MONTH_NAMES[last.getMonth()].slice(0, 3) + ' ' : '') + last.getDate() + ', ' + last.getFullYear();
  } else {
    var view = new Date();
    view.setDate(1); // avoid month-length overflow when adding months (e.g. Jan 31 + 1mo)
    view.setMonth(view.getMonth() + volCalOffset);
    targetMonth = view.getMonth();
    cells = buildMonthCells(view.getFullYear(), targetMonth);
    titleText = MONTH_NAMES[targetMonth] + ' ' + view.getFullYear();
  }

  document.getElementById('vol-cal-title').textContent = titleText;

  var cellsHtml = cells.map(function(d) {
    var dateStr = ymd(d);
    var inMonth = volCalViewMode === 'week' ? true : (d.getMonth() === targetMonth);
    var dayKey  = AVAIL_DAYS[d.getDay()];
    var mySignups = mySignupsOnDate(dateStr);
    var openCount = openShiftsOnDate(dateStr).length;
    var iAmAvailable = availableHoursForDay(myAvail, dayKey).length > 0;

    var badges = '';
    if (mySignups.length) {
      var confirmedCount = mySignups.filter(function(e) { return e.signup.status === 'CONFIRMED'; }).length;
      var cls = confirmedCount === mySignups.length ? 'tag-done' : 'tag-dist';
      badges += '<span class="tag ' + cls + '" style="font-size:9.5px;padding:1px 6px;">' + mySignups.length + ' signed up</span>';
    }
    if (openCount > 0) badges += '<span class="tag tag-need" style="font-size:9.5px;padding:1px 6px;">' + openCount + ' open</span>';

    var classes = 'cal-day' + (inMonth ? '' : ' other-month') + (dateStr === todayStr ? ' today' : '') +
      (dateStr === volCalSelectedDate ? ' selected' : '') + (iAmAvailable ? ' avail-marked' : '');

    return '<div class="' + classes + '" onclick="selectVolCalDay(\'' + dateStr + '\')">' +
      '<div class="cal-day-num">' + d.getDate() + '</div>' +
      '<div style="display:flex;flex-wrap:wrap;gap:3px;">' + badges + '</div>' +
    '</div>';
  }).join('');

  grid.innerHTML = renderDowRow() + cellsHtml;

  if (!volCalSelectedDate) volCalSelectedDate = todayStr;
  renderVolCalDetail(volCalSelectedDate);
}

function selectVolCalDay(dateStr) {
  volCalSelectedDate = dateStr;
  renderVolCalendar();
}

function renderVolCalDetail(dateStr) {
  var el = document.getElementById('vol-cal-detail');
  if (!el) return;

  var d = new Date(dateStr + 'T00:00:00');
  var dayKey = AVAIL_DAYS[d.getDay()];
  var label  = DOW_FULL[d.getDay()] + ', ' + MONTH_NAMES[d.getMonth()] + ' ' + d.getDate();
  var mySlots = availableHoursForDay(getMyAvailability(), dayKey);

  var mySignups = mySignupsOnDate(dateStr);
  var signupsHtml = mySignups.length === 0
    ? '<div style="padding:8px 0 4px;font-size:12px;color:var(--text-3);">Nothing you\'ve signed up for this day.</div>'
    : mySignups.map(function(e) {
        var canceled = e.shift.status === 'CANCELED';
        var confirmed = e.signup.status === 'CONFIRMED';
        var loc = shiftLocation(e.shift);
        var statusTag = canceled ? '<span class="tag tag-urgent">Event canceled</span>' : '<span class="tag ' + (confirmed ? 'tag-done' : 'tag-dist') + '">' + (confirmed ? 'Confirmed' : 'Pending') + '</span>';
        return '<div class="hours-item">' +
          '<div class="hours-dot ' + (canceled ? 'logged' : (confirmed ? 'confirmed' : 'pending')) + '"></div>' +
          '<div class="hours-info"><div class="hours-title">' + shiftTitle(e.shift) + '</div><div class="hours-org">' + loc.siteName + ' · ' + formatShiftTime(e.shift) + '</div></div>' +
          statusTag +
        '</div>';
      }).join('');

  var open = openShiftsOnDate(dateStr);
  var openHtml = open.length === 0
    ? '<div style="padding:8px 0 4px;font-size:12px;color:var(--text-3);">No open shifts this day.</div>'
    : open.map(function(s) {
        var fill = shiftFillInfo(s);
        var cat  = CATEGORY_META[shiftCategoryKey(s)] || CATEGORY_META.unassigned;
        var loc  = shiftLocation(s);
        return '<div class="opp-item">' +
          '<div class="opp-org-mark mark-amber">' + CAL_ICON + '</div>' +
          '<div class="opp-info">' +
            '<div class="opp-title">' + shiftTitle(s) + '</div>' +
            '<div class="opp-org">' + loc.siteName + ' · ' + formatShiftTime(s) + '</div>' +
            '<div class="opp-meta"><span class="tag ' + cat.tagClass + '">' + cat.label + '</span><span class="tag tag-dist">' + fill.remaining + ' spots open</span></div>' +
          '</div>' +
          '<div class="opp-actions"><button class="btn btn-primary btn-sm" onclick="event.stopPropagation();signUpFromCalendar(\'' + s.id + '\')">Sign up</button></div>' +
        '</div>';
      }).join('');

  el.innerHTML =
    '<div class="card-header"><div class="card-title">' + label + '</div></div>' +
    '<div class="card-body">' +
      '<div style="font-size:12px;color:var(--text-3);margin-bottom:14px;">' +
        (mySlots.length
          ? 'You\'re marked available: ' + summarizeAvailHours(mySlots)
          : 'You haven\'t marked yourself available this day.') +
        ' · <a onclick="navTo(\'profile\')" style="color:var(--accent);cursor:pointer;font-weight:600;">Edit availability</a>' +
      '</div>' +
      '<div style="font-size:11px;font-weight:700;letter-spacing:.4px;text-transform:uppercase;color:var(--text-3);margin-bottom:2px;">Signed up</div>' +
      signupsHtml +
      '<div style="font-size:11px;font-weight:700;letter-spacing:.4px;text-transform:uppercase;color:var(--text-3);margin:16px 0 2px;">Open shifts you could take</div>' +
      openHtml +
    '</div>';
}

function signUpFromCalendar(shiftId) {
  var myName = getMyName();
  var existing = DEMO_SIGNUPS.find(function(s) { return s.shiftId === shiftId && s.volunteerName === myName; });
  if (existing) { existing.status = 'CONFIRMED'; }
  else { DEMO_SIGNUPS.push({ id: Date.now(), shiftId: shiftId, volunteerName: myName, notes: '', status: 'CONFIRMED' }); }

  showToast('+75 XP — you\'re signed up! Hours confirmed after the shift.');
  awardXP(75);
  renderVolCalendar();
}

// ─── BOOT ───────────────────────────────────────────────────────────────

renderOrgCalendar();
renderVolCalendar();
