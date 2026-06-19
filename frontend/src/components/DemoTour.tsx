import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { driver, type Driver, type Side } from 'driver.js'
import 'driver.js/dist/driver.css'
import { DEMO_MODE, demoHeartbeat } from '../api/demo'
import { useTourStore } from '../store/tourStore'
import { useBentoLayoutStore } from '../store/bentoLayoutStore'
import { useQuizPromptStore } from '../store/quizPromptStore'
import { useAuthStore } from '../store/authStore'

/**
 * The game-tutorial overlay for the demo. driver.js paints the spotlight + popover;
 * we own step advancement (so we can navigate across routes and auto-advance GATED
 * steps when the visitor performs the real action). The 25-step graph mirrors
 * Demo-script.md; copy is i18n (`demo.tour.s<N>.{title,body}`).
 *
 * Interaction model:
 *  - modal/coachmark steps set {@code disableActiveInteraction} so the dimmed page
 *    (including the highlighted region) is NOT clickable — you can't wander off by
 *    clicking "Join session" mid-explanation.
 *  - gated steps allow interaction with the highlighted element only, so the
 *    visitor performs the real action (open a Bubble, type a message, …).
 *
 * Also runs the demo heartbeat while a guest is signed in, keeping their world
 * alive against the idle-TTL sweep. Mounted once in the Layout; no-op unless DEMO_MODE.
 */

type StepType = 'modal' | 'coachmark' | 'gated'

interface TourStep {
  type: StepType
  /** i18n copy key under `demo.tour.*` (e.g. 's14'). Decoupled from array position
   *  so steps can be reordered without renumbering the JSON. */
  key: string
  /** data-tour anchor key; omit for a centered modal step. */
  anchor?: string
  /**
   * A second anchor that appears only after the visitor acts (e.g. the PDF viewer
   * that mounts once a file is opened). When present, the spotlight hops onto it.
   */
  revealAnchor?: string
  /** Navigate here before showing the step. */
  route?: string
  side?: Side
  /** Popover alignment along its side. Defaults to 'start'. */
  align?: 'start' | 'center' | 'end'
  /** Side effect when the step is entered (e.g. pop the quiz). */
  onEnter?: () => void
  /** GATED auto-advance: when this returns true, advance automatically. */
  watch?: () => boolean
  /** GATED auto-advance: advance when this window event fires (the real action). */
  awaitEvent?: string
  /**
   * GATED unlock: Next stays hidden until this returns true, then it appears and
   * the visitor advances manually (vs `watch`, which auto-advances). Use when the
   * real action should be required but the visitor still confirms with Next —
   * e.g. "open the PDF, then continue".
   */
  gate?: () => boolean
}

const HOME = '/groups'

// data-tour anchors are added to the real components; see Demo-script.md §4.
// `key` is the i18n copy slot (demo.tour.<key>); array order is the play order. The
// two are intentionally decoupled — "Share it" (s14 copy) plays right after "Say hi"
// so it stays in the chat-focused layout (no jarring maximize swap).
const STEPS: TourStep[] = [
  { key: 's1', type: 'modal' },                                                   // welcome
  { key: 's2', type: 'coachmark', anchor: 'home-feed', side: 'left' },            // home (spotlights the feed)
  { key: 's3', type: 'coachmark', anchor: 'home-section-live', side: 'bottom' },  // live
  { key: 's4', type: 'coachmark', anchor: 'home-section-upcoming', side: 'bottom' }, // upcoming
  { key: 's5', type: 'coachmark', anchor: 'home-section-activity', side: 'bottom' }, // activity
  { key: 's6', type: 'gated', anchor: 'sidebar-starter-bubble', side: 'left',     // open a bubble (only this row is clickable)
    watch: () => !!document.querySelector('[data-tour="bento-chat"]') },
  { key: 's7', type: 'coachmark', anchor: 'bubble-hub', side: 'left',             // this is a bubble (spotlights the open Bubble)
    onEnter: () => useBentoLayoutStore.getState().setFocused('chat') },
  { key: 's8', type: 'coachmark', anchor: 'bento-chat' },                         // chat
  { key: 's9', type: 'gated', anchor: 'chat-composer', side: 'top', align: 'end', // send a message (advances on send)
    awaitEvent: 'demo:chat-sent' },
  { key: 's10', type: 'gated', anchor: 'bento-calendar-maximize', side: 'left',   // maximize calendar
    watch: () => useBentoLayoutStore.getState().focused === 'calendar' },
  { key: 's11', type: 'gated', anchor: 'calendar-grid', side: 'top', align: 'start', // add an event
    awaitEvent: 'demo:event-created' },
  // Share an event to chat: open any event, hit "Share to chat". The view modal
  // renders inside the calendar-grid element (the spotlight hole) so it stays
  // visible + interactive over driver's overlay; advances on the real share.
  // Popover sits top-END (vs s11's top-START) so the visitor sees the task move.
  { key: 's14', type: 'gated', anchor: 'calendar-grid', side: 'top', align: 'end', // share an event to chat
    awaitEvent: 'demo:link-shared' },
  { key: 's14b', type: 'coachmark', anchor: 'bento-chat',                         // ...and it lands in the chat
    onEnter: () => useBentoLayoutStore.getState().setFocused('chat') },
  { key: 's12', type: 'gated', anchor: 'bento-files-maximize', side: 'left',      // maximize files
    watch: () => useBentoLayoutStore.getState().focused === 'files' },
  { key: 's13', type: 'gated', anchor: 'file-syllabus', revealAnchor: 'file-viewer', // open a pdf (Next unlocks once the preview is open)
    gate: () => !!document.querySelector('[data-tour="file-viewer"]') },
  { key: 's15', type: 'coachmark', anchor: 'bubble-live-room' },                  // live-room teaser
  { key: 's16', type: 'gated', anchor: 'nav-home', side: 'right',                 // back home
    watch: () => !!document.querySelector('[data-tour="home-feed"]') },
  { key: 's17', type: 'coachmark', anchor: 'home-section-discovery', side: 'bottom' }, // explore
  { key: 's18', type: 'modal' },                                                  // profile explain
  { key: 's19', type: 'gated', anchor: 'quiz-prompt', side: 'top',                // answer popup quiz (advances on the real answer)
    onEnter: () => useQuizPromptStore.getState().requestOpen(),
    awaitEvent: 'demo:quiz-answered' },
  { key: 's20', type: 'gated', anchor: 'nav-settings', side: 'right',             // go to settings (advances on arrival)
    watch: () => !!document.querySelector('[data-tour="settings-tab-matching"]') },
  { key: 's21', type: 'gated', anchor: 'settings-tab-matching', route: '/settings', // open the Matching tab (advances when it opens)
    watch: () => !!document.querySelector('[data-tour="matching-quiz"]') },
  { key: 's22', type: 'gated', anchor: 'matching-quiz', side: 'right',            // build profile until matchable
    awaitEvent: 'demo:matchable' },
  { key: 's23', type: 'gated', anchor: 'nav-home', side: 'right',                 // back home (no route pin — it would bounce back here)
    watch: () => !!document.querySelector('[data-tour="home-feed"]') },
  { key: 's24', type: 'coachmark', anchor: 'home-section-discovery', side: 'top', align: 'start' }, // look at that (explain)
  // Choose + join a matched Bubble: click a matched card → preview modal → Join. The
  // modal (z-50, below driver's overlay) is revealed by hopping the spotlight onto
  // it; its Cancel/close are locked during the tour. Advances on the real join, so
  // the visitor ends with 2 Bubbles.
  { key: 's24b', type: 'gated', anchor: 'home-section-discovery', side: 'top', align: 'end', // choose one + Join
    revealAnchor: 'bubble-join-modal', awaitEvent: 'demo:joined-match' },
  { key: 's25', type: 'modal' },                                                  // sandbox handoff
]

// The step that itself pops the "Daily Drop" quiz; suppress the quiz until then so
// it can't surface mid-tour. Derived from the key so reordering can't drift it.
const QUIZ_STEP_INDEX = STEPS.findIndex((s) => s.key === 's19')

const HEARTBEAT_MS = 3 * 60 * 1000

export function DemoTour() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const running = useTourStore((s) => s.running)
  const stepIndex = useTourStore((s) => s.stepIndex)
  const accessToken = useAuthStore((s) => s.accessToken)
  const driverRef = useRef<Driver | null>(null)

  // ── Heartbeat: keep the guest's world alive while signed in ──────────────────
  useEffect(() => {
    if (!DEMO_MODE || !accessToken) return
    void demoHeartbeat()
    const id = window.setInterval(() => void demoHeartbeat(), HEARTBEAT_MS)
    return () => window.clearInterval(id)
  }, [accessToken])

  // ── Drive the current step ───────────────────────────────────────────────────
  useEffect(() => {
    if (!DEMO_MODE) return
    const { next, stop } = useTourStore.getState()

    // Fresh driver per step so disableActiveInteraction can vary by step type.
    driverRef.current?.destroy()
    driverRef.current = null
    if (!running) { useQuizPromptStore.getState().setSuppressed(false); return }

    const step = STEPS[stepIndex]
    if (!step) { stop(); return }

    // Hold the "Daily Drop" quiz back until the tour reaches its matching phase.
    useQuizPromptStore.getState().setSuppressed(stepIndex < QUIZ_STEP_INDEX)

    // Cross-route: get on the right page first, then let the effect re-run.
    if (step.route && location.pathname !== step.route) {
      navigate(step.route, { state: { home: true } })
      return
    }

    step.onEnter?.()

    const isLast = stepIndex === STEPS.length - 1
    // watch / awaitEvent steps withhold Next and AUTO-advance on the real action. A
    // `gate` also withholds Next, but only UNLOCKS it (the visitor then clicks Next).
    // In all three the "Skip tutorial" link is the escape hatch.
    const autoAdvance = step.type === 'gated' && (!!step.watch || !!step.awaitEvent)
    const hasGate = step.type === 'gated' && !!step.gate
    let nextUnlocked = !(autoAdvance || hasGate)

    const d = driver({
      allowClose: false,
      // Block clicks on the dimmed page (incl. the highlighted region) unless this
      // is a gated step where the visitor must act on the highlighted element.
      disableActiveInteraction: step.type !== 'gated',
      overlayColor: 'rgba(17, 12, 34, 0.5)',
      stagePadding: 6,
      stageRadius: 14,
      popoverClass: 'bubbleup-tour',
      // "Skip tutorial" is a fixed top-right control (rendered below), not in the
      // popover — so it never crowds the step copy and is always reachable.
    })
    driverRef.current = d

    // Rebuilt on demand so a gate can reveal Next after the visitor acts. The tour is
    // forward-only — no Back button (it confused more than it helped).
    const buildPopover = () => {
      const buttons: ('next' | 'previous')[] = []
      if (nextUnlocked) buttons.push('next')
      return {
        title: t(`demo.tour.${step.key}.title`),
        description: t(`demo.tour.${step.key}.body`),
        side: step.side ?? ('bottom' as Side),
        align: step.align ?? 'start',
        showButtons: buttons,
        nextBtnText: isLast ? t('demo.tour.finish') : t('demo.tour.next'),
        onNextClick: () => { if (isLast) stop(); else next() },
      }
    }

    // Wait for the anchor to mount (route just changed, panel still rendering),
    // then highlight. Modal steps (no anchor) show a centered popover.
    let tries = 0
    let timer = 0
    let centeredShown = false
    let lastEl: HTMLElement | null = null
    // Re-cut the spotlight when the highlighted element changes size (e.g. the
    // matching card grows as the Q&A options load), so the hole tracks its bounds.
    const resizeObs = new ResizeObserver(() => {
      const s = useTourStore.getState()
      if (s.running && s.stepIndex === stepIndex) driverRef.current?.refresh()
    })
    const find = (key?: string) =>
      key ? document.querySelector<HTMLElement>(`[data-tour="${key}"]`) : null
    const show = () => {
      const s = useTourStore.getState()
      if (!s.running || s.stepIndex !== stepIndex) return
      // Prefer the reveal anchor (e.g. the PDF viewer) once it has mounted, so the
      // spotlight hops onto it after the visitor opens the file; else the base anchor.
      const revealEl = find(step.revealAnchor)
      const el = revealEl ?? find(step.anchor)
      if (el) {
        if (el !== lastEl) {
          lastEl = el
          el.scrollIntoView({ block: 'center', behavior: 'smooth' })
          d.highlight({ element: el, popover: buildPopover() })
          resizeObs.disconnect()
          resizeObs.observe(el)
        }
        // Keep watching so we can hop onto the reveal target once it appears.
        if (step.revealAnchor && !revealEl && tries++ < 400) timer = window.setTimeout(show, 200)
        return
      }
      // No anchor yet: show a centered popover immediately (never go invisible),
      // then keep polling and snap onto the element once it mounts.
      if (!centeredShown) { d.highlight({ popover: buildPopover() }); centeredShown = true }
      if ((step.anchor || step.revealAnchor) && tries++ < 40) timer = window.setTimeout(show, 150)
    }
    show()

    // GATE: poll until the condition is met, then reveal Next and re-render the
    // popover. The visitor still advances manually (unlike `watch`).
    let gateTimer = 0
    if (hasGate) {
      const pollGate = () => {
        const s = useTourStore.getState()
        if (!s.running || s.stepIndex !== stepIndex) return
        if (step.gate!()) {
          nextUnlocked = true
          lastEl = null          // force a re-highlight so Next appears
          centeredShown = false
          show()
          return
        }
        gateTimer = window.setTimeout(pollGate, 300)
      }
      gateTimer = window.setTimeout(pollGate, 300)
    }

    // GATED auto-advance: poll the real-action signal and advance when met.
    let watchTimer = 0
    if (step.watch) {
      const poll = () => {
        const s = useTourStore.getState()
        if (!s.running || s.stepIndex !== stepIndex) return
        if (step.watch!()) { next(); return }
        watchTimer = window.setTimeout(poll, 400)
      }
      watchTimer = window.setTimeout(poll, 400)
    }

    // GATED auto-advance off a real window event (e.g. the visitor sent a message).
    let onEvent: (() => void) | null = null
    if (step.awaitEvent) {
      onEvent = () => {
        const s = useTourStore.getState()
        if (s.running && s.stepIndex === stepIndex) next()
      }
      window.addEventListener(step.awaitEvent, onEvent)
    }

    return () => {
      window.clearTimeout(timer)
      window.clearTimeout(watchTimer)
      window.clearTimeout(gateTimer)
      resizeObs.disconnect()
      if (step.awaitEvent && onEvent) window.removeEventListener(step.awaitEvent, onEvent)
    }
  }, [running, stepIndex, location.pathname, navigate, t])

  // Tear down on unmount.
  useEffect(() => () => { driverRef.current?.destroy() }, [])

  // The step UI is driver.js's popover. The only React-rendered chrome is an
  // always-present "Skip tutorial" pill, portaled to <body> above the overlay so
  // it's reliably clickable from any step (the tour's escape hatch).
  if (!DEMO_MODE || !running) return null
  return createPortal(
    <button
      type="button"
      onClick={() => useTourStore.getState().stop()}
      className="demo-skip-btn fixed top-4 right-4 z-[2147483647] rounded-full bg-white/95 backdrop-blur
                 border border-rose-200 text-rose-600 px-4 py-2 text-sm font-semibold
                 shadow-lg hover:bg-rose-50 hover:border-rose-300 bubble-pop"
    >
      {t('demo.tour.skip')}
    </button>,
    document.body,
  )
}
