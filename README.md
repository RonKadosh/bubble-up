# Bubble.up

> Find the right people to study with — and get everything you need to actually study together.

**Bubble.up** is a platform for university students that solves a deceptively hard problem: studying alone is inefficient, but finding *good* study partners is mostly luck. You ask around, you join a random WhatsApp group, you hope someone shows up. Bubble.up replaces that luck with a matching engine — and then hands every group the infrastructure (chat, calendar, shared files, live sessions, experts) it needs to learn together from day one.

The unit of the product is the **Bubble**: a small, course-scoped study group. Bubble.up's job is to put the right students in the right Bubble, and make that Bubble a place where real work happens.

---

## Table of contents

- [What Bubble.up v1 solves](#what-bubbleup-v1-solves)
- [What is a Bubble?](#what-is-a-bubble)
- [The matching system](#the-matching-system)
- [The expert system](#the-expert-system)
- [Under the hood](#under-the-hood)
- [Running it locally](#running-it-locally)

---

## What Bubble.up v1 solves

Every student knows the value of a good study group and the pain of forming one. The people who'd be a great fit are sitting in the same lecture hall, but there's no mechanism to find them — so groups form by proximity and friendship, not by fit, and half of them never produce a single shared note.

Bubble.up v1 is built around one idea: **suitable study partners, plus the infrastructure to study with them.**

- **Matching, not browsing.** Instead of scrolling a list of groups and guessing, students take a short character quiz and simply *use the app*. Both feed a profile that the matching engine uses to recommend Bubbles where they'd genuinely add something.
- **A complete workspace per group.** Every Bubble comes with live chat, a shared calendar, file storage, live video, and a collaborative whiteboard — so a freshly matched group can start working immediately, with no setup and no glue tools.
- **Experts on tap.** TAs and private tutors can become verified experts and open study sessions that Bubbles enroll into — bringing teaching capacity into the same place the learning already happens.

The whole product is scoped to the academic catalog — **University → Department → Course** — so a Bubble is always anchored to a specific course, and matching is always comparing like with like.

---

## What is a Bubble?

A **Bubble** is a small study group scoped to a single course. It's the heart of the product, and it's more than a chat room — it's a self-contained collaborative workspace. Each Bubble gives its members a four-tab hub:

| Tab | What it does |
|-----|--------------|
| **Chat** | Real-time messaging over WebSocket (STOMP). System messages mark joins/leaves; messages can link to calendar events and polls inline. |
| **Calendar** | Shared study events and deadlines, scoped to the group. Expert sessions a Bubble enrolls into show up here too. |
| **Files** | Upload, download, and manage shared materials (notes, slides, problem sets) with per-file access control. |
| **Members** | Who's in the Bubble. Membership is gated on course enrollment — you can only join a Bubble for a course you're actually taking. |

On top of the hub, Bubbles get **live sessions** — video calls plus a shared **collaborative whiteboard** — so a study session can happen entirely inside the app.

A few deliberate design choices:

- **Bubbles are course-anchored.** A Bubble always belongs to exactly one course in the catalog. This keeps groups focused and gives the matching engine a clean comparison space.
- **Enrollment gates membership.** Joining, creating, or being added to a Bubble requires enrollment in that course's offering. You can't end up in a study group for a class you aren't taking.
- **Every Bubble is born ready.** Creating a Bubble automatically provisions its default chat room, so the workspace is never empty on day one.

The name is the metaphor: a Bubble is a small, self-contained space where a handful of people focus together — and the UI leans into it, with round, soft, "bubble-pop" visuals throughout.

---

## The matching system

This is the core of Bubble.up, and the part I've put the most thought into. The goal isn't to find students who are *similar* to each other — it's to build **balanced, complementary** groups. A Bubble of five natural leaders and no planners isn't a good group.

### Seven roles

Every student and every Bubble is described as a vector over seven collaboration roles:

> **Leader · Planner · Expert · Creative · Communicator · TeamPlayer · Challenger**

A student's role vector comes from blending **two independent sources of evidence**:

1. **A character quiz.** Short, opt-in questions whose answers carry weight toward one or more roles. The accumulated weights are normalized to a *shape* (the strongest role sits at 1.0), so answering the same way three times or thirty times yields the same profile — what's measured is the shape of who you are, not how chatty you were.

2. **Your behavior in the app.** Real actions — sending messages, sharing files, joining sessions, and so on — are counted and mapped to roles through a configurable signal table. Each action type runs through a *saturating* curve, so the signal has diminishing returns: it sharpens the picture without letting any one habit dominate it.

The two sources are blended with a weight that shifts over time: a brand-new user is mostly quiz-driven (we don't have behavior yet), and as they use the app, observed behavior carries more of the profile.

### Confidence, and why it matters

Every profile carries a **confidence** — how much evidence actually backs it. This is what keeps the system honest:

- Confidence rises with the **diversity** of evidence (more answered questions, a *spread* of distinct actions), not raw volume. Sending a thousand chat messages can't fake a strong profile — only doing a variety of things can.
- A Bubble's profile confidence accounts for how many members have meaningful profiles and how large the group is, so a one-person group never reads as a confident signal.

### How a match is scored

For a given student and a candidate Bubble:

- We compute what the group is **short on** — the roles it's weak in — and measure how well the student fills exactly those gaps (cosine similarity against the group's *need* vector). This is **complementarity, not similarity**: you score high when you're strong where the group is weak.
- That personalized score is blended with a **trending** score (group activity, recent joins, size, upcoming sessions) according to confidence. When we have little evidence about a student or a group, we lean on what's popular and active; as confidence grows, we lean on the real personalized fit.

The whole formula lives in one pure, deterministic, unit-tested scorer — no hidden state, no I/O — so the matching logic is auditable and the numbers are reproducible.

### An open design question: should we explain *why*?

One thing I'm still deliberately undecided on: **how much of the matching ideology to expose to the user.**

There's a real tension here. Showing a student *"you're being recommended this Bubble because it needs a Planner and you're a strong Planner"* is transparent and can build trust. But it also invites gaming (people answering the quiz to chase a label), it can feel reductive (nobody wants to be told they're "the Communicator"), and it pressures the model to be legible rather than accurate. Hiding it entirely is simpler and safer, but risks feeling like a black box.

The current build leans toward showing a match *percentage* and a light "matched vs. trending" distinction without exposing the seven-role decomposition. Whether to open that box further — and how to do it without inviting gaming — is an active product decision, not a settled one.

---

## The expert system

Bubble.up isn't only peer-to-peer. Teaching assistants and private tutors can bring real teaching capacity into the platform through the **expert system**.

The flow is built around verification, because "expert" has to mean something:

1. **Apply.** Any user can apply to become an expert, describing who they are and what they can teach. The application lands in a `PENDING` state.
2. **Admin review.** An admin inspects the request and either **verifies** or **rejects** it (`PENDING → VERIFIED / REJECTED`). Only verified experts get expert capabilities — there's no self-serve path to the badge.
3. **Open sessions.** Once verified, an expert exposes themselves by opening **study sessions**: scheduled sessions with a capacity, backed by a calendar event and the platform's live-session infrastructure (video + whiteboard).
4. **Bubbles enroll.** Study groups enroll into a session up to its capacity. The session shows up on the group's calendar, and the expert hosts it inside the app — with host-gated controls over the room, the whiteboard, and the call.

The result is a clean loop: a TA or tutor proves who they are once, then becomes discoverable and bookable by exactly the groups studying the material they teach.

---

## Under the hood

A short tour for the curious — the engineering reflects a few deliberate principles.

### Backend

- **Java 21 · Spring Boot 3 · Postgres · JPA/Hibernate · JWT · STOMP over WebSocket.**
- **Modular by feature, strict boundaries.** Each feature (`auth`, `groups`, `chat`, `matching`, `expert`, `catalog`, …) is a self-contained module with the same internal shape: `model → persistence → application → api`, plus an `internal/` interface that is the *only* surface other modules may call. Features never reach into each other's repositories — they fuse if you let them, so the architecture forbids it.
- **A shared `common/` infrastructure layer** carries the cross-cutting machinery: a single response envelope, typed error codes mapped to HTTP statuses in one place, an injected clock and current-user provider (both swappable for tests and simulation), file storage behind an interface, a single WebSocket publisher with named destinations, and typed configuration properties. Feature code composes these rather than re-inventing them.
- **The matching engine is a pure function.** All scoring is centralized in one stateless, deterministic scorer with no I/O, so the same code serves both the live path and the precomputed match cache and the two can't drift apart.

### Frontend

- **React 18 · TypeScript (strict) · Vite · Tailwind · Zustand · @stomp/stompjs.**
- **State has a deliberate scope ladder:** local `useState` by default, lift to the page when siblings must agree, and a **Zustand** store only when state genuinely crosses pages or must survive a remount (auth, theme, language, layout). One concern per store, granular selectors, persistence only where it's truly needed. No Redux, no Context-as-state-manager.
- **One client for each transport.** A single axios instance and a single STOMP client are shared app-wide; pages subscribe to live rooms and clean up on unmount, while the app shell owns the connection lifecycle.
- **Bilingual and RTL-aware from the start.** Every user-facing string is internationalized (English + Hebrew), and layout uses logical CSS properties so the same components render correctly left-to-right and right-to-left.

---

## Running it locally

```bash
docker-compose up                    # full stack: Postgres + backend (:8080) + frontend (:3000)
```

Or run the sides independently:

```bash
cd backend  && mvn spring-boot:run   # backend only (needs Postgres on :5432)
cd frontend && npm run dev           # frontend dev server on :3000, proxies /api to :8080
```

Sanity checks:

```bash
cd backend  && mvn -DskipTests clean compile   # backend compiles
cd backend  && mvn test                        # ~test suite, H2 in-memory, no Docker needed
cd frontend && npm run build                   # strict tsc + vite build
```

---

<sub>Bubble.up is an evolving project. The matching ideology, the depth of expert tooling, and how much of the model to surface to users are all areas of active iteration.</sub>
