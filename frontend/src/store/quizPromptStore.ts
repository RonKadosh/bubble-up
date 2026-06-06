import { create } from 'zustand'

/**
 * One-way trigger to surface the floating QuizPrompt on demand (e.g. the
 * onboarding "Answer your first Daily Drop" card). Not persisted — it's an
 * ephemeral signal. `QuizPrompt` watches `openSignal` and re-polls when it bumps;
 * if no question is available (cooldown/exhausted) the request is a quiet no-op.
 */
interface QuizPromptState {
  openSignal: number
  requestOpen: () => void
}

export const useQuizPromptStore = create<QuizPromptState>()((set) => ({
  openSignal: 0,
  requestOpen: () => set((s) => ({ openSignal: s.openSignal + 1 })),
}))
