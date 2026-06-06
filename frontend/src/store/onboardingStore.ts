import { create } from 'zustand'
import {
  acknowledgeOnboarding,
  getOnboardingStatus,
  setOnboardingCollapsed,
  setOnboardingLevel,
  type OnboardingStatus,
} from '../api/onboarding'

/** Total onboarding-wizard levels; `wizardLevel > LEVEL_COUNT` means finished. */
export const ONBOARDING_LEVEL_COUNT = 5

/**
 * Has the user finished onboarding? True once they complete the wizard
 * (`wizardLevel > 5`) or — for existing/"veteran" users whose stored level still
 * defaults to 1 — once every step is genuinely done (incl. ≥1 answered quiz, so it
 * can't fire mid-flow for a new user). The home gate + `QuizPrompt` read this.
 */
export function isOnboarded(status: OnboardingStatus): boolean {
  // Explicitly finished the wizard.
  if (status.wizardLevel > ONBOARDING_LEVEL_COUNT) return true
  // Grandfather ONLY accounts that never engaged the wizard (still at the default
  // level 1) yet already have everything set up — i.e. users who predate it. A user
  // who has advanced into the wizard must reach Finish, so completing the last quiz
  // answer doesn't auto-onboard them and skip the "you're all set" celebration.
  return status.wizardLevel === 1
    && status.studyBase.affiliationDone
    && status.studyBase.coursesDone
    && status.inBubble
    && status.reliability.answeredQuestions >= 1
}

/**
 * App surfaces that unlock progressively as the onboarding wizard advances — the
 * "dopamine" of watching the app open up. An onboarded (or still-loading) user has
 * everything unlocked.
 *
 * Two thresholds per feature, "one step apart":
 *  - ROUTE: the page is *reachable* while you're on the level that uses it (so the
 *    wizard's "Set up profile" button can open /settings during the profile step).
 *  - NAV: the sidebar icon stays locked until you *complete* that level — finishing
 *    the step is what visibly unlocks it (the "You unlocked Settings!" moment).
 */
export type LockableFeature = 'settings' | 'academy' | 'bubbles' | 'experts'

const ROUTE_UNLOCK_AT: Record<LockableFeature, number> = {
  settings: 2, // reachable at L2 (profile)
  academy: 3,  // reachable at L3 (enroll)
  bubbles: 4,  // reachable at L4 (bubble)
  experts: ONBOARDING_LEVEL_COUNT + 1,
}

export const NAV_UNLOCK_AT: Record<LockableFeature, number> = {
  settings: 3, // unlocks after completing L2 (profile)
  academy: 4,  // after completing L3 (enroll)
  bubbles: 5,  // after completing L4 (bubble)
  experts: ONBOARDING_LEVEL_COUNT + 1, // only once fully onboarded
}

/** Can the route be reached right now (for the wizard's in-step link-out)? */
export function isRouteAccessible(feature: LockableFeature, status: OnboardingStatus | null): boolean {
  if (status === null || isOnboarded(status)) return true
  return status.wizardLevel >= ROUTE_UNLOCK_AT[feature]
}

/** Is the sidebar icon unlocked (i.e. that step is completed)? */
export function isNavUnlocked(feature: LockableFeature, status: OnboardingStatus | null): boolean {
  if (status === null || isOnboarded(status)) return true
  return status.wizardLevel >= NAV_UNLOCK_AT[feature]
}

/**
 * Single owner of onboarding data: an **in-memory cache** of the `/status` response
 * (never persisted). One shared fetch backs the home gate, the wizard, and
 * `QuizPrompt`, so an onboarded user pays no redundant calls. Writes are optimistic.
 *
 * Two fetch entry points:
 *  - `ensureHydrated()` — fetch once per session (deduped). Used by *gating* readers
 *    (dashboard gate, `QuizPrompt`); an onboarded user never refetches.
 *  - `refresh()` — force a re-fetch. Used by the wizard while active (catches "you
 *    just enrolled / created a bubble") + the one-shot after the L5 answer.
 *
 * Keys are namespaced (`explainer:*`, `guide:*`); see `OnboardingGuide` + the wizard.
 */
interface OnboardingState {
  status: OnboardingStatus | null
  acknowledged: Set<string>
  collapsed: boolean
  wizardLevel: number
  hydrated: boolean
  inflight: boolean
  refresh: () => Promise<void>
  ensureHydrated: () => void
  isAcknowledged: (key: string) => boolean
  acknowledge: (key: string) => void
  setCollapsed: (collapsed: boolean) => void
  setWizardLevel: (level: number) => void
}

export const useOnboardingStore = create<OnboardingState>()((set, get) => ({
  status: null,
  acknowledged: new Set(),
  collapsed: false,
  wizardLevel: 1,
  hydrated: false,
  inflight: false,

  refresh: async () => {
    set({ inflight: true })
    try {
      const status = await getOnboardingStatus()
      set({
        status,
        acknowledged: new Set(status.acknowledged),
        collapsed: status.collapsed,
        wizardLevel: status.wizardLevel,
        hydrated: true,
      })
    } catch {
      /* best-effort; gate/wizard stay hidden on error */
    } finally {
      set({ inflight: false })
    }
  },

  ensureHydrated: () => {
    const s = get()
    if (s.hydrated || s.inflight) return
    void s.refresh()
  },

  isAcknowledged: (key) => get().acknowledged.has(key),

  acknowledge: (key) => {
    if (get().acknowledged.has(key)) return
    // Optimistic: a new Set so subscribers re-render, then persist best-effort.
    set((s) => ({ acknowledged: new Set(s.acknowledged).add(key) }))
    acknowledgeOnboarding(key).catch(() => { /* server resyncs on next refresh */ })
  },

  setCollapsed: (collapsed) => {
    set({ collapsed })
    setOnboardingCollapsed(collapsed).catch(() => { /* best-effort */ })
  },

  setWizardLevel: (level) => {
    // Optimistic: bump the level locally (incl. the cached status copy the gate
    // reads) so the UI advances without a round-trip, then persist.
    set((s) => ({
      wizardLevel: level,
      status: s.status ? { ...s.status, wizardLevel: level } : s.status,
    }))
    setOnboardingLevel(level).catch(() => { /* server resyncs on next refresh */ })
  },
}))
