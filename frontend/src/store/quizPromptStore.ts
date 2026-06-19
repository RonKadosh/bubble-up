import { create } from 'zustand'
import { DEMO_MODE } from '../api/demo'

/**
 * One-way trigger to surface the floating QuizPrompt on demand (e.g. the
 * onboarding "Answer your first Daily Drop" card). Not persisted — it's an
 * ephemeral signal. `QuizPrompt` watches `openSignal` and re-polls when it bumps;
 * if no question is available (cooldown/exhausted) the request is a quiet no-op.
 */
interface QuizPromptState {
  openSignal: number
  /** When true, the QuizPrompt stays hidden and stops polling. The demo tour sets
   *  this to keep the "Daily Drop" from popping until the tour's matching phase. */
  suppressed: boolean
  requestOpen: () => void
  setSuppressed: (suppressed: boolean) => void
}

export const useQuizPromptStore = create<QuizPromptState>()((set) => ({
  openSignal: 0,
  // In demo mode, start suppressed so QuizPrompt never polls on mount. The very
  // first `getNextQuestion` stamps `lastQuestionShownAt` (arming the cooldown), so
  // an early auto-poll would put the guest in cooldown and the tour's quiz step
  // (s19) would find nothing to show. The tour un-suppresses exactly at that step.
  suppressed: DEMO_MODE,
  requestOpen: () => set((s) => ({ openSignal: s.openSignal + 1 })),
  setSuppressed: (suppressed) => set({ suppressed }),
}))
