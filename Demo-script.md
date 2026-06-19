# Demo Script — Bubble.up Guided Tour

The game-tutorial-style guided tour a visitor sees right after clicking **Start
demo**. It walks them through the world defined in [Demo-world.md](Demo-world.md),
then hands them a free sandbox.

> **Companion doc:** [Demo-world.md](Demo-world.md) defines the *data*. This defines
> the *walkthrough* over that data. Slugs like `cs101-night-owls` refer to bubbles
> in that file.

---

## 1. Voice & feel

Playful, warm, second-person — a friendly game tutorial, not a manual. Short
sentences. One idea per popup. A little personality, a little 🫧. Never more than
~2 lines of body text per step. Always show **Skip tour** and **Back**; gated steps
gently pulse the thing you're asked to click.

> Tone reference: *"This is the Chat. Messages, polls, shared files — your Bubble's
> group chat lives here. Go on, say hi 👋"* — not *"The chat panel enables message
> exchange between group members."*

---

## 2. Tour mechanics (engine requirements)

The script is **engine-agnostic** but assumes these capabilities. (Library pick is
still open — `driver.js` is the lean for lightest dep; a small custom overlay also
works. Whatever we pick must do all of this.)

- **Spotlight overlay** — dim the page, cut a hole around the anchored element,
  float a bubble-styled tooltip next to it.
- **Three step types:**
  - `MODAL` — centered card, no anchor. Just text + **Next**. (Intro / transitions.)
  - `COACHMARK` — tooltip anchored to an element, **Next** advances. (Explainers.)
  - `GATED` — tooltip anchored to an element; **Next is disabled** until the visitor
    performs the real action (send a message, create an event…). The target pulses.
    This is what makes it feel like a game tutorial, not a slideshow.
- **Cross-route orchestration** — the tour spans Home → a Bubble → Settings → Home.
  Step state must survive route changes. Lives in a **`tourStore`** (Zustand),
  **not persisted** (a fresh visit = a fresh tour). The store holds `stepIndex`,
  `running`, and the resolved `starterGroupId` from the seeded world.
- **Stable anchors** — every targeted element carries a **`data-tour="<key>"`**
  attribute (never CSS/text selectors). The full anchor list is §4. Adding these
  attributes is the only frontend change the tour needs in existing components.
- **Advance signals for GATED steps** — the tour listens for the real app action.
  Source per step is in §5's "advance when" column. Prefer existing state/stores
  (e.g. messages array grew, calendar refetch, route changed, quiz `answered`
  count rose) over new plumbing; add a tiny tour event bus only where no signal
  exists.
- **Mocked live room.** Real JaaS/Jitsi never runs in the demo. The room is
  enterable but **mocked** — opening it shows a placeholder "stage" (the room shell:
  header, participant strip, chat, whiteboard) with a friendly "video is off in the
  demo" stand-in where the call would be, instead of a real video bridge. The *tour*
  still only *explains* the live room (step 15, explain-only); the *sandbox* lets the
  visitor open it to the mock.
- **Escape hatch** — Skip/Exit ends the tour and drops the visitor into the sandbox
  immediately, world intact.

---

## 3. Preconditions (set up by the seeder, see Demo-world.md)

1. Visitor is **auto-logged-in as "You"** — no login screen.
2. "You" is **pre-onboarded** — seed `onboarding_state` as finished (`wizard_level
   = 6`, not collapsed) so Home renders the **hub feed**, not the Getting-Started
   wizard. *(This refines Demo-world.md §10.)*
3. "You" has **no matching profile yet** — do **not** seed character answers for the
   guest. This is what makes Explore show **only Trending** until the quiz steps.
   *(Overrides the "optional profile" note in Demo-world.md §10 — the script builds
   the profile live, on camera.)*
4. The world is fully seeded so Home looks alive on arrival:
   - **Live:** the `CS Algorithms Exam Crunch` expert session (starts T0+15min, already
     enterable) surfaces in the LIVE section.
   - **Upcoming:** `cs101-night-owls` events + the upcoming expert sessions.
   - **Bubble Activity:** seeded joins / files / messages from `cs101-night-owls`.
   - **Explore:** trending CS101/SOC101 bubbles ("You" is enrolled, not a member).
5. Starter bubble = **`cs101-night-owls`** ("You" is already a member, so its chat /
   calendar / files are populated for the tour).

---

## 4. Anchors to add (`data-tour` keys)

| key | element | file (approx) |
|---|---|---|
| `home-feed` | the hub feed container | `groups/HubFeed.tsx` (PageShell) |
| `home-section-live` | LIVE `<section>` | `HubFeed.tsx` (SECTION_ORDER map) |
| `home-section-upcoming` | UPCOMING `<section>` | `HubFeed.tsx` |
| `home-section-activity` | ACTIVITY `<section>` | `HubFeed.tsx` |
| `home-section-discovery` | DISCOVERY (Explore) `<section>` | `HubFeed.tsx` |
| `sidebar-starter-bubble` | the `cs101-night-owls` row in the Bubble list | `groups/GroupSidebar.tsx` |
| `bubble-hub` | the Bubble's bento shell once open | `GroupsPage.tsx` |
| `bento-chat` / `bento-calendar` / `bento-files` | the three `BentoCell`s | `GroupsPage.tsx` |
| `bento-chat-maximize` / `bento-calendar-maximize` / `bento-files-maximize` | each cell's maximize (⤢) control | `components/BentoCell.tsx` (`groups.bento.maximize`) |
| `bubble-live-room` | the start/join live-room affordance inside the Bubble | `GroupsPage.tsx` / `groups/GroupHeader.tsx` |
| `chat-input` | the chat message textbox | `groups/ChatPanel.tsx` |
| `calendar-grid` | the calendar day grid/list | `groups/CalendarPanel.tsx` |
| `calendar-add-event` | the "add event" affordance / a day cell | `CalendarPanel.tsx` |
| `files-list` | the files/folders list | `groups/FilesPanel.tsx` |
| `file-syllabus` | the `Syllabus.pdf` row | `FilesPanel.tsx` |
| `file-share-to-chat` | the "share to chat" action in the viewer | `groups/FileViewer.tsx` |
| `nav-home` | Home (logo) nav target | `components/Sidebar.tsx` |
| `nav-settings` | Settings nav target | `components/Sidebar.tsx` |
| `settings-tab-matching` | the Matching tab | `SettingsPage.tsx` (`TABS`) |
| `matching-quiz` | the quiz question block in Settings → Matching | `SettingsPage.tsx` (`MatchingSection`) |
| `quiz-prompt` | the floating popup-quiz card | `components/QuizPrompt.tsx` |
| `discovery-matched-card` | the first MATCHED recommendation card | `HubFeed.tsx` (recommendation renderer) |

---

## 5. The script (step by step)

Sections below are "Acts." Each step: **type · anchor · copy · advance**. All copy
strings live under the **`demo.tour.*`** namespace in **both** `en.json` and
`he.json` (RTL-checked) — the strings below are the English source.

### Act 1 — Welcome & Home

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 1 | MODAL | — | **Welcome to Bubble.up 🫧** — Your campus, bubbled up. Find your people, study together, and never grind alone. Let's take 90 seconds to show you around. | Next |
| 2 | COACHMARK | `home-feed` | **This is Home.** Everything happening across your study Bubbles lands right here. | Next |
| 3 | COACHMARK | `home-section-live` | **Live.** Sessions and rooms happening *right now* — one click to hop in. | Next |
| 4 | COACHMARK | `home-section-upcoming` | **Upcoming.** Events and expert sessions, gathered from all your Bubbles so nothing slips. | Next |
| 5 | COACHMARK | `home-section-activity` | **Bubble Activity.** New members, fresh files, unread messages — the pulse of your Bubbles. | Next |

### Act 2 — Step into a Bubble

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 6 | GATED | `sidebar-starter-bubble` | **Here are your Bubbles.** Let's open one — click **CS101 Night Owls**. 👈 | the starter Bubble is opened in the hub (`selectedGroupId === starterGroupId`) |
| 7 | MODAL | — | **This is a Bubble.** A study group's home base: chat, calendar, shared files, and live rooms — all in one cozy place. | Next |

### Act 3 — Chat

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 8 | COACHMARK | `bento-chat` | **The Chat.** Messages, polls, and shared links live here. It's the heart of every Bubble. | Next |
| 9 | GATED | `chat-input` | **Your turn — say hi! 👋** Type anything and hit Enter. | a message is sent (chat messages array grew with a `meId`-authored msg) |

### Act 4 — Calendar

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 10 | GATED | `bento-calendar-maximize` | **Every panel can go full-screen.** Click ⤢ to **maximize the Calendar** (and ⤡ to shrink it back anytime). | calendar bento becomes the focused/maximized cell (`bentoLayoutStore.focused === 'calendar'` / maximized) |
| 11 | GATED | `calendar-grid` | **Plan something.** Click any day and add your own study event. | a calendar event created by "You" appears (calendar refetch / create success) |

### Act 5 — Files

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 12 | GATED | `bento-files-maximize` | **Now the Files.** Maximize it — this is your Bubble's shared shelf: notes, past exams, PDFs. | files bento becomes focused/maximized (`focused === 'files'`) |
| 13 | GATED | `file-syllabus` | **Take a peek.** Open **Syllabus.pdf** — it previews right here, no download needed. | the file viewer opens for that file |
| 14 | GATED | `file-share-to-chat` | **Found something useful?** Share it straight to the chat for everyone. *(Or skip — your call.)* | file shared to chat **or** Skip-step (this one is skippable) |

### Act 5½ — Live rooms (teaser)

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 15 | COACHMARK | `bubble-live-room` | **And when it's crunch time… 🎥** A Bubble can open a **live room** — video, voice, and a shared whiteboard, all together in real time. Don't start one now — but give it a try later while you explore! | Next |

### Act 6 — Back Home → Explore

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 16 | GATED | `nav-home` | **Let's head back Home.** Click the Home button. | route is Home / hub feed (no Bubble selected) |
| 17 | COACHMARK | `home-section-discovery` | **Explore.** This is where Bubble.up finds the study groups you'll *click* with. Right now it only shows **Trending** Bubbles — because we don't know you yet. | Next |
| 18 | MODAL | — | **So let's get to know you.** Bubble.up learns who you are from quick **popup questions** and how you act in the app, then matches you to Bubbles where you'll fit best. | Next |

### Act 7 — Build your profile

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 19 | GATED | `quiz-prompt` | **Here's one now.** Answer this quick question — it's how your match profile starts forming. | the popup quiz is answered (quiz `answered` count rose). *Tour triggers the prompt via `quizPromptStore`.* |
| 20 | GATED | `nav-settings` | **Want sharper matches?** Let's answer a few more. Head to **Settings**. | route is Settings |
| 21 | GATED | `settings-tab-matching` | Open the **Matching** tab. | Matching tab active |
| 22 | GATED | `matching-quiz` | **Answer until your profile's ready.** Each answer sharpens who we match you with. Keep going until you're *matchable*. | `answered` ≥ the matchable threshold (e.g. enough for `matching_confidence` to cross `matched-display-threshold`) |
| 23 | GATED | `nav-home` | **Now the magic — back Home.** | route is Home |
| 24 | GATED | `discovery-matched-card` | **Look at that ✨** Real **matched** Bubbles now, each with your fit score. Join the one that calls to you. | "You" joins a Bubble from Explore (`joinGroup` success) |

### Act 8 — Sandbox handoff

| # | type | anchor | title / body | advance when |
|---|---|---|---|---|
| 25 | MODAL | — | **That's the tour! 🎉** The campus is yours now — explore Bubbles, chat, plan events, build your profile. Poke at everything. Nothing here is real, so go wild. 🫧 | **Start exploring** → tour ends, sandbox begins |

---

## 6. Edge cases & rules

- **Skip anytime.** Skip/Exit closes the overlay and leaves the visitor exactly
  where they are, world intact. The tour never re-triggers in the same session.
- **GATED steps can't be brute-forced past.** Next stays disabled until the real
  action fires; a small "do this to continue" hint + pulse keeps it unambiguous.
  Each GATED step gets a soft **"Skip this step"** link so a stuck visitor is never
  trapped (step 14, share-to-chat, is explicitly optional; the others advance on the
  real action).
- **Anchors that scroll off-screen** must `scrollIntoView` before the spotlight
  paints (engine responsibility).
- **Matchable threshold (step 22)** should be tuned so 4–6 answers is enough — long
  enough to feel real, short enough not to bore. Confirm against
  `app.matching.confidence` + `matched-display-threshold` when wiring.
- **Step 24 depends on step 22 producing MATCHED cards.** If the seeded world +
  guest answers don't yield a MATCHED card, the tour falls back to pointing at the
  top Trending card with softened copy ("Join one that looks good") so it never
  dead-ends.
- **Live-room teaser (step 15) is explain-only; the sandbox room is mocked.** The
  tour never enters a room. When the visitor takes up "try it later" in the sandbox,
  the room opens to a **mock** (see §2): the full room shell with a "video is off in
  the demo" placeholder instead of a real JaaS call — so "try it later" lands on a
  real, explorable screen, never a dead button.
- **Mobile/RTL.** Tooltips reposition on small screens; all copy is i18n
  (`demo.tour.*`) and must read correctly under `dir="rtl"` (Hebrew).

---

## 7. What this needs from the rest of the system

- **`data-tour` attributes** added to the elements in §4 (the only edits to existing
  components; purely additive).
- **`tourStore`** (Zustand, ephemeral) + a thin tour engine/overlay component
  mounted once (e.g. in `App.tsx`), reading `running`/`stepIndex`.
- **Start trigger:** the **Start demo** landing CTA seeds the world, logs in "You",
  then sets `tourStore.running = true` at step 0.
- **Advance signals:** wire each GATED step to the existing store/state changes noted
  in §5; add a minimal tour event bus only for actions with no observable signal.
- **i18n:** all `demo.tour.*` keys in `en.json` **and** `he.json`.

> Tour content is final-ish; wording can be polished in i18n without touching the
> step graph. The step graph (types, anchors, advance signals) is the contract the
> engine implements.
