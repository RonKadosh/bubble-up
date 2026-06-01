# backend/CLAUDE.md

Loaded when you work under `backend/`. Read [root CLAUDE.md](../CLAUDE.md) first if you haven't.

Java 21, Spring Boot 3, JPA, Postgres, JWT, STOMP over raw WebSocket, Maven, Lombok.

---

## Find before write

If you're about to write infrastructure code, **grep `common/` first.** The "one rule" in [root CLAUDE.md](../CLAUDE.md) lists the most common cases. The full inventory of what exists is below under "common/ reference."

Before adding anything to `common/`: it has to be reusable, infrastructural, and stable. If the same pattern shows up in only one feature, it stays in that feature. Promote to `common/` only when a second feature needs it.

---

## Feature module shape

Every feature module looks like this (`auth/`, `groups/`, `chat/` already do):

```
<feature>/
├── api/
│   ├── <Feature>Controller.java
│   ├── dto/                          ← request/response records
│   └── mapper/                       ← optional, entity ↔ DTO
├── application/
│   ├── <Feature>CommandService.java  ← writes
│   ├── <Feature>QueryService.java    ← reads
│   └── <Feature>InternalServiceImpl.java   ← if exposed cross-module
├── model/                            ← JPA entities + feature-local enums
├── persistence/                      ← Spring Data repositories
└── internal/                         ← only if other modules call you
    ├── <Feature>InternalService.java ← interface
    └── dto/<Feature>Summary.java
```

Build bottom-up: `model/` → `persistence/` → `application/` → `api/` → `internal/`.

### Layer responsibilities

- **`model/`** — entities and value objects. Entities enforce their own invariants. **Do not** call repositories, services, or HTTP from inside an entity.
- **`persistence/`** — Spring Data repositories. **Private to the module.** Only `application/` in the same module imports repositories.
- **`application/`** — use cases. Owns transactions (`@Transactional` on writes, `@Transactional(readOnly = true)` on reads). Returns DTOs, never raw entities. Calls other modules only through their `internal/` interface.
- **`api/`** — thin HTTP layer. Validates the request, delegates, wraps in `ApiResponse`. **No business logic.**
- **`internal/`** — the only layer other modules may import. Interface in `internal/`, impl in `application/`.

### Cross-module rule

```java
// Wrong — from dashboard module
private final GroupRepository groupRepository;

// Right — from dashboard module
private final GroupInternalService groupInternalService;
```

A module's `api/`, `application/`, `model/`, `persistence/` are private. Outsiders see only `internal/`.

---

## `common/` reference (the meat)

| Sub-package | What's there | When to reach for it |
|---|---|---|
| `api/` | `ApiResponse`, `ErrorResponse`, `FieldErrorResponse`, `ApiPaths` | Response envelope, path constants |
| `error/` | `AppException`, `ErrorCode`, `ErrorCategory`, `GlobalExceptionHandler` | Throw typed errors → automatic HTTP mapping |
| `context/` | `CurrentUser`, `CurrentUserProvider`, `SecurityContextCurrentUserProvider`, `RequestContext`, `RequestContextFilter`, `UserRole`, `SystemActor` | Who's calling; per-request scratchpad |
| `security/` | `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `SecurityConstants` | JWT + Spring Security wiring (don't re-configure) |
| `websocket/` | `WebSocketConfig`, `WebSocketPublisher`, `WebSocketDestination`, `WebSocketUserTracker`, `WebSocketEvent`, `StompPrincipal`, `StompAuthChannelInterceptor`, `WsChannelInterceptor` | STOMP transport. CONNECT enforces JWT via the auth interceptor; feature modules can register their own `WsChannelInterceptor` beans (e.g. `@Order(2+)`) to gate per-destination SUBSCRIBE — see `chat/api/ChatTopicSubscribeInterceptor` for the pattern. |
| `config/` | `SecurityProperties`, `CorsProperties`, `FileStorageProperties`, `AgentProperties`, `SimulationProperties` | Typed config bound from `application.yml` |
| `pagination/` | `PageRequestDto`, `PageResponseDto`, `PageMapper` | Page request in, mapped page out |
| `filtering/` | `FilterRequest`, `SearchCriteria`, `FilterOperator`, `SortRequest`, `SpecificationBuilder` | Dynamic JPA queries |
| `datetime/` | `TimeProvider`, `SystemTimeProvider`, `SimulatedTimeProvider`, `DateTimeUtils` | Testable clock |
| `file/` | `FileStorageService`, `LocalFileStorageService` (primary), `CloudFileStorageService` (stub), `FileUploadRequest`, `StoredFile`, `FileMetadata`, `FileAccessPolicy` | Upload/download/delete. Multipart caps + MIME blocklist live in the feature module (e.g. `groups/application/GroupFileTypeFilter`) since they're policy, not infrastructure. |

### Recipes

**Controller**
```java
@RestController
@RequestMapping(ApiPaths.GROUPS_BASE)        // <-- constant, never a literal
@RequiredArgsConstructor
public class GroupController {
    private final GroupCommandService commands;
    private final GroupQueryService queries;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResponse> create(@Valid @RequestBody CreateGroupRequest req) {
        CurrentUser me = currentUserProvider.get();   // throws UNAUTHORIZED if missing
        return ApiResponse.success(commands.create(req, me.id()));
    }
}
```

**Errors**
```java
throw new AppException(ErrorCode.GROUP_NOT_FOUND);
throw new AppException(ErrorCode.GROUP_IS_FULL, "Group max size is " + max);
```
Add new codes to `ErrorCode` (category + HTTP status live there). Don't `try/catch` to repackage. Don't throw `ResponseStatusException`. Don't `return ResponseEntity.status(...)` from a controller.

Response shape is always:
```json
{ "success": false, "error": { "code": "...", "message": "...", "category": "...", "fields": [...]? } }
```
`fields` only appears for `VALIDATION_ERROR` (handled automatically when `@Valid` fails).

**Current user / role**
```java
CurrentUser me = currentUserProvider.get();
if (me.role() == UserRole.ADMIN) { ... }
```
`UserRole` is an enum in `common/context/`. Don't compare role strings. For non-user callers (cron, agents, sim), label with `SystemActor`.

**Time**
```java
group.setCreatedAt(timeProvider.now());   // injected TimeProvider
```
Never `Instant.now()` / `LocalDateTime.now()` in business logic. `SystemTimeProvider` is `@Primary`; `SimulatedTimeProvider` available by name for sim/tests.

**Pagination**
```java
Pageable pageable = PageRequest.of(req.page(), req.size(),
        Sort.by(Sort.Direction.fromString(req.sortDirection()), req.sortBy()));
Page<Group> page = repo.findAll(pageable);
return ApiResponse.success(pageMapper.toDto(page.map(GroupResponse::from)));
```
`PageRequestDto`'s compact constructor clamps page/size/sort defaults — don't re-validate.

**Dynamic filtering**
```java
Specification<Group> spec = specBuilder.build(filterRequest);
Page<Group> result = repo.findAll(spec, pageable);
```
Operators: `EQUALS`, `NOT_EQUALS`, `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `GREATER_THAN`, `LESS_THAN`, `GREATER_THAN_OR_EQUAL`, `LESS_THAN_OR_EQUAL`, `IN` (`Collection` value required). Misuse throws `IllegalArgumentException` — fix the caller, don't silently drop.

**Files**
```java
StoredFile stored = fileStorageService.upload(
        new FileUploadRequest(name, contentType, bytes, FileAccessPolicy.PRIVATE));
// persist stored.fileId() on your entity
byte[] data = fileStorageService.download(fileId);
```
Don't touch the filesystem. Don't import a cloud SDK in a feature module. Switching backends = `app.file-storage.type` config flip.

**WebSocket**
```java
publisher.publishToUser(userId, WebSocketDestination.NOTIFICATIONS, payload);
publisher.publishToTopic(WebSocketDestination.CHAT, payload);
publisher.publishToTopic(WebSocketDestination.chatRoom(roomId), payload);   // per-room
```
New destination = add to `WebSocketDestination`. Don't sprinkle string literals.

**WebSocket auth model**
- The HTTP handshake at `/ws` is `permitAll` so the upgrade happens. **JWT is enforced at the STOMP `CONNECT` frame** by `StompAuthChannelInterceptor` reading the `Authorization` header. Invalid/missing → ERROR frame, connection closes.
- The Principal on the session is a `StompPrincipal(userId)` — `WebSocketPublisher.publishToUser(userId, ...)` routes correctly because `Principal.getName()` returns the UUID string.
- `SUBSCRIBE` frames are gated per-destination by feature-owned `WsChannelInterceptor` beans. See [`chat/api/ChatTopicSubscribeInterceptor`](src/main/java/com/ronkadosh/bubbleup/chat/api/ChatTopicSubscribeInterceptor.java) for the pattern: implement `WsChannelInterceptor`, `@Component`, `@Order(2+)`, match the destination regex, look up membership via the relevant `internal/` service, throw `AppException` to reject.
- **Do not** drop the `/ws/**` `permitAll` rule from `SecurityConfig` — the comment in that file explains why.

**Config**
```java
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(boolean enabled, String fromAddress) {}
```
Picked up automatically by `@ConfigurationPropertiesScan` on `BubbleUpApplication`. Never `@Value("${...}")` in feature code.

---

## Anti-patterns (if you wrote this, you skipped this guide)

| You wrote | Replace with |
|---|---|
| `return ResponseEntity.status(...).body(...)` | `return ApiResponse.success(...)` or throw `AppException` |
| `throw new ResponseStatusException(...)` | `throw new AppException(ErrorCode.X)` |
| `SecurityContextHolder.getContext().getAuthentication()` | Inject `CurrentUserProvider` |
| `Instant.now()` / `LocalDateTime.now()` in a service | Inject `TimeProvider` |
| `@Value("${some.prop}")` | `@ConfigurationProperties` record in `common/config/` |
| `new SimpMessagingTemplate(...)` calls | Inject `WebSocketPublisher` |
| `Files.write(...)` / `Paths.get(...)` in a feature | `FileStorageService` |
| `"/api/something"` string literal in a controller | Constant in `ApiPaths` |
| `currentUser.role().equals("ADMIN")` | `currentUser.role() == UserRole.ADMIN` |
| Custom `Map<String, Object>` error body | `AppException` + `ErrorCode` |
| Re-registering `RequestContextFilter` | Already wired in `SecurityConfig` |
| New `SecurityFilterChain` bean | Edit the existing one in `SecurityConfig` |
| Helper class in `common/` named `XHelper` / `XUtils` for a single feature | Keep it in the feature module |
| `BCryptPasswordEncoder` on high-entropy random tokens (e.g. refresh tokens) | SHA-256. BCrypt is for low-entropy passwords. See `RefreshTokenService.sha256Hex` |
| Throwing from a `@Transactional` method right after doing work that must survive the rollback | Split the survives-rollback work into a separate `@Service` with `@Transactional(propagation = REQUIRES_NEW)`. See `RefreshTokenChainRevoker` |
| Manually creating a chat room when a group is first created | `GroupCommandService.createGroup` already auto-creates a "general" room via `ChatInternalService.createRoomForGroup`. The frontend hub assumes this room exists. |

---

## Adding a new feature — checklist

1. Create the folders: `api/` (+ `dto/`), `application/`, `model/`, `persistence/`. Add `internal/` only if another module needs to call you.
2. Add `<FEATURE>_BASE` constant to `common/api/ApiPaths`. Use it on the controller's `@RequestMapping`.
3. Add `ErrorCode` entries for any new error conditions (with `ErrorCategory` + `HttpStatus`).
4. Inject providers (`CurrentUserProvider`, `TimeProvider`, `FileStorageService`, `WebSocketPublisher`, …). Don't `new` infrastructure.
5. Controller stays thin: `@Valid` request → service call → `ApiResponse.success(...)`.
6. Writes are `@Transactional`. Reads are `@Transactional(readOnly = true)`.
7. Other modules call you via `internal/<Feature>InternalService`. Return purpose-built summaries, not entities.
8. If you find yourself adding to `common/`, ask: does a second feature need this *now*? If no, keep it in the feature.

---

## Build & run

```bash
mvn -DskipTests clean compile        # quick build check after edits
mvn spring-boot:run                  # local backend (needs postgres on :5432)
mvn test                             # run tests (uses in-memory H2 — no Docker required)
```

Postgres via `docker-compose up postgres` (or full stack from repo root). Config lives in `src/main/resources/application.yml`; everything is env-overridable (see `docker-compose.yml`).

## Tests

- Base class: [`support/IntegrationTest`](src/test/java/com/ronkadosh/bubbleup/support/IntegrationTest.java). Extend it for any controller test.
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`. Test DB is H2 in PostgreSQL compatibility mode (see `src/test/resources/application-test.yml`).
- Helpers: `registerAndLogin()` returns `AuthedUser(id, email, jwt)`. `bearer(authed)` is a `RequestPostProcessor` for `mvc.perform(...).with(bearer(u))`.
- Pattern: one happy + one negative test per public endpoint. Name files `*IT.java` — Surefire's includes picks them up alongside `*Test.java`.
- `mvn test` runs ~32 tests in ~20s with no Docker dependency.

---

## Known gaps (do not invent silently — call them out if relevant to your task)

- **Tests run on H2, not Postgres**. H2 in PostgreSQL compatibility mode covers vanilla JPA, but PG-specific behavior (partial unique indexes, JSONB, advisory locks) is untested. Switching tests to Testcontainers Postgres requires resolving Windows Docker named-pipe friction (set `DOCKER_HOST=tcp://localhost:2375` and enable Docker Desktop's "Expose daemon" toggle).
- **`ChatBroadcastIT` is `@Disabled`**. Verifying HTTP-POST → STOMP-topic delivery in a MockMvc + RANDOM_PORT setup doesn't deliver to the in-process subscriber even though publish + subscribe both execute. Broadcast is verified by manual two-browser-tab walkthrough.
- **Chat unread count is N+1 per room.** `ChatQueryService.getRoomsForUser` does one `countByRoomIdAndSentAtGreaterThan` per room. Cursors and cursor-messages are batched, but the count itself isn't. Fine at hub scale (a user has ~few groups × ~1 default room each); revisit if rooms-per-user grows.
- **`ErrorCode.CHAT_MESSAGE_NOT_IN_ROOM` is defined but unused.** Reserved for if/when chat starts cross-validating link targets, message-edit, or pin endpoints that need to assert a message belongs to a specific room. Left in to avoid renaming if those land.
- **Chat polish schema is dev/test only (H2 ddl-auto create-drop).** Prod Postgres needs `ALTER TABLE chat_messages ADD COLUMN message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT'` + flip `sender_id` to nullable + add `subject_user_id UUID NULL`, `link_target_type VARCHAR(32) NULL`, `link_target_id UUID NULL`, plus index `idx_chat_messages_room_sent (room_id, sent_at DESC)` and create `message_read_cursors`. **Note**: `message_type` was originally provisioned at VARCHAR(16); the room-lifecycle iteration widened it to VARCHAR(32) to fit `SYSTEM_ROOM_END_SOON` / `SYSTEM_ROOM_EXTENDED` (20 chars each). Existing deploys need `ALTER TABLE chat_messages ALTER COLUMN message_type TYPE VARCHAR(32);`. H2 in PG-compat mode silently accepts VARCHAR overflow, so tests passed even with the old width — only real Postgres rejects. There's no migration tool wired up yet (Flyway / Liquibase iter 3+).
- **`ChatInternalServiceImpl` injects `WebSocketPublisher` with `@Lazy`** to break a cycle (`WebSocketPublisher` → `WebSocketConfig` → `ChatTopicSubscribeInterceptor` → `ChatInternalService` → `ChatInternalServiceImpl` → `WebSocketPublisher`). Don't remove the `@Lazy`.
- **No scheduled cleanup of expired/revoked refresh tokens** — iter 3+. Rows accumulate. See TODO in `RefreshTokenRepository`.
- **Default chat room auto-create assumes the frontend uses it.** Hub ChatPanel picks the oldest room (the "general" one) for each group. If a feature wants per-channel rooms with no default, change `GroupCommandService.createGroup` and the hub together.
- **Calendar `ownerType=USER` is accepted but enforces `ownerId == me`** — USER-scope events have no user-facing entry point yet; the enum value exists for forward compatibility but isn't a product feature.
- **Calendar recurrence is not supported** — iter 3 scope. Only one-off `(startsAt, endsAt)` events.
- **No user search endpoint** — owner adds members by pasting a UUID. Add `GET /api/users?email=` when needed.
- **`common/validation/` and `common/utils/`** were referenced in older docs but don't exist. Don't create them speculatively.
- **Matching v1 schema is dev/test only (H2 ddl-auto create-drop).** Prod Postgres needs: `ALTER TABLE user_profiles ADD COLUMN meaningful_behavior_events INT NOT NULL DEFAULT 0`; on `group_profiles` add `group_profile_confidence DOUBLE PRECISION NOT NULL DEFAULT 0`, `trending_activity_count BIGINT NOT NULL DEFAULT 0`, `trending_recent_joins INT NOT NULL DEFAULT 0`, `trending_upcoming_sessions INT NOT NULL DEFAULT 0`; on `user_match_cache` add `matching_confidence DOUBLE PRECISION NOT NULL DEFAULT 0` and `match_percent INT NULL`. Same "no Flyway yet" caveat as the chat-polish note above. (The v1 redesign also dropped the `app.matching.credibility-threshold` and `targets` config — replaced by `matched-display-threshold`, `confidence`, and `trending` blocks.)
- **Matching v1 = complementarity + confidence blend.** `MatchingScorer` scores `final = matching_confidence·cosine(user, 1−group_avg) + (1−matching_confidence)·trending`, where `matching_confidence = user_confidence · group_profile_confidence`. Ranking uses `final_score`; the displayed `%` is the raw cosine (only for MATCHED, i.e. `matching_confidence ≥ matched-display-threshold`). Trending uses only existing signals (chat+file activity, recent joins, member count, upcoming GROUP calendar events) — no reaction/whiteboard instrumentation exists.
- **Trending signals refresh opportunistically, not on a timer.** `GroupProfile`'s trending counts are recomputed only when a matching event re-fires `doRecomputeGroupProfile` (membership change, or a member's behavior event). A quiet group's `upcoming_sessions` won't decay as time passes until something re-triggers it. A future `@Scheduled` nightly recompute would fix this (iter 3+).
- **`MatchingScenarioSeeder`** (`app.matching.scenario.seed.enabled`, default OFF) seeds end-to-end recommendation scenarios by writing `UserProfile`/`GroupProfile` rows **directly** and never firing matching events — scenario users/groups are *inert* so the async pipeline doesn't recompute (and zero) the hand-set vectors. Same inert technique is used by `MatchingRecommendationsIT`. Don't "fix" these to route through the services.
