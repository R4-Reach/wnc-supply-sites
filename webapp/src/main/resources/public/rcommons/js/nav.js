/**
 * nav.js — Navigation, utilities, R-Link panel, and boot sequence
 */

// ─── NAVIGATION ─────────────────────────────────────────────────────────────

var PAGES = ['dashboard','opportunities','needs','hours','map','messages','orgs','profile','background'];

function navTo(page) {
  PAGES.forEach(function(p) {
    var el  = document.getElementById('page-' + p);
    var nav = document.getElementById('nav-' + p);
    if (el)  el.classList.toggle('active', p === page);
    if (nav) nav.classList.toggle('active', p === page);
  });
}

// ─── TOAST ──────────────────────────────────────────────────────────────────

function showToast(msg) {
  var t = document.getElementById('toast');
  document.getElementById('toast-msg').textContent = msg;
  t.classList.add('show');
  setTimeout(function() { t.classList.remove('show'); }, 3500);
}

// ─── AVAILABILITY CELL TOGGLE ────────────────────────────────────────────────

function toggleAvail(cell) { cell.classList.toggle('on'); }

// ─── R-LINK LIVE PANEL ───────────────────────────────────────────────────────

function loadRLink() {
  var panel = document.getElementById('rlink-content');
  var meta  = document.getElementById('rlink-meta');
  if (!panel) return;

  fetch('https://api.allorigins.win/get?url=' + encodeURIComponent('https://rlink.r4reach.org/jan2026/'), {
    signal: AbortSignal.timeout ? AbortSignal.timeout(6000) : undefined
  }).then(function(r) { return r.json(); }).then(function(json) {
    var html   = json.contents || '';
    var parser = new DOMParser();
    var doc    = parser.parseFromString(html, 'text/html');
    var main   = doc.querySelector('#main-content') || doc.querySelector('main') || doc.querySelector('article');
    if (!main) { panel.innerHTML = rlinkFallback(); return; }

    var sections = [];
    main.querySelectorAll('h2, h3, p, li').forEach(function(el) {
      var text = el.textContent.trim();
      if (!text || text.length < 5 || text.includes('cookie') || text.includes('Cookie')) return;
      if (el.tagName === 'H2' || el.tagName === 'H3') {
        sections.push({ type: 'heading', text: text });
      } else {
        sections.push({ type: 'item', text: text, link: el.querySelector && el.querySelector('a') ? el.querySelector('a').href : null });
      }
    });

    if (sections.length === 0) { panel.innerHTML = rlinkFallback(); return; }

    panel.innerHTML = sections.slice(0, 12).map(function(s) {
      if (s.type === 'heading') {
        return '<div style="padding:8px 18px 4px;font-size:10px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:var(--text-3);">' + escHtml(s.text) + '</div>';
      }
      var link = s.link && s.link.startsWith('http')
        ? ' <a href="' + s.link + '" target="_blank" style="color:var(--accent);font-size:11px;">→</a>'
        : '';
      return '<div class="alert-item"><div class="alert-pip read"></div>' +
        '<div class="alert-text" style="font-size:12px;">' + escHtml(s.text.slice(0, 180)) + (s.text.length > 180 ? '…' : '') + link + '</div></div>';
    }).join('');

    if (meta) meta.textContent = 'R-Link · Live';
  }).catch(function() {
    panel.innerHTML = rlinkFallback();
    if (meta) meta.textContent = 'R-Link · Cached';
  });
}

function rlinkFallback() {
  return '<div class="alert-item"><div class="alert-pip unread"></div><div class="alert-text"><strong>Jan 2026 Snow/Ice Storm:</strong> Carbon monoxide risk — symptoms include dull headache, dizziness, fatigue. Leave building if symptoms appear.</div></div>' +
    '<div class="alert-item"><div class="alert-pip read"></div><div class="alert-text"><strong>Hypothermia warning:</strong> Children: bright red cold skin. Early: shivering, confusion. Severe: slurred speech, drowsiness.</div></div>' +
    '<div class="alert-item"><div class="alert-pip read"></div><div class="alert-text"><strong>Crisis line:</strong> Dial 988 · Disaster distress: 1-800-985-5990</div></div>' +
    '<div class="alert-item"><div class="alert-pip read"></div><div class="alert-text">Full updates at <a href="https://rlink.r4reach.org/jan2026/" target="_blank" style="color:var(--accent);">rlink.r4reach.org</a></div></div>';
}

function escHtml(str) {
  return String(str || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

// ─── GLOBAL SEARCH ───────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', function() {
  var searchEl = document.getElementById('global-search');
  if (searchEl) {
    searchEl.addEventListener('keydown', function(e) {
      if (e.key === 'Enter' && this.value.trim()) {
        navTo('opportunities');
        this.value = '';
      }
    });
  }
});

// ─── BOOT ───────────────────────────────────────────────────────────────────
// Runs immediately on script load.
// Fetches live WSS site data, populates globals, then renders.

(function boot() {
  loadProfile();
  loadRLink();

  document.addEventListener('DOMContentLoaded', function() {
    // Render opportunity list immediately (uses only demo data)
    renderOpps();

    // Show loading state in map and needs panels while API call is in flight
    var mapSub = document.getElementById('map-sub');
    if (mapSub) mapSub.textContent = 'Loading WSS supply sites…';

    // Fetch live site data from Spring Boot
    apiGetLocations()
      .then(function(sites) {
        // Populate globals used by render.js
        WSS_SITES = sites;

        // Build WSS_URGENT_ITEMS from sites that have urgently needed items,
        // sorted by urgency and capped at the top 8 for the needs page.
        WSS_URGENT_ITEMS = sites
          .filter(function(s) { return s.urgentCount > 0; })
          .sort(function(a, b) { return b.urgentCount - a.urgentCount; })
          .slice(0, 8)
          .map(function(s) {
            return {
              dbId:        s.dbId,
              id:          s.id,
              site:        s.site,
              county:      s.county + ', ' + s.state,
              items:       s.urgentItems || [],
              urgentCount: s.urgentCount
            };
          });

        renderWSSList();
        renderWSSNeeds();
        renderDashboardUrgent();
        renderNeedsList();
      })
      .catch(function(err) {
        console.warn('R-Commons: failed to load site data from API', err);
        var mapList = document.getElementById('wss-list');
        if (mapList) mapList.innerHTML = '<div class="empty-state">Could not load supply sites. Please refresh.</div>';
      });
  });
})();
