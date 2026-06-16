/**
 * modals.js — Modal open/close/confirm logic
 *
 * Handles:
 *   - Commit modal (supply needs → POST /volunteer/delivery)
 *   - Shift sign-up modal
 *   - RSVP modal
 *   - Log hours modal
 */

// ─── COMMIT MODAL STATE ────────────────────────────────────────────────────

var currentNeedIdx  = null;
var currentSiteDbId = null;   // internal site.id for API calls
var currentSiteName = null;

// ─── COMMIT MODAL — OPEN ──────────────────────────────────────────────────

/** Opens the commit modal for a specific WSS_URGENT_ITEMS entry by index. */
function openNeedCommit(idx) {
  var DEMO_NEEDS = buildDemoNeeds();
  currentNeedIdx  = idx;
  var n           = DEMO_NEEDS[idx];
  if (!n) { openCommit(); return; }

  var urgentEntry = WSS_URGENT_ITEMS[idx];
  currentSiteDbId = urgentEntry ? urgentEntry.dbId : null;
  currentSiteName = n.org;

  document.getElementById('modal-need-title').textContent = n.title;
  document.getElementById('modal-need-org').textContent   = n.org + ' · ' + n.county;
  document.getElementById('commit-note').value            = '';
  buildDateOptions();

  // Pre-populate volunteer info from saved profile
  prefillVolunteerFields();

  // Show placeholder items immediately, then replace with live items if we have a dbId
  var fallbackItems = (n.items && n.items.length)
    ? n.items.map(function(name) { return { id: null, name: name }; })
    : [{ id: null, name: 'Whatever I can bring' }];
  renderCommitItems(fallbackItems);

  document.getElementById('commit-panel').classList.add('open');

  if (currentSiteDbId) {
    apiGetSiteItems(currentSiteDbId)
      .then(function(data) {
        var items = data.site && data.site.items ? data.site.items : [];
        if (items.length > 0) renderCommitItems(items);
      })
      .catch(function() { /* keep fallback items */ });
  }
}

/** Opens the commit modal triggered from the WSS map (by site name). */
function openWSSCommit(siteName) {
  var urgentEntry = WSS_URGENT_ITEMS.find(function(w) { return w.site === siteName; });
  if (urgentEntry) {
    var allNeeds = buildDemoNeeds();
    var idx = allNeeds.findIndex(function(n) { return n.org === siteName; });
    if (idx >= 0) { openNeedCommit(idx); return; }
  }

  // Site is on the map but not in the urgent list — generic commit
  var siteEntry = WSS_SITES.find(function(s) { return s.site === siteName; });
  currentNeedIdx  = null;
  currentSiteDbId = siteEntry ? siteEntry.dbId : null;
  currentSiteName = siteName;

  document.getElementById('modal-need-title').textContent = 'Donation to ' + siteName;
  document.getElementById('modal-need-org').textContent   = siteName + ' · WSS supply site';
  document.getElementById('commit-note').value            = '';
  buildDateOptions();
  prefillVolunteerFields();

  if (currentSiteDbId) {
    // Start with a generic row, replace once items load
    renderCommitItems([{ id: null, name: 'Whatever I can bring' }]);
    document.getElementById('commit-panel').classList.add('open');

    apiGetSiteItems(currentSiteDbId)
      .then(function(data) {
        var items = data.site && data.site.items ? data.site.items : [];
        if (items.length > 0) renderCommitItems(items);
      })
      .catch(function() { /* keep generic row */ });
  } else {
    renderCommitItems([{ id: null, name: 'Whatever I can bring' }]);
    document.getElementById('commit-panel').classList.add('open');
  }
}

/** Opens the generic "log a commitment" modal (no specific site pre-selected). */
function openCommit() {
  currentNeedIdx  = null;
  currentSiteDbId = null;
  currentSiteName = null;
  document.getElementById('modal-need-title').textContent = 'Make a commitment';
  document.getElementById('modal-need-org').textContent   = 'Tell us what you can bring';
  renderCommitItems([{ id: null, name: 'Whatever I can bring' }]);
  var row = document.querySelector('.item-check-row');
  if (row) toggleItemRow(row);
  buildDateOptions();
  document.getElementById('commit-note').value = '';
  prefillVolunteerFields();
  document.getElementById('commit-panel').classList.add('open');
}

// ─── COMMIT MODAL — ITEM RENDERING ────────────────────────────────────────

/**
 * Renders item checkboxes. Items can have { id, name } (live API) or { id: null, name } (fallback).
 * The item.id is the site_item table id needed for the delivery POST.
 */
function renderCommitItems(items) {
  document.getElementById('item-check-list').innerHTML = items.map(buildItemRow).join('');
}

function buildItemRow(item) {
  var label  = typeof item === 'string' ? item : item.name;
  var itemId = (item && item.id != null) ? item.id : '';
  return '<div class="item-check-row" onclick="toggleItemRow(this)" data-item="' + label.replace(/"/g, '&quot;') + '" data-item-id="' + itemId + '">' +
    '<div class="item-checkbox"></div>' +
    '<div class="item-check-label">' + label + '</div>' +
    '<div class="item-qty-row">' +
      '<button class="item-qty-btn" onclick="event.stopPropagation();changeItemQty(this,-1)">&#8722;</button>' +
      '<div class="item-qty-val">1</div>' +
      '<button class="item-qty-btn" onclick="event.stopPropagation();changeItemQty(this,1)">&#43;</button>' +
    '</div>' +
  '</div>';
}

function toggleItemRow(row) { row.classList.toggle('checked'); }

function changeItemQty(btn, delta) {
  var valEl   = btn.parentElement.querySelector('.item-qty-val');
  var current = parseInt(valEl.textContent, 10) || 1;
  valEl.textContent = Math.max(1, current + delta);
}

function buildDateOptions() {
  var sel = document.getElementById('commit-date-select');
  if (!sel) return;
  var today = new Date();
  var opts  = '';
  for (var i = 0; i < 5; i++) {
    var d = new Date(today);
    d.setDate(today.getDate() + i);
    var label = i === 0 ? 'Today' : i === 1 ? 'Tomorrow'
      : d.toLocaleDateString('en-US', { weekday: 'short', month: 'short', day: 'numeric' });
    opts += '<option>' + label + '</option>';
  }
  sel.innerHTML = opts;
}

function prefillVolunteerFields() {
  var raw = localStorage.getItem('rcommons_demo_profile');
  var p   = raw ? JSON.parse(raw) : {};
  var nameEl  = document.getElementById('commit-volunteer-name');
  var phoneEl = document.getElementById('commit-volunteer-phone');
  if (nameEl  && !nameEl.value)  nameEl.value  = p.full_name || '';
  if (phoneEl && !phoneEl.value) phoneEl.value = p.phone || '';
}

// ─── COMMIT MODAL — CLOSE / CONFIRM ────────────────────────────────────────

function closeCommit(e)   { if (e.target === document.getElementById('commit-panel')) closeCommitBtn(); }
function closeCommitBtn() { document.getElementById('commit-panel').classList.remove('open'); }

function confirmCommit() {
  var checkedRows = document.querySelectorAll('.item-check-row.checked');
  if (checkedRows.length === 0) { showToast('Check at least one item you can bring.'); return; }

  var nameEl  = document.getElementById('commit-volunteer-name');
  var phoneEl = document.getElementById('commit-volunteer-phone');
  var volunteerName    = nameEl  ? nameEl.value.trim()  : '';
  var volunteerContact = phoneEl ? phoneEl.value.trim() : '';

  if (!volunteerName || !volunteerContact) {
    showToast('Please enter your name and phone number.');
    return;
  }

  var DEMO_NEEDS = buildDemoNeeds();
  var need       = (typeof currentNeedIdx === 'number') ? DEMO_NEEDS[currentNeedIdx] : null;
  var date       = document.getElementById('commit-date-select')
                    ? document.getElementById('commit-date-select').value : 'Today';
  var org        = need ? need.org : (currentSiteName || 'Organization');

  var totalQty    = 0;
  var itemIds     = [];

  checkedRows.forEach(function(row) {
    var itemName = row.dataset.item;
    var itemId   = row.dataset.itemId ? parseInt(row.dataset.itemId, 10) : null;
    var qty      = parseInt(row.querySelector('.item-qty-val').textContent, 10) || 1;
    totalQty += qty;
    if (itemId) itemIds.push(itemId);
    DEMO_COMMITMENTS.unshift({ item: itemName, org: org, date: date, qty: qty, status: 'pending' });
  });

  DEMO_TOTAL_ITEMS += totalQty;

  if (typeof currentNeedIdx === 'number') {
    CONTRIBUTED_NEEDS.add(currentNeedIdx);
    var src = WSS_URGENT_ITEMS[currentNeedIdx];
    if (src) src.urgentCount = Math.max(0, src.urgentCount - totalQty);
  }

  closeCommitBtn();

  var xpEarned = XP_PER_COMMIT + Math.floor((totalQty - 1) * 5);
  showToast('+' + xpEarned + ' XP — ' + totalQty + ' item' + (totalQty === 1 ? '' : 's') + ' committed! The org has been notified.');
  awardXP(xpEarned);

  // Submit to Spring Boot — fire-and-forget (UI already updated optimistically)
  if (currentSiteDbId && itemIds.length > 0) {
    apiSubmitCommitment(currentSiteDbId, itemIds, volunteerName, volunteerContact)
      .then(function(urlKey) {
        console.log('Commitment submitted, delivery key:', urlKey);
      })
      .catch(function(err) {
        console.warn('Commitment submitted locally but backend call failed:', err);
      });
  }

  renderDashboardCommitments();
  renderDashboardUrgent();
  renderNeedsList();
  renderOpps();
  updateItemsContributed();

  var el = document.getElementById('stat-commitments');
  if (el) el.textContent = DEMO_COMMITMENTS.length;
}

// ─── SHIFT SIGN-UP MODAL ───────────────────────────────────────────────────

function openShift(id) {
  var opp = DEMO_OPPS.find(function(o) { return o.id === id; });
  if (!opp) return;
  document.getElementById('shift-title').textContent    = opp.title;
  document.getElementById('shift-org').textContent      = opp.org;
  document.getElementById('shift-time').textContent     = opp.time;
  document.getElementById('shift-location').textContent = opp.location;
  document.getElementById('shift-spots').textContent    = opp.spots || 'Open';
  document.getElementById('shift-note').value           = '';
  document.getElementById('shift-panel').dataset.oppId  = id;
  document.getElementById('shift-panel').classList.add('open');
}

function closeShift() { document.getElementById('shift-panel').classList.remove('open'); }

function confirmShift() {
  var id  = document.getElementById('shift-panel').dataset.oppId;
  var opp = DEMO_OPPS.find(function(o) { return o.id === id; });
  if (!opp) return;

  opp.signedUp = true;
  DEMO_SIGNUPS.unshift({ type: 'shift', title: opp.title, org: opp.org, time: opp.time });
  DEMO_HOURS.unshift({ activity: opp.title, org: opp.org, date: opp.time.split(',')[0], hrs: null, status: 'pending', cat: opp.catLabel });
  DEMO_COMMITMENTS.unshift({ item: opp.title, org: opp.org, date: opp.time.split(',')[0], qty: 1, status: 'pending' });

  closeShift();
  showToast('+75 XP — you\'re signed up! Hours confirmed after the shift.');
  awardXP(75);

  renderOpps();
  renderDashboardUpcoming();
  renderDashboardCommitments();
  renderHoursList();
  renderDashboardHours();

  var el = document.getElementById('stat-commitments');
  if (el) el.textContent = DEMO_COMMITMENTS.length;
}

function signUpShift(id) { openShift(id); }
function rsvpEvent(id)   { openRSVP(id); }

// ─── RSVP MODAL ────────────────────────────────────────────────────────────

function openRSVP(id) {
  var opp = DEMO_OPPS.find(function(o) { return o.id === id; });
  if (!opp) return;
  document.getElementById('rsvp-title').textContent    = opp.title;
  document.getElementById('rsvp-org').textContent      = opp.org;
  document.getElementById('rsvp-time').textContent     = opp.time;
  document.getElementById('rsvp-location').textContent = opp.location;
  document.getElementById('rsvp-note').value           = '';
  document.getElementById('rsvp-panel').dataset.oppId  = id;
  document.getElementById('rsvp-panel').classList.add('open');
}

function closeRSVP() { document.getElementById('rsvp-panel').classList.remove('open'); }

function confirmRSVP() {
  var id  = document.getElementById('rsvp-panel').dataset.oppId;
  var opp = DEMO_OPPS.find(function(o) { return o.id === id; });
  if (!opp) return;

  opp.signedUp = true;
  DEMO_SIGNUPS.unshift({ type: 'event', title: opp.title, org: opp.org, time: opp.time });
  DEMO_COMMITMENTS.unshift({ item: 'RSVP — ' + opp.title, org: opp.org, date: opp.time.split(',')[0], qty: 1, status: 'pending' });

  closeRSVP();
  showToast('+25 XP — RSVP confirmed! See you there.');
  awardXP(25);

  renderOpps();
  renderDashboardUpcoming();
  renderDashboardCommitments();

  var el = document.getElementById('stat-commitments');
  if (el) el.textContent = DEMO_COMMITMENTS.length;
}

// ─── LOG HOURS MODAL ───────────────────────────────────────────────────────

function openLogHours() {
  var dateEl = document.getElementById('lh-date');
  if (dateEl && !dateEl.value) dateEl.value = new Date().toISOString().split('T')[0];
  document.getElementById('log-hours-panel').classList.add('open');
}
function closeLogHours() { document.getElementById('log-hours-panel').classList.remove('open'); }

function submitLogHours() {
  var org      = document.getElementById('lh-org').value.trim();
  var activity = document.getElementById('lh-activity').value.trim();
  var hrs      = parseFloat(document.getElementById('lh-hours').value);
  var date     = document.getElementById('lh-date').value;

  if (!org || !activity || !hrs) { showToast('Please fill in all fields.'); return; }

  DEMO_HOURS.unshift({
    activity: activity, org: org,
    date:     date || 'Today', hrs: hrs,
    status:   'pending',
    cat:      document.getElementById('lh-category').value
  });

  document.getElementById('lh-org').value      = '';
  document.getElementById('lh-activity').value = '';
  document.getElementById('lh-hours').value    = '';

  closeLogHours();
  showToast('Hours logged — awaiting org confirmation.');
  renderHoursList();
  renderDashboardHours();
}
