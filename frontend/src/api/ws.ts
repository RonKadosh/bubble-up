import { Client, StompSubscription } from '@stomp/stompjs'
import client, { ApiSuccess } from './client'
import { useAuthStore } from '../store/authStore'
import type { ChatMessage, PinUpdate } from './chat'
import type { PresenceEntry } from './presence'
import type { PollUpdate } from './polls'
import type { ExcalidrawSnapshot, RoomLifecycleEvent, RoomPresenceEvent } from './room'
import type { WhiteboardWritersEvent } from './expert'

interface TopicSubscription {
  destination: string
  handler: (payload: unknown) => void
  subscription: StompSubscription | null
}

let stompClient: Client | null = null
/** Keyed by full STOMP destination string so the same topic can have multiple listeners. */
const topicSubscriptions = new Map<string, TopicSubscription[]>()
/** Fires every time the STOMP client reaches CONNECTED. Used by callers that need to
 *  re-snapshot REST state once they know the WS is live (e.g. presence). */
const connectListeners = new Set<() => void>()

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
      const res = await client.post<ApiSuccess<{
        accessToken: string
        refreshToken: string
        userId: string
        email: string
        role: string
        displayName: string
        avatarUrl: string | null
        emailVerified: boolean
      }>>(
        '/auth/refresh', { refreshToken: rt }, { _isRefreshCall: true } as any
      )
      const data = res.data.data
      useAuthStore.getState().setAuth(data.accessToken, data.refreshToken, {
        id: data.userId,
        email: data.email,
        role: data.role,
        displayName: data.displayName,
        avatarUrl: data.avatarUrl,
        emailVerified: data.emailVerified,
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
      // Re-subscribe every live topic. Subscriptions survive reconnect.
      for (const [destination, subs] of topicSubscriptions.entries()) {
        const stompSub = c.subscribe(destination, (frame) => {
          const payload = JSON.parse(frame.body)
          for (const s of subs) s.handler(payload)
        })
        for (const s of subs) s.subscription = stompSub
      }
      // Notify listeners that the WS is live — they typically re-fetch REST snapshots
      // here to pick up state changes that occurred during the connect handshake.
      for (const fn of connectListeners) {
        try { fn() } catch (e) { console.warn('[ws] onConnect listener threw', e) }
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
  for (const subs of topicSubscriptions.values()) {
    for (const s of subs) {
      s.subscription?.unsubscribe()
      s.subscription = null
    }
  }
  topicSubscriptions.clear()
  connectListeners.clear()
  if (stompClient?.active) {
    stompClient.deactivate()
  }
  stompClient = null
  lastFailedToken = null
}

/** Internal: subscribe to an arbitrary STOMP topic. Auto-resubscribes on reconnect. */
function subscribeToTopic<T>(destination: string, handler: (payload: T) => void): () => void {
  const entry: TopicSubscription = {
    destination,
    handler: handler as (p: unknown) => void,
    subscription: null,
  }
  const existing = topicSubscriptions.get(destination) ?? []
  existing.push(entry)
  topicSubscriptions.set(destination, existing)

  if (stompClient?.connected) {
    entry.subscription = stompClient.subscribe(destination, (frame) => {
      handler(JSON.parse(frame.body) as T)
    })
  }

  return () => {
    entry.subscription?.unsubscribe()
    const list = topicSubscriptions.get(destination)
    if (!list) return
    const idx = list.indexOf(entry)
    if (idx >= 0) list.splice(idx, 1)
    if (list.length === 0) topicSubscriptions.delete(destination)
  }
}

/**
 * Register a callback that fires every time the STOMP client reaches CONNECTED
 * (including reconnects). Returns an unsubscribe function.
 *
 * Use this when REST state queried before the WS finishes connecting can be stale
 * relative to live broadcasts that fire as part of the connect — presence is the
 * canonical case: your own `SessionConnectedEvent` broadcast can race ahead of your
 * SUBSCRIBE frame, so a re-snapshot here closes the gap.
 */
export function onWsConnect(handler: () => void): () => void {
  connectListeners.add(handler)
  // Fire immediately if we're already connected — caller doesn't have to special-case
  // the "WS was up before I registered" path.
  if (stompClient?.connected) {
    queueMicrotask(() => { if (connectListeners.has(handler)) handler() })
  }
  return () => { connectListeners.delete(handler) }
}

export function subscribeToRoom(roomId: string, handler: (msg: ChatMessage) => void): () => void {
  return subscribeToTopic<ChatMessage>(`/topic/chat/${roomId}`, handler)
}

export function subscribeToPresence(groupId: string, handler: (e: PresenceEntry) => void): () => void {
  return subscribeToTopic<PresenceEntry>(`/topic/presence/${groupId}`, handler)
}

export function subscribeToRoomPins(roomId: string, handler: (e: PinUpdate) => void): () => void {
  return subscribeToTopic<PinUpdate>(`/topic/chat/${roomId}/pins`, handler)
}

export function subscribeToRoomPolls(roomId: string, handler: (e: PollUpdate) => void): () => void {
  return subscribeToTopic<PollUpdate>(`/topic/chat/${roomId}/polls`, handler)
}

export function subscribeToRoomWhiteboard(roomId: string, handler: (snap: ExcalidrawSnapshot) => void): () => void {
  return subscribeToTopic<ExcalidrawSnapshot>(`/topic/rooms/${roomId}/whiteboard`, handler)
}

/**
 * Send a whiteboard snapshot over the open STOMP connection (write path), avoiding an
 * HTTP POST per drawing burst. Auth + relay + broadcast happen server-side in the
 * `/app/rooms/{id}/whiteboard` handler. Returns false when the client isn't connected
 * so the caller can fall back to the HTTP endpoint.
 */
export function publishToRoomWhiteboard(roomId: string, snap: ExcalidrawSnapshot): boolean {
  if (!stompClient?.connected) return false
  stompClient.publish({
    destination: `/app/rooms/${roomId}/whiteboard`,
    body: JSON.stringify(snap),
  })
  return true
}

export function subscribeToRoomLifecycle(roomId: string, handler: (e: RoomLifecycleEvent) => void): () => void {
  return subscribeToTopic<RoomLifecycleEvent>(`/topic/rooms/${roomId}/lifecycle`, handler)
}

/** Live "N in call" count for a room (video-call presence). Distinct from
 *  `subscribeToPresence(groupId)`, which is bubble-online presence. */
export function subscribeToRoomPresence(roomId: string, handler: (e: RoomPresenceEvent) => void): () => void {
  return subscribeToTopic<RoomPresenceEvent>(`/topic/rooms/${roomId}/presence`, handler)
}

/**
 * Report that we joined/left a room's video call, off Jitsi's conference events.
 * The backend updates its in-memory presence map and broadcasts the new count.
 * No-op (returns false) when the STOMP client isn't connected.
 */
export function publishToRoomPresence(roomId: string, event: 'JOIN' | 'LEAVE'): boolean {
  if (!stompClient?.connected) return false
  stompClient.publish({
    destination: `/app/rooms/${roomId}/presence`,
    body: JSON.stringify({ event }),
  })
  return true
}

export function subscribeToExpertSessionWriters(
  sessionId: string,
  handler: (e: WhiteboardWritersEvent) => void,
): () => void {
  return subscribeToTopic<WhiteboardWritersEvent>(`/topic/expert-sessions/${sessionId}/writers`, handler)
}
