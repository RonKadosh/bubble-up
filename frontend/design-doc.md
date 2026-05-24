# Bubble.up — Design System

> The product is StudySync in code (CLAUDE.md still says so) but **Bubble.up** in
> every place the user sees a name. Study groups are **Bubbles**.

This doc is the source of truth for what the app *feels* like and how to keep
it that way as we add features. It pairs with [`CLAUDE.md`](./CLAUDE.md) — read
that first for engineering conventions.

---

## 1. Feeling

Bubble.up should feel:

- **Light and airy.** White space, soft surfaces, no hard slabs.
- **Bubbly.** Everything tends toward circular. Buttons are pills, avatars are
  circles, cards have generous corners, accents are dots and rings.
- **Friendly, not corporate.** Copy uses the bubble metaphor where it lands
  naturally — "Hop in", "Pop this Bubble", "🫧 floated into the Bubble" — but
  never at the cost of clarity (Cancel is still Cancel).
- **Soft motion.** Hover lifts are tiny and springy, not snappy. Nothing
  bounces hard.

If you're adding a feature and the result feels like a spreadsheet, you've
drifted. Pull a card from the [Dashboard](./src/pages/DashboardPage.tsx) feed
or the Bubbles list in [GroupsPage](./src/pages/GroupsPage.tsx) and copy its
silhouette.

---

## 2. Color tokens

All colors live as CSS custom properties in [`src/index.css`](./src/index.css)
under `:root` (light) and `.dark`. Dark mode is a class on `<html>` that swaps
the same variable names — the rest of the codebase never special-cases dark.

### Brand — light blue ladder

```
--color-primary-50   #f0faff   ← faint wash
--color-primary-100  #dff3ff
--color-primary-200  #b9e3fd
--color-primary-300  #8acdf6   ← the "bubble" blue
--color-primary-400  #5cb5ee
--color-primary-500  #389ae0   ← solid primary (rare; we prefer gradients)
--color-primary-600  #2780c3   ← accent text on light surfaces
--color-primary-700  #21659a
--color-primary-800  #1d527a
--color-primary-900  #19405d
```

Exposed to Tailwind as `bg-primary-500`, `text-primary-600`, `border-primary-300`,
etc. (see [`tailwind.config.js`](./tailwind.config.js)). The `brand-*` alias maps
to the same scale.

### Neutrals & surfaces

| CSS var            | Light       | Dark        | When to use                            |
|--------------------|-------------|-------------|----------------------------------------|
| `--bg`             | `#f4f9fc`   | `#0c1218`   | Page background                        |
| `--bg-secondary`   | `#ecf3f9`   | `#131c24`   | Secondary base (rare)                  |
| `--surface`        | `#ffffff`   | `#19232d`   | Card, modal, input fill                |
| `--surface-hover`  | `#f1f7fc`   | `#213040`   | Hovered row / card                     |
| `--surface-muted`  | `#ebf2f8`   | `#1c2731`   | Section subtle fill, skeleton bars     |
| `--border`         | `#d7e2ec`   | `#2d3d4d`   | All dividers / borders                 |
| `--border-strong`  | `#b8cad9`   | `#3f5365`   | Border on hover / focus                |
| `--text`           | `#15202b`   | `#eaf4fa`   | Headings, body                         |
| `--text-secondary` | `#4a5965`   | `#b3c4cf`   | Captions, subtitles                    |
| `--text-muted`     | `#6f8090`   | `#7f919e`   | Hints, timestamps                      |
| `--silver`         | `#cfdae3`   | `#2a3540`   | Cool partner of the brand — gradient tail |
| `--silver-soft`    | `#e6ecf1`   | `#1f2932`   | Soft variant of the above              |
| `--success`        | `#2fb36d`   | `#35d48a`   | Future use (no semantic green yet)     |
| `--warning`        | `#d4a72c`   | `#e8bc4b`   | EXAM badge tint                        |
| `--danger`         | `#d94f4f`   | `#ff7676`   | Destructive actions ("Pop")            |

### Don't use raw Tailwind grays anywhere

If you find yourself reaching for `bg-gray-50`, `text-gray-500`, or
`border-gray-200`, stop. The right answer is one of the semantic utilities in
[`src/index.css`](./src/index.css) `@layer utilities`:

- `bg-base` / `bg-base-secondary` / `bg-surface` / `bg-surface-hover` / `bg-surface-muted`
- `text-base` / `text-secondary` / `text-muted`
- `border-line` / `border-line-strong`
- `text-danger`, `bg-danger-soft`

These automatically dark-mode-swap.

### Gradients (the signature)

Bubble.up's hero look is **light blue → silver**, never a single solid color
across large surfaces. Use:

- `bg-brand-gradient`         — the standard light-blue → silver
- `bg-brand-gradient-soft`    — pale primary → soft silver (washes, empty states)
- `bg-brand-gradient-strong`  — primary-400 → silver (CTAs, active tabs, sidebar)
- `bg-brand-gradient-vertical`— vertical variant (sidebar)
- `bg-bubble-glow`            — radial highlight + linear gradient (login hero)

Text on top of a gradient uses `text-on-brand` (dark navy `#0d2334`). Don't use
white over our pale gradients; contrast is too thin.

---

## 3. Shapes — round everything

| Element              | Class                         | Notes                                     |
|----------------------|-------------------------------|-------------------------------------------|
| Buttons (default)    | `rounded-full`                | Always pill. No corner buttons.           |
| Avatars              | `rounded-full`                | Circle, always.                           |
| Inputs               | `rounded-2xl` / `rounded-full`| Form fields → 2xl; chat composer → full.  |
| Small badges/chips   | `rounded-md`                  | The only tame radius we use.              |
| List rows            | `rounded-xl` / `rounded-2xl`  | Pill rows for nav, 2xl for content rows.  |
| Cards (standard)     | `rounded-2xl`                 | Use the `<Card />` component (size `md`). |
| Cards (large)        | `rounded-3xl`                 | Feed items, modal bodies, sidebar/main panels. |
| Top-level panels     | `rounded-[2rem]` / `[2.5rem]` | Sidebar, login hero, main outlet.         |
| Decorative bubbles   | `rounded-full aspect-square`  | Always perfect circles.                   |

Sharp corners are jarring in this design — we don't use them anywhere.

---

## 4. Elevation / shadow

Three levels:

- `shadow-sm`       — barely there. Avatars, small badges.
- `shadow-themed`   — the workhorse. Cards, buttons, nav rows.
- `shadow-bubble`   — heroic. CTAs on the login page, modals, dashboard hero.

Shadows are tinted with the brand blue (`rgba(56,154,224,...)`) so the lift
feels like it belongs in the palette. Don't override with raw Tailwind
`shadow-lg`/`shadow-xl` — they look gray and out of place.

---

## 5. Motion — `bubble-pop`

Every interactive element should spring on hover. We have one utility:

```css
.bubble-pop {
  transition: transform 200ms cubic-bezier(.34,1.56,.64,1),
              box-shadow 200ms,
              filter 200ms;
}
.bubble-pop:hover  { transform: translateY(-1px) scale(1.02); filter: brightness(1.04); }
.bubble-pop:active { transform: translateY(0) scale(0.98);    filter: brightness(0.97); }
```

Apply it to:
- All `<Button />` / `<IconButton />` (built in)
- Interactive cards (`<Card interactive />` adds it)
- Tab buttons
- Nav rows

Do **not** apply to non-interactive things. The bounce stops feeling delightful
when static elements wobble on cursor passes.

For width/height transitions (sidebar collapse): `transition-[width] duration-200 ease-out`.
For fades (modals, tooltips): plain `transition-opacity` is fine.

---

## 6. Component inventory

Everything below lives under [`src/components/`](./src/components/). Use these
before hand-rolling new markup.

| Component                | File                         | Use it for                                          |
|--------------------------|------------------------------|-----------------------------------------------------|
| `<Avatar id name size?>` | [`Avatar.tsx`](./src/components/Avatar.tsx)   | Initial-on-gradient circle. Stable color from `id`. |
| `<Button variant size?>` | [`Button.tsx`](./src/components/Button.tsx)   | Pill button. Variants: primary / secondary / ghost / danger. Sizes: xs / sm / md / lg. |
| `<LinkButton>`           | [`Button.tsx`](./src/components/Button.tsx)   | Same look as `<Button>`, renders `<a>`.             |
| `<IconButton size?>`     | [`Button.tsx`](./src/components/Button.tsx)   | Round icon-only button (chat send, calendar arrows).|
| `<Card size? interactive?>` | [`Card.tsx`](./src/components/Card.tsx)    | Themed surface card. Sizes: sm / md / lg.           |
| `<Layout>`               | [`Sidebar.tsx`](./src/components/Sidebar.tsx) | The whole shell — collapsible sidebar + `<Outlet />`. |
| Icons (`BookIcon`, …)    | [`Icons.tsx`](./src/components/Icons.tsx)     | Centralized SVG set. `currentColor` + `className`.  |

### When to add a new component

Three rules:

1. **Used in 2+ places** today (not "might be useful eventually").
2. The thing isn't already covered by `<Card>` / `<Button>` / `<Avatar>`.
3. Lifting it doesn't make the call site harder to read.

If it's only in one page, keep it inline — see the inline `BubbleCollage`,
`FeedCard`, `ChatPanel`, etc. They're tied to one page and don't need lifting.

### When to vary button sizes

Bubble.up deliberately mixes sizes for a playful look. A page's primary CTA is
`md` or `lg`; its secondary actions are `sm`; its tiny inline actions are `xs`.
Don't homogenize.

| Intent                              | Variant     | Size |
|-------------------------------------|-------------|------|
| Page-level "do the thing"           | `primary`   | `md` / `lg` |
| Form submit inline                  | `primary`   | `sm` |
| Cancel, dismiss                     | `ghost`     | `sm` |
| Leave, undo                         | `secondary` | `xs` |
| Pop, destructive                    | `danger`    | `sm` |

---

## 7. Typography

System UI stack. We don't ship a custom font. Sizes are Tailwind defaults —
mostly:

- Page heading: `text-2xl` / `text-3xl` `font-bold`
- Card heading: `text-base` / `text-lg` `font-semibold`
- Body: `text-sm`
- Caption / hint: `text-xs text-muted`
- UUIDs / emails / chat sender: `font-mono`, with `dir="ltr"` so they don't get
  reordered under RTL.

---

## 8. i18n & RTL

### Languages

English (`en`) and Hebrew (`he`). Lives in
[`src/i18n/en.json`](./src/i18n/en.json) and
[`src/i18n/he.json`](./src/i18n/he.json). Initialized once in
[`src/i18n/index.ts`](./src/i18n/index.ts).

Library: **`react-i18next`** (with `i18next`). Picked because it's the standard,
supports pluralization out of the box, and handles `returnObjects` for our
"Focus · Learn · Grow" arrays. **Don't bring in a second i18n library.**

### The store

User's choice persists in [`src/store/languageStore.ts`](./src/store/languageStore.ts)
(Zustand, key `bubbleup-lang`). It also applies `<html lang>` and `<html dir>`
on every change, which is what Tailwind's `rtl:` / `ltr:` variants read.

### Switching language

Sidebar bottom row has the language switcher — `<LanguageSwitcher>` inside
[`Sidebar.tsx`](./src/components/Sidebar.tsx). Calling
`useLanguageStore.getState().setLang('he')` is the only API:
1. tells i18next to change language,
2. sets `<html lang dir>`,
3. persists.

### Adding a string

1. Add the key under the relevant section in `en.json` **and** `he.json`. Keep
   keys flat or one level deep — don't nest 4 layers.
2. In the component, `const { t } = useTranslation()` and reference as
   `t('section.key')`.
3. For plurals use suffixes: `memberLabel_one` / `memberLabel_other`, then
   `t('groups.memberLabel', { count })`.
4. For arrays (like the focus/learn/grow words) use
   `t('login.collage.focusLearnGrow', { returnObjects: true }) as string[]`.

### Adding a language

1. Make a new JSON next to `en.json` / `he.json`.
2. Register it in [`src/i18n/index.ts`](./src/i18n/index.ts)'s `resources` map.
3. Add it to `SUPPORTED_LANGUAGES` with `code`, `label`, and `dir`.
4. The switcher and `dirOf()` pick it up automatically.

### RTL — use logical CSS properties

Hebrew is right-to-left. We use Tailwind's logical properties so one set of
classes works for both directions. **Don't write `ml-`/`mr-`/`pl-`/`pr-`/`left-`/`right-`/`text-left`/`text-right`/`border-l`/`border-r` for new code.**

| Don't write | Write instead |
|-------------|---------------|
| `ml-2`      | `ms-2`        |
| `mr-2`      | `me-2`        |
| `pl-4`      | `ps-4`        |
| `pr-4`      | `pe-4`        |
| `left-0`    | `start-0`     |
| `right-0`   | `end-0`       |
| `text-left` | `text-start`  |
| `text-right`| `text-end`    |
| `border-l`  | `border-s`    |
| `border-r`  | `border-e`    |

For things that should **stay** physically left/right regardless of direction
(an emoji that points "back in time", a chevron icon), keep the physical class
and add `rtl:rotate-180` to flip the visual.

For things that should **stay LTR** regardless of language (UUIDs, emails,
mathematical expressions), set `dir="ltr"` on the element. We do this for the
email field, the "Signed in" footer, the chat sender hash prefix, and the
add-member UUID input.

### Common RTL gotchas

- **Don't translate code symbols.** `Group`, `getGroups()`, `Bubble` (as a
  variable) stay English. Only user-visible strings get translated.
- **Date formatting**: pass `i18n.language` to `toLocaleDateString` if you want
  month/weekday names localized. We currently do this for `monthLabel` only;
  feed timestamps are intentionally short ("2h") so they don't need localization
  yet.
- **The chat send button (`➤`)** rotates 180° under RTL so it points toward
  the inline-end edge — that's where "send" lives in any direction.
- **Tooltips on collapsed sidebar** use `start-full ms-2` so the popout
  emerges on the correct side.

---

## 9. Brand voice — copy guidelines

- App name: **Bubble.up** (capital B, lowercase u, dot included). Never
  "Bubbleup", never "BubbleUp", never "Bubble Up".
- Use "**Bubble**" wherever you'd otherwise say "study group". "Pop" for delete,
  "Hop in" for join, "floated into" for joined-the-room.
- Don't over-cute. Cancel is **Cancel**. Save is **Save**. Email is **Email**.
  Reserve the playful vocabulary for high-visibility CTAs and empty states.
- Emoji used as accents:
  - 🫧 for joins / Bubble references / "you're caught up"
  - 👋 for leaves
  - 📅 for events, 📁 for files, 💬 for chat, 👥 for members, 📭 for empty
- No exclamation marks in error messages. The form is calm.

---

## 10. Pitfalls / open questions

- **Browser native `confirm()` / `alert()`** is still used for destructive
  actions and Help/Report placeholders. They render in OS chrome and ignore our
  theme. Replacing them with a `<Modal />` component is open work.
- **Date / time formatting** is partly localized (`monthLabel`) and partly
  Intl-default (`fmtRange`). When real product needs Hebrew-formatted
  date ranges in chat link cards, switch every `toLocaleString(undefined, ...)`
  call to `toLocaleString(i18n.language, ...)`.
- **Hebrew translations** were authored by Claude and may need a native
  speaker's pass — especially the bubble-metaphor adaptations ("Hop in" →
  "להצטרף", "Pop this Bubble" → "פוצץ").
- **No keyboard shortcut surface yet.** When we add one, the help row in the
  sidebar should open a real panel, not an `alert`.
- **Tailwind opacity modifiers (`bg-primary-500/40`)** don't work on our
  CSS-var-backed colors because Tailwind needs `<alpha-value>` in the var
  declaration. If you need alpha on a brand color, write it explicitly:
  `bg-[rgba(56,154,224,0.4)]`, or add a partly-transparent CSS var to
  `index.css`.
