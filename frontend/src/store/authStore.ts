import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface AuthUser {
  id: string
  email: string
  role: string
  displayName: string
  /** Cache-busted absolute path from the server, or null when no avatar set. */
  avatarUrl: string | null
  /**
   * Always true for accounts that can reach the app: Google verifies the
   * address and the academic-domain check is the sign-up gate (there is no
   * separate email-verification step). Kept for forward-compat.
   */
  emailVerified: boolean
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: AuthUser | null
  setAuth: (accessToken: string, refreshToken: string, user: AuthUser) => void
  /** Merge partial fields into the current user (e.g. after profile edit). */
  updateUser: (patch: Partial<AuthUser>) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      setAuth: (accessToken, refreshToken, user) => set({ accessToken, refreshToken, user }),
      updateUser: (patch) => {
        const u = get().user
        if (!u) return
        set({ user: { ...u, ...patch } })
      },
      clearAuth: () => set({ accessToken: null, refreshToken: null, user: null }),
    }),
    // Bumped v5 -> v6: restore Bubble.up's own first-signup email verification
    // step, so older persisted auth states should be discarded.
    { name: 'bubbleup-auth-v6' }
  )
)
