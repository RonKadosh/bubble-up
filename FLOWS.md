# Bubble.up — User Flow Catalog

A click-path map of every user-facing flow in the app, written for QA to walk
through the visuals across **viewports** (Mobile / iPad / Desktop / Wide),
**themes** (Light / Dark), and **languages** (English LTR / Hebrew RTL).

**Cross-cutting checks on every screen:**
- Light/Dark toggle (sidebar moon/sun icon).
- English (LTR) vs Hebrew (RTL): sidebar flips to the right edge, drawers slide
  from the opposite side, chevrons / send-arrow rotate, and `datetime-local` /
  email / password inputs stay force-`dir="ltr"`.
- The 4 viewports. Breakpoints: `phone` / `tablet` / `desktop (1200px)` / wide.
- The left **rail sidebar** is always-on (icon-only, 4.5rem).

---

## 1. Auth & Entry

**1.1 Sign in**
User lands on `/login` → System presents the bubble-decorated split screen
(photo + empty bubbles float only at `desktop+`; hidden on phone/tablet) +
Sign-in card → User types email + password → User clicks "Show/Hide" on password
→ System toggles plaintext → User clicks **Sign in** → submitting state → on
success navigates to `/dashboard`; on failure shows red error banner in the card.

**1.2 Register**
User clicks "Create one / Register" → System swaps card to Register mode (adds
Display name field + password hint) → User fills all three → clicks **Create
account** → success → `/dashboard` (lands in onboarding wizard).

**1.3 Bad credentials / duplicate email**
Wrong password → "invalid credentials" banner. Register existing email → "email
already exists" banner. Validation field errors render as "field: message • …".

**1.4 Logout**
User clicks the red **Logout** icon (bottom of rail) → token revoked → auth
cleared → navigates to `/login`.

**1.5 Guarded redirects**
Unauthenticated user hits any deep URL → bounced to `/login`. Unknown URL →
redirect to `/dashboard`.

---

## 2. Onboarding wizard (gates the whole app)

A not-yet-onboarded user sees the **wizard instead of the dashboard feed**, and
locked nav icons (greyed + lock badge + "unlocks at step N" tooltip).

**2.1 Walk the 5 levels**
L1 Welcome with endowed progress bar (~17%, grey→light-blue gradient, fills
left-to-right; RTL fills right-to-left) → **Next** → L2 Profile (gated) → CTA →
`/settings` carrying a guide callout.

**2.2 Profile step (arrival guide)**
On `/settings`, an **OnboardingGuide banner** appears at top (iridescent ring,
primary tint) → User edits + saves affiliation → banner dot turns green +
"Back to setup" button appears → returns to `/dashboard` wizard, L2 shows ✓.

**2.3 Enroll step**
L3 CTA → `/academy` with enroll guide banner → user enrolls → returns → done.

**2.4 Bubble step (inline)**
L4 → **Find a Bubble** → loads discoverable bubbles inline (member counts, Join,
Full state) → **Join** → step unlocks. OR **Create a Bubble** → `/groups` with
create form pre-opened.

**2.5 Matching step + celebration**
L5 shows the **ReliabilityMeter** + "Answer a Daily Drop" → the floating
**QuizPrompt** pops (bottom-start) → answer → **Finish** enables → confetti /
bubbles rise, "You're all set!", bar fills 100% → unmounts into the feed. Each
completed level (2/3/4) fires a bubble-rise **celebration badge**.

**2.6 Back / locked guards**
**Back** disabled on L1. Jumping to a locked route via URL → bounced to
`/dashboard`.

---

## 3. Dashboard (feed)

**3.1 Browse the four sections**
4 always-present stacked sections: **Live / Upcoming / Activity / Discovery**,
each with header + cards or a dashed empty hint. Loading shows skeletons.

**3.2 Live session card**
Click a **liveSession / liveGroupRoom** card (or "Hop in"/"Join") → into
`/sessions/:id` or `/rooms/:id`.

**3.3 Upcoming / activity / unread / file cards**
Clicking routes to `/groups` with that Bubble pre-selected.

**3.4 Discovery preview modal**
Click a **recommendation** card (MATCHED % or TRENDING badge) →
**PublicBubbleModal** (avatar, badge, course, about, member/visibility chips) →
**Join** → joins + lands in hub; Private bubble shows note + no join; errors
(full / already member / not enrolled) inline.

**3.5 Empty Discovery (two branches)**
No enrollments → **Browse courses** → `/academy`. Enrolled but no bubbles →
**Start a Bubble** → `/groups` with create form open.

---

## 4. Academy & Course

**4.1 Three-pane browse (desktop) / stacked (mobile)**
`/academy` → **My Courses** carousel (scroll arrows on overflow; hidden on phone)
+ 3-column pane grid: Departments → Courses → Detail (single column on phone) →
click Department → Course → Detail (code/name/credits/offerings/description,
enrolled badge).

**4.2 Term filter**
Change the **Term** dropdown in the header → list refilters.

**4.3 Enroll / unenroll**
**Enroll for <term>** → enrolled badge + "Open course". **Unenroll** →
ConfirmDialog. Error toasts: no current offering / no current term / affiliation
required.

**4.4 My Courses card → course**
**Open course** on a carousel card, or × unenroll icon (→ confirm).

**4.5 Course page (gated)**
`/courses/:id` → not enrolled → **GatedCard** (lock hero) → **Enroll** → reloads
to ready.

**4.6 Course page (ready) — groups browse**
**CourseHero** + Info "soon" card + **Study groups** with live filters (search,
visibility dropdown, "joined only" checkbox) → **Join** (→ "Open") / **Open** →
`/groups`. Empty/filtered states; **Start the first Bubble** → `/groups` create
form pre-targeted to this course/dept.

---

## 5. The Bubble Hub (`/groups`) — core surface

Densest responsive screen. **Desktop+**: left bubble sidebar + header + members
strip + **bento grid** (one big cell + two compact). **Phone**: sidebar becomes a
slide-over drawer; bento becomes a **tab bar** (Chat / Calendar / Files).

**5.1 Pick a bubble**
Empty state "pick from sidebar" (+ "Open bubble list" on mobile) → click a bubble
(active row gets iridescent ring; unread badge, live red ping marker) → header +
strip + panels render.

**5.2 Create a bubble**
**New Bubble** → form expands → name/description, university label, Department →
Course cascade, Public/Private radios, Max size stepper (4–10) → **Create** →
created + auto-selected. Deep-linked create pre-fills course.

**5.3 Mobile sidebar drawer**
Tap hamburger (or "Open bubble list") → drawer slides from start edge with
backdrop → pick bubble → auto-closes.

**5.4 Bento focus switching (desktop)**
Click a compact cell's **Maximize** → grid re-lays (chat = 2fr left col;
calendar/files = 2fr right col). Three distinct grid templates — test each in RTL.

**5.5 Phone tabs**
Tap **Chat / Calendar / Files** → panel swaps in (iridescent-framed card).

**5.6 Join / Hop in (non-member)**
Header shows **Hop in** (or disabled **Full**). Panels show "join to view" until
member.

**5.7 Bubble Info drawer (members + management)**
Click the **members strip** → **BubbleInfoDrawer** slides down over bento with
backdrop → roster (avatar, online dot, last-seen, role) → click a member →
**UserProfileCard**. Owner sees per-row **Make owner** / **Remove**, **Add member
by UUID**, and **Leave** / **Pop** (danger glow).
- Transfer ownership → confirm.
- Leave (owner must transfer/empty first → error).
- **Pop bubble** → confirm → deletes (error if not empty).

**5.8 Schedule a live room**
Member clicks header **Create Live** → **ScheduleRoomModal** (start, duration,
description) → **Schedule** → within 15-min window shows **"Preparing live…"**
overlay, then header CTA flips to **Join live** (pulsing red dot).

**5.9 Join live**
**Join live** (red ping) → resolves room → `/rooms/:id` (study) or
`/sessions/:id` (expert). Errors: not yet open / ended / not member / jitsi not
configured (red banner top of hub).

---

## 6. Chat panel (inside hub & rooms)

**6.1 Send a message**
Type in contentEditable composer → Enter sends (Shift+Enter newline) → own bubble
right-aligned (brand gradient); others left with avatar + name. Send arrow rotates
in RTL. Rate-limit error surfaces.

**6.2 Emoji**
Click the **bubble-emoji** button → popover → pick → inserted inline.

**6.3 Composer "+" menu**
Click **＋** → **Share link** or **Create poll**.

**6.4 Share calendar event / file to chat**
Share link → **LinkPickerModal** (Calendar / File tabs) → select + optional
caption → **Share** → LINK card. Calendar cards: type badge + time + **Enter
Room** (disabled "Opens in N min" / "Ended"). File cards: icon/size, clickable →
focuses Files tile + opens file.

**6.5 Polls**
Create poll → **PollComposerModal** (question + options + allow-multiple) → Poll
card → vote (live counts over WS) → creator **Close** (confirm).

**6.6 Reply & jump**
Hover → **↩** → reply preview bar → send → quoted snippet → click quote →
scrolls + flashes parent (focus-loads if off-screen).

**6.7 Pin**
Hover → **📌** → pinned strip at top → click to jump, or **View all** →
**PinnedListModal** → unpin.

**6.8 Infinite scroll + unread divider**
Scroll to top → older page prepends (position preserved). "New messages" divider
at first-unread; mark-read at bottom; sidebar badge clears.

**6.9 Timestamp popover**
Tap/hover a message time → full date tooltip (LTR-forced) — test in RTL bubbles.

**6.10 Room-lifecycle system messages**
"Session ends in 15 min" amber card with **Extend +15 min** (→ extended / conflict
error); "Session extended" italic line; expert-session-open card with Enter
button; join/leave italic lines.

---

## 7. Calendar panel

**7.1 Agenda (compact)**
Rolling 30-day agenda list (date chip + type badge + time + "more events").

**7.2 Month grid (focused)**
Toolbar (‹ month ›, **Today**, **New event**) + 6×7 grid (localized weekday
headers, today highlighted) → click empty day → **EventModal** create (type,
start/end, description) → **Create**. Month grid + RTL weekday order is a strong
visual story.

**7.3 Edit / delete / share event**
Click an event chip → EventModal edit (locked if started — disabled fields) →
**Save** / **Delete** (confirm; author or owner) / **Share to chat** / **Enter
Room** (study sessions, open-window gated).

---

## 8. Files panel

**8.1 Agenda (compact)**
Top-5 most recent files, read-only (icon + size + "more files").

**8.2 Browser (focused)**
Breadcrumb nav + **New folder** + **Upload** → upload (spinner; errors: too large
/ blocked type / rate-limited). Create folder (inline input, Enter/Esc;
name-taken/invalid). Navigate subfolders via breadcrumb. Delete file/folder
(confirm; uploader/owner; folder-not-empty error).

**8.3 Preview / download**
Click a previewable file → **FileViewer** inline (fullscreen toggle → fixed
overlay). Non-previewable → downloads. From a chat FileLinkCard → auto-focus
Files + navigate into folder + preview.

---

## 9. Bubble Room (`/rooms/:id`) — live video study room

**9.1 Enter & layout**
**RoomBentoShell**: header (title, live "N in call" rose pill, **Open Bubble**,
**Leave**) + Video cell (Jitsi overlay) + Whiteboard + Chat. Load errors →
centered message + "back to Bubbles".

**9.2 Whiteboard**
Excalidraw canvas (lazy ~1.5MB) → draw → strokes broadcast live; view-only if not
a writer.

**9.3 Leave / PiP persistence**
**Open Bubble** (back to hub, call continues) → Jitsi shrinks to **picture-in-
picture** pill bottom-end (hover → "Return to room") → click → re-docks into the
cell. **Leave** clears active room (PiP gone). Backend hard-close → PiP vanishes.

---

## 10. Expert Session room (`/sessions/:id`)

**10.1 Pre-open countdown**
Before video opens → **VideoCountdownPlaceholder** in the video cell → at open
time video flips in (re-fetches JWT).

**10.2 Host controls**
Host sees floating **"👥 Participants"** button bottom-end → panel → per-row
**Grant / Revoke** whiteboard write (live-synced; host always "Can draw").

**10.3 Participant whiteboard**
Non-host view-only until granted; grant flips them to writer live.

---

## 11. Experts / Booking

**11.1 Directory**
`/experts` → **Open sessions** list + **Experts** grid (search by
headline/bio/tag) → click expert → `/experts/:userId`.

**11.2 Enroll a group into an open session**
Owner clicks **Enroll** on an OpenSessionCard → inline group picker → pick owned
group → success/info, or error (already enrolled / capacity / closed / schedule
conflict / not owner). Non-owners see "no owned groups".

**11.3 Public expert profile + request booking**
**Request a booking** (disabled w/ tooltip if no owned group) →
**RequestBookingModal** (group, start/end, message) → **Send** → confirmation;
errors (bad range / not owner / not verified).

**11.4 Become an expert**
Non-expert hits an expert-only route → `/become-expert` → headline/bio/tags →
**Submit** → auto-verified, role→EXPERT, sidebar gains Expert hub, lands on
`/expert`. Existing profile → redirect.

**11.5 Expert dashboard**
`/expert` → profile snippet (+ verification badge) + **Booking requests**
(Accept/Reject) + **Sessions** (status chips, **Enter**, **Cancel**→confirm) +
**Schedule** → **ScheduleExpertSessionModal** (title/desc/start/end/capacity) →
Create. Edit profile → `/expert/profile/edit`.

**11.6 Booking inbox/outbox**
`/expert/requests` (inbound) or `/bookings` (outbound). With both roles, a
**toggle** flips Inbound/Outbound. Pending: Accept/Reject (inbound) or
**Withdraw** (outbound). Status chips.

---

## 12. Settings & Profile

**12.1 Settings tabs**
`/settings` → tabs **Profile / Matching / Language** (Button pills).

**12.2 Edit own profile**
Profile tab → **Edit** → form (display name, bio, university→department cascade,
enrollment year) → **Save**. Upload avatar (type/size errors) / Remove avatar.

**12.3 Matching strength**
Matching tab → **ReliabilityMeter** + answered-count.

**12.4 Language switch (the big RTL story)**
Language tab → click **עברית** → whole app flips to RTL instantly (each option
rendered in its own `dir`). Switch back. Do this on every screen.

**12.5 View another user**
`/profile/:userId` → read-only card (or "cannot view" if hidden). Own id →
redirects to `/settings`.

---

## 13. Admin (ADMIN role only)

**13.1 Admin hub tabs**
`/admin` → tabs **Overview / Users / Catalog / Groups / Quiz** + "Expert
verification →" link.

**13.2 Users**
Search by email/name + role filter + paginate → click user → modal → **Change
role** (STUDENT/EXPERT/ADMIN).

**13.3 Catalog / Groups / Quiz**
Manage universities/departments/courses; browse groups; manage quiz questions.

**13.4 Expert verification**
`/admin/experts` → status tabs (PENDING/VERIFIED/REJECTED) → click application →
detail modal → **Verify / Revoke / Reject (delete+demote)** → **ReasonPromptModal**
→ confirm.

---

## 14. App-wide floating elements (test on top of every page)

- **QuizPrompt** — bottom-start card, appears on cadence post-onboarding; answer
  to dismiss.
- **UserProfileCard** — centered modal from any avatar/name click; Esc/backdrop
  closes.
- **PersistentVideo PiP** — bottom-end pill whenever a call is active and you're
  off the room page.
- **Sidebar tooltips** — hover each rail icon (label slides from icon, start-side;
  RTL flips).
- **Help / Report** rail icons → "coming soon" alert.
- **Error banners** — hub top red bar, academy warning bars, modal inline errors
  (Light/Dark contrast).
- **Locked nav** (mid-onboarding) — greyed icons + lock badge + tooltip.

---

## Highest-value responsive / RTL / theme stress screens

1. **`/groups` hub** — sidebar drawer vs inline, 3 bento layouts vs phone tabs,
   info drawer, members strip.
2. **Login** — floating bubbles only at desktop+, card padding scale.
3. **Academy** — 3-pane → single column, carousel arrows.
4. **Calendar month grid** — weekday order + event chips in RTL.
5. **Chat** — bubble alignment, send arrow + timestamp tooltip direction in RTL,
   link/poll/file cards in Light/Dark.
6. **Rooms** — bento shell + PiP docking, countdown, host-controls floating panel.
7. **Onboarding wizard** — progress bar fill direction, celebration overlay,
   arrival guide banners.
