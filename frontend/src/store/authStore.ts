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
   * True once the user has proved ownership of an Israeli academic email
   * (either directly via a .ac.il Google account, or via a SES-sent
   * verification link). The router uses this to push unverified users to
   * /auth/verify before any other page.
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
    // Bumped v3 -> v4: AuthUser gained `emailVerified`. Existing v3 users get
    // re-prompted to sign in via Google (no migration in dev's create-drop world).
    { name: 'bubbleup-auth-v4' }
  )
)
