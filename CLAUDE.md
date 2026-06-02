# CLAUDE.md — Bubble.up

You are working in **Bubble.up** (the Java package and source symbols use `bubbleup` — same project). This file is loaded into every agent session in this repo. Read it before you change anything.

---

## The one rule

**Before writing new code, look for existing shared code first.**

Most "I'll just write a quick helper" instincts in this repo are wrong — the helper already exists. Specifically, before you:

- Build an HTTP response envelope by hand → `common/api/ApiResponse`
- Throw a status-coded exception → `common/error/AppException` + `ErrorCode`
- Read `SecurityContextHolder` → `common/context/CurrentUserProvider`
- Call `Instant.now()` / `LocalDateTime.now()` → `common/datetime/TimeProvider`
- Hand-roll page math → `common/pagination/PageMapper`
- Build a JPA `Specification` from scratch → `common/filtering/SpecificationBuilder`
- Touch the filesystem → `common/file/FileStorageService`
- Use `SimpMessagingTemplate` → `common/websocket/WebSocketPublisher`
- Read `@Value("${...}")` → `common/config/*Properties`
- Hard-code `/api/...` paths → `common/api/ApiPaths`
- Reach for chat message types as strings → backend `ChatMessageType` / frontend `ChatMessageType` (`TEXT | SYSTEM_JOIN | SYSTEM_LEAVE | LINK`); LINK targets use `ChatLinkTargetType` (`CALENDAR_EVENT | POLL`). Adding a new linkable kind = new enum value on both sides + a new frontend render branch.
- `new axios(...)` / `axios.create(...)` on the frontend → `frontend/src/api/client.ts`
- `new Client(...)` from `@stomp/stompjs` or `new WebSocket(...)` on the frontend → `frontend/src/api/ws.ts`
- Touch `localStorage` for auth state → `frontend/src/store/authStore.ts` (Zustand, persisted)
- Add a new state library or context provider → Zustand is the chosen library; add a store under `frontend/src/store/`

**If you catch yourself doing any of these, stop and grep `common/` (backend) or `src/api/` (frontend) first.** The rule is "find before write."

If the helper truly doesn't exist and the same pattern will show up in two features, add it to `common/`. If it's one-off, keep it in the feature.

---

## Stack

- **Backend**: Java 21, Spring Boot 3, Spring Security, JPA + Hibernate, Postgres 16, JWT, STOMP over raw WebSocket, Maven, Lombok.
- **Frontend**: React 18, TypeScript (strict), Vite, Tailwind, react-router-dom, axios, Zustand (state), @stomp/stompjs (live WebSocket).
- **Infra**: Postgres in Docker. `docker-compose up` runs the whole stack (db + backend on 8080 + frontend on 3000).

## Repo layout

```
bubble-up/
├── CLAUDE.md                  ← you are here (always loaded)
├── docker-compose.yml         ← postgres + backend + frontend
├── backend/
│   ├── CLAUDE.md              ← loaded when working under backend/
│   ├── pom.xml
│   └── src/main/java/com/ronkadosh/bubbleup/
│       ├── BubbleUpApplication.java
│       ├── common/            ← shared infrastructure (do NOT re-invent)
│       ├── auth/              ← feature module
│       ├── groups/            ← feature module
│       └── chat/              ← feature module
└── frontend/
    ├── CLAUDE.md              ← loaded when working under frontend/
    ├── package.json
    └── src/
        ├── api/               ← axios client + per-feature API + ws.ts (STOMP client)
        ├── store/             ← Zustand stores (auth, …)
        ├── pages/             ← route-level components
        ├── components/        ← shared UI
        ├── App.tsx            ← router + auth guard + WS lifecycle
        └── main.tsx
```

`backend/CLAUDE.md` and `frontend/CLAUDE.md` carry the detailed rules for their side. This file is repo-wide only.

## Module boundaries

- **`common/` (backend) and `src/api/` (frontend) are infrastructure layers.** Features depend on them. They never depend on a feature.
- **Backend features (`auth/`, `groups/`, `chat/`) talk to each other only via `internal/` interfaces.** See `backend/CLAUDE.md`.
- **Don't create new top-level folders** without explicit reason. Add a feature module, not a new layer.

## Running the app

```bash
docker-compose up                    # full stack (recommended)
cd backend && mvn spring-boot:run    # backend only (needs postgres running)
cd frontend && npm run dev           # frontend dev server on :3000, proxies /api to :8080
cd backend && mvn -DskipTests clean compile   # quick build sanity-check
```

## Building a feature end-to-end (full-stack checklist)

These are the things that already exist in this repo but quietly get skipped when a new feature lands fast. Read them before adding endpoints + a page. The detailed mechanics live in [backend/CLAUDE.md](backend/CLAUDE.md) and [frontend/CLAUDE.md](frontend/CLAUDE.md); this list is the workflow they sit inside.

### 1. Plan the contract first, then build bottom-up

Write the request and response DTOs on paper (or in this chat) before you touch a file. The shape of `<Feature>Request` / `<Feature>Response` is the contract — both sides depend on it, and reshaping it later is the most expensive thing you can do.

Backend build order: `model/` → `persistence/` → `application/` → `api/` → `internal/`. Frontend build order: `api/<feature>.ts` (types + functions) → page or panel → register the route or tab. Don't start in the controller and don't start in JSX — both sides flow from the DTO.

### 2. Controllers are a contract, not a place for logic

A controller does four things and nothing else:

1. Declare the path via an `ApiPaths` constant on `@RequestMapping` — never a string literal.
2. Validate with `@Valid` on the request record.
3. Pull the caller via `currentUserProvider.get()` (never `SecurityContextHolder`).
4. Delegate to `<Feature>CommandService` / `<Feature>QueryService` and wrap the result in `ApiResponse.success(...)`.

If you find an `if (...) throw` chain, a `try/catch`, a `Map.of("error", ...)`, or a `ResponseEntity.status(...)` in a controller, move it. The controller is the contract; the service does the work.

### 3. Only request DTOs in, only response DTOs out

Never accept or return a JPA entity from a controller. Records (`record CreateGroupRequest(...)`) for input, records (`record GroupResponse(...)`) for output, with a `from(entity)` static factory or a mapper class in `api/mapper/`. Entities carry hibernate lazy proxies, leak internal columns, and tie your wire format to your schema. The DTO is the wire format; the entity is private to the module.

Same on the frontend: each `src/api/<feature>.ts` declares its own typed request/response shapes alongside the function that uses them. Don't reuse a backend type by inference — declare it.

### 4. Throw `AppException`, let `GlobalExceptionHandler` do the rest

Every error flows through one path: throw `new AppException(ErrorCode.X)` (optionally with a message override). `GlobalExceptionHandler` in `common/error/` maps it to the right HTTP status, the right category, and the standard envelope. You don't write try/catch to repackage, you don't return `ResponseEntity.status(...)`, you don't throw `ResponseStatusException`. New error condition? Add an `ErrorCode` entry with its `ErrorCategory` + `HttpStatus` and throw it — that's the whole change. `@Valid` failures and unknown exceptions are already handled; don't catch them again.

### 5. `common/` before anything else — find before write

Before you write infrastructure code, grep `common/`. The full inventory is in [backend/CLAUDE.md](backend/CLAUDE.md) — `ApiResponse`, `ApiPaths`, `AppException`, `CurrentUserProvider`, `TimeProvider`, `PageMapper`, `SpecificationBuilder`, `FileStorageService`, `WebSocketPublisher`, `*Properties` records, JWT + Security wiring. If you reach for `Instant.now()`, `@Value("${...}")`, `SecurityContextHolder`, `Files.write(...)`, `SimpMessagingTemplate`, or a hand-built error map, you skipped this step.

Promotion rule: keep helpers in the feature module until a second feature genuinely needs them. `common/` is for stable, infrastructural, multi-feature pieces — not for "I might reuse this someday."

### 6. Cross-module calls go through `internal/`, not repositories

A feature module's `api/`, `application/`, `model/`, `persistence/` are private. Outsiders see only `internal/<Feature>InternalService`. If you're in `chat/` and you want a group's members, inject `GroupInternalService` — never `GroupRepository`. The internal service returns purpose-built summaries (`GroupMembershipSummary`), not raw entities. This is what keeps modules from fusing.

### 7. Frontend: reuse the components that already exist

Before you write `<button className="...">`, look in `src/components/`: `Button` (variants: primary / secondary / ghost / danger / cell, sizes xs–lg), `IconButton`, `LinkButton`, `Card`, `Avatar`, `BentoCell`, `Sidebar`, `Icons`. They encode the design language (pill shape, `bubble-pop` hover, brand gradient, surface tokens) — re-implementing them by hand produces inconsistent UI and locks you out of theme changes. If the variant you want doesn't exist, add it to the component, don't fork it inline.

Co-location rule: hub-specific panels (chat / calendar / files / members) live under `src/pages/groups/`, not `src/components/`. `components/` is for genuinely cross-page reuse only.

### 8. Frontend state: pick the right scope

- **`useState`** — state that belongs to one component and dies with it. The default.
- **Lift to a parent page** — state shared by sibling panels within one page (e.g. `GroupsPage` hoists `rooms` so the sidebar badge and chat panel agree).
- **Zustand store in `src/store/`** — state shared across pages, or state that must survive remount. Auth, theme, language, viewport, layout prefs.

Stores already in use: `authStore` (persisted, token + user), `themeStore`, `languageStore`, `viewportStore`, `roomLayoutStore`, `bentoLayoutStore`, `activeRoomStore`. Before adding a new store, check whether the state really crosses pages — if only one page reads it, keep it in `useState`. When you do add one: one concern per store, granular selectors (`useStore((s) => s.field)`, never `(s) => s`), `persist` only for state that must survive reload (never server data).

No Context providers, no Redux, no Jotai — Zustand is the chosen library.

### 9. Every user-facing string goes through i18n

The app ships English and Hebrew (`frontend/src/i18n/en.json`, `he.json`). Hebrew is RTL, so layout that hard-codes `ml-2` / `pr-4` breaks under `dir="rtl"` — prefer logical properties (`ms-2`, `pe-4`) or test in both directions. Workflow for any string:

1. Add the key to **both** `en.json` and `he.json` under the right namespace — never to just one.
2. In the component, `const { t } = useTranslation()` and render `{t('namespace.key')}`.
3. For error messages, use `describeError(e, t, { CODE: 'feature.error.key' }, 'feature.error.fallback')` from `src/api/errors.ts` — don't hand-roll `e?.response?.data?.error?.code` chains.

If you're writing literal English in JSX, you skipped this step. The exception is developer-facing strings (console.error, dev-only UI) — those stay English.

### 10. Don't add dependencies

Before `npm install <x>` or `mvn` adding a dependency, check what's already in `package.json` / `pom.xml`. Installed and load-bearing: Zustand for state, `react-i18next` for i18n, `@stomp/stompjs` for WebSocket, axios for HTTP, Tailwind for styling, `react-pdf` for PDF, `@excalidraw/excalidraw` for whiteboard. Backend: Spring Boot 3, JPA, Lombok, JWT. If you want a UI kit, a CSS-in-JS lib, SockJS, Redux, a date library, a form library — stop. Either it's already covered (Tailwind covers styling, Zustand covers state) or the answer is "ask first."

New dependency = new attack surface, new bundle weight, new upgrade burden. The bar is high.

### 11. Time and identity are injected, never read directly

Backend: `timeProvider.now()` (not `Instant.now()`), `currentUserProvider.get()` (not `SecurityContextHolder`). Both are swappable for tests and simulation. Frontend: `useAuthStore((s) => s.user)` for identity (never decode the JWT client-side), and don't reach for `new Date()` in business-meaningful places — use the helpers in `src/pages/groups/calendarFormat.ts` for consistency.

### 12. WebSocket: one client, one publisher, named destinations

Backend: inject `WebSocketPublisher`, publish to a `WebSocketDestination` constant — never construct a `SimpMessagingTemplate`, never sprinkle string topics. New destination = add to `WebSocketDestination`. Per-destination SUBSCRIBE auth = a `WsChannelInterceptor` bean in your feature module (see `ChatTopicSubscribeInterceptor`). Frontend: `subscribeToRoom(...)` from `src/api/ws.ts` — never construct a STOMP `Client` yourself. `App.tsx` owns connect/disconnect via the auth-store subscription; pages just subscribe and return the unsubscribe from their effect.

### 13. Configure via typed properties, not `@Value` or env literals

New config? Add a `@ConfigurationProperties(prefix = "app.x")` record in `common/config/`, bind from `application.yml`, env-overridable via `docker-compose.yml`. Never `@Value("${...}")` in feature code. Frontend has no environment config yet — `client.ts` baseURL and Vite proxy are dev-hard-coded; flag that gap if you need to deploy.

### 14. Don't add new top-level folders

Frontend has `api/`, `store/`, `pages/`, `components/`, `i18n/`. Backend has `common/` and feature modules. That's it. No `hooks/`, `utils/`, `types/`, `models/`, `services/` until there's real reuse demand — and even then, the right answer is usually "co-locate with the feature" or "add to `common/`." New folder = new convention to remember.

### 15. Sanity-check the build before declaring done

```bash
cd backend && mvn -DskipTests clean compile    # backend compiles
cd frontend && npm run build                   # tsc (strict) + vite build
cd backend && mvn test                         # ~32 tests, ~20s, no Docker
```

For UI changes, also run the app (`docker-compose up`) and use the feature in a browser. Type-checking confirms it compiles; only the browser confirms it works.

### Anti-pattern quick scan

If your diff contains any of these, you skipped a step above:

- Backend: `ResponseEntity.status(...)`, `ResponseStatusException`, `SecurityContextHolder`, `Instant.now()`, `@Value("${...}")`, `Files.write(...)`, `"/api/..."` string literal, `try/catch` repackaging an exception, a JPA entity in a controller signature, a feature importing another feature's `Repository`.
- Frontend: `import axios from 'axios'` in a page, `localStorage.setItem` for auth, `createContext` for shared state, a `<button className="...">` instead of `<Button>`, an English string in JSX without `t(...)`, a key added to `en.json` but not `he.json`, a new top-level folder, a new dependency.

---

## Doc policy

Conventions live in **CLAUDE.md files**, not scattered `*-guide.md` files. If you want to document a convention, edit the relevant CLAUDE.md. Do not create new top-level `.md` docs unless explicitly asked.
