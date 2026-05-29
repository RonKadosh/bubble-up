import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface AuthUser {
  id: string
  email: string
  role: string
  displayName: string
  /** Cache-busted absolute path from the server, or null when no avatar set. */
  avatarUrl: string | null
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
    // Bumped from v2 → v3 because AuthUser gained `displayName` + `avatarUrl`.
    // Existing users on v2 re-login (no migration needed in dev's create-drop world).
    { name: 'bubbleup-auth-v3' }
  )
)
