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

// hover content for role cards in onboarding.html

const hoverContent = {
  "Initiator": `
    <div class="class-name">Initiator</div>
    <div class="class-desc">
      You're the spark. You start things. You act when others are still thinking. You're good at sensing what needs to happen and taking the first step—even when the path isn't clear yet. You often rally people before there's a plan.
    </div>
  `,

  "Synthesizer": `
    <div class="class-name">Synthesizer</div>
    <div class="class-desc">
      You make sense of the mess. You connect dots others don't see. You hold multiple perspectives and help make things coherent. When people talk past each other, you find the throughline. When there's chaos, you find structure.
    </div>
  `,

  "Implementer": `
    <div class="class-name">Implementer</div>
    <div class="class-desc">
      You get it done. You turn ideas into action. You build, fix, move, execute. You like clear roles, working parts, and visible progress. You're happiest when a project goes from plan to reality—and you were part of making it happen.
    </div>
  `,

  "Sustainer": `
    <div class="class-name">Sustainer</div>
    <div class="class-desc">
      You keep things running. You're the quiet backbone of teams and systems. You hold routines, check in, and keep people and projects from falling through the cracks. You think long-term and tend what others forget.
    </div>
  `,

  "Strategic Challenger": `
    <div class="class-name">Strategic Challenger</div>
    <div class="class-desc">
      You name the tension. You're willing to question what others avoid. You notice blind spots, power dynamics, and flawed assumptions. You push for truth, clarity, and change—not because you want conflict, but because you care.
    </div>
  `,

  "Relational Weaver": `
    <div class="class-name">Relational Weaver</div>
    <div class="class-desc">
      You build the web. You know who's connected to whom—and who should be. You build trust, hold relationships, and keep the social fabric strong. You make space for care, inclusion, and collaboration.
    </div>
  `,

  "Pattern Tracker": `
    <div class="class-name">Pattern Tracker</div>
    <div class="class-desc">
      You see the deeper currents. You zoom out. You notice patterns over time, across systems, or under the surface. You help others see root causes, not just symptoms—and you often warn of risks before they arrive.
    </div>
  `,

  "Meaning Maker": `
    <div class="class-name">Meaning Maker</div>
    <div class="class-desc">
      You make it matter. You create clarity, resonance, and shared purpose. You use story, ritual, metaphor, or reflection to help people connect. You bring the "why" into the "what." Without you, things feel flat or transactional.
    </div>
  `,

  "Resource Mobilizer": `
    <div class="class-name">Resource Mobilizer</div>
    <div class="class-desc">
      You make things possible. You know how to find what's needed—funds, tools, talent, space. You move resources where they'll have the most impact. You're pragmatic, creative, and often the person who knows a person.
    </div>
  `,

  "Edgewalker": `
    <div class="class-name">Edgewalker</div>
    <div class="class-desc">
      You live at the frontier. You explore new ways of thinking, creating, and organizing. You often live between worlds—bridging cultures, disciplines, or paradigms. You stretch what's possible and bring back insights others might miss.
    </div>
  `,

  "Experimenter": `
    <div class="class-name">Experimenter</div>
    <div class="class-desc">
      You test things. You prototype, iterate, and learn in motion. You thrive in uncertainty and enjoy learning by doing. You help groups evolve quickly and avoid perfection paralysis.
    </div>
  `,

  "Conflict Alchemist": `
    <div class="class-name">Conflict Alchemist</div>
    <div class="class-desc">
      You work with tension. You don't just mediate—you transform. You help groups face hard things, shift stuck patterns, and emerge stronger. You move toward conflict, not away from it.
    </div>
  `,

  "Sensemaker / Educator": `
    <div class="class-name">Sensemaker / Educator</div>
    <div class="class-desc">
      You help people understand. You distill complexity. You turn big ideas into understandable guidance. You train, teach, or translate.
    </div>
  `,

  "Caregiver / Nourisher": `
    <div class="class-name">Caregiver / Nourisher</div>
    <div class="class-desc">
      You tend what others depend on. You tend people and the unseen. You notice what needs warmth, rest, or support, and you bring it. You create safety for others to grow, act, or speak.
    </div>
  `,

  "Guardian / Steward / Protector": `
    <div class="class-name">Guardian / Steward / Protector</div>
    <div class="class-desc">
      You hold the line. You watch the edges. You hold ethical boundaries, protect the vulnerable, and help groups stay aligned with values, place, and purpose.
    </div>
  `,

  "Welcomer": `
    <div class="class-name">Welcomer</div>
    <div class="class-desc">
      You grow the circle. You notice who isn't here yet—and you reach out. You welcome people into the space, meet them where they are, and help them find their place in the work. You make complexity feel less intimidating and community feel more human.
    </div>
  `
};

document.querySelectorAll(".class-card").forEach(card => {

    // Save the original HTML
    card.dataset.original = card.innerHTML;

    //on mouse enter, replace the content with the hover content

    card.addEventListener("mouseenter", () => {
        const className = card.dataset.class;
        if (hoverContent[className]) {
            card.innerHTML = hoverContent[className];
        }
    });

    //on mouse leave, restore the original HTML

    card.addEventListener("mouseleave", () => {
        card.innerHTML = card.dataset.original;
    });

});