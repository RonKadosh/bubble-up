import { create } from 'zustand'

/**
 * Tracks the room the current user is "in" for this session.
 *
 * Deliberately NOT persisted — the call ends when the tab closes, so survival
 * across reloads would lie about the actual call state. Set by
 * {@link BubbleRoomPage} on mount; cleared on unmount.
 *
 * Other surfaces (e.g. the global Layout sidebar) read this to render a
 * "return to room" pill when the user navigates away mid-call.
 */
interface ActiveRoomState {
  roomId: string | null
  /** Null for EXPERT_SESSION-scoped rooms (which carry expertSessionId instead). */
  groupId: string | null
  joinedAt: number | null
  setActive: (roomId: string, groupId: string | null) => void
  clearActive: () => void
}

export const useActiveRoomStore = create<ActiveRoomState>((set) => ({
  roomId: null,
  groupId: null,
  joinedAt: null,
  setActive: (roomId, groupId) => set({ roomId, groupId, joinedAt: Date.now() }),
  clearActive: () => set({ roomId: null, groupId: null, joinedAt: null }),
}))
