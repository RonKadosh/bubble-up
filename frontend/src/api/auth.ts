import client from './client'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userId: string
  email: string
  role: string
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const res = await client.post<{ success: boolean; data: AuthResponse }>('/auth/login', { email, password })
  return res.data.data
}

export async function register(email: string, password: string): Promise<AuthResponse> {
  const res = await client.post<{ success: boolean; data: AuthResponse }>('/auth/register', { email, password })
  return res.data.data
}

export async function refresh(refreshToken: string): Promise<AuthResponse> {
  const res = await client.post<{ success: boolean; data: AuthResponse }>('/auth/refresh', { refreshToken })
  return res.data.data
}

export async function logout(refreshToken: string): Promise<void> {
  await client.post('/auth/logout', { refreshToken })
}
