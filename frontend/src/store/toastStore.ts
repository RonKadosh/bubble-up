import { create } from 'zustand'

/**
 * Lightweight transient notifications ("Added Bob", "Removed Carol", errors).
 * App-wide because actions in one surface (e.g. the Bubble Info drawer) should
 * surface their ack regardless of which page renders. Not persisted — toasts are
 * ephemeral by definition. The {@link Toaster} component (mounted once in the
 * Layout) renders the stack; call {@code useToastStore.getState().show(...)} from
 * anywhere, including non-React event handlers.
 */
export type ToastKind = 'success' | 'error' | 'info'

/** Optional avatar shown in the toast (e.g. the member that was added / removed). */
export interface ToastAvatar {
  id: string
  name: string
  imageUrl?: string | null
}

export interface Toast {
  id: number
  message: string
  kind: ToastKind
  avatar?: ToastAvatar
}

interface ToastState {
  toasts: Toast[]
  show: (message: string, kind?: ToastKind, avatar?: ToastAvatar) => void
  dismiss: (id: number) => void
}

let nextId = 1
const AUTO_DISMISS_MS = 3200

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  show: (message, kind = 'success', avatar) => {
    const id = nextId++
    set((s) => ({ toasts: [...s.toasts, { id, message, kind, avatar }] }))
    window.setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
    }, AUTO_DISMISS_MS)
  },
  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}))
