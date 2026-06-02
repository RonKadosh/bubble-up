import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { FeedSectionKey } from '../api/feed'

interface FeedLayoutState {
  focused: FeedSectionKey
  setFocused: (k: FeedSectionKey) => void
}

// Mirrors bentoLayoutStore: pure focus state for the dashboard's bento grid —
// which of the four feed sections (LIVE / UPCOMING / ACTIVITY / DISCOVERY) is
// currently maximized. Persisted so the user's chosen focus survives reloads.
export const useFeedLayoutStore = create<FeedLayoutState>()(
  persist(
    (set) => ({
      focused: 'DISCOVERY',
      setFocused: (focused) => set({ focused }),
    }),
    // Key bumped to v2 when the default changed LIVE → DISCOVERY, so a stale
    // persisted 'LIVE' doesn't override the new base view.
    { name: 'bubbleup-feed-layout-v2' }
  )
)
