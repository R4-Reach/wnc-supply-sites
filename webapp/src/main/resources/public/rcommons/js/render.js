/**
 * render.js — All DOM rendering functions
 *
 * Responsible for turning data (from data.js or api.js) into HTML.
 * No business logic here — just rendering.
 */

// ─── OPPORTUNITY LIST ───────────────────────────────

// Filter state for Opportunities page
var activeType   = 'all';
var activeCat    = 'all';
var activeSignup = 'available'; // 'available' | 'signedup' | 'all'

function filterOpps(type)  { activeType   = type;  renderOpps(); }
function filterCat(cat)    { activeCat    = cat;   renderOpps(); }
function filterSignup(val) {
  activeSignup = val;
  var sel = document.getElementById('filter-signup-select');
  if (sel) sel.value = val;
  renderOpps();
}

function renderOpps() {
  var list = document.getElementById('opp-list');
  if (!list) return;

  var filtered = DEMO_OPPS.filter(function(o) {
    if (activeType   !== 'all' && o.type     !== activeType)   return false;
    if (activeCat    !== 'all' && o.cat      !== activeCat)    return false;
    if (activeSignup === 'available' && o.signedUp)            return false;
    if (activeSignup === 'signedup'  && !o.signedUp)           return false;
    return true;
  });

  var showNeeds = (activeType === 'all' || activeType === 'need') && activeSignup !== 'signedup';
  var needsHtml = showNeeds ? renderOppsNeedsHtml() : '';
  var count     = document.getElementById('opp-count');

  if (filtered.length === 0 && !needsHtml) {
    var emptyMsg = activeSignup === 'signedup'
      ? 'You haven\'t signed up for anything yet. <a onclick="filterSignup(\'available\')" style="color:var(--accent);cursor:pointer;font-weight:600;">Browse available →</a>'
      : 'No opportunities match your filters.';
    list.innerHTML = '<div style="padding:24px 18px;text-align:center;font-size:13px;color:var(--text-3);">' + emptyMsg + '</div>';
    if (count) count.textContent = '0 opportunities';
    return;
  }

  var wssCount = showNeeds ? Math.min(3, WSS_URGENT_ITEMS.length) : 0;
  var total    = filtered.length + wssCount;
  if (count) count.textContent = total + ' opportunit' + (total === 1 ? 'y' : 'ies');

  list.innerHTML = filtered.map(function(o) {
    var signedUpTag = o.signedUp ? '<span class="tag tag-done">Signed up ✓</span>' : '';
    var spotsTag    = o.spots && !o.signedUp ? '<span class="tag tag-dist">' + o.spots + '</span>' : '';
    var btn = o.signedUp
      ? '<button class="btn btn-secondary btn-sm" style="opacity:0.7;" disabled>Signed up ✓</button>'
      : (o.type === 'shift'
          ? '<button class="btn btn-primary btn-sm" onclick="signUpShift(\'' + o.id + '\')">Sign up</button>'
          : '<button class="btn btn-primary btn-sm" onclick="rsvpEvent(\'' + o.id + '\')">RSVP</button>');
    return '<div class="opp-item" data-type="' + o.type + '" data-cat="' + o.cat + '">' +
      '<div class="opp-org-mark ' + (o.signedUp ? 'mark-blue' : 'mark-amber') + '"><svg viewBox="0 0 16 16" fill="none" stroke-width="1.5">' + o.iconPath + '</svg></div>' +
      '<div class="opp-info">' +
        '<div class="opp-title">' + o.title + '</div>' +
        '<div class="opp-org">'   + o.org   + '</div>' +
        '<div class="opp-meta">' +
          '<span class="tag tag-event">' + (o.type === 'shift' ? 'Shift' : 'Event') + '</span>' +
          '<span class="tag ' + o.catTag + '">' + o.catLabel + '</span>' +
          '<span class="tag tag-dist">' + o.time + '</span>' +
          spotsTag + signedUpTag +
        '</div>' +
      '</div>' +
      '<div class="opp-actions">' + btn + '</div>' +
    '</div>';
  }).join('') + needsHtml;
}

function renderOppsNeedsHtml() {
  return WSS_URGENT_ITEMS.slice(0, 3).map(function(w, i) {
    var markCls      = w.urgentCount > 100 ? 'mark-red' : 'mark-amber';
    var title        = w.items.slice(0,3).join(', ') + (w.items.length > 3 ? ' & more' : '');
    var contributed  = CONTRIBUTED_NEEDS.has(i);
    var contributedTag = contributed ? '<span class="tag tag-done" style="color:var(--accent);">Contributed ✓</span>' : '';
    var btnLabel     = contributed ? 'Help again' : 'I can help';
    var btnCls       = contributed ? 'btn btn-secondary btn-sm' : 'btn btn-primary btn-sm';
    return '<div class="opp-item" data-type="need" data-cat="disaster">' +
      '<div class="opp-org-mark ' + markCls + '"><svg viewBox="0 0 16 16" fill="none" stroke-width="1.5"><path d="M5 7V5.5a3 3 0 0 1 6 0V7"/><rect x="2" y="7" width="12" height="7" rx="1"/></svg></div>' +
      '<div class="opp-info">' +
        '<div class="opp-title">' + title + '</div>' +
        '<div class="opp-org">' + w.site + ' · ' + w.county + '</div>' +
        '<div class="opp-meta">' +
          '<span class="tag tag-urgent">Urgent</span>' +
          '<span class="tag tag-need">Need</span>' +
          '<span class="tag tag-dist">' + w.urgentCount + ' items needed</span>' +
          contributedTag +
        '</div>' +
      '</div>' +
      '<div class="opp-actions"><button class="' + btnCls + '" onclick="openNeedCommit(' + i + ')">' + btnLabel + '</button></div>' +
    '</div>';
  }).join('');
}

// ─── DASHBOARD PANELS ───────────────────────────────

function renderDashboardUpcoming() {
  var el = document.getElementById('dashboard-upcoming-list');
  if (!el) return;
  if (DEMO_SIGNUPS.length === 0) {
    el.innerHTML = '<div style="padding:20px 18px;text-align:center;">' +
      '<div style="font-size:13px;color:var(--text-3);margin-bottom:6px;">Nothing scheduled yet</div>' +
      '<div style="font-size:12px;color:var(--text-3);">Sign up for a shift or RSVP to an event to see it here.</div>' +
      '</div>';
    return;
  }
  el.innerHTML = DEMO_SIGNUPS.map(function(s) {
    var icon    = s.type === 'shift'
      ? '<path d="M2 4h12v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4z"/><path d="M2 4l6 4 6-4"/>'
      : '<rect x="2" y="2" width="12" height="12" rx="1.5"/><path d="M2 6h12M5 2v12"/>';
    var typeTag = '<span class="tag tag-event">' + (s.type === 'shift' ? 'Shift' : 'Event') + '</span>';
    return '<div class="opp-item">' +
      '<div class="opp-org-mark mark-amber"><svg viewBox="0 0 16 16" fill="none" stroke-width="1.5">' + icon + '</svg></div>' +
      '<div class="opp-info">' +
        '<div class="opp-title">' + s.title + '</div>' +
        '<div class="opp-org">'   + s.org   + '</div>' +
        '<div class="opp-meta">' + typeTag + '<span class="tag tag-dist">' + s.time + '</span><span class="tag tag-done">Signed up ✓</span></div>' +
      '</div>' +
    '</div>';
  }).join('');
}

function renderDashboardCommitments() {
  var el = document.getElementById('dashboard-commitments-list');
  if (!el) return;
  if (DEMO_COMMITMENTS.length === 0) {
    el.innerHTML = '<div style="padding:14px 18px;font-size:13px;color:var(--text-3);">No commitments yet — click "I can help" on any need below to get started.</div>';
    return;
  }
  el.innerHTML = DEMO_COMMITMENTS.slice(0, 3).map(function(c) {
    var cls = c.status === 'completed' ? 'confirmed' : 'pending';
    return '<div class="hours-item">' +
      '<div class="hours-dot ' + cls + '"></div>' +
      '<div class="hours-info"><div class="hours-title">' + c.item + '</div><div class="hours-org">' + c.org + ' · ' + c.date + '</div></div>' +
      '<div><div class="hours-val">×' + c.qty + '</div><div class="hours-status ' + cls + '">' + (c.status === 'completed' ? 'Done' : 'Pending') + '</div></div>' +
    '</div>';
  }).join('');
}

function renderDashboardHours() {
  var el = document.getElementById('dashboard-hours-list');
  if (!el) return;
  el.innerHTML = DEMO_HOURS.slice(0, 3).map(function(h) {
    var cls = h.status === 'confirmed' ? 'confirmed' : 'pending';
    return '<div class="hours-item">' +
      '<div class="hours-dot ' + cls + '"></div>' +
      '<div class="hours-info"><div class="hours-title">' + h.activity + ' · ' + h.org + '</div><div class="hours-org">' + h.date + '</div></div>' +
      '<div><div class="hours-val">' + (h.hrs ? h.hrs + 'h' : '—') + '</div><div class="hours-status ' + cls + '">' + h.status.charAt(0).toUpperCase() + h.status.slice(1) + '</div></div>' +
    '</div>';
  }).join('');
}

function renderDashboardUrgent() {
  var DEMO_NEEDS = buildDemoNeeds();
  var el = document.getElementById('dashboard-urgent-list');
  if (!el) return;
  var urgent = DEMO_NEEDS
    .map(function(n, idx) { return { need: n, idx: idx }; })
    .filter(function(e) { return e.need.urgent; })
    .slice(0, 3);
  el.innerHTML = urgent.map(function(e) { return renderNeedItem(e.need, e.idx); }).join('');
}

// ─── NEEDS PAGE ─────────────────────────────────────

// Derives DEMO_NEEDS from WSS_URGENT_ITEMS (single source of truth)
function buildDemoNeeds() {
  return WSS_URGENT_ITEMS.map(function(w) {
    var pct = Math.max(8, Math.min(82, 100 - Math.round(w.urgentCount / 1.6)));
    return {
      title:       w.items.slice(0,3).join(', ') + (w.items.length > 3 ? ' & more' : ''),
      org:         w.site, county: w.county,
      urgentCount: w.urgentCount, pct: pct, remaining: w.urgentCount,
      urgent:      w.urgentCount > 80, items: w.items
    };
  });
}

function needIcon() {
  return '<svg viewBox="0 0 16 16" fill="none" stroke-width="1.5"><path d="M5 7V5.5a3 3 0 0 1 6 0V7"/><rect x="2" y="7" width="12" height="7" rx="1"/></svg>';
}

function renderNeedItem(n, idx) {
  var lowClass  = n.pct < 40 ? ' low' : '';
  var markCls   = n.urgent ? 'mark-red' : 'mark-amber';
  var urgentTag = n.urgent ? '<span class="tag tag-urgent">Urgent</span> ' : '';
  return '<div class="opp-item" onclick="openNeedCommit(' + idx + ')">' +
    '<div class="opp-org-mark ' + markCls + '">' + needIcon() + '</div>' +
    '<div class="opp-info">' +
      '<div class="opp-title">' + n.title + '</div>' +
      '<div class="opp-org">' + n.org + ' · ' + n.county + '</div>' +
      '<div class="opp-meta" style="margin-bottom:4px;">' + urgentTag + '</div>' +
      '<div class="item-progress">' +
        '<div class="item-progress-meta"><span>' + n.pct + '% filled</span><span>' + n.remaining + ' urgent items needed</span></div>' +
        '<div class="item-progress-bar"><div class="item-progress-fill' + lowClass + '" style="width:' + n.pct + '%"></div></div>' +
      '</div>' +
    '</div>' +
    '<div class="opp-actions"><button class="btn btn-primary btn-sm" onclick="event.stopPropagation();openNeedCommit(' + idx + ')">I can help</button></div>' +
  '</div>';
}

function renderNeedsList() {
  var DEMO_NEEDS = buildDemoNeeds();
  var el  = document.getElementById('needs-local-list');
  var cnt = document.getElementById('needs-local-count');
  if (cnt) cnt.textContent = DEMO_NEEDS.length + ' active needs from WSS network';
  if (!el) return;
  el.innerHTML = DEMO_NEEDS.map(function(n, i) { return renderNeedItem(n, i); }).join('');
}

// ─── HOURS PAGE ─────────────────────────────────────

function renderHoursList() {
  var el = document.getElementById('hours-full-list');
  if (!el) return;
  el.innerHTML = DEMO_HOURS.map(function(h) {
    var cls = h.status === 'confirmed' ? 'confirmed' : 'pending';
    return '<div class="hours-item">' +
      '<div class="hours-dot ' + cls + '"></div>' +
      '<div class="hours-info">' +
        '<div class="hours-title">' + h.activity + ' · ' + h.org + '</div>' +
        '<div class="hours-org">' + (h.cat ? h.cat + ' · ' : '') + h.date + '</div>' +
      '</div>' +
      '<div><div class="hours-val">' + (h.hrs ? h.hrs + ' hrs' : '—') + '</div>' +
      '<div class="hours-status ' + cls + '">' + h.status.charAt(0).toUpperCase() + h.status.slice(1) + '</div></div>' +
    '</div>';
  }).join('');
}

// ─── WSS SUPPLY MAP ─────────────────────────────────

var grayModeActive = false;

function toggleGrayMode() {
  grayModeActive = !grayModeActive;
  var btn    = document.getElementById('map-gray-btn');
  var banner = document.getElementById('gray-sky-banner');
  if (grayModeActive) {
    btn.style.background  = 'var(--urgent)';
    btn.style.color       = '#fff';
    btn.style.borderColor = 'var(--urgent)';
    banner.style.display  = 'flex';
  } else {
    btn.style.background  = '';
    btn.style.color       = '';
    btn.style.borderColor = '';
    banner.style.display  = 'none';
  }
  renderWSSList();
}

function getUrgencyColor(site) {
  if (site.urgentCount > 50) return { cls: 'mark-red',   label: 'Critical',     labelCls: 'tag-urgent' };
  if (site.urgentCount > 10) return { cls: 'mark-amber', label: 'Urgent',       labelCls: 'tag-need' };
  if (site.urgentCount > 0)  return { cls: 'mark-amber', label: 'Needs items',  labelCls: 'tag-need' };
  if (site.neededCount > 0)  return { cls: 'mark-green', label: 'Accepting',    labelCls: 'tag-done' };
  return { cls: 'mark-blue', label: 'Stocked', labelCls: 'tag-done' };
}

function getTypeIcon(type) {
  if (type === 'Food Pantry') return '<svg viewBox="0 0 16 16" fill="none" stroke-width="1.5"><path d="M5 7V5.5a3 3 0 0 1 6 0V7"/><rect x="2" y="7" width="12" height="7" rx="1"/></svg>';
  if (type === 'Supply Hub')  return '<svg viewBox="0 0 16 16" fill="none" stroke-width="1.5"><rect x="2" y="2" width="12" height="12" rx="1.5"/><path d="M2 6h12M5 2v12"/></svg>';
  return '<svg viewBox="0 0 16 16" fill="none" stroke-width="1.5"><path d="M8 2a6 6 0 0 1 6 6c0 4-6 8-6 8S2 12 2 8a6 6 0 0 1 6-6z"/><circle cx="8" cy="8" r="2"/></svg>';
}

function renderWSSList() {
  var search   = (document.getElementById('wss-search').value || '').toLowerCase();
  var stateF   = document.getElementById('wss-state').value;
  var typeF    = document.getElementById('wss-type').value;
  var urgencyF = document.getElementById('wss-urgency').value;

  var filtered = WSS_SITES.filter(function(s) {
    if (search   && !s.site.toLowerCase().includes(search) && !s.county.toLowerCase().includes(search)) return false;
    if (stateF   && s.state    !== stateF)   return false;
    if (typeF    && s.siteType !== typeF)     return false;
    if (urgencyF === 'urgent'   && s.urgentCount === 0)       return false;
    if (urgencyF === 'accepting' && !s.acceptingDonations)    return false;
    if (grayModeActive && (s.urgentCount === 0 || !s.acceptingDonations)) return false;
    return true;
  });

  filtered.sort(function(a, b) { return b.urgentCount - a.urgentCount || b.neededCount - a.neededCount; });

  document.getElementById('wss-count').textContent = filtered.length + ' site' + (filtered.length === 1 ? '' : 's');
  document.getElementById('map-sub').textContent   = 'WSS network · ' + WSS_SITES.length + ' sites loaded · Sorted by urgency';

  if (filtered.length === 0) {
    document.getElementById('wss-list').innerHTML = '<div class="empty-state"><svg viewBox="0 0 24 24"><circle cx="12" cy="10" r="3"/><path d="M12 2a8 8 0 0 1 8 8c0 5.5-8 13-8 13S4 15.5 4 10a8 8 0 0 1 8-8z"/></svg><div>No sites match your filters</div></div>';
    return;
  }

  document.getElementById('wss-list').innerHTML = filtered.map(function(s) {
    var u           = getUrgencyColor(s);
    var icon        = getTypeIcon(s.siteType);
    var urgentBadge = s.urgentCount > 0 ? '<span class="tag ' + u.labelCls + '" style="margin-right:4px;">' + s.urgentCount + ' urgent</span>' : '';
    var neededBadge = s.neededCount > 0 ? '<span class="tag tag-dist">' + s.neededCount + ' items needed</span>' : '';
    var donBadge    = s.acceptingDonations ? '<span class="tag tag-done">Accepting donations</span>' : '<span class="tag tag-dist">Not accepting</span>';
    var updated     = s.inventoryLastUpdated ? '<span class="tag tag-dist">Updated ' + s.inventoryLastUpdated + '</span>' : '';
    return '<div class="opp-item">' +
      '<div class="opp-org-mark ' + u.cls + '">' + icon + '</div>' +
      '<div class="opp-info">' +
        '<div class="opp-title">' + s.site + '</div>' +
        '<div class="opp-org">' + s.siteType + ' · ' + s.county + ', ' + s.state + '</div>' +
        '<div class="opp-meta" style="margin-top:4px;">' + urgentBadge + neededBadge + donBadge + updated + '</div>' +
      '</div>' +
      '<div class="opp-actions">' +
        '<button class="btn btn-primary btn-sm" onclick="openWSSCommit(' + JSON.stringify(s.site) + ')">I can help</button>' +
        '<button class="btn btn-secondary btn-sm">Details</button>' +
      '</div>' +
    '</div>';
  }).join('');
}

function renderWSSNeeds() {
  var container = document.getElementById('wss-needs-list');
  if (!container) return;
  container.innerHTML = WSS_URGENT_ITEMS.map(function(n, i) {
    var pct       = Math.max(10, Math.min(85, 100 - Math.round(n.urgentCount / 1.5)));
    var lowClass  = pct < 40 ? ' low' : '';
    var colorClass = n.urgentCount > 100 ? 'mark-red' : 'mark-amber';
    return '<div class="opp-item" onclick="openWSSCommit(' + JSON.stringify(n.site) + ')">' +
      '<div class="opp-org-mark ' + colorClass + '"><svg viewBox="0 0 16 16" fill="none" stroke-width="1.5"><path d="M5 7V5.5a3 3 0 0 1 6 0V7"/><rect x="2" y="7" width="12" height="7" rx="1"/></svg></div>' +
      '<div class="opp-info">' +
        '<div class="opp-title">' + n.items.slice(0,3).join(', ') + (n.items.length > 3 ? ' +' + (n.items.length-3) + ' more' : '') + '</div>' +
        '<div class="opp-org">' + n.site + ' · ' + n.county + '</div>' +
        '<div class="item-progress" style="max-width:300px;">' +
          '<div class="item-progress-meta"><span>' + pct + '% filled</span><span>' + n.urgentCount + ' urgent items needed</span></div>' +
          '<div class="item-progress-bar"><div class="item-progress-fill' + lowClass + '" style="width:' + pct + '%"></div></div>' +
        '</div>' +
      '</div>' +
      '<div class="opp-actions"><button class="btn btn-primary btn-sm" onclick="event.stopPropagation();openWSSCommit(' + JSON.stringify(n.site) + ')">I can help</button></div>' +
    '</div>';
  }).join('');
  var cnt = document.getElementById('wss-needs-count');
  if (cnt) cnt.textContent = WSS_URGENT_ITEMS.length + ' WSS sites with urgent needs';
}
