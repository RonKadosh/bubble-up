import client from './client'

export type ChatMessageType = 'TEXT' | 'SYSTEM_JOIN' | 'SYSTEM_LEAVE' | 'LINK'
export type ChatLinkTargetType = 'CALENDAR_EVENT'

export interface ChatRoom {
  id: string
  name: string
  groupId: string
  createdAt: string
  /** Unread messages for the current user (per-room). */
  unreadCount: number
}

export interface ChatMessage {
  id: string
  roomId: string
  /** Null for SYSTEM_JOIN / SYSTEM_LEAVE messages. */
  senderId: string | null
  content: string
  sentAt: string
  messageType: ChatMessageType
  /** Non-null for SYSTEM_* — who joined/left. */
  subjectUserId: string | null
  /** Non-null for LINK. */
  linkTargetType: ChatLinkTargetType | null
  /** Non-null for LINK. */
  linkTargetId: string | null
}

export async function getRooms(): Promise<ChatRoom[]> {
  const res = await client.get<{ success: boolean; data: ChatRoom[] }>('/chat/rooms')
  return res.data.data
}

export async function createRoom(name: string, groupId: string): Promise<ChatRoom> {
  const res = await client.post<{ success: boolean; data: ChatRoom }>('/chat/rooms', { name, groupId })
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
  const res = await client.get<{ success: boolean; data: ChatMessage[] }>(
    `/chat/rooms/${roomId}/messages`,
    { params }
  )
  return res.data.data
}

export async function sendTextMessage(roomId: string, content: string): Promise<ChatMessage> {
  const res = await client.post<{ success: boolean; data: ChatMessage }>(
    `/chat/rooms/${roomId}/messages`,
    { type: 'TEXT', content }
  )
  return res.data.data
}

export async function sendLinkMessage(
  roomId: string,
  linkTargetType: ChatLinkTargetType,
  linkTargetId: string,
  caption?: string
): Promise<ChatMessage> {
  const res = await client.post<{ success: boolean; data: ChatMessage }>(
    `/chat/rooms/${roomId}/messages`,
    { type: 'LINK', linkTargetType, linkTargetId, content: caption ?? '' }
  )
  return res.data.data
}

export async function markRead(roomId: string, lastReadMessageId: string): Promise<void> {
  await client.post(`/chat/rooms/${roomId}/read`, { lastReadMessageId })
}
