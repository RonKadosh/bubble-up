# Onboarding — Bubble.up

Welcome Gal & Amit. This file is the 5-minute orientation. The real rules live in [CLAUDE.md](CLAUDE.md), [backend/CLAUDE.md](backend/CLAUDE.md), and [frontend/CLAUDE.md](frontend/CLAUDE.md) — and they are loaded into every Claude / agent session automatically. **Read them before you touch code.** Everything below is a map to that material, plus state-of-the-world.

---

## TL;DR for humans

- **Stack**: Spring Boot 3 / Java 21 / Postgres on the backend, React 18 + Vite + TypeScript (strict) + Tailwind + Zustand + STOMP on the frontend.
- **Run it**: `docker-compose up` from repo root → frontend at `http://localhost:3000`, backend at `:8080`, Postgres at `:5432`.
- **Demo login** (when `APP_DEMO_SEED_ENABLED=true`, default in `docker-compose.yml`): `alice@bubble.up` … `jack@bubble.up`, password `Passw0rd!`. Seeded on first boot, split across 3 Bubbles.
- **Env**: copy `.env.example` → `.env` (gitignored). For Bubble Room video you'll need JaaS (Jitsi) creds — leave blank until needed; rooms fail with `JITSI_NOT_CONFIGURED` until set.

---

## TL;DR for your agents

Tell Claude (or any agent) to read [CLAUDE.md](CLAUDE.md) first. The root file embeds a 15-step "build a feature end-to-end" checklist; the per-side files embed the recipes. **The one rule**: find before write — almost every helper your instinct says to invent already exists in `common/` (backend) or `src/api/` (frontend). The anti-pattern tables at the bottom of each CLAUDE.md are the single best way to catch yourself.

---

## What's in the repo right now

Java package is `com.ronkadosh.bubbleup.*`. Frontend bundle is `bubbleup`. The product is **Bubble.up** (study groups = "Bubbles"). The original StudyBuddy/StudySync name has been fully removed — don't reintroduce it. `StudyGroup` JPA entity + `Group`/`getGroups()` code symbols are deliberately preserved.

### Backend feature modules (`backend/src/main/java/com/ronkadosh/bubbleup/`)

| Module | What it does |
|---|---|
| `common/` | Shared infra — `ApiResponse`, `ApiPaths`, `AppException` + `ErrorCode`, `CurrentUserProvider`, `TimeProvider`, `WebSocketPublisher`, `FileStorageService`, `*Properties`, JWT + Security wiring. Features depend on this; never the reverse. |
| `auth/` | Register / login / refresh / logout, user profiles, JWT-signed tokens, refresh-token rotation. |
| `catalog/` | University → Department → Course (with explicit M:N join). Seeded by `CatalogSeedConfig` — replace the default `defaultCatalogSeedData()` for real data. |
| `groups/` | Bubbles (study groups), membership, group files (≤25 MB), file ACLs. `createGroup` auto-creates a "general" chat room. |
| `chat/` | Live messaging via STOMP over `/ws`. Message types: `TEXT \| SYSTEM_JOIN \| SYSTEM_LEAVE \| LINK \| SYSTEM_ROOM_END_SOON \| SYSTEM_ROOM_EXTENDED`. LINK targets: `CALENDAR_EVENT \| POLL`. |
| `calendar/` | Group-scoped events (USER-scope accepted but no UI yet). No recurrence. |
| `room/` | "Bubble Room" — scheduled live sessions inside a Bubble with bento layout (video, chat, whiteboard). Video via JaaS (Jitsi). |
| `expert/` | Expert directory, public profiles, onboarding/verification flow, paid booking + scheduled expert sessions. Admin-gated verification. |
| `enrollment/` | User ↔ course enrollment surface used by Academy / matching. |
| `matching/` | Course-based suggestions of Bubbles / experts. |
| `admin/` | Admin panels: users, groups, catalog, expert verification, quiz, overview. Role-gated (`UserRole.ADMIN`). |
| `bootstrap/seed/` | `DemoSeeder` — 10 demo users + 3 Bubbles on first boot, idempotent, off via `APP_DEMO_SEED_ENABLED=false`. |

Every feature follows the same shape: `model/` → `persistence/` → `application/` → `api/` (+ `dto/`) → `internal/` (interface in `internal/`, impl in `application/`). Cross-module calls go **only** through `internal/<Feature>InternalService` — never reach into another module's repository.

### Frontend (`frontend/src/`)

| Path | Purpose |
|---|---|
| `api/client.ts` | The **only** axios instance. Adds `/api` baseURL + `Authorization: Bearer …`. Single-flight 401 → `/auth/refresh` retry. Never `import axios from 'axios'` in a page. |
| `api/ws.ts` | The **only** STOMP client. `subscribeToRoom(...)` / `connectWs()` / `disconnectWs()`. Lifecycle owned by `App.tsx`. |
| `api/<feature>.ts` | One file per feature. Typed request/response, returns unwrapped `data`. |
| `api/errors.ts` | `describeError(e, t, { CODE: 'i18n.key' }, fallback)` — use this, don't hand-roll `e?.response?.data?.error?.code`. |
| `store/` | Zustand. `authStore` (persisted as `bubbleup-auth-v2`), `themeStore`, `languageStore`, `viewportStore`, `activeRoomStore`, `roomLayoutStore`, `bentoLayoutStore`. One concern per store. No Context / Redux. |
| `pages/` | One file per route. `App.tsx` registers routes + `RequireAuth` / `RequireExpert` / `RequireAdmin` guards. |
| `pages/groups/` | The Bubble hub panels (`ChatPanel`, `CalendarPanel`, `FilesPanel`, `MembersStrip`, …). Live here, **not** in `components/`. |
| `pages/room/` | Bubble Room bento shell (`RoomBentoShell`, `VideoPanel`, `WhiteboardPanel`, `RoomChatPanel`, …). |
| `pages/admin/` | Admin tabs. |
| `pages/expert/` | Expert onboarding, dashboard, booking requests. |
| `components/` | Shared UI only: `Button`, `IconButton`, `LinkButton`, `Card`, `Avatar`, `BentoCell`, `Sidebar`, `Icons`, `PersistentVideo`, `QuizPrompt`, `BubbleEmojis`. **Use these — don't `<button className="…">`.** |
| `i18n/en.json`, `i18n/he.json` | **Every** user-facing string lives in both. Hebrew is RTL — use logical Tailwind props (`ms-…`, `pe-…`). |
| `index.css` | Design tokens: silver/light-blue gradient surfaces, iridescent bubble rings (`ring-iridescent`), `bubble-pop` hover, dark mode via `<html class="dark">`. Design language doc: `frontend/design-doc.md`. |

### Routes (`App.tsx`)

`/login` · `/dashboard` · `/groups` (the Bubble hub) · `/academy` · `/courses/:id` · `/profile[/:userId]` · `/rooms/:roomId` · `/sessions/:sessionId` · `/become-expert` · `/experts[/:userId]` · `/expert*` (EXPERT-gated) · `/bookings` · `/admin[/:tab]` (ADMIN-gated).

---

## How to work here — the short version

The detailed checklist is in [CLAUDE.md](CLAUDE.md) §"Building a feature end-to-end". The condensed version:

1. **Plan the DTO contract first.** `<Feature>Request` / `<Feature>Response` shapes both sides. Reshaping later is the most expensive thing.
2. **Backend build order**: `model/` → `persistence/` → `application/` → `api/` → `internal/`. **Frontend build order**: `api/<feature>.ts` → page → route.
3. **Controllers do 4 things only**: `ApiPaths` constant, `@Valid`, `currentUserProvider.get()`, delegate to a service and wrap with `ApiResponse.success(...)`. No `try/catch`, no `ResponseEntity.status(...)`.
4. **Errors are typed**. `throw new AppException(ErrorCode.X)` — `GlobalExceptionHandler` does the rest. Add a new `ErrorCode` entry for any new failure mode.
5. **No raw entities in or out of controllers.** Records for input, records (+ `from(entity)` factory) for output.
6. **Cross-module calls go via `internal/`**, not `Repository`.
7. **Time + identity are injected.** `timeProvider.now()`, `currentUserProvider.get()`. Never `Instant.now()`, never `SecurityContextHolder`.
8. **Config is typed.** `@ConfigurationProperties` record in `common/config/`. Never `@Value("${...}")` in feature code.
9. **WebSocket**: backend → `WebSocketPublisher` + `WebSocketDestination` constants. Frontend → `subscribeToRoom(...)`. Never construct a STOMP client yourself.
10. **Every string in i18n** — keys added to **both** `en.json` and `he.json`.
11. **Reuse the components.** `<Button variant="…">`, `<IconButton>`, `<Card>`, `<Avatar>`, `<BentoCell>` — extend variants rather than forking inline.
12. **Don't add dependencies.** Zustand for state, react-i18next for i18n, axios for HTTP, @stomp/stompjs for WS, Tailwind for styling, react-pdf for PDF, @excalidraw/excalidraw for whiteboard. If you want to add anything else, ask.
13. **Don't add new top-level folders.** No `hooks/`, `utils/`, `types/`, `models/`, `services/`.

### Sanity-check before declaring done

```bash
cd backend  && mvn -DskipTests clean compile     # backend compiles
cd backend  && mvn test                          # ~35+ tests, ~20s, H2 in PG-compat mode (no Docker)
cd frontend && npm run build                     # tsc strict + vite build
```

For UI changes, also run the app (`docker-compose up`) and use the feature in a browser — type-checking confirms it compiles, only the browser confirms it works.

---

## Known gaps — don't "fix" these silently

These are documented intentional gaps. Each side's CLAUDE.md has the full list (`backend/CLAUDE.md` §"Known gaps", `frontend/CLAUDE.md` §"Known gaps"). The highlights:

**Backend**
- Tests run on H2 in Postgres-compat mode, not real Postgres. PG-specific behavior (partial unique indexes, JSONB, advisory locks) is untested. Testcontainers migration is a separate task.
- `ChatBroadcastIT` is `@Disabled` — MockMvc + STOMP delivery in-process doesn't work; broadcast verified by manual two-browser-tab walkthrough.
- Chat unread count is N+1 per room. Fine at hub scale; revisit if rooms/user grows.
- No DB migrations tool (Flyway / Liquibase). Schema lives in JPA + dev/test relies on `ddl-auto`. Prod changes need manual ALTERs — see backend gaps for the current outstanding list.
- No scheduled cleanup of expired refresh tokens.
- Calendar: no recurrence; USER-scope events are accepted but have no UI entry.
- No user search endpoint (`GET /api/users?email=`) — owner adds members by pasting a UUID.

**Frontend**
- No environment config — `client.ts` baseURL and Vite proxy are hard-coded for dev. Add `.env` + `import.meta.env` before any non-localhost deploy.
- No frontend tests (`npm test` doesn't exist). If you change behavior, say so explicitly in your PR.
- Hub chat uses the auto-created "general" room only. Multi-room UX would mean a room dropdown in `ChatPanel`.
- No calendar grid view — month list only.
- Destructive actions use native `confirm()`/`alert()` — a real `<Modal>` component is open work.
- Hebrew strings need a native speaker review pass.
- No "open in tab" deep link to a specific group (`/groups/:id`).

**Doc / build hygiene**
- `frontend/postcss.config.js` was ESM-only without `"type": "module"`; renamed to `.cjs` (already done — keep it that way).

If you intentionally close one of these gaps, delete it from the relevant CLAUDE.md in the same PR.

---

## Conventions for collaboration

- **Commits**: small, focused, with a one-line "why". Match the existing voice in `git log`.
- **PRs**: one feature/fix per PR. Run the sanity-check commands above. If you touch UI, note that you ran it in the browser.
- **CLAUDE.md is the source of truth for conventions.** If you change a convention, update the relevant CLAUDE.md in the same PR. **Don't create new top-level `*.md` docs** for conventions — they belong in CLAUDE.md.
- **Agents**: any Claude / coding agent in this repo will read CLAUDE.md automatically. Trust it; the anti-pattern tables are the fastest correctness check.

Welcome aboard.
