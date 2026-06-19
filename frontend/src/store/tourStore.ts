import { create } from 'zustand'

/**
 * Drives the guided demo tour. Ephemeral on purpose — NOT persisted: a fresh demo
 * visit always starts the tour from step 0. The {@code DemoTour} component reads
 * {@code running} + {@code stepIndex} and renders the driver.js spotlight; the
 * step graph (copy, anchors, advance rules) lives in that component.
 *
 * {@code starterGroupId} is the Bubble the tour tells the visitor to open (resolved
 * from the {@code /demo/start} response), so the sidebar step can highlight it.
 */
interface TourState {
  running: boolean
  stepIndex: number
  starterGroupId: string | null
  start: (starterGroupId: string) => void
  next: () => void
  back: () => void
  goTo: (index: number) => void
  stop: () => void
}

export const useTourStore = create<TourState>((set) => ({
  running: false,
  stepIndex: 0,
  starterGroupId: null,
  start: (starterGroupId) => set({ running: true, stepIndex: 0, starterGroupId }),
  next: () => set((s) => ({ stepIndex: s.stepIndex + 1 })),
  back: () => set((s) => ({ stepIndex: Math.max(0, s.stepIndex - 1) })),
  goTo: (index) => set({ stepIndex: Math.max(0, index) }),
  stop: () => set({ running: false, stepIndex: 0 }),
}))
