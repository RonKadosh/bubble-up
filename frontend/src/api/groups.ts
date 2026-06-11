import client, { ApiSuccess } from './client'

export type Visibility = 'PUBLIC' | 'PRIVATE'
export type GroupStatus = 'ACTIVE' | 'ARCHIVED' | 'SUSPENDED'
export type MembershipRole = 'OWNER' | 'MEMBER'

export interface Group {
  id: string
  name: string
  description: string
  visibility: Visibility
  status: GroupStatus
  offeringId: string
  createdBy: string
  ownerId: string
  memberCount: number
  /** Max members allowed in this Bubble. Chosen once at creation (4–10); immutable. */
  maxMembers: number
  /** Cache-busted cover-image URL, or null when no image is set (falls back to the generated avatar). */
  imageUrl: string | null
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
  /** Required: max members (4–10), chosen once at creation. */
  maxMembers: number
  courseId: string
}

export interface UpdateGroupPayload {
  name?: string
  description?: string
  visibility?: Visibility
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

/**
 * Public, not-yet-joined bubbles across the caller's current-term enrolled
 * courses. Backs the onboarding "Find a Bubble" step. Empty (not an error) when
 * the user has no affiliation / current term / enrolment.
 */
export async function getDiscoverableBubbles(): Promise<Group[]> {
  const res = await client.get<ApiSuccess<Group[]>>('/groups/discoverable')
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

/** Upload / replace the Bubble's cover image (owner only). Returns the updated group. */
export async function uploadGroupImage(id: string, file: File): Promise<Group> {
  const form = new FormData()
  form.append('file', file)
  const res = await client.post<ApiSuccess<Group>>(`/groups/${id}/image`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data.data
}

/** Clear the Bubble's cover image (owner only). Returns the updated group. */
export async function deleteGroupImage(id: string): Promise<Group> {
  const res = await client.delete<ApiSuccess<Group>>(`/groups/${id}/image`)
  return res.data.data
}

export async function getMembers(groupId: string): Promise<GroupMember[]> {
  const res = await client.get<ApiSuccess<GroupMember[]>>(`/groups/${groupId}/members`)
  return res.data.data
}

/** A user the owner can add: enrolled in the Bubble's offering, not yet a member. */
export interface GroupCandidate {
  userId: string
  displayName: string | null
  avatarUrl: string | null
}

/**
 * Owner-only: students enrolled in this Bubble's offering who aren't members yet.
 * Optional {@code q} filters by display name. Backs the member-search picker.
 */
export async function getGroupCandidates(groupId: string, q?: string): Promise<GroupCandidate[]> {
  const res = await client.get<ApiSuccess<GroupCandidate[]>>(`/groups/${groupId}/candidates`, {
    params: q ? { q } : undefined,
  })
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

/**
 * Bubbles the caller can invite {@code userId} into — owned, not full, sharing the
 * target's enrolled offering, target not already a member. Backs the user card's
 * "Invite to Bubble" action.
 */
export async function getInvitableGroups(userId: string): Promise<Group[]> {
  const res = await client.get<ApiSuccess<Group[]>>(`/groups/invitable-for/${userId}`)
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
