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
  /** True once the user has proved ownership of a .ac.il address. */
  emailVerified: boolean
}

export interface VerifyEmailResponse {
  emailVerified: boolean
  email: string
}

/**
 * The browser's address bar URL to start a Google sign-in. The backend
 * (proxied by nginx in prod, by Vite in dev) handles the actual redirect
 * to accounts.google.com.
 */
export const GOOGLE_OAUTH_START_URL = '/oauth2/authorization/google'

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

/**
 * Send a verification link to the academic email the user supplied.
 * Requires an authed session. The link is good for 30 minutes.
 */
export async function requestVerificationEmail(academicEmail: string): Promise<void> {
  await client.post('/auth/verify-email/request', { academicEmail })
}

/**
 * Redeem the token from the link in the verification email.
 * Public endpoint: the token itself is the proof.
 */
export async function confirmVerificationEmail(token: string): Promise<VerifyEmailResponse> {
  const res = await client.post<ApiSuccess<VerifyEmailResponse>>(
    '/auth/verify-email/confirm',
    { token },
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
