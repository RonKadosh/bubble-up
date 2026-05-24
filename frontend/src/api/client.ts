import axios, { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../store/authStore'

declare module 'axios' {
  export interface InternalAxiosRequestConfig {
    _isRefreshCall?: boolean
    _retried?: boolean
  }
}

const client = axios.create({
  baseURL: '/api',
})

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshInFlight: Promise<string> | null = null

async function performRefresh(refreshToken: string): Promise<string> {
  const config = { _isRefreshCall: true } as Partial<InternalAxiosRequestConfig>
  const res = await client.post<{
    success: boolean
    data: { accessToken: string; refreshToken: string; userId: string; email: string; role: string }
  }>('/auth/refresh', { refreshToken }, config)
  const data = res.data.data
  useAuthStore.getState().setAuth(data.accessToken, data.refreshToken, {
    id: data.userId,
    email: data.email,
    role: data.role,
  })
  return data.accessToken
}

function bounceToLogin(): void {
  useAuthStore.getState().clearAuth()
  if (window.location.pathname !== '/login') {
    window.location.assign('/login')
  }
}

client.interceptors.response.use(
  (res: AxiosResponse) => res,
  async (err: AxiosError) => {
    const config = err.config as InternalAxiosRequestConfig | undefined
    if (!config || config._isRefreshCall || config._retried) throw err
    if (err.response?.status !== 401) throw err

    const rt = useAuthStore.getState().refreshToken
    if (!rt) {
      bounceToLogin()
      throw err
    }

    if (!refreshInFlight) {
      refreshInFlight = performRefresh(rt).finally(() => {
        refreshInFlight = null
      })
    }

    try {
      const newAccess = await refreshInFlight
      config._retried = true
      config.headers.Authorization = `Bearer ${newAccess}`
      return client.request(config)
    } catch (refreshErr) {
      bounceToLogin()
      throw refreshErr
    }
  }
)

export default client
