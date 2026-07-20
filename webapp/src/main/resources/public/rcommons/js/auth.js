/**
 * auth.js — Login, registration, and onboarding flow
 *
 * Used only by onboarding.html.
 * In production: replace localStorage calls with apiLogin() / apiRegister().
 */

// ─── ONBOARDING STATE ───────────────────────────────

var S = { firstName: '', lastName: '', email: '', city: '', classes: [], skills: [], availability: {}, bio: '' };
var previousScreen = 'screen-login';

// ─── LOGO INJECTION ─────────────────────────────────

var LOGO_HTML = '<div class="logo"><div class="logo-mark" style="background:#950000;"><svg viewBox="0 0 16 16" fill="none" stroke="#fff" stroke-width="1.5"><circle cx="8" cy="8" r="6"/><path d="M8 2c0 3-2 5-2 5s2 1.5 2 7"/><path d="M8 2c0 3 2 5 2 5s-2 1.5-2 7"/><line x1="2" y1="8" x2="14" y2="8"/></svg></div><span class="logo-name">R-Commons</span></div>';

(function injectLogos() {
  document.querySelectorAll('.logo-placeholder').forEach(function(el) {
    el.outerHTML = LOGO_HTML;
  });
})();

// ─── SCREEN NAVIGATION ──────────────────────────────

function goToScreen(id) {
  document.querySelectorAll('.screen').forEach(function(s) { s.classList.remove('active'); });
  document.getElementById(id).classList.add('active');
  window.scrollTo(0, 0);
}

// ─── TOAST ──────────────────────────────────────────

var toastTimer;
function showToast(msg) {
  var t = document.getElementById('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(function() { t.classList.remove('show'); }, 3200);
}

// ─── AUTH ───────────────────────────────────────────

function doLogin() {
  // Demo mode: password field is for visual realism only.
  // TODO: replace with apiLogin(email, password).then(...)
  var email = document.getElementById('login-email').value.trim();
  var err   = document.getElementById('login-error');
  err.classList.remove('show');
  if (!email) { err.textContent = 'Please enter your email.'; err.classList.add('show'); return; }

  var saved = localStorage.getItem('rcommons_demo_profile');
  if (saved) {
    try {
      var p = JSON.parse(saved);
      if (p.classes && p.classes.length > 0) { window.location.href = 'dashboard.html'; return; }
    } catch (e) {}
  }

  var namePart = email.split('@')[0].replace(/[._-]/g, ' ').trim();
  S.firstName = namePart.split(' ')[0];
  S.firstName = S.firstName.charAt(0).toUpperCase() + S.firstName.slice(1).toLowerCase();
  S.lastName  = '';
  S.email     = email;
  S.city      = 'Asheville, NC';
  previousScreen = 'screen-login';
  goToScreen('screen-class');
}

function doRegister() {
  // Demo mode: password field is for visual realism only.
  // TODO: replace with apiRegister(first, last, email, city).then(...)
  var first = document.getElementById('reg-first').value.trim();
  var last  = document.getElementById('reg-last').value.trim();
  var email = document.getElementById('reg-email').value.trim();
  var city  = document.getElementById('reg-city').value.trim();
  var err   = document.getElementById('reg-error');
  err.classList.remove('show');
  if (!first || !email) { err.textContent = 'Please fill in your name and email.'; err.classList.add('show'); return; }

  S.firstName = first;
  S.lastName  = last;
  S.email     = email;
  S.city      = city || 'Asheville, NC';
  previousScreen = 'screen-register';
  goToScreen('screen-class');
}

// ─── CLASS SELECTION ────────────────────────────────

function toggleClass(el) {
  var cls = el.dataset.class;
  if (el.classList.contains('selected')) {
    el.classList.remove('selected');
    S.classes = S.classes.filter(function(c) { return c !== cls; });
  } else {
    if (S.classes.length >= 2) { showToast('You can pick up to 2 character classes.'); return; }
    el.classList.add('selected');
    S.classes.push(cls);
  }
  document.getElementById('class-count').textContent = S.classes.length;
  document.getElementById('btn-class-next').disabled = S.classes.length === 0;
}

// ─── SKILL SELECTION ────────────────────────────────

function toggleSkill(el) {
  var skill = el.textContent.trim();
  el.classList.toggle('selected');
  if (el.classList.contains('selected')) { S.skills.push(skill); }
  else { S.skills = S.skills.filter(function(s) { return s !== skill; }); }
}

// ─── AVAILABILITY GRID ───────────────────────────────
// Grid-building, hour keys, and quick-action presets live in
// availability.js (shared with the Profile page and the calendars).

function goToAvail() {
  renderAvailGrid(document.getElementById('avail-grid'), S.availability, null);
  renderAvailPresetButtons(document.getElementById('avail-presets'), function(presetKey) {
    applyAvailPreset(S.availability, presetKey);
    renderAvailGrid(document.getElementById('avail-grid'), S.availability, null);
  });
  goToScreen('screen-avail');
}

// ─── FINISH ONBOARDING ───────────────────────────────

function finishOnboarding() {
  S.bio      = document.getElementById('bio-input').value.trim();
  var fullName = (S.firstName + ' ' + S.lastName).trim();
  var initials = (S.firstName[0] || 'V').toUpperCase() + (S.lastName[0] || '').toUpperCase();

  var profile = {
    full_name:    fullName,
    email:        S.email,
    city:         S.city,
    initials:     initials,
    classes:      S.classes,
    skills:       S.skills,
    availability: S.availability,
    bio:          S.bio,
    xp:           100,
    level:        1
  };

  // TODO: replace with apiSaveProfile('demo', profile).then(...)
  localStorage.setItem('rcommons_demo_profile', JSON.stringify(profile));

  // Populate success screen
  document.getElementById('done-classes').innerHTML = S.classes.length
    ? S.classes.map(function(c) { return '<span class="summary-chip accent">' + c + '</span>'; }).join('')
    : '<span class="summary-chip">None selected</span>';
  document.getElementById('done-skills').innerHTML = S.skills.length === 0
    ? '<span class="summary-chip">None selected — add later in your profile</span>'
    : S.skills.slice(0, 8).map(function(s) { return '<span class="summary-chip">' + s + '</span>'; }).join('')
      + (S.skills.length > 8 ? '<span class="summary-chip">+' + (S.skills.length - 8) + ' more</span>' : '');

  goToScreen('screen-done');
}

function goToDashboard() { window.location.href = 'dashboard.html'; }

// ─── BOOT ───────────────────────────────────────────
// Redirect to dashboard if already onboarded

(function() {
  var saved = localStorage.getItem('rcommons_demo_profile');
  if (saved) {
    try {
      var p = JSON.parse(saved);
      if (p.classes && p.classes.length > 0) { window.location.href = 'dashboard.html'; }
    } catch (e) {}
  }
})();
