import { create } from 'zustand'

/**
 * Which user's profile card is open, app-wide. A single {@link UserProfileCard}
 * mounted at the Layout root reads this; any avatar/name (chat, Bubble Info, …)
 * calls `open(userId)` to pop the read-only card instead of navigating away.
 */
interface UserCardState {
  userId: string | null
  open: (userId: string) => void
  close: () => void
}

export const useUserCardStore = create<UserCardState>((set) => ({
  userId: null,
  open: (userId) => set({ userId }),
  close: () => set({ userId: null }),
}))
