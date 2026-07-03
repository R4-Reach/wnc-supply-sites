/**
 * profile.js — Volunteer profile, XP system, session management
 *
 * Handles:
 *   - loadProfile()     reads from localStorage (→ apiGetProfile() later)
 *   - updateXPDisplay() syncs all XP UI elements
 *   - awardXP()         increments XP, triggers level-up toast
 *   - signOut()         clears session and redirects
 */

// ─── XP CONFIG ─────────────────────────────────────

var DEMO_XP       = 120;   // starts partially full so the bar is visible
var DEMO_LEVEL    = 1;
var XP_PER_LEVEL  = 500;
var XP_PER_COMMIT = 75;

// Running total of physical items committed across all WSS needs
var DEMO_TOTAL_ITEMS = 0;

// Tracks WSS need indices the volunteer has contributed to
var CONTRIBUTED_NEEDS = new Set();

// ─── PROFILE LOAD ──────────────────────────────────

function loadProfile() {
  var raw = localStorage.getItem('rcommons_demo_profile');
  var p   = raw ? JSON.parse(raw) : null;

  // Fallback demo profile so the dashboard renders without onboarding
  if (!p || !p.full_name) {
    p = {
      full_name: 'Jordan Wilson', email: 'jordan@example.com',
      city: 'Asheville, NC', initials: 'JW',
      classes: ['Caregiver', 'Builder'],
      skills: ['Cooking / Food prep', 'Driving / Transport', 'First aid / CPR'],
      xp: 340, level: 3,
      bio: 'Longtime Asheville resident passionate about food security and community resilience.'
    };
  }

  var firstName   = (p.full_name || 'Volunteer').split(' ')[0];
  var initials    = p.initials || (firstName[0] || 'V').toUpperCase();
  var city        = p.city || 'your area';
  var classes     = p.classes || [];
  var skills      = p.skills || [];
  var xp          = p.xp || 0;
  var level       = p.level || 1;
  var xpPerLevel  = XP_PER_LEVEL;
  var xpThisLevel = xp % xpPerLevel;
  var xpToNext    = xpPerLevel - xpThisLevel;
  var xpPct       = Math.round((xpThisLevel / xpPerLevel) * 100);

  // Sync live XP state
  DEMO_XP    = xp;
  DEMO_LEVEL = level;

  // ── Topbar ──
  var av = document.getElementById('topbar-avatar');
  if (av) av.textContent = initials;

  // ── Sidebar ──
  var roleText = document.getElementById('sidebar-role-text');
  if (roleText) roleText.textContent = (classes[0] || 'Volunteer') + ' · Lv ' + level;
  var sxp = document.getElementById('sidebar-xp');
  if (sxp) sxp.textContent = xp + ' XP';
  var sxpNext = document.getElementById('sidebar-xp-next');
  if (sxpNext) sxpNext.textContent = xpToNext + ' XP to Lv ' + (level + 1);
  var sxpFill = document.getElementById('sidebar-xp-fill');
  if (sxpFill) sxpFill.style.width = xpPct + '%';

  // ── Dashboard greeting ──
  var hour     = new Date().getHours();
  var greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  var greetEl  = document.getElementById('dashboard-greeting');
  if (greetEl) greetEl.textContent = greeting + ', ' + firstName;
  var subEl = document.getElementById('dashboard-sub');
  if (subEl) {
    var now    = new Date();
    var days   = ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday'];
    var months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'];
    subEl.textContent = days[now.getDay()] + ', ' + months[now.getMonth()] + ' ' + now.getDate() + ' · ' + city;
  }

  // ── Stat cards ──
  var totalHrs = DEMO_HOURS.reduce(function(s, h) { return s + (h.hrs || 0); }, 0);
  var orgSet   = new Set(DEMO_HOURS.map(function(h) { return h.org; }));
  var el;
  el = document.getElementById('stat-total-hours');      if (el) el.textContent = totalHrs;
  el = document.getElementById('stat-commitments');       if (el) el.textContent = DEMO_COMMITMENTS.length;
  el = document.getElementById('stat-commitments-sub');  if (el) el.innerHTML = '<span>' + DEMO_COMMITMENTS.filter(function(c) { return c.status === 'pending'; }).length + ' pending</span>';
  el = document.getElementById('stat-xp-display');       if (el) el.textContent = DEMO_XP;
  el = document.getElementById('stat-hours-total');      if (el) el.textContent = totalHrs;
  el = document.getElementById('stat-hours-month');      if (el) el.textContent = totalHrs;
  el = document.getElementById('stat-hours-pending');    if (el) el.textContent = '0';
  el = document.getElementById('stat-hours-orgs');       if (el) el.textContent = orgSet.size;

  // ── XP display ──
  updateXPDisplay(false);

  // ── Profile page ──
  var pa = document.getElementById('profile-avatar');    if (pa) pa.textContent = initials;
  var pn = document.getElementById('profile-name');      if (pn) pn.textContent = p.full_name || 'Volunteer';
  var pr = document.getElementById('profile-role');      if (pr) pr.textContent = city + ' · Member since Apr 2025';
  var pc = document.getElementById('profile-classes');
  if (pc) {
    pc.innerHTML = classes.length
      ? classes.map(function(c) {
          return '<span class="role-chip"><svg width="10" height="10" viewBox="0 0 10 10" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="5" cy="5" r="4"/><path d="M5 1v8M1 5h8"/></svg>' + c + '</span>';
        }).join('')
      : '<span class="role-chip" style="opacity:0.5;">None selected</span>';
  }
  var ps = document.getElementById('profile-skills');
  if (ps) {
    ps.innerHTML = skills.length
      ? skills.map(function(s) { return '<span class="skill-chip">' + s + '</span>'; }).join('')
      : '<span class="skill-chip" style="opacity:0.5;">No skills added yet</span>';
  }
  var pb = document.getElementById('profile-bio');       if (pb) pb.textContent = p.bio || 'No bio yet.';

  // ── Render dynamic lists ──
  renderDashboardCommitments();
  renderDashboardHours();
  renderDashboardUrgent();
  renderNeedsList();
  renderHoursList();
  updateItemsContributed();
}

// ─── XP SYSTEM ─────────────────────────────────────

function updateXPDisplay(animated) {
  var xpThisLevel = DEMO_XP % XP_PER_LEVEL;
  var xpPct       = Math.round((xpThisLevel / XP_PER_LEVEL) * 100);
  var xpToNext    = XP_PER_LEVEL - xpThisLevel;

  // Sidebar
  var sxp = document.getElementById('sidebar-xp');
  if (sxp) sxp.textContent = DEMO_XP + ' XP';
  var sxpNext = document.getElementById('sidebar-xp-next');
  if (sxpNext) sxpNext.textContent = xpToNext + ' XP to Lv ' + (DEMO_LEVEL + 1);
  var fill = document.getElementById('sidebar-xp-fill');
  if (fill) {
    if (animated) fill.style.transition = 'width 0.8s cubic-bezier(0.34,1.56,0.64,1)';
    fill.style.width = xpPct + '%';
  }
  var roleText = document.getElementById('sidebar-role-text');
  if (roleText) {
    var raw     = localStorage.getItem('rcommons_demo_profile');
    var classes = raw ? (JSON.parse(raw).classes || []) : [];
    roleText.textContent = (classes[0] || 'Volunteer') + ' · Lv ' + DEMO_LEVEL;
  }

  // Dashboard stat card
  var xpEl = document.getElementById('stat-xp-display');
  if (xpEl) xpEl.textContent = DEMO_XP;
  var xpSubEl = document.getElementById('stat-xp-sub');
  if (xpSubEl) xpSubEl.innerHTML = '<span>' + xpToNext + ' XP</span> to Lv ' + (DEMO_LEVEL + 1);

  // Profile XP card
  var pxp  = document.getElementById('profile-xp-val');   if (pxp)  pxp.textContent  = DEMO_XP;
  var psub = document.getElementById('profile-xp-sub');   if (psub) psub.textContent = '/ 500 XP · Level ' + DEMO_LEVEL;
  var pfill = document.getElementById('profile-xp-fill');
  if (pfill) {
    if (animated) pfill.style.transition = 'width 0.8s cubic-bezier(0.34,1.56,0.64,1)';
    pfill.style.width = xpPct + '%';
  }
  var pnote = document.getElementById('profile-xp-note');
  if (pnote) pnote.textContent = xpToNext + ' XP to Level ' + (DEMO_LEVEL + 1) + ' · Keep volunteering!';
}

function awardXP(amount) {
  var oldLevel = DEMO_LEVEL;
  DEMO_XP    += amount;
  DEMO_LEVEL  = Math.floor(DEMO_XP / XP_PER_LEVEL) + 1;
  updateXPDisplay(true);
  if (DEMO_LEVEL > oldLevel) {
    setTimeout(function() { showToast('🎉 Level up! You are now Level ' + DEMO_LEVEL + '!'); }, 800);
  }
}

// ─── ITEMS CONTRIBUTED ─────────────────────────────

function updateItemsContributed() {
  var el  = document.getElementById('stat-items-contributed');  if (el)  el.textContent = DEMO_TOTAL_ITEMS;
  var el2 = document.getElementById('profile-items-contributed'); if (el2) el2.textContent = DEMO_TOTAL_ITEMS;

  var breakdown = document.getElementById('profile-contributions-breakdown');
  if (!breakdown) return;
  if (DEMO_TOTAL_ITEMS === 0) {
    breakdown.innerHTML = '<div style="font-size:12px;color:var(--text-3);">Make your first commitment to see your impact here.</div>';
    return;
  }
  var byOrg = {};
  DEMO_COMMITMENTS.forEach(function(c) {
    if (!c.qty) return;
    byOrg[c.org] = (byOrg[c.org] || 0) + c.qty;
  });
  breakdown.innerHTML = Object.keys(byOrg).map(function(org) {
    return '<div style="display:flex;justify-content:space-between;align-items:center;font-size:12px;padding:5px 0;border-bottom:1px solid var(--border);">' +
      '<span style="color:var(--text-2);">' + org + '</span>' +
      '<span style="font-weight:700;color:var(--accent);">' + byOrg[org] + ' items</span>' +
    '</div>';
  }).join('');
}

// ─── SIGN OUT ───────────────────────────────────────

function signOut() {
  apiSignOut().then(function() {
    window.location.href = 'onboarding.html';
  });
}
