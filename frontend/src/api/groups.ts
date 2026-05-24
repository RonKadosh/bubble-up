import client from './client'

export type Visibility = 'PUBLIC' | 'PRIVATE'
export type MembershipRole = 'OWNER' | 'MEMBER'

export interface Group {
  id: string
  name: string
  description: string
  visibility: Visibility
  createdBy: string
  ownerId: string
  memberCount: number
  createdAt: string
}

export interface GroupMember {
  userId: string
  role: MembershipRole
  joinedAt: string
}

export interface CreateGroupPayload {
  name: string
  description?: string
  visibility?: Visibility
}

export interface UpdateGroupPayload {
  name?: string
  description?: string
  visibility?: Visibility
}

export async function getGroups(): Promise<Group[]> {
  const res = await client.get<{ success: boolean; data: Group[] }>('/groups')
  return res.data.data
}

export async function getGroup(id: string): Promise<Group> {
  const res = await client.get<{ success: boolean; data: Group }>(`/groups/${id}`)
  return res.data.data
}

export async function createGroup(payload: CreateGroupPayload): Promise<Group> {
  const res = await client.post<{ success: boolean; data: Group }>('/groups', payload)
  return res.data.data
}

export async function updateGroup(id: string, payload: UpdateGroupPayload): Promise<Group> {
  const res = await client.patch<{ success: boolean; data: Group }>(`/groups/${id}`, payload)
  return res.data.data
}

export async function deleteGroup(id: string): Promise<void> {
  await client.delete(`/groups/${id}`)
}

export async function getMembers(groupId: string): Promise<GroupMember[]> {
  const res = await client.get<{ success: boolean; data: GroupMember[] }>(`/groups/${groupId}/members`)
  return res.data.data
}

export async function joinGroup(id: string): Promise<GroupMember> {
  const res = await client.post<{ success: boolean; data: GroupMember }>(`/groups/${id}/join`)
  return res.data.data
}

export async function addMember(groupId: string, userId: string): Promise<GroupMember> {
  const res = await client.post<{ success: boolean; data: GroupMember }>(
    `/groups/${groupId}/members`,
    { userId }
  )
  return res.data.data
}

export async function leaveGroup(groupId: string): Promise<void> {
  await client.delete(`/groups/${groupId}/members/me`)
}

export async function removeMember(groupId: string, userId: string): Promise<void> {
  await client.delete(`/groups/${groupId}/members/${userId}`)
}

export async function transferOwnership(groupId: string, newOwnerId: string): Promise<void> {
  await client.post(`/groups/${groupId}/transfer-ownership`, { newOwnerId })
}
