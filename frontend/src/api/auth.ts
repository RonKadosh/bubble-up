import client, { ApiSuccess } from './client'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userId: string
  email: string
  role: string
  displayName: string
  /** Cache-busted URL or null when no avatar set. */
  avatarUrl: string | null
  /**
   * Always true for accounts reachable here: Google verifies the address and
   * the academic-domain check is the sign-up gate. Kept for forward-compat.
   */
  emailVerified: boolean
}

/**
 * The browser's address bar URL to start a Google sign-in. The backend
 * (proxied by nginx in prod, by Vite in dev) handles the actual redirect
 * to accounts.google.com.
 */
export const GOOGLE_OAUTH_START_URL = '/oauth2/authorization/google'

/**
 * Legacy email + password sign-in. Production uses Google OAuth exclusively;
 * these two functions exist only for the dev-only `/login/testing` page, which
 * is gated behind `import.meta.env.DEV` and dead-code-eliminated from prod
 * builds (see App.tsx). Don't import these from product code.
 */
export async function login(email: string, password: string): Promise<AuthResponse> {
  const res = await client.post<ApiSuccess<AuthResponse>>('/auth/login', { email, password })
  return res.data.data
}

export async function register(
  email: string,
  password: string,
  displayName: string,
): Promise<AuthResponse> {
  const res = await client.post<ApiSuccess<AuthResponse>>(
    '/auth/register',
    { email, password, displayName },
  )
  return res.data.data
}

export async function refresh(refreshToken: string): Promise<AuthResponse> {
  const res = await client.post<ApiSuccess<AuthResponse>>('/auth/refresh', { refreshToken })
  return res.data.data
}

export async function logout(refreshToken: string): Promise<void> {
  await client.post('/auth/logout', { refreshToken })
}
