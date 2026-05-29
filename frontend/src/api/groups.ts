import client, { ApiSuccess } from './client'

export type Visibility = 'PUBLIC' | 'PRIVATE'
export type MembershipRole = 'OWNER' | 'MEMBER'

export interface Group {
  id: string
  name: string
  description: string
  visibility: Visibility
  offeringId: string
  createdBy: string
  ownerId: string
  memberCount: number
  createdAt: string
}

export interface GroupMember {
  userId: string
  /** Display name embedded server-side. Null only for deleted users. */
  displayName: string | null
  /** Cache-busted avatar URL or null when no avatar set. */
  avatarUrl: string | null
  role: MembershipRole
  joinedAt: string
}

export interface CreateGroupPayload {
  name: string
  description?: string
  visibility?: Visibility
  courseId: string
}

export interface UpdateGroupPayload {
  name?: string
  description?: string
  visibility?: Visibility
}

export async function getGroups(): Promise<Group[]> {
  const res = await client.get<ApiSuccess<Group[]>>('/groups')
  return res.data.data
}

/** Groups the current user is a member of. Backs the "My Bubbles" hub sidebar. */
export async function getMyGroups(): Promise<Group[]> {
  const res = await client.get<ApiSuccess<Group[]>>('/groups/me')
  return res.data.data
}

export interface GroupsByCourseFilters {
  q?: string
  visibility?: Visibility
  joinedOnly?: boolean
}

/**
 * Groups under {@code courseId}'s current-term offering. Enrollment-gated on
 * the backend — non-enrolled callers get a 403 NOT_ENROLLED_IN_COURSE.
 */
export async function getGroupsByCourse(
  courseId: string,
  filters: GroupsByCourseFilters = {}
): Promise<Group[]> {
  const res = await client.get<ApiSuccess<Group[]>>(`/groups/by-course/${courseId}`, {
    params: filters,
  })
  return res.data.data
}

export async function getGroup(id: string): Promise<Group> {
  const res = await client.get<ApiSuccess<Group>>(`/groups/${id}`)
  return res.data.data
}

export async function createGroup(payload: CreateGroupPayload): Promise<Group> {
  const res = await client.post<ApiSuccess<Group>>('/groups', payload)
  return res.data.data
}

export async function updateGroup(id: string, payload: UpdateGroupPayload): Promise<Group> {
  const res = await client.patch<ApiSuccess<Group>>(`/groups/${id}`, payload)
  return res.data.data
}

export async function deleteGroup(id: string): Promise<void> {
  await client.delete(`/groups/${id}`)
}

export async function getMembers(groupId: string): Promise<GroupMember[]> {
  const res = await client.get<ApiSuccess<GroupMember[]>>(`/groups/${groupId}/members`)
  return res.data.data
}

export async function joinGroup(id: string): Promise<GroupMember> {
  const res = await client.post<ApiSuccess<GroupMember>>(`/groups/${id}/join`)
  return res.data.data
}

export async function addMember(groupId: string, userId: string): Promise<GroupMember> {
  const res = await client.post<ApiSuccess<GroupMember>>(`/groups/${groupId}/members`, { userId })
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
