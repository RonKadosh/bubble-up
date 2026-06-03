import client, { ApiSuccess } from './client'
import type { Reliability } from './matching'

/**
 * Live onboarding progress for the dashboard "Getting started" widget. Every
 * field is derived server-side from the user's real state (no stored flags), so
 * `complete` flips the moment setup is actually finished. The widget renders only
 * while `complete` is false — a veteran never sees it regardless of localStorage.
 */
export interface OnboardingStatus {
  complete: boolean
  studyBase: { affiliationDone: boolean; coursesDone: boolean }
  inBubble: boolean
  reliability: Reliability
  /** Namespaced keys the user has dismissed (e.g. `explainer:whatIsBubble`, `guide:enroll`). */
  acknowledged: string[]
  collapsed: boolean
  /** Current onboarding-wizard level (1–5; 6 = finished). */
  wizardLevel: number
}

export async function getOnboardingStatus(): Promise<OnboardingStatus> {
  const res = await client.get<ApiSuccess<OnboardingStatus>>('/onboarding/status')
  return res.data.data
}

/** Persist that the user dismissed a namespaced explainer/guide key. */
export async function acknowledgeOnboarding(key: string): Promise<void> {
  await client.post('/onboarding/ack', { key })
}

/** Persist the widget collapse preference. */
export async function setOnboardingCollapsed(collapsed: boolean): Promise<void> {
  await client.put('/onboarding/collapsed', { collapsed })
}

/** Persist the onboarding wizard's current level (1–5; 6 = finished). */
export async function setOnboardingLevel(level: number): Promise<void> {
  await client.put('/onboarding/level', { level })
}
