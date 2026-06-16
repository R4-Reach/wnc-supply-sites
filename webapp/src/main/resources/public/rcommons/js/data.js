/**
 * data.js — Static demo data for R-Commons
 *
 * WSS_SITES and WSS_URGENT_ITEMS start empty and are populated at boot
 * by apiGetLocations() in nav.js. All other arrays (opportunities, hours,
 * commitments, sign-ups) remain as demo data until those backend endpoints exist.
 */

// ─── WSS LIVE DATA (populated from /rcommons/api/sites at boot) ────────────

var WSS_SITES        = [];
var WSS_URGENT_ITEMS = [];

// ─── HOURS ─────────────────────────────────────────────────────────────────

var DEMO_HOURS = [
  { activity: 'Supply drop-off',         org: 'Civilian Disaster Response Org',   date: 'Mar 29', hrs: 3, status: 'confirmed', cat: 'Disaster Response' },
  { activity: 'Food distribution shift', org: 'His Humble Hands',                 date: 'Mar 22', hrs: 4, status: 'confirmed', cat: 'Food Distribution' },
  { activity: 'Sorting & inventory',     org: 'Breathitt County Hunger Alliance', date: 'Mar 15', hrs: 2, status: 'confirmed', cat: 'Food Distribution' }
];

// ─── COMMITMENTS (grows via user actions) ──────────────────────────────────

var DEMO_COMMITMENTS = [];

// ─── OPPORTUNITIES ─────────────────────────────────────────────────────────

var DEMO_OPPS = [
  {
    id: 'shift-1', type: 'shift', cat: 'food', signedUp: false,
    title: 'Meal prep & distribution shift',
    org:   'His Humble Hands · Caldwell, NC',
    time:  'Apr 9, 9am–1pm', location: 'His Humble Hands site, Caldwell, NC',
    spots: '6 spots left', catTag: 'tag-cat-food', catLabel: 'Food Distribution',
    iconPath: '<path d="M2 4h12v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4z"/><path d="M2 4l6 4 6-4"/>'
  },
  {
    id: 'shift-2', type: 'shift', cat: 'disaster', signedUp: false,
    title: 'Sorting & inventory — supply hub',
    org:   'Civilian Disaster Response Org · Buncombe, NC',
    time:  'Apr 10, 10am–2pm', location: 'CDRO Supply Hub, Buncombe, NC',
    spots: '4 spots left', catTag: 'tag-cat-disaster', catLabel: 'Disaster Response',
    iconPath: '<path d="M8 2l1.5 3.5H13L10.5 7.5l1 3.5L8 9l-3.5 2 1-3.5L3 5.5h3.5z"/>'
  },
  {
    id: 'shift-3', type: 'shift', cat: 'food', signedUp: false,
    title: 'Weekend food pantry — stocking & intake',
    org:   'Old Fort Church of God · McDowell, NC',
    time:  'Apr 13, 8am–12pm', location: 'Old Fort Church of God, McDowell, NC',
    spots: '8 spots left', catTag: 'tag-cat-food', catLabel: 'Food Distribution',
    iconPath: '<path d="M2 4h12v8a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4z"/><path d="M2 4l6 4 6-4"/>'
  },
  {
    id: 'event-1', type: 'event', cat: 'unassigned', signedUp: false,
    title: 'Volunteer coordination training',
    org:   'R-Commons Community Hub · Online',
    time:  'Apr 12, 2pm', location: 'Online — link sent after RSVP',
    spots: null, catTag: 'tag-cat-unassigned', catLabel: 'Training',
    iconPath: '<rect x="2" y="2" width="12" height="12" rx="1.5"/><path d="M2 6h12M5 2v12"/>'
  },
  {
    id: 'event-2', type: 'event', cat: 'unassigned', signedUp: false,
    title: 'Community resilience planning session',
    org:   'Breathitt County Hunger Alliance · Breathitt, KY',
    time:  'Apr 15, 6pm', location: 'Breathitt County Community Center',
    spots: null, catTag: 'tag-cat-unassigned', catLabel: 'Community',
    iconPath: '<rect x="2" y="2" width="12" height="12" rx="1.5"/><path d="M2 6h12M5 2v12"/>'
  }
];

var DEMO_SIGNUPS = [];
