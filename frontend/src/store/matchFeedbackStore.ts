import { create } from 'zustand'

/**
 * Whether the current user has already rated a Bubble's fit, keyed by group id.
 * The "is this Bubble a good fit?" prompt is rendered by TWO mounted instances at
 * once — one in the GroupHeader (desktop) and one in the BubbleInfoDrawer (the
 * place phone users reach it) — so each must agree on ratedness. Without a shared
 * source of truth, voting in one leaves the other still showing the prompt and the
 * user can answer twice. This store is that source of truth.
 *
 * Status per group:
 *   undefined → not yet checked
 *   'loading' → a status fetch is in flight (claimed by the first instance to mount)
 *   'unrated' → server says no prior vote; show the prompt
 *   'rated'   → already voted (server-confirmed or just optimistically submitted); hide it
 *
 * Not persisted: ratedness lives server-side; this is a per-session reflection of it,
 * re-fetched on mount. Cleared by no one — it only grows by the few groups visited.
 */
export type MatchFeedbackStatus = 'loading' | 'unrated' | 'rated'

interface MatchFeedbackState {
  statusByGroup: Record<string, MatchFeedbackStatus>
  setStatus: (groupId: string, status: MatchFeedbackStatus) => void
}

export const useMatchFeedbackStore = create<MatchFeedbackState>((set) => ({
  statusByGroup: {},
  setStatus: (groupId, status) =>
    set((s) => ({ statusByGroup: { ...s.statusByGroup, [groupId]: status } })),
}))
