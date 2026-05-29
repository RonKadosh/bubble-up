import client, { ApiSuccess } from './client'

export interface PresenceEntry {
  userId: string
  online: boolean
  /** ISO timestamp. Null only if the user has never connected over WebSocket. */
  lastSeenAt: string | null
}

export async function getPresence(groupId: string): Promise<PresenceEntry[]> {
  const res = await client.get<ApiSuccess<PresenceEntry[]>>(`/groups/${groupId}/presence`)
  return res.data.data
}
