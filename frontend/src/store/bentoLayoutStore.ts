import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type BentoKey = 'chat' | 'calendar' | 'files'

interface BentoLayoutState {
  focused: BentoKey
  setFocused: (k: BentoKey) => void
}

// Key bumped from v1 → v2 when 'members' was dropped as a focusable cell;
// any persisted 'members' value would otherwise crash the layout switch.
// v2 → v3 resets stale 'calendar' focuses so entering a Bubble defaults to Chat.
export const useBentoLayoutStore = create<BentoLayoutState>()(
  persist(
    (set) => ({
      focused: 'chat',
      setFocused: (focused) => set({ focused }),
    }),
    { name: 'bubbleup-bento-layout-v3' }
  )
)
