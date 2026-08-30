# Style guide

How to color and style pages in this app so the site stays cohesive and stays
easy to re-skin. Read this before you write a color into any CSS or template.

The one rule that makes the rest work: **raw color values live in exactly one
file, [`tokens.css`](../webapp/src/main/resources/public/tokens.css). Everywhere
else, you reference a token with `var(--…)` — you never write a hex or `rgb()`.**

That is what lets us restyle the whole site by editing one file, and it is what
stops each page from quietly growing its own palette.

## 1. Single source of truth

`webapp/src/main/resources/public/tokens.css` defines every color the app is
allowed to use, each under a *semantic* name (`--urgent`, `--surface`) rather
than a descriptive one (`--red`, `--white`). It is imported at the top of
`style.css`, which every server-rendered page links, so the tokens are available
on every page for free.

- **In a page stylesheet:** `color: var(--danger);`, `background: var(--well);`.
- **Never** `color: #b60808;` in a page sheet, and never in an inline
  `style="…"` attribute either.

If you find yourself typing a `#` outside `tokens.css`, stop — either a token
already covers it (§2) or you need to add one (§4).

## 2. The token catalog

Pick by **meaning**, not by hue. "This number is a required field" → `--danger`,
not "this should be red." When the brand changes, the meaning stays put and the
color moves with the token.

### Chrome — carries the brand

The "negative-space" look: a cool tinted canvas with white surfaces floating on
it. Hierarchy comes from the white-on-tint step, recessed wells, and a slate
border — *not* from blue fills. Blue is reserved for accents and focus.

| Token | Value | Use for |
|-------|-------|---------|
| `--bg` | `#f0f4fa` | Page canvas (the tinted backdrop) |
| `--text` | `#0a0e2a` | Body text |
| `--surface` | `#ffffff` | Buttons, floating controls |
| `--surface-alt` | `#ffffff` | Content containers, cards on the tint |
| `--well` | `#eaf0fa` | Inset panels recessed below a surface |
| `--border-strong` | `#7c8aa6` | Container borders (slate, ≥3:1 both ways) |
| `--shadow-inset` | `#d4dcf0` | Inset edge on raised buttons |
| `--accent` | `#162df0` | Primary accent / emphasis |
| `--focus-ring` | `#162df0` | Keyboard focus outline |
| `--nav-btn-bg` | `#ffffff` | Nav button face |
| `--nav-btn-text` | `#0a0e2a` | Nav button label |

### Results table — chrome for tabular data

| Token | Value | Use for |
|-------|-------|---------|
| `--table-head-bg` | `#dbe4f5` | Table header background |
| `--table-head-text` | `#0a0e2a` | Table header text |
| `--row-odd` | `#e2eaf6` | Zebra-stripe odd rows |
| `--row-even` | `#ffffff` | Zebra-stripe even rows |

### Near-white tints — separating side-by-side lanes

A ramp of barely-there pastels, each able to host body text and white cards but
distinct enough to separate adjacent columns (e.g. the deliveries kanban lanes).
Use as many as you have lanes, in wheel order: cream, amber, citron, mint, aqua,
lavender, rose.

| Token | Value | Token | Value |
|-------|-------|-------|-------|
| `--tint-cream` | `#fdf5f0` | `--tint-aqua` | `#eff9fa` |
| `--tint-amber` | `#fdf9ef` | `--tint-lavender` | `#f5f2fc` |
| `--tint-citron` | `#fafbef` | `--tint-rose` | `#fcf0f6` |
| `--tint-mint` | `#f0faf1` | | |

### Status — carries meaning, not brand

Deliberately independent of the brand palette, so a re-skin doesn't accidentally
recolor "error" or "in stock." Chosen for meaning and hue-separated so the two
dark reds stay distinct.

| Token | Value | Use for |
|-------|-------|---------|
| `--urgent` | `#b3330c` | Urgent items (vermilion) |
| `--danger` | `#b60808` | Errors, required-field markers |
| `--needed` | `#880055` | Needed items |
| `--available` | `#006400` | Available / in-stock |
| `--oversupply` | `#162df0` | Oversupply emphasis (same blue as `--accent`) |
| `--success` | `#008000` | Confirmations |
| `--warning` | `#805800` | Privately-visible notices |

Light status **surfaces** (dark text/border on a pale ground), plus a transient
attention highlight that carries "look here" rather than any one status:

| Token | Value | Use for |
|-------|-------|---------|
| `--available-bg` | `#e8f5e9` | Pale green ground behind available/success content |
| `--danger-bg` | `#f8d7da` | Pale red ground behind errors/danger content |
| `--highlight` | `#ffe680` | Transient highlight — active/pressed, flashes |
| `--highlight-soft` | `#fff6cc` | Gentler highlight — hover |

### Neutral greys — utilitarian, brand-independent

A small grey ramp for plain chrome (form-control borders, dividers, disabled and
auxiliary panels) that sits outside the cool-tinted surface system. Use these
instead of an ad-hoc `#ccc`/`#999`/`#eee`; reach for a chrome token
(`--surface`, `--well`, `--border-strong`) first when the element is part of the
tinted-surface look.

| Token | Value | Use for |
|-------|-------|---------|
| `--neutral-fill` | `#f5f5f5` | Subtle grey background — hover rows, disabled/auxiliary panels |
| `--neutral-fill-alt` | `#e4e4e4` | Deeper grey background / soft divider |
| `--neutral-border` | `#bcbcbc` | Default grey border in neutral contexts |
| `--neutral-border-strong` | `#8a8a8a` | Heavier grey border / soft shadow |
| `--neutral-muted-text` | `#636363` | Secondary or disabled text (AA on white) |

### Elevation — shadows share one base

Every drop shadow draws from one warm near-black base so raised elements read as
one system. Use `--shadow-raised` for the standard raised-button/dialog stack;
compose bespoke elevations as `rgba(var(--shadow-rgb), α) …`.

| Token | Value | Use for |
|-------|-------|---------|
| `--shadow-rgb` | `45, 35, 66` | Shadow base — always via `rgba(var(--shadow-rgb), α)` |
| `--shadow-raised` | *(multi-layer)* | Standard raised-button / dialog elevation |
| `--overlay` | `rgba(0,0,0,.5)` | Scrim — modal backdrops, scrollbar thumb |
| `--overlay-light` | `rgba(255,255,255,.5)` | Faint light edge highlight |

## 3. The rules

1. **No raw color outside `tokens.css`.** Page sheets and templates use
   `var(--…)` only. No `#hex`, no `rgb()`, no named colors like `red`.
2. **No inline `style="…"` for anything reusable.** Put it in the page's
   stylesheet as a class. Inline styles can't be tokenized, can't be restyled in
   one place, and hide color literals from review.
3. **Pick a status token by meaning, not hue.** Don't reach for `--danger`
   because you want red; reach for it because the thing is an error.
4. **Don't invent a page-local palette.** If no token fits, you add a token
   (§4) — you do not define a new color in your page sheet. A one-off green in
   `admin/users.css` is exactly the fragmentation this guide exists to prevent.
5. **Reuse before you add.** Most needs are already a token. Scan the catalog
   first; a near-match you can live with beats a new near-duplicate token.

## 4. Adding a color

Only when nothing in the catalog fits its role:

1. Add the token to `tokens.css`, in the right section (chrome / table / tint /
   status / neutral / elevation), with a semantic name and a comment saying what
   it's for.
2. Check contrast. Text and UI colors must clear WCAG AA on the surfaces they'll
   sit on (4.5:1 for text, 3:1 for borders/focus). The existing tokens annotate
   their ratios — match that rigor and note it in the comment.
3. Reference it from your page with `var(--your-token)`.

If you're tempted to add something that's a near-duplicate of an existing token,
that's a signal to reuse the existing one instead.

## 5. Brand vs. status — why they're split

`tokens.css` keeps two groups apart on purpose:

- **Chrome** tokens carry the brand — the blues, the tinted canvas, the surfaces.
- **Status** tokens carry meaning — urgent, danger, available — and are brand-
  independent.

The payoff: **re-skinning the site is editing the chrome tokens, and only those.**
Change `--accent`, `--bg`, `--surface`, the table and tint colors, and the whole
app follows. The status colors stay put because "error" should look like an error
regardless of brand. Don't blur the line by using `--accent` where you mean a
status, or a status token for decoration.

## 6. Beyond color

The same single-source principle should extend past color as the system matures:

- **Shadows** are tokenized (`--shadow-rgb`, `--shadow-raised`, `--overlay*`) —
  use them rather than writing a new shadow literal.
- **Radii and spacing.** When these start repeating, tokenize them too rather
  than hardcoding per page.
- **Inline styles.** ~127 inline `style="…"` attributes across the templates are
  the biggest hole in the color system — they bypass tokens entirely. Migrate
  them to classes over time.

## 7. rcommons is out of scope

The volunteer portal under `webapp/src/main/resources/public/rcommons/` is a
transplanted separate app with its **own** token file (`rcommons/css/tokens.css`)
and its own `--brand-red-*` / `--brand-blue-*` vocabulary. Per `AGENTS.md`, it is
legacy-to-be-migrated, not house style.

- This guide and `tokens.css` are the house system. When building house pages,
  **ignore rcommons as a reference.**
- Do **not** copy rcommons' `--brand-*` tokens outward, and don't import its
  token file into house pages.
- The intended direction is to migrate rcommons *onto* the house tokens, not to
  let its palette spread.

## 8. Known debt

Named so it gets fixed, not treated as precedent. None of this blocks using the
guide today.

| Item | Where | Fix |
|------|-------|-----|
| `--urgent` defined twice with different values (`#b3330c` house vs `#950000` rcommons) | house vs `rcommons/css/tokens.css` | Reconcile as part of the rcommons migration |
| ~127 inline `style="…"` attributes | 14 templates | Move to classes |
| `mobile.css` empty but linked from 24 templates | `public/mobile.css` | Fill in or remove the links |
| No shared head/layout partial; inconsistent relative CSS link paths | across templates | Centralize CSS linking |
