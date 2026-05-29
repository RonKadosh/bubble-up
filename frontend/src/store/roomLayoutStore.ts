import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export type RoomBentoKey = 'video' | 'whiteboard' | 'chat'

interface RoomLayoutState {
  focused: RoomBentoKey
  setFocused: (k: RoomBentoKey) => void
}

export const useRoomLayoutStore = create<RoomLayoutState>()(
  persist(
    (set) => ({
      focused: 'video',
      setFocused: (focused) => set({ focused }),
    }),
    { name: 'bubbleup-room-layout' }
  )
)
