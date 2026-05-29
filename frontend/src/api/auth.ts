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
}

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
