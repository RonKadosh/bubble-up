# CLAUDE.md — StudySync

You are working in **StudySync** (also called StudyBuddy in source — same project). This file is loaded into every agent session in this repo. Read it before you change anything.

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
- Reach for chat message types as strings → backend `ChatMessageType` / frontend `ChatMessageType` (`TEXT | SYSTEM_JOIN | SYSTEM_LEAVE | LINK`); LINK targets use `ChatLinkTargetType` (currently only `CALENDAR_EVENT`). Adding a new linkable kind = new enum value on both sides + a new frontend render branch.
- `new axios(...)` / `axios.create(...)` on the frontend → `frontend/src/api/client.ts`
- `new Client(...)` from `@stomp/stompjs` or `new WebSocket(...)` on the frontend → `frontend/src/api/ws.ts`
- Touch `localStorage` for auth state → `frontend/src/store/authStore.ts` (Zustand, persisted)
- Add a new state library or context provider → Zustand is the chosen library; add a store under `frontend/src/store/`

**If you catch yourself doing any of these, stop and grep `common/` (backend) or `src/api/` (frontend) first.** The rule is "find before write."

If the helper truly doesn't exist and the same pattern will show up in two features, add it to `common/`. If it's one-off, keep it in the feature.

---

## Stack

- **Backend**: Java 21, Spring Boot 3, Spring Security, JPA + Hibernate, Postgres 16, JWT, STOMP/SockJS WebSocket, Maven, Lombok.
- **Frontend**: React 18, TypeScript (strict), Vite, Tailwind, react-router-dom, axios, Zustand (state), @stomp/stompjs (live WebSocket).
- **Infra**: Postgres in Docker. `docker-compose up` runs the whole stack (db + backend on 8080 + frontend on 3000).

## Repo layout

```
StudySync/
├── CLAUDE.md                  ← you are here (always loaded)
├── docker-compose.yml         ← postgres + backend + frontend
├── backend/
│   ├── CLAUDE.md              ← loaded when working under backend/
│   ├── pom.xml
│   └── src/main/java/com/ronkadosh/studybuddy/
│       ├── StudyBuddyApplication.java
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

## Doc policy

Conventions live in **CLAUDE.md files**, not scattered `*-guide.md` files. If you want to document a convention, edit the relevant CLAUDE.md. Do not create new top-level `.md` docs unless explicitly asked.
