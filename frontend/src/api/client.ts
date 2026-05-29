import axios, { AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '../store/authStore'
import { useLanguageStore } from '../store/languageStore'

declare module 'axios' {
  export interface InternalAxiosRequestConfig {
    _isRefreshCall?: boolean
    _retried?: boolean
  }
}

/**
 * Backend success envelope. Every endpoint that returns 2xx returns this shape
 * around its payload. Use as `client.get<ApiSuccess<MyType>>(...)`, then peel
 * `res.data.data`. Errors are surfaced by axios throwing on non-2xx — see
 * `errors.ts` for the failure envelope.
 */
export interface ApiSuccess<T> {
  success: true
  data: T
}

const client = axios.create({
  baseURL: '/api',
})

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers['Accept-Language'] = useLanguageStore.getState().lang
  return config
})

let refreshInFlight: Promise<string> | null = null

async function performRefresh(refreshToken: string): Promise<string> {
  const config = { _isRefreshCall: true } as Partial<InternalAxiosRequestConfig>
  const res = await client.post<ApiSuccess<{
    accessToken: string
    refreshToken: string
    userId: string
    email: string
    role: string
    displayName: string
    avatarUrl: string | null
  }>>(
    '/auth/refresh', { refreshToken }, config
  )
  const data = res.data.data
  useAuthStore.getState().setAuth(data.accessToken, data.refreshToken, {
    id: data.userId,
    email: data.email,
    role: data.role,
    displayName: data.displayName,
    avatarUrl: data.avatarUrl,
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
