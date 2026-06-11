import client, { ApiSuccess } from './client'

export type ChatMessageType =
  | 'TEXT'
  | 'SYSTEM_JOIN'
  | 'SYSTEM_LEAVE'
  | 'LINK'
  | 'SYSTEM_ROOM_END_SOON'
  | 'SYSTEM_ROOM_EXTENDED'
  | 'SYSTEM_EXPERT_SESSION_OPEN'
  | 'SYSTEM_GROUP_ROOM_OPEN'
  | 'SYSTEM_OWNERSHIP_TRANSFER'
export type ChatLinkTargetType = 'CALENDAR_EVENT' | 'POLL' | 'ROOM' | 'FILE' | 'EXPERT_SESSION'

export interface ChatRoom {
  id: string
  name: string
  /** Null for EXPERT_SESSION-scoped rooms (which carry expertSessionId instead). */
  groupId: string | null
  /** Null for GROUP-scoped rooms. */
  expertSessionId: string | null
  createdAt: string
  /** Unread messages for the current user (per-room). */
  unreadCount: number
}

export interface ChatMessage {
  id: string
  roomId: string
  /** Null for SYSTEM_JOIN / SYSTEM_LEAVE messages. */
  senderId: string | null
  /** Sender's display name. Null for SYSTEM_* messages and for deleted users. */
  senderDisplayName: string | null
  /** Cache-busted avatar URL or null (no avatar set / SYSTEM_*). */
  senderAvatarUrl: string | null
  content: string
  sentAt: string
  messageType: ChatMessageType
  /** Non-null for SYSTEM_* — who joined/left. */
  subjectUserId: string | null
  /** Non-null for LINK. */
  linkTargetType: ChatLinkTargetType | null
  /** Non-null for LINK. */
  linkTargetId: string | null
  /** When set, this message is a reply quoting the referenced message. */
  replyToMessageId: string | null
  pinned: boolean
  /** ISO timestamp; null when {@code pinned} is false. */
  pinnedAt: string | null
  /** Who pinned it; null when {@code pinned} is false. */
  pinnedByUserId: string | null
}

/** WebSocket payload broadcast on {@code /topic/chat/{roomId}/pins} when a pin changes. */
export interface PinUpdate {
  messageId: string
  pinned: boolean
  pinnedAt: string | null
  pinnedByUserId: string | null
}

export async function getRooms(): Promise<ChatRoom[]> {
  const res = await client.get<ApiSuccess<ChatRoom[]>>('/chat/rooms')
  return res.data.data
}

/**
 * Fetch a single chat room by id — works for both GROUP and EXPERT_SESSION
 * scopes. Used by the expert-session room page, where {@link getRooms} would
 * miss the session's chat room (that endpoint only lists rooms for groups the
 * user belongs to).
 */
export async function getChatRoom(roomId: string): Promise<ChatRoom> {
  const res = await client.get<ApiSuccess<ChatRoom>>(`/chat/rooms/${roomId}`)
  return res.data.data
}

export async function createRoom(name: string, groupId: string): Promise<ChatRoom> {
  const res = await client.post<ApiSuccess<ChatRoom>>('/chat/rooms', { name, groupId })
  return res.data.data
}

/**
 * Cursor pagination. {@code before} is the id of the oldest message already loaded;
 * the server returns the next N older messages, DESC by sentAt. Omit {@code before} to
 * fetch the latest N. Size is clamped server-side to [1, 100] (default 50).
 */
export async function getMessages(
  roomId: string,
  opts: { before?: string; size?: number } = {}
): Promise<ChatMessage[]> {
  const params: Record<string, string | number> = {}
  if (opts.before) params.before = opts.before
  if (opts.size != null) params.size = opts.size
  const res = await client.get<ApiSuccess<ChatMessage[]>>(
    `/chat/rooms/${roomId}/messages`,
    { params }
  )
  return res.data.data
}

/**
 * Focus mode: returns a page of messages centered on {@code focusId}. Same DESC ordering
 * as {@link getMessages}. Used by reply/pin click-to-jump when the target is outside the
 * already-loaded window.
 */
export async function getMessagesFocused(
  roomId: string,
  focusId: string,
  size?: number
): Promise<ChatMessage[]> {
  const params: Record<string, string | number> = { focus: focusId }
  if (size != null) params.size = size
  const res = await client.get<ApiSuccess<ChatMessage[]>>(
    `/chat/rooms/${roomId}/messages`,
    { params }
  )
  return res.data.data
}

export async function sendTextMessage(
  roomId: string,
  content: string,
  replyToMessageId?: string
): Promise<ChatMessage> {
  const res = await client.post<ApiSuccess<ChatMessage>>(
    `/chat/rooms/${roomId}/messages`,
    { type: 'TEXT', content, replyToMessageId }
  )
  return res.data.data
}

export async function sendLinkMessage(
  roomId: string,
  linkTargetType: ChatLinkTargetType,
  linkTargetId: string,
  caption?: string,
  replyToMessageId?: string
): Promise<ChatMessage> {
  const res = await client.post<ApiSuccess<ChatMessage>>(
    `/chat/rooms/${roomId}/messages`,
    { type: 'LINK', linkTargetType, linkTargetId, content: caption ?? '', replyToMessageId }
  )
  return res.data.data
}

export async function markRead(roomId: string, lastReadMessageId: string): Promise<void> {
  await client.post(`/chat/rooms/${roomId}/read`, { lastReadMessageId })
}

export async function pinMessage(roomId: string, messageId: string): Promise<PinUpdate> {
  const res = await client.post<ApiSuccess<PinUpdate>>(
    `/chat/rooms/${roomId}/messages/${messageId}/pin`
  )
  return res.data.data
}

export async function unpinMessage(roomId: string, messageId: string): Promise<PinUpdate> {
  const res = await client.delete<ApiSuccess<PinUpdate>>(
    `/chat/rooms/${roomId}/messages/${messageId}/pin`
  )
  return res.data.data
}

export async function getPinnedMessages(roomId: string): Promise<ChatMessage[]> {
  const res = await client.get<ApiSuccess<ChatMessage[]>>(
    `/chat/rooms/${roomId}/pins`
  )
  return res.data.data
}
