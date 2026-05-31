import client, { ApiSuccess } from './client'

export type RoomScope = 'GROUP' | 'EXPERT_SESSION'

export interface BubbleRoom {
  id: string
  scope: RoomScope
  calendarEventId: string | null
  groupId: string | null
  expertSessionId: string | null
  chatRoomId: string
  jitsiRoomName: string
  jitsiServerUrl: string
  jitsiAppId: string
  /**
   * Jitsi JWT. Null when {@link videoOpensAt} is in the future — chat and
   * whiteboard work; the Video cell renders a countdown placeholder until
   * the page re-fetches the room past that moment.
   */
  jitsiJwt: string | null
  whiteboardEnabled: boolean
  startsAt: string | null
  endsAt: string | null
  /**
   * When the Video cell becomes joinable. GROUP rooms: same as the join
   * window opening (startsAt - 15min). EXPERT_SESSION rooms: startsAt.
   */
  videoOpensAt: string | null
  /** How many users are currently in the video call — seeds the live "N in call" pill. */
  participantCount: number
  createdAt: string
}

/**
 * Whiteboard payload — opaque to the backend, Excalidraw owns the schema.
 * We just store `elements` (the full ordered element list) and `appState`
 * (shared view state) and relay snapshots over STOMP.
 */
export interface ExcalidrawSnapshot {
  elements: unknown[]
  appState: Record<string, unknown>
}

export async function getRoom(roomId: string): Promise<BubbleRoom> {
  const res = await client.get<ApiSuccess<BubbleRoom>>(`/rooms/${roomId}`)
  return res.data.data
}

/**
 * Group IDs (among the caller's groups) that are live right now — a Bubble Room in
 * its join window or a joinable enrolled expert session. Drives the red live marker
 * on the My Bubbles sidebar.
 */
export async function getLiveGroupIds(): Promise<string[]> {
  const res = await client.get<ApiSuccess<string[]>>('/rooms/live-groups')
  return res.data.data
}

export async function getRoomForEvent(eventId: string): Promise<BubbleRoom> {
  const res = await client.get<ApiSuccess<BubbleRoom>>(`/rooms/by-event/${eventId}`)
  return res.data.data
}

export async function getWhiteboard(roomId: string): Promise<ExcalidrawSnapshot> {
  const res = await client.get<ApiSuccess<ExcalidrawSnapshot>>(`/rooms/${roomId}/whiteboard`)
  return res.data.data
}

export async function pushWhiteboard(roomId: string, snapshot: ExcalidrawSnapshot): Promise<void> {
  await client.post<ApiSuccess<void>>(`/rooms/${roomId}/whiteboard/elements`, snapshot)
}

/** Extend the room's session by a fixed +15 min (any bubble member). */
export async function extendRoom(roomId: string): Promise<BubbleRoom> {
  const res = await client.post<ApiSuccess<BubbleRoom>>(`/rooms/${roomId}/extend`)
  return res.data.data
}

/** Lifecycle events broadcast on /topic/rooms/{id}/lifecycle. */
export interface RoomLifecycleEvent {
  event: 'ENDED' | 'EXTENDED'
  roomId: string
  endsAt: string | null
}

/** Call-presence count broadcast on /topic/rooms/{id}/presence as people join/leave. */
export interface RoomPresenceEvent {
  roomId: string
  count: number
}
