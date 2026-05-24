# Handoff — picking up where we left off

Quick context note for the next agent session (likely on a different machine).
Read [`CLAUDE.md`](./CLAUDE.md), [`backend/CLAUDE.md`](./backend/CLAUDE.md),
and [`frontend/CLAUDE.md`](./frontend/CLAUDE.md) for the actual rules — this
file is just a snapshot of recent work + the current goal.

---

## What just landed

Recent session work, all on `main`:

- **Bubble.up rebrand** — full visual redesign. Light-blue → silver gradient
  palette, very round shapes, soft "bubble pop" hover motion, dark mode via
  `<html class="dark">`. Study groups are called **Bubbles** in all UI copy
  (code symbols like `Group` / `getGroups()` are unchanged).
- **Design system** — see [`frontend/design-doc.md`](./frontend/design-doc.md).
  Tokens live in [`frontend/src/index.css`](./frontend/src/index.css); Tailwind
  is configured to map them.
- **Reusable components** under [`frontend/src/components/`](./frontend/src/components/):
  - `Avatar`, `Button` / `LinkButton` / `IconButton`, `Card`, `Icons` (centralized SVG set)
  - `Sidebar.tsx` is also the `<Layout>` wrapper for protected routes.
- **Pages**: `LoginPage` (bubble-collage hero), `DashboardPage` (activity feed),
  `GroupsPage` (the hub — Bubble list + 4-tab panel).
- **i18n + RTL** — `react-i18next`, English + Hebrew. Bundles in
  [`frontend/src/i18n/`](./frontend/src/i18n/), persisted via
  `useLanguageStore`. Direction-sensitive classes use Tailwind logical
  properties (`ms`/`me`/`start`/`end`/`text-start`). Language switcher is in
  the sidebar bottom row.
- **Theme store** — `useThemeStore` toggles light/dark; also in the sidebar.
- **Repo init** — pushed to `RonKadosh/bubble-up`. `.gitignore` covers Node /
  Maven / IDE noise. `application.yml` and `docker-compose.yml` ship
  dev-default secrets (JWT, DB) that must be overridden before any real
  deploy.

---

## Current goal

**Perfect the single-Bubble UX** — i.e. polish what happens inside one Bubble
once you click it from the sidebar. That's the 4-tab hub inside
[`frontend/src/pages/GroupsPage.tsx`](./frontend/src/pages/GroupsPage.tsx):

1. **Chat tab** — `ChatPanel`. Live WebSocket via
   [`frontend/src/api/ws.ts`](./frontend/src/api/ws.ts). Backend in
   [`backend/src/main/java/com/ronkadosh/studybuddy/chat/`](./backend/src/main/java/com/ronkadosh/studybuddy/chat/).
2. **Calendar tab** — `CalendarPanel`. List view per month (no grid yet).
3. **Files tab** — `FilesPanel`. Upload up to 25 MB, browser blocklist,
   download/delete.

(Members tab works fine — not in scope for "perfecting".)

### Things already known to be missing or rough (from CLAUDE.md "gaps")

Pick from these if useful, but follow the user's direction first:

- **No calendar grid view** — only list per month. Week/day grids are open work.
- **Chat only uses the auto-created "general" room.** Backend supports more
  rooms per Bubble but UI has no room dropdown yet.
- **"Add member by UUID" UX** is paste-a-UUID. No user search endpoint yet
  (`GET /api/users?email=` would unblock autocomplete).
- **Native `confirm()` / `alert()`** for destructive actions and Help/Report —
  flagged in the design doc; a real `<Modal>` component is open work.
- **Hebrew strings** were authored by Claude; native speaker pass advisable
  (especially the bubble-metaphor adaptations like "Pop this Bubble" → "פוצץ").
- **Chat unread count is N+1** per room server-side — see backend gap notes.
- **`postcss.config.js`** is ESM-only without `"type": "module"` in
  `package.json`, so `npm run build` fails at the vite step. `npm run dev`
  works fine. Renaming to `.cjs` or adding `"type": "module"` would fix it.

### Where to start

```bash
git pull                                  # grab any newer commits first
docker-compose up                         # full stack on :3000 + :8080 + postgres
# or, in two terminals:
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

Open <http://localhost:3000>, sign up, create a Bubble, click into it, and
start iterating on whichever tab the user names.
