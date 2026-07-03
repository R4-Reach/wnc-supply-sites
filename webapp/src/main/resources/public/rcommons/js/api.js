/**
 * api.js — R-Commons API client (Spring Boot backend)
 *
 * Each function calls a real Spring Boot endpoint. Profile and auth
 * still use localStorage for Phase 1 — wired to real endpoints in a later phase.
 */

// ─── AUTH ──────────────────────────────────────────────────────────────────

function apiLogin(email, password) {
  var saved = localStorage.getItem('rcommons_demo_profile');
  if (saved) {
    try {
      var p = JSON.parse(saved);
      if (p.classes && p.classes.length > 0) {
        return Promise.resolve({ success: true, volunteerId: 'demo', error: null });
      }
    } catch (e) {}
  }
  return Promise.resolve({ success: true, volunteerId: null, error: null });
}

function apiRegister(firstName, lastName, email, city) {
  return Promise.resolve({ success: true, volunteerId: 'demo', error: null });
}

function apiSignOut() {
  localStorage.removeItem('rcommons_demo_profile');
  return Promise.resolve({ success: true });
}

// ─── VOLUNTEER / PROFILE ───────────────────────────────────────────────────

function apiGetProfile(volunteerId) {
  var saved = localStorage.getItem('rcommons_demo_profile');
  if (saved) {
    try { return Promise.resolve(JSON.parse(saved)); } catch (e) {}
  }
  return Promise.resolve({
    full_name: 'Volunteer', email: '', city: '', initials: 'V',
    classes: [], skills: [], availability: {}, bio: '', xp: 0, level: 1
  });
}

function apiSaveProfile(volunteerId, profileData) {
  localStorage.setItem('rcommons_demo_profile', JSON.stringify(profileData));
  return Promise.resolve({ success: true });
}

// ─── SUPPLY SITES (WSS — live from Spring Boot) ───────────────────────────

/**
 * Fetches all active WSS supply sites.
 * Returns an array of site objects with: dbId, id (wssId), site, siteType,
 * county, state, acceptingDonations, urgentCount, neededCount, urgentItems[].
 * The dbId is the internal site.id used for /volunteer/site-items and /volunteer/delivery.
 */
function apiGetLocations() {
  return fetch('/rcommons/api/sites')
    .then(function(r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
}

/**
 * Fetches needed items for a specific site by internal DB id.
 * Returns { site: { id, name, address, county, state, items: [{ id, name, status }] } }
 * where item.id is the site_item table id (used as neededItems in the delivery POST).
 */
function apiGetSiteItems(dbId) {
  return fetch('/volunteer/site-items?siteId=' + dbId)
    .then(function(r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
}

/**
 * Submits a supply commitment (volunteer pledging to bring items to a site).
 * Maps to POST /volunteer/delivery.
 *
 * @param {number} siteDbId   - Internal site.id (not wssId)
 * @param {number[]} itemIds  - site_item.id values for the chosen items
 * @param {string} volunteerName
 * @param {string} volunteerContact - Phone number
 * @returns {Promise<string>} urlKey for the created delivery request
 */
function apiSubmitCommitment(siteDbId, itemIds, volunteerName, volunteerContact) {
  return fetch('/volunteer/delivery', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      site:             String(siteDbId),
      neededItems:      itemIds,
      volunteerName:    volunteerName,
      volunteerContact: volunteerContact
    })
  }).then(function(r) {
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.text();
  });
}

// ─── OPPORTUNITIES (demo — wire to backend in Phase 3) ────────────────────

function apiGetOpportunities() {
  return Promise.resolve(DEMO_OPPS);
}

function apiSignUpShift(opportunityId, volunteerId, note) {
  return Promise.resolve({ success: true });
}

function apiRsvpEvent(opportunityId, volunteerId, note) {
  return Promise.resolve({ success: true });
}

// ─── HOURS (demo — wire to backend in Phase 2) ────────────────────────────

function apiGetHours(volunteerId) {
  return Promise.resolve(DEMO_HOURS);
}

function apiLogHours(volunteerId, entry) {
  return Promise.resolve({ success: true });
}
