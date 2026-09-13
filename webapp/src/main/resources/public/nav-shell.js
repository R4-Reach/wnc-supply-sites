// Behavior for the site-wide app shell (header.html / sidebar.html):
//  - marks the nav item matching the current page as active
//  - toggles the off-canvas sidebar on narrow screens
(function () {
  "use strict";

  // First path segment, e.g. "/manage/status" -> "manage". Groups every page under a section
  // to its single nav item without needing an exact href match per route.
  function firstSegment(path) {
    var parts = path.split("/").filter(Boolean);
    return parts.length ? parts[0] : "";
  }

  function markActive() {
    var current = firstSegment(window.location.pathname);
    if (!current) {
      return; // home page has no owning nav item
    }
    document.querySelectorAll(".sidebar .nav-item").forEach(function (item) {
      // Same-origin links only; external (jotform) links never own an app section.
      if (item.origin === window.location.origin && firstSegment(item.pathname) === current) {
        item.classList.add("active");
      }
    });
  }

  function wireToggle() {
    var toggle = document.getElementById("nav-toggle");
    var sidebar = document.getElementById("sidebar");
    var scrim = document.getElementById("sidebar-scrim");
    if (!toggle || !sidebar || !scrim) {
      return;
    }
    function setOpen(open) {
      sidebar.classList.toggle("open", open);
      scrim.classList.toggle("show", open);
      toggle.setAttribute("aria-expanded", String(open));
    }
    toggle.addEventListener("click", function () {
      setOpen(!sidebar.classList.contains("open"));
    });
    scrim.addEventListener("click", function () {
      setOpen(false);
    });
  }

  function init() {
    markActive();
    wireToggle();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
