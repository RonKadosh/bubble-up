import client, { ApiSuccess } from './client'

export interface UserProfile {
  userId: string
  displayName: string
  bio: string | null
  /** Cache-busted absolute URL or null when no avatar set. */
  avatarUrl: string | null
  universityId: string | null
  universityName: string | null
  departmentId: string | null
  departmentName: string | null
  enrollmentYear: number | null
  role: string
  createdAt: string
}

export interface UpdateProfilePayload {
  displayName?: string
  bio?: string
  universityId?: string
  departmentId?: string
  enrollmentYear?: number
}

export async function getMyProfile(): Promise<UserProfile> {
  const res = await client.get<ApiSuccess<UserProfile>>('/users/me/profile')
  return res.data.data
}

export async function getUserProfile(userId: string): Promise<UserProfile> {
  const res = await client.get<ApiSuccess<UserProfile>>(`/users/${userId}/profile`)
  return res.data.data
}

export async function updateMyProfile(patch: UpdateProfilePayload): Promise<UserProfile> {
  const res = await client.patch<ApiSuccess<UserProfile>>('/users/me/profile', patch)
  return res.data.data
}

export async function uploadAvatar(file: File): Promise<UserProfile> {
  const fd = new FormData()
  fd.append('file', file)
  const res = await client.post<ApiSuccess<UserProfile>>(
    '/users/me/avatar',
    fd,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  )
  return res.data.data
}

export async function deleteAvatar(): Promise<UserProfile> {
  const res = await client.delete<ApiSuccess<UserProfile>>('/users/me/avatar')
  return res.data.data
}
