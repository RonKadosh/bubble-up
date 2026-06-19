# Demo World — Bubble.up Interactive Demo

This is the **single source of truth** for the data seeded into the public, no-login
demo (`demo.bubbleup.online`). It is written so a `DemoWorldSeeder` can build the
entire world from it deterministically.

> **Status:** content spec. The guided-tour *script* that walks a visitor through
> this world is a separate section (TBD, added next). This document defines only
> the **world** the script stands on.

---

## 1. How this world is seeded (the model)

- **Full world per click.** Each visitor who clicks **Start demo** gets their *own
  complete, isolated copy* of everything below — university, departments, courses,
  term, offerings, people, bubbles, files, calendar, experts, sessions — built
  fresh at click time.
- **One session = one partition.** Every visitor's world is keyed by a short random
  **session token** (e.g. 8 hex chars, call it `«tok»`). Two visitors running
  concurrently never see or corrupt each other's data because their worlds are
  entirely separate row-sets.
- **What actually needs the token** (everything else is naturally isolated because
  it hangs off the per-session university / users, whose child uniqueness is scoped
  by `universityId`):
  - `University.shortCode` — globally unique → **`BUU«tok»`** (≤16 chars: `BUU`+8 = 11 ✓).
  - `User.email` — globally unique → **`<slug>@s«tok».demo.bubble.up`**.
  - Course codes, department short-codes, term codes can stay plain — their unique
    constraints are `(universityId, …)` and the university is already per-session.
- **Cleanup.** A scheduled sweep deletes worlds older than the session TTL. Deletion
  is per-session: tag every seeded row with the session token (implementation:
  `demo_session_id` column or a side table — decided in the build plan) and delete
  the whole partition. *(Mechanism is plan-level; this doc only fixes the data.)*
- **Storage cap.** Group total file storage is capped at **25 MB** (see
  `5de39f3`). Keep each bubble's seeded files well under that — the sample assets
  below are deliberately small.

Everything below is **time-relative to seed time** (`T0` = the moment the visitor
clicks Start demo) wherever a date appears, so a freshly minted world always looks
"live" no matter when the demo is run.

---

## 2. Asset conventions (where the photos / files live)

All demo assets are **bundled in the backend image as classpath resources** under
`backend/src/main/resources/demo/`. The seeder reads each file, pushes it through
`FileStorageService.upload(...)`, and stores the returned `fileId` on the row
(`User.avatarFileId`, `StudyGroup.imageFileId`, `GroupFile.fileId`).

```
backend/src/main/resources/demo/
├── avatars/
│   ├── people/<persona-slug>.jpg        ← one per persona in §5  (48 files)
│   └── experts/<expert-slug>.jpg        ← one per expert in §10  (5 files)
├── covers/<bubble-slug>.jpg             ← OPTIONAL bubble cover; omit → generated avatar (§6)
└── files/                               ← reusable sample file bytes (§8)
    ├── syllabus.pdf
    ├── welcome.txt
    ├── lecture-notes-1.pdf
    ├── lecture-notes-2.pdf
    ├── past-exam-2025.pdf
    ├── cheat-sheet.pdf
    └── diagram.png
```

> **Photos to supply (batch-add):** the full manifest is in §12. If a persona photo
> is missing, the seeder falls back to the app's generated avatar — so a partial
> photo set still produces a working demo.

- People/expert avatars: `image/jpeg`, square, ~256–512px, < 200 KB each.
- Bubble covers: `image/jpeg`, ~1200×400, < 400 KB each. Optional.

---

## 3. University · Term · Departments

### University
| Field | Value |
|---|---|
| name | `Bubble.up University` |
| shortCode | `BUU«tok»` |
| country | `US` |

### Term (one current term, computed at T0 so offerings are always "current")
| Field | Value |
|---|---|
| code | `DEMO` |
| name | `Current Semester` |
| kind | `SPRING` |
| academicYear | year of T0 |
| startsOn | `T0 − 45 days` |
| endsOn | `T0 + 75 days` |

> Enrollment + group create/join are gated on an enrollment in the course's
> offering for the **current** term, so the term window must straddle T0.

### Departments
| name | shortCode |
|---|---|
| Science Department | `SCI` |
| Sociology Department | `SOC` |

---

## 4. Courses (8 — four per department)

Each course gets exactly one `CourseOffering` in the term above. Each course is
linked to its department via `CourseDepartment` (`primary = true`).

### Science Department
| code | name | creditPoints | description |
|---|---|---|---|
| `CS101` | Introduction to Computer Science | 4.0 | Foundations of programming, algorithms, and computational thinking. |
| `MAT110` | Calculus I | 5.0 | Limits, derivatives, integrals, and their applications. |
| `PHY120` | General Physics | 4.0 | Classical mechanics, energy, and motion. |
| `CHM130` | Organic Chemistry | 4.0 | Structure, mechanisms, and reactions of carbon compounds. |

### Sociology Department
| code | name | creditPoints | description |
|---|---|---|---|
| `SOC101` | Introduction to Sociology | 3.0 | Society, institutions, and the sociological imagination. |
| `PSY110` | Social Psychology | 3.0 | How people think, feel, and behave in social contexts. |
| `RES120` | Research Methods in Social Science | 4.0 | Survey design, qualitative methods, and statistics. |
| `ANT130` | Cultural Anthropology | 3.0 | Culture, ritual, kinship, and ethnographic fieldwork. |

---

## 5. People (persona pool)

A shared pool of 48 named personas (24 per department). Bubbles draw their members
from their own department's pool (a persona can belong to several bubbles within
that department — realistic, and keeps the photo count to 48).

- Every persona is a `STUDENT`, `universityId` = the session university,
  `departmentId` = their department, `enrollmentYear` = year of `T0`.
- `avatarFileId` ← upload of `demo/avatars/people/<slug>.jpg`.
- **CharacterRole** drives `matchingCommandService.seedCharacterAnswers(userId, roleIndex, 7)`
  so each bubble develops a distinct profile vector (→ varied MATCHED/TRENDING in
  Discovery). Role → index: `0 LEADER · 1 PLANNER · 2 EXPERT · 3 CREATIVE ·
  4 COMMUNICATOR · 5 TEAM_PLAYER · 6 CHALLENGER`.
- Enrollment: a persona is enrolled in every course on which they own or join a
  bubble (the seeder unions this from §6). Enroll **before** creating/joining.

### Science pool
| slug | displayName | role |
|---|---|---|
| `ava-cohen` | Ava Cohen | PLANNER |
| `noah-levi` | Noah Levi | EXPERT |
| `mia-katz` | Mia Katz | COMMUNICATOR |
| `liam-shapiro` | Liam Shapiro | LEADER |
| `emma-roth` | Emma Roth | CREATIVE |
| `ethan-mizrahi` | Ethan Mizrahi | CHALLENGER |
| `sara-friedman` | Sara Friedman | TEAM_PLAYER |
| `daniel-peretz` | Daniel Peretz | EXPERT |
| `tamar-ben-david` | Tamar Ben-David | PLANNER |
| `omar-haddad` | Omar Haddad | COMMUNICATOR |
| `lily-nguyen` | Lily Nguyen | CREATIVE |
| `jonah-stern` | Jonah Stern | LEADER |
| `maya-azoulay` | Maya Azoulay | TEAM_PLAYER |
| `adam-klein` | Adam Klein | CHALLENGER |
| `nicole-bar` | Nicole Bar | PLANNER |
| `ryan-oliveira` | Ryan Oliveira | EXPERT |
| `hana-suzuki` | Hana Suzuki | COMMUNICATOR |
| `leo-martin` | Leo Martin | CREATIVE |
| `priya-sharma` | Priya Sharma | LEADER |
| `yossi-gabay` | Yossi Gabay | TEAM_PLAYER |
| `zoe-kim` | Zoe Kim | CHALLENGER |
| `marco-rossi` | Marco Rossi | EXPERT |
| `dana-vardi` | Dana Vardi | PLANNER |
| `felix-braun` | Felix Braun | COMMUNICATOR |

### Sociology pool
| slug | displayName | role |
|---|---|---|
| `lena-fischer` | Lena Fischer | COMMUNICATOR |
| `amir-cohen` | Amir Cohen | LEADER |
| `grace-owens` | Grace Owens | TEAM_PLAYER |
| `tariq-aziz` | Tariq Aziz | EXPERT |
| `yael-sade` | Yael Sade | CREATIVE |
| `ben-harel` | Ben Harel | PLANNER |
| `sofia-romano` | Sofia Romano | COMMUNICATOR |
| `malik-johnson` | Malik Johnson | CHALLENGER |
| `ruth-adler` | Ruth Adler | TEAM_PLAYER |
| `ivan-petrov` | Ivan Petrov | EXPERT |
| `nina-lopez` | Nina Lopez | CREATIVE |
| `caleb-wright` | Caleb Wright | LEADER |
| `aisha-rahman` | Aisha Rahman | PLANNER |
| `theo-dubois` | Theo Dubois | COMMUNICATOR |
| `hila-regev` | Hila Regev | TEAM_PLAYER |
| `samuel-okafor` | Samuel Okafor | CHALLENGER |
| `clara-meyer` | Clara Meyer | CREATIVE |
| `josh-green` | Josh Green | LEADER |
| `fatima-ali` | Fatima Ali | PLANNER |
| `david-stein` | David Stein | EXPERT |
| `olivia-park` | Olivia Park | COMMUNICATOR |
| `rami-khoury` | Rami Khoury | TEAM_PLAYER |
| `esther-weiss` | Esther Weiss | CHALLENGER |
| `lucas-silva` | Lucas Silva | CREATIVE |

---

## 6. Bubbles (32 — four per course)

- `visibility = PUBLIC`, `status = ACTIVE`.
- **Owner** is the first member listed and is created with `MembershipRole.OWNER`;
  the rest join as `MEMBER`. Total members (owner included) = the **Members** count
  and ranges 2–7 as required.
- `maxMembers` is the cap (must be ≥ member count).
- `offeringId` = the course's offering in the current term.
- Cover image: `demo/covers/<slug>.jpg` if present, else null (generated avatar).
- A "general" chat room is auto-created with the group (see backend rules) — the
  hub uses it; no need to create rooms here.

### CS101 — Introduction to Computer Science
| slug | name | max | members (owner first) | description |
|---|---|---|---|---|
| `cs101-night-owls` | CS101 Night Owls | 8 | ava-cohen, noah-levi, mia-katz, liam-shapiro, emma-roth (5) | Late-night coding and problem-set grinding. |
| `cs101-recursion-club` | Recursion Club | 6 | noah-levi, ethan-mizrahi, sara-friedman (3) | We solve it by solving it again. |
| `cs101-debuggers` | The Debuggers | 6 | mia-katz, daniel-peretz, tamar-ben-david, omar-haddad (4) | Stuck on a bug? Bring it here. |
| `cs101-hello-world` | Hello, World! | 10 | liam-shapiro, lily-nguyen, jonah-stern, maya-azoulay, adam-klein, nicole-bar, ryan-oliveira (7) | Absolute beginners welcome. |

### MAT110 — Calculus I
| slug | name | max | members (owner first) | description |
|---|---|---|---|---|
| `mat110-limits-crew` | Limits & Beyond | 6 | tamar-ben-david, ava-cohen (2) | Conquering limits, derivatives, integrals. |
| `mat110-derivative-dojo` | Derivative Dojo | 8 | emma-roth, hana-suzuki, leo-martin, priya-sharma, yossi-gabay (5) | Daily practice — black belt by finals. |
| `mat110-integral-squad` | Integral Squad | 6 | ryan-oliveira, zoe-kim, marco-rossi (3) | Area under the curve, area under pressure. |
| `mat110-epsilon-delta` | Epsilon-Delta Gang | 5 | daniel-peretz, dana-vardi, felix-braun, nicole-bar (4) | For the rigor lovers. |

### PHY120 — General Physics
| slug | name | max | members (owner first) | description |
|---|---|---|---|---|
| `phy120-newtons-crew` | Newton's Crew | 7 | jonah-stern, liam-shapiro, maya-azoulay, adam-klein, ethan-mizrahi, sara-friedman (6) | Forces, motion, and free pizza. |
| `phy120-momentum` | Momentum | 6 | priya-sharma, leo-martin (2) | Keep the study streak going. |
| `phy120-quantum-leap` | Quantum Leap | 6 | marco-rossi, omar-haddad, hana-suzuki, zoe-kim (4) | From classical to spooky. |
| `phy120-lab-partners` | Lab Partners | 8 | yossi-gabay, ava-cohen, noah-levi, mia-katz, dana-vardi (5) | Lab reports, decoded together. |

### CHM130 — Organic Chemistry
| slug | name | max | members (owner first) | description |
|---|---|---|---|---|
| `chm130-mole-people` | The Mole People | 6 | felix-braun, dana-vardi, nicole-bar (3) | Stoichiometry support group. |
| `chm130-benzene-buddies` | Benzene Buddies | 7 | emma-roth, lily-nguyen, leo-martin, priya-sharma, hana-suzuki, adam-klein (6) | Mechanisms, rings, and reactions. |
| `chm130-titration-nation` | Titration Nation | 5 | zoe-kim, marco-rossi, ethan-mizrahi, sara-friedman (4) | Drop by drop to an A. |
| `chm130-orgo-survivors` | Orgo Survivors | 6 | tamar-ben-david, ryan-oliveira (2) | We made it past midterm 1. Barely. |

### SOC101 — Introduction to Sociology
| slug | name | max | members (owner first) | description |
|---|---|---|---|---|
| `soc101-society-now` | Society Now | 8 | lena-fischer, amir-cohen, grace-owens, tariq-aziz, yael-sade (5) | Current events through a sociological lens. |
| `soc101-norm-breakers` | Norm Breakers | 6 | amir-cohen, ben-harel, sofia-romano (3) | Studying social norms by questioning them. |
| `soc101-first-years` | First-Year Sociologists | 10 | grace-owens, malik-johnson, ruth-adler, ivan-petrov, nina-lopez, caleb-wright, aisha-rahman (7) | New to the major, learning together. |
| `soc101-coffee-and-theory` | Coffee & Theory | 6 | yael-sade, theo-dubois (2) | Espresso-fueled discussions of the classics. |

### PSY110 — Social Psychology
| slug | name | max | members (owner first) | description |
|---|---|---|---|---|
| `psy110-mind-the-group` | Mind the Group | 7 | sofia-romano, hila-regev, samuel-okafor, clara-meyer, josh-green, fatima-ali (6) | Group dynamics — studied in a group. |
| `psy110-cognitive-crew` | Cognitive Crew | 6 | caleb-wright, david-stein, olivia-park, rami-khoury (4) | Biases, heuristics, and us. |
| `psy110-experiment-club` | The Experiment Club | 5 | aisha-rahman, esther-weiss, lucas-silva (3) | Designing and dissecting classic studies. |
| `psy110-attachment-theory` | Securely Attached | 6 | nina-lopez, lena-fischer (2) | Attachment theory study circle. |

### RES120 — Research Methods in Social Science
| slug | name | max | members (owner first) | description |
|---|---|---|---|---|
| `res120-data-diggers` | Data Diggers | 7 | ben-harel, ruth-adler, ivan-petrov, malik-johnson, grace-owens (5) | Surveys, stats, and SPSS tears. |
| `res120-qualitative-circle` | The Qualitative Circle | 6 | clara-meyer, theo-dubois, hila-regev (3) | Interviews, coding, grounded theory. |
| `res120-p-value-pals` | p-value Pals | 8 | david-stein, olivia-park, rami-khoury, esther-weiss, lucas-silva, samuel-okafor (6) | Making peace with statistics. |
| `res120-methodology-mavens` | Methodology Mavens | 5 | fatima-ali, josh-green (2) | Designing bulletproof studies. |

### ANT130 — Cultural Anthropology
| slug | name | max | members (owner first) | description |
|---|---|---|---|---|
| `ant130-fieldwork-friends` | Fieldwork Friends | 7 | tariq-aziz, yael-sade, amir-cohen, sofia-romano (4) | Ethnography, notes, and stories. |
| `ant130-ritual-roundtable` | Ritual Roundtable | 6 | ruth-adler, nina-lopez, caleb-wright, aisha-rahman, ivan-petrov (5) | Rites, myths, and meaning. |
| `ant130-culture-club` | Culture Club | 6 | samuel-okafor, clara-meyer, olivia-park (3) | Cross-cultural comparison, weekly. |
| `ant130-kinship-crew` | Kinship Crew | 5 | esther-weiss, rami-khoury (2) | Mapping families and societies. |

> **Member-count distribution:** 2,3,4,5,6,7 all represented across the 32 bubbles
> (eight bubbles at each of the smaller sizes; see counts in parentheses).

---

## 7. Calendar events (per-bubble template, time-relative)

Calendar events are `ownerType = GROUP`, `ownerId = bubbleId`, `createdBy = owner`.
Rather than enumerate 32×N events, every bubble gets the same **template**, staggered
by the bubble's index `i` (0-based, in the §6 order) so worlds don't all collide on
the same wall-clock slot. `«course»` = the bubble's course name.

| # | eventType | title / description | startsAt | duration |
|---|---|---|---|---|
| E1 | `STUDY_SESSION` | "Weekly study session" | `T0 + 2 days`, 18:00, `+ (i mod 4) h` | 90 min |
| E2 | `DEADLINE` | "«course» problem set due" | `T0 + 5 days`, 23:59 | 30 min |
| E3 | `MEETING` | "Group sync — plan the week" | `T0 + 1 day`, 17:00, `+ (i mod 3) h` | 30 min |
| E4 | `EXAM` (even-`i` bubbles only) | "«course» midterm" | `T0 + 21 days`, 10:00 | 120 min |

- E1–E3 on every bubble; E4 only on even-indexed bubbles (≈16) so the calendar has
  variety without every group showing an exam.
- Expert-session events are **not** added here — `expertSessionCommandService.createSession`
  creates its own `EXPERT_SESSION` calendar event (§10).

---

## 8. Files & folders (per-bubble template)

Reusable sample bytes live in `demo/files/` (§2) and are uploaded once per bubble
(each `GroupFile` row gets its own stored copy). `uploaderId` = the bubble owner;
`uploadedAt` staggered into the recent past (`T0 − rand(1..10) days`). Folder
sibling-name uniqueness is enforced, so the names below are unique within a bubble.

**Per-bubble layout** (well under the 25 MB cap):

```
(root)
├── Syllabus.pdf            ← demo/files/syllabus.pdf        (application/pdf)
├── Welcome.txt             ← demo/files/welcome.txt         (text/plain)
├── 📁 Lecture Notes
│   ├── Week-1-Notes.pdf    ← demo/files/lecture-notes-1.pdf (application/pdf)
│   └── Week-2-Notes.pdf    ← demo/files/lecture-notes-2.pdf (application/pdf)
├── 📁 Past Exams
│   └── 2025-Midterm.pdf    ← demo/files/past-exam-2025.pdf  (application/pdf)
└── 📁 Resources            ← only on bubbles with ≥ 4 members
    ├── Cheat-Sheet.pdf      ← demo/files/cheat-sheet.pdf     (application/pdf)
    └── Diagram.png          ← demo/files/diagram.png         (image/png)
```

- Folders → `GroupFolder` rows (`parentId = null` for the three top-level folders).
- Root files have `folderId = null`; foldered files point at their folder.
- "Resources" folder only on bubbles with ≥4 members, to vary the file trees.

**Sample asset library to supply** (`demo/files/`): `syllabus.pdf`, `welcome.txt`,
`lecture-notes-1.pdf`, `lecture-notes-2.pdf`, `past-exam-2025.pdf`, `cheat-sheet.pdf`,
`diagram.png`. Keep each < 1 MB.

---

## 9. Seeded chat messages (per-bubble chat history)

So a Bubble's chat reads as *alive* the instant the tour opens it, every bubble gets
a short **backdated** message history in its general chat room.

**How it's written (text only — no assets):**
- Written as `ChatMessage` rows **directly** via `ChatMessageRepository` (the
  `LoadTestSeeder` precedent), because `ChatCommandService.sendMessage` stamps
  `sentAt = now()` and we need *past* timestamps.
- `roomId` = the bubble's auto-created general room; `messageType = TEXT`;
  `senderId` = the bubble's own members, **round-robin** (owner included).
- `sentAt` staggered across **`T0 − 7 days` … `T0 − 2 h`**, ascending, so the
  history reads chronologically and the newest line is recent.
- Member `joinGroup` already posts `SYSTEM_JOIN` lines — those interleave naturally;
  we don't add them here.

**Per-bubble template:** **4–6 messages** drawn in order from the generic pool below,
cycling senders through the member list. `«course»` = the bubble's course name
(e.g. "Calculus I") where the snippet interpolates it.

### Generic snippet pool
| # | message |
|---|---|
| 1 | Hey everyone 👋 glad we got this Bubble going! |
| 2 | When are we meeting this week? |
| 3 | Thursday evening works for me 🙌 |
| 4 | I dropped my «course» notes in Files 📄 |
| 5 | Anyone get Q3 on the problem set? 😅 |
| 6 | Same, totally stuck on that one — let's go over it together |
| 7 | I added a study session to the calendar, check it out 📅 |
| 8 | Thanks, that really helped 🙏 |
| 9 | I'll bring snacks 🍪 |
| 10 | Good luck on the «course» midterm everyone! 🍀 |

> Seeder picks a contiguous 4–6 slice per bubble (varying the start index by bubble
> index so they're not all identical), interpolates `«course»`, and assigns senders
> round-robin. Bubbles with 2 members simply alternate the two senders.

### Curated conversation — `cs101-night-owls` (the tour's starter bubble)
This is the chat the tour opens onto, so it's hand-written to read naturally. Senders
are its members (ava-cohen = owner, then noah-levi, mia-katz, liam-shapiro, emma-roth),
backdated ascending into the last few days:

| sender | message |
|---|---|
| ava-cohen | Hey Night Owls 🦉 welcome to the Bubble! |
| noah-levi | Finally a place to grind CS101 together 😄 |
| mia-katz | I uploaded the Week 1 + Week 2 notes to Files 📄 |
| liam-shapiro | Legend 🙏 anyone else lost on the recursion exercise? |
| ava-cohen | Let's cover it at the study session — added it to the calendar 📅 |
| emma-roth | I'll be there. Bringing snacks 🍪 |

---

## 10. Experts & sessions

### Experts (5)
`ExpertProfile` rows on dedicated expert users (role `STUDENT` + expert profile,
exactly as `DemoSeeder` does). Four are `VERIFIED`; one stays `PENDING` so the
`/admin` verification queue isn't empty. `avatarFileId` ← `demo/avatars/experts/<slug>.jpg`.

| slug | displayName | status | headline | bio | tags |
|---|---|---|---|---|---|
| `prof-hannah-gold` | Prof. Hannah Gold | VERIFIED | Algorithms & data structures, ex-FAANG | Twelve years teaching CS fundamentals. I make Big-O click. | `algorithms`, `data-structures`, `computer-science` |
| `dr-omar-said` | Dr. Omar Said | VERIFIED | Physicist — classical mechanics | From free-body diagrams to orbital motion, one clear step at a time. | `physics`, `mechanics`, `calculus` |
| `dr-rachel-stone` | Dr. Rachel Stone | VERIFIED | Organic chemistry tutor | Reaction mechanisms without the memorization panic. | `chemistry`, `organic-chemistry`, `lab-safety` |
| `prof-daniel-roth` | Prof. Daniel Roth | VERIFIED | Sociologist — social theory | Durkheim to Bourdieu, and why it matters today. | `sociology`, `social-theory`, `research` |
| `dr-lily-chen` | Dr. Lily Chen | **PENDING** | Quantitative research methods | Survey design and statistics for social scientists. | `statistics`, `research-methods`, `data-analysis` |

### Sessions (3 — time-relative; each auto-creates its EXPERT_SESSION calendar event)
| host | title | description | startsAt | duration | capacity | enrolled bubbles (by owner) |
|---|---|---|---|---|---|---|
| `prof-hannah-gold` | CS Algorithms Exam Crunch | Past-paper walkthrough + your hardest questions before the CS final. | `T0 + 15 min` | 120 min | 4 | `cs101-night-owls` (ava-cohen), `cs101-debuggers` (mia-katz) |
| `dr-omar-said` | Mechanics Problem-Solving Clinic | We work through the trickiest force and energy problems together. | `T0 + 2 days` | 90 min | 6 | `phy120-newtons-crew` (jonah-stern), `phy120-lab-partners` (yossi-gabay) |
| `prof-daniel-roth` | Sociological Theory: Office Hours | Bring a theorist you're stuck on; we'll untangle it live. | `T0 + 7 days` | 60 min | 8 | `soc101-society-now` (lena-fischer), `ant130-fieldwork-friends` (tariq-aziz) |

> The first session starts in **15 minutes** and (per the room entry window) is
> already enterable — it's the one the tour points at as "happening now."
> `enrollGroup(sessionId, groupId, ownerId)` enrolls each listed bubble.

---

## 11. The visitor ("You")

Created fresh per session, *after* the world above exists:

| Field | Value |
|---|---|
| displayName | `You` |
| email | `you@s«tok».demo.bubble.up` |
| role | `STUDENT` |
| university / department | session university / `SCI` |
| enrollmentYear | year of `T0` |
| avatar | generated (no photo) |

- **Enrollments:** `CS101` + `SOC101` (one course per department) — so Discovery
  has candidate bubbles in both.
- **Starter membership:** auto-joined to **`cs101-night-owls`** (owner ava-cohen),
  bringing it to 6/8. This guarantees the tour opens onto a bubble whose chat,
  files, and calendar are already populated.
- **Discovery candidates** (enrolled, not a member): the other CS101 bubbles
  (`cs101-recursion-club`, `cs101-debuggers`, `cs101-hello-world`) and all SOC101
  bubbles — these are exactly what the matching feed ranks for "You."
- **Optional matching profile:** the tour script may seed "You" with a character
  role (e.g. `LEADER` via `seedCharacterAnswers`) so some candidates render as
  MATCHED rather than all TRENDING. Left to the script (§ next).

---

## 12. Photo / asset manifest (batch-add checklist)

Drop these into the paths in §2. Missing persona/expert photos fall back to the
generated avatar; missing sample files would break that bubble's file tree, so the
seven `demo/files/` assets are **required**, the rest optional-but-recommended.

**People avatars — `demo/avatars/people/` (48, optional):**
`ava-cohen` `noah-levi` `mia-katz` `liam-shapiro` `emma-roth` `ethan-mizrahi`
`sara-friedman` `daniel-peretz` `tamar-ben-david` `omar-haddad` `lily-nguyen`
`jonah-stern` `maya-azoulay` `adam-klein` `nicole-bar` `ryan-oliveira` `hana-suzuki`
`leo-martin` `priya-sharma` `yossi-gabay` `zoe-kim` `marco-rossi` `dana-vardi`
`felix-braun` `lena-fischer` `amir-cohen` `grace-owens` `tariq-aziz` `yael-sade`
`ben-harel` `sofia-romano` `malik-johnson` `ruth-adler` `ivan-petrov` `nina-lopez`
`caleb-wright` `aisha-rahman` `theo-dubois` `hila-regev` `samuel-okafor` `clara-meyer`
`josh-green` `fatima-ali` `david-stein` `olivia-park` `rami-khoury` `esther-weiss`
`lucas-silva`  → `<slug>.jpg`

**Expert avatars — `demo/avatars/experts/` (5, optional):**
`prof-hannah-gold` `dr-omar-said` `dr-rachel-stone` `prof-daniel-roth` `dr-lily-chen`
→ `<slug>.jpg`

**Bubble covers — `demo/covers/` (32, optional):** `<bubble-slug>.jpg` for any of the
slugs in §6 you want a custom cover on; omit for a generated avatar.

**Sample files — `demo/files/` (7, REQUIRED):** `syllabus.pdf` `welcome.txt`
`lecture-notes-1.pdf` `lecture-notes-2.pdf` `past-exam-2025.pdf` `cheat-sheet.pdf`
`diagram.png`

---

## 13. Seeding order (so foreign keys resolve)

1. University → Term → Departments → Courses (+ `CourseDepartment`) → Offerings.
2. Personas (§5) + expert users (§10). Upload avatars, set `avatarFileId`.
3. Enrollments for every persona on every course they touch (§5 note).
4. Bubbles (§6): create as owner, others join. (General chat room auto-created.)
5. `seedCharacterAnswers` for each persona by role (§5) — gives bubbles their vectors.
6. Calendar events per bubble (§7).
7. Folders then files per bubble (§8).
8. Backdated chat history per bubble (§9), into each general room.
9. Expert profiles + verification state (§10), then sessions + group enrollments (§10).
10. The visitor "You" (§11): user → enrollments → starter-bubble join → optional profile.

> Every row created above is tagged with the session token for later cleanup (§1).
