import { Client, StompSubscription } from '@stomp/stompjs'
import client from './client'
import { useAuthStore } from '../store/authStore'
import type { ChatMessage } from './chat'

type RoomHandler = (msg: ChatMessage) => void

interface RoomSubscription {
  handler: RoomHandler
  subscription: StompSubscription | null
}

let stompClient: Client | null = null
const roomSubscriptions = new Map<string, RoomSubscription[]>()

// Single-flight refresh shared with axios isn't accessible directly, so we run our own.
// Worst case both fire — backend rotates the token either way.
let wsRefreshInFlight: Promise<string | null> | null = null
/**
 * Token that the last CONNECT was rejected with. If the store's current accessToken
 * still equals this, refresh didn't help — deactivate instead of reconnect-looping.
 * Reset on a successful CONNECT.
 */
let lastFailedToken: string | null = null

function brokerUrl(): string {
  const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${proto}//${window.location.host}/ws`
}

async function refreshAccessToken(): Promise<string | null> {
  if (wsRefreshInFlight) return wsRefreshInFlight
  const rt = useAuthStore.getState().refreshToken
  if (!rt) return null
  wsRefreshInFlight = (async () => {
    try {
      const res = await client.post<{
        success: boolean
        data: { accessToken: string; refreshToken: string; userId: string; email: string; role: string }
      }>('/auth/refresh', { refreshToken: rt }, { _isRefreshCall: true } as any)
      const data = res.data.data
      useAuthStore.getState().setAuth(data.accessToken, data.refreshToken, {
        id: data.userId, email: data.email, role: data.role,
      })
      return data.accessToken
    } catch {
      return null
    } finally {
      wsRefreshInFlight = null
    }
  })()
  return wsRefreshInFlight
}

function buildClient(): Client {
  const c = new Client({
    brokerURL: brokerUrl(),
    reconnectDelay: 2000,
    beforeConnect: async () => {
      const token = useAuthStore.getState().accessToken
      if (!token) {
        // No token — nothing useful to try. App.tsx re-activates us on login.
        c.deactivate()
        return
      }
      if (token === lastFailedToken) {
        // Previous CONNECT was rejected with this exact token and a refresh either
        // wasn't possible or didn't replace it. Stop reconnect-looping.
        c.deactivate()
        return
      }
      c.connectHeaders = { Authorization: `Bearer ${token}` }
    },
    onConnect: () => {
      lastFailedToken = null
      for (const [roomId, subs] of roomSubscriptions.entries()) {
        const stompSub = c.subscribe(`/topic/chat/${roomId}`, (frame) => {
          const payload = JSON.parse(frame.body) as ChatMessage
          for (const s of subs) s.handler(payload)
        })
        for (const s of subs) s.subscription = stompSub
      }
    },
    onStompError: async (frame) => {
      console.warn('[ws] STOMP error', frame.headers['message'], frame.body)
      // CONNECT was rejected (most often: expired / missing token). Mark the token as
      // failed and try to refresh; beforeConnect will gate the next attempt.
      lastFailedToken = useAuthStore.getState().accessToken
      const fresh = await refreshAccessToken()
      // If the client deactivated itself (e.g. because we got here mid-cycle and
      // beforeConnect saw a stale token), nudge it back to life with the fresh token.
      if (fresh && fresh !== lastFailedToken && stompClient && !stompClient.active) {
        stompClient.activate()
      }
    },
    onWebSocketError: (e) => {
      console.warn('[ws] socket error', e)
    },
  })
  return c
}

export function connectWs(): void {
  if (stompClient?.active) return
  lastFailedToken = null
  stompClient = buildClient()
  stompClient.activate()
}

/**
 * Watches the auth store so a token refresh that happened outside this module
 * (axios's interceptor) wakes a deactivated client. Idempotent — installed once.
 */
let storeUnsub: (() => void) | null = null
function ensureStoreWatcher() {
  if (storeUnsub) return
  storeUnsub = useAuthStore.subscribe((state, prev) => {
    if (!state.accessToken) return
    if (state.accessToken === prev.accessToken) return
    // Token rotated. Clear the failure gate; re-activate the client if it dozed off.
    lastFailedToken = null
    if (stompClient && !stompClient.active) stompClient.activate()
  })
}
ensureStoreWatcher()

export function disconnectWs(): void {
  for (const subs of roomSubscriptions.values()) {
    for (const s of subs) {
      s.subscription?.unsubscribe()
      s.subscription = null
    }
  }
  roomSubscriptions.clear()
  if (stompClient?.active) {
    stompClient.deactivate()
  }
  stompClient = null
  lastFailedToken = null
}

export function subscribeToRoom(roomId: string, handler: RoomHandler): () => void {
  const entry: RoomSubscription = { handler, subscription: null }
  const existing = roomSubscriptions.get(roomId) ?? []
  existing.push(entry)
  roomSubscriptions.set(roomId, existing)

  if (stompClient?.connected) {
    entry.subscription = stompClient.subscribe(`/topic/chat/${roomId}`, (frame) => {
      const payload = JSON.parse(frame.body) as ChatMessage
      handler(payload)
    })
  }

  return () => {
    entry.subscription?.unsubscribe()
    const list = roomSubscriptions.get(roomId)
    if (!list) return
    const idx = list.indexOf(entry)
    if (idx >= 0) list.splice(idx, 1)
    if (list.length === 0) roomSubscriptions.delete(roomId)
  }
}
