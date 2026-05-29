# frontend/CLAUDE.md

Loaded when you work under `frontend/`. Read [root CLAUDE.md](../CLAUDE.md) first if you haven't.

React 18 + TypeScript (strict) + Vite + Tailwind + react-router-dom + axios + **Zustand** for shared state + **@stomp/stompjs** for live WebSocket. Axios client has a **single-flight 401 → /auth/refresh** interceptor. No UI kit, no CSS-in-JS.

---

## Find before write

Before you reach for something new, check what already exists:

- HTTP call → import from `src/api/<feature>.ts` (or add one). **Don't** call `axios` directly.
- Live WebSocket → `subscribeToRoom` / `connectWs` / `disconnectWs` in `src/api/ws.ts`. **Don't** construct a STOMP client yourself.
- Auth state (token, current user) → `useAuthStore` in `src/store/authStore.ts`. **Don't** touch `localStorage` directly.
- Route guard → `RequireAuth` in `src/App.tsx`. Wrap protected routes with it.
- Cross-page state → add a Zustand store in `src/store/`. Don't add a Context provider, don't reach for Redux/Jotai/etc.
- Styling → Tailwind utility classes. Don't add a CSS file, styled-components, or a UI library.
- Routing → `react-router-dom`. All routes live in `App.tsx`.
- Chat / Calendar / Files UI → **already inside the GroupsPage hub as tab panels.** Don't add separate `/chat` or `/calendar` routes — the hub is the only entry point.

---

## Folder shape

```
frontend/src/
├── api/
│   ├── client.ts          ← single axios instance (baseURL /api + Bearer header from authStore)
│   ├── ws.ts              ← single STOMP client (broker URL /ws + Bearer header from authStore)
│   ├── auth.ts            ← login, register, refresh, logout
│   ├── groups.ts          ← CRUD + membership + visibility + transfer
│   ├── chat.ts            ← REST + ChatMessage type (re-used by ws.ts)
│   ├── files.ts           ← upload/list/download/delete group files
│   └── calendar.ts        ← CRUD calendar events (GROUP-owned)
├── store/
│   └── authStore.ts       ← Zustand: { accessToken, refreshToken, user }, persisted as bubbleup-auth-v2
├── pages/                 ← route-level components, one per route
│   ├── LoginPage.tsx
│   ├── GroupsPage.tsx     ← the hub: orchestrator only — state + handlers + layout
│   └── groups/            ← panel components owned by GroupsPage (not generic UI)
│       ├── GroupSidebar.tsx, GroupHeader.tsx
│       ├── ChatPanel.tsx (+ nested ChatMessageRow / CalendarLinkCard / LinkPickerModal)
│       ├── CalendarPanel.tsx, FilesPanel.tsx, MembersPanel.tsx
│       └── calendarFormat.ts   ← shared date/badge helpers for Chat link cards + Calendar
├── components/            ← shared UI used by multiple pages
│   └── Navbar.tsx         ← Groups link + Logout (no Chat/Calendar links — only the hub)
├── App.tsx                ← <BrowserRouter> + /login + /groups + RequireAuth + WS lifecycle
├── main.tsx               ← ReactDOM root
└── index.css              ← Tailwind directives only
```

Rules:
- **One file per feature in `api/`.** Each file exports typed functions and any DTO types that file needs.
- **One store per concern in `store/`.** `authStore.ts` for auth. Add `<feature>Store.ts` only when state is shared across pages — single-page state stays in `useState`.
- **One page per route in `pages/`.** Page components own their own local state and call `api/<feature>.ts` functions.
- **Group sub-features (chat/calendar/files/members + sidebar/header) live in `pages/groups/`** as components owned by the GroupsPage hub. They are not separate pages and not under `components/` (which is for generic, multi-page UI). `GroupsPage.tsx` itself is a thin orchestrator (~270 lines) that owns the state and handlers and composes the panels. New hub-specific UI goes here, NOT in `components/`.
- **`components/` is for reuse.** If only one page uses it, keep it inline or co-located.
- **No new top-level folders** without good reason. No `hooks/`, `utils/`, `types/` until there's real reuse demand.

---

## The axios client contract

`src/api/client.ts` is the only place that constructs axios. It:
- sets `baseURL: '/api'` (Vite proxies `/api` → `http://localhost:8080`),
- injects `Authorization: Bearer <token>` by reading `useAuthStore.getState().token` on every request.

It uses `getState()` (not the React hook) because axios runs outside React. That's intentional — don't try to "fix" it with a hook.

**Always import the default export:**
```ts
import client from './client'

export async function getGroups(): Promise<Group[]> {
  const res = await client.get<{ success: boolean; data: Group[] }>('/groups')
  return res.data.data
}
```

**Never:**
```ts
import axios from 'axios'                       // wrong — no auth header, no baseURL
const res = await axios.get('http://localhost:8080/api/groups')   // wrong — bypasses proxy + auth
```

---

## Backend response envelope

Every backend endpoint returns:
```ts
{ success: true,  data: T }                      // success
{ success: false, error: {                       // failure
    code: string,
    message: string,
    category: string,
    fields?: { field: string, message: string }[]   // present only for VALIDATION_ERROR
  }
}
```

In API functions, peel `res.data.data` and let errors throw (axios throws on non-2xx). Pages handle them with `try/catch`. See `src/api/groups.ts` for the pattern.

The shape is stable — don't try to "normalize" it per call. If you find yourself writing the `{ success, data }` type three times, see "Gaps" below.

---

## State management (Zustand)

Zustand is the only state library. One store per concern, in `src/store/`. Stores are plain TypeScript modules — import the hook and read what you need.

**The auth store** (`src/store/authStore.ts`) — token + current user, persisted to localStorage via the `persist` middleware under the key `bubbleup-auth-v2`:

```ts
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      clearAuth: () => set({ token: null, user: null }),
    }),
    { name: 'bubbleup-auth-v2' }
  )
)
```

**Reading in a component** (reactive — re-renders on change):
```ts
const token = useAuthStore((s) => s.token)
const user  = useAuthStore((s) => s.user)
```
Select one field at a time. Don't return whole objects from the selector unless you also pass a custom equality fn — you'll churn renders.

**Calling actions**:
```ts
const setAuth   = useAuthStore((s) => s.setAuth)
const clearAuth = useAuthStore((s) => s.clearAuth)
```

**Reading outside React** (axios interceptors, plain functions):
```ts
const token = useAuthStore.getState().token
```

### Adding a new store

- Create `src/store/<feature>Store.ts`.
- Only add a store when state must be shared across pages, or must survive unmount. Otherwise stick with `useState`.
- Use `persist` only for state that must survive a reload (auth, user prefs). Don't persist server data that should be re-fetched.
- Keep selectors granular: `useStore((s) => s.field)`, not `useStore((s) => s)`.
- One store per concern. Don't build a god-store with everything inside.

## The WebSocket client contract

`src/api/ws.ts` is the only place that constructs a STOMP client. Mirrors the axios "one client" rule.

```ts
import { connectWs, disconnectWs, subscribeToRoom } from '../api/ws'

// In a page effect, subscribe to a chat room. Returns an unsubscribe function.
useEffect(() => {
  const off = subscribeToRoom(roomId, (msg) => {
    setMessages((prev) => prev.some(m => m.id === msg.id) ? prev : [...prev, msg])
  })
  return off
}, [roomId])
```

Rules:
- **Lifecycle is owned by `App.tsx`.** It subscribes to `useAuthStore` and calls `connectWs()` when a token appears, `disconnectWs()` when it goes away. Pages never call connect/disconnect themselves.
- **CONNECT auth** rides as a STOMP `Authorization: Bearer <token>` header read fresh from `useAuthStore.getState().token` on every reconnect (`beforeConnect` hook).
- **Subscriptions survive reconnect.** `subscribeToRoom` returns an unsubscribe; calling it on unmount is required to avoid leaks. The internal subscription map auto-re-subscribes on reconnect.
- **Send is HTTP, not STOMP.** `sendMessage(roomId, content)` from `chat.ts` does a POST and the backend broadcasts. Dedupe in the page by message `id` if you display the POST response *and* receive the broadcast.
- **No raw `new Client(...)` / `new SockJS(...)`.** SockJS isn't installed — modern WebSocket only. Frontend uses `brokerURL` against `${proto}//${location.host}/ws`; Vite proxies WS to backend `:8080` in dev.

## Auth flow

- **State lives in `useAuthStore`** (Zustand, persisted to localStorage as `bubbleup-auth-v2`). Holds `{ accessToken, refreshToken, user }`. Don't touch `localStorage` directly anywhere.
- **Set on login/register**: `setAuth(res.accessToken, res.refreshToken, { id, email, role })` in `LoginPage.tsx`.
- **Logout**: `Navbar.tsx` calls `logoutApi(refreshToken)` best-effort, then `clearAuth()`.
- **Route guard**: `RequireAuth` in `App.tsx` subscribes to `accessToken`.
- **Axios header**: `client.ts` reads `useAuthStore.getState().accessToken` per request.
- **Token expiry is handled automatically.** 401 from any request → `client.ts` interceptor calls `/auth/refresh` once (single-flight; concurrent 401s share one in-flight promise), retries the original request with the new access token. If `/auth/refresh` itself fails → `clearAuth()` + hard navigate to `/login`. Pages don't need to think about expiry.
- **Persist key is `bubbleup-auth-v2`** — bump it again if the store shape changes; old localStorage blobs become invalid and users re-login.

---

## Adding a new feature (frontend side)

1. Define API types and functions in `src/api/<feature>.ts`. Pattern: one async function per backend endpoint, returns the unwrapped `data`.
2. Build the page in `src/pages/<Feature>Page.tsx`. Use React hooks (`useState`, `useEffect`) for local state; reach for a Zustand store only if the state is shared with another page.
3. Register the route in `App.tsx`. Wrap with `<RequireAuth>` unless it's public.
4. Need user identity in the page? `const user = useAuthStore((s) => s.user)`. Don't decode the JWT client-side.
5. Style with Tailwind utility classes inline. Match the existing visual language (indigo-600 primary, white cards, gray-50 background, rounded borders).
6. Add a link in `components/Navbar.tsx` if it should be top-nav reachable.

---

## Anti-patterns

| You wrote | Replace with |
|---|---|
| `import axios from 'axios'` in a page or feature file | `import client from '../api/client'` |
| `axios.create({...})` anywhere outside `client.ts` | Use the existing `client` |
| `new Client(...)` from `@stomp/stompjs` outside `ws.ts` | `subscribeToRoom(roomId, handler)` from `ws.ts` |
| `new SockJS(...)` anywhere | Not installed; use the raw WS via `ws.ts` |
| Calling `connectWs()` / `disconnectWs()` from a page | `App.tsx` owns the lifecycle via the auth-store subscription |
| `localStorage.setItem(...)` / `localStorage.getItem(...)` for auth | `useAuthStore` (`setAuth`, `clearAuth`, or `useAuthStore.getState().token` outside React) |
| `localStorage` for any other persisted state | A Zustand store with the `persist` middleware in `src/store/` |
| `createContext` + `useContext` for shared state | A Zustand store in `src/store/` |
| Adding Redux, Jotai, Recoil, MobX, … | Zustand is the chosen library — add a store, don't add a second library |
| Decoding the JWT client-side to read user info | `useAuthStore((s) => s.user)` — set on login, persisted |
| `useAuthStore((s) => s)` (whole-store selector) | Select one field at a time: `useAuthStore((s) => s.token)` |
| A new CSS file or styled-components / emotion | Tailwind utility classes |
| `fetch(...)` | Use `client` (auth + baseURL handled) |
| Putting routes inside a page component | Routes live in `App.tsx` |
| A `types/` or `models/` folder for shared types | Keep the type next to the API function that returns it; promote only when 2+ files import it |
| New `/chat` or `/calendar` top-level route | The hub (GroupsPage tabs) is the only entry. The standalone pages were deliberately removed in iter 3. |
| Creating a chat room from scratch for a new group | `GroupCommandService.createGroup` auto-creates a "general" room. ChatPanel picks the oldest room for the group. |

---

## Build & run

```bash
npm install
npm run dev       # Vite dev server on :3000, proxies /api → :8080
npm run build     # tsc + vite build (strict TS check runs here)
npm run preview   # serve the built bundle
```

Backend must be running on `:8080` for the dev proxy to work. Easiest full setup: `docker-compose up` from repo root.

---

## Known gaps (don't invent silently — flag if relevant)

- **No shared `ApiError` type yet**. The success envelope is captured by `ApiSuccess<T>` in `src/api/client.ts` (re-export it: `import client, { ApiSuccess } from './client'`). The failure envelope is still unwrapped ad-hoc via `src/api/errors.ts`. If the failure shape gets formalized, it lands next to `ApiSuccess`.
- **Error-code mapping lives in `src/api/errors.ts`** (`errorCode`, `errorBody`, `describeError`). Call sites pass a `{ CODE: 'i18n.key' }` map plus a fallback key — no more hand-rolled `e?.response?.data?.error?.code` chains. `LoginPage.describeError` is the lone holdout because it has a `fields?.length` validation-error branch, but it uses `errorBody()` for unwrapping.
- **No environment config**. `client.ts` baseURL and the Vite proxy targets are hard-coded for dev. A `.env` + `import.meta.env` setup is needed before any non-localhost deploy.
- **No tests** — `npm test` doesn't exist. If you change behavior, say so explicitly.
- **"Add member by UUID" UX in GroupsPage**. Owners paste a user UUID — no user search yet. Add `GET /api/users?email=` + autocomplete when needed.
- **No calendar grid view** — list view per month only inside the hub. A real grid (week/day) is iter 3+ work.
- **Chat in the hub uses the auto-created "general" room only**. Manually-created additional rooms exist in the backend but aren't reachable from the UI yet. If you need multi-room UX, the next step is a room dropdown inside `ChatPanel`.
- **Chat polish (iter 3) shape contract.** `ChatMessage.messageType` is one of `TEXT | SYSTEM_JOIN | SYSTEM_LEAVE | LINK`; `ChatMessageRow` branches on it. SYSTEM rows render centered italic; LINK rows render a `CalendarLinkCard` that resolves via `getEvent(linkTargetId)` and falls back to "Link unavailable" on 404/403 — the backend never validates link targets. New linkable thing later (SESSION / FILE / …) = new backend enum value + a new render branch + a new `LinkPicker*` entry, no schema change.
- **Cursor pagination in `ChatPanel`.** `getMessages(roomId, { before, size })` returns DESC; the panel reverses for display, prepends older pages on scroll-to-top, and preserves scroll position via `requestAnimationFrame`. No page numbers / no `MessagePage` envelope — that shape was removed in iter 3.
- **Unread badges are derived state.** `GroupsPage` hoists `rooms: ChatRoom[]` and memoizes `unreadByGroup` (Σ `unreadCount` per `groupId`). Refreshed via `refreshRooms()` on mount and after join / leave / add / remove / delete / create / share-to-chat / mark-read. `ChatPanel` calls `markRead(roomId, latestId)` on first render + after new arrivals while user is at the bottom, then bubbles `onUnreadChanged` so the sidebar badge clears.
- **No "open in a tab" deep link to a specific group**. URL stays `/groups`; the expanded group is local state. Adding `/groups/:id` would mirror the in-place hub.
- **`postcss.config.js` is ESM-only without `"type": "module"`** in `package.json`, so `npm run build` fails at the vite step (tsc passes). Pre-existing. Either rename to `postcss.config.cjs` with `module.exports` or add `"type": "module"` (and rename Vite/Tailwind configs accordingly).
