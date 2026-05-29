import client, { ApiSuccess } from './client'

export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'
export type ExpertSessionStatus = 'OPEN' | 'FULL' | 'LIVE' | 'ENDED' | 'CANCELLED'
export type BookingRequestStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN'

export interface ExpertProfile {
  id: string
  userId: string
  headline: string
  bio: string | null
  expertiseTags: string[]
  verificationStatus: VerificationStatus
  verifiedAt: string | null
  appliedAt: string
}

export interface ExpertSession {
  id: string
  expertUserId: string
  calendarEventId: string
  roomId: string | null
  title: string
  description: string | null
  capacity: number
  enrolledGroupCount: number
  status: ExpertSessionStatus
  startsAt: string | null
  endsAt: string | null
  createdAt: string
}

export interface BookingRequest {
  id: string
  expertUserId: string
  groupId: string
  requestedBy: string
  proposedStartsAt: string
  proposedEndsAt: string
  message: string | null
  status: BookingRequestStatus
  acceptedSessionId: string | null
  decidedAt: string | null
  createdAt: string
}

export interface ApplyAsExpertPayload {
  headline: string
  bio?: string
  expertiseTags?: string[]
}

export interface UpdateExpertProfilePayload {
  headline?: string
  bio?: string
  expertiseTags?: string[]
}

export interface CreateExpertSessionPayload {
  title: string
  description?: string
  startsAt: string
  endsAt: string
  capacity: number
}

export interface CreateBookingPayload {
  expertUserId: string
  groupId: string
  proposedStartsAt: string
  proposedEndsAt: string
  message?: string
}

/** Broadcast on `/topic/expert-sessions/{id}/writers` whenever the host grants / revokes write. */
export interface WhiteboardWritersEvent {
  sessionId: string
  writers: string[]
}

/**
 * Authorized participant in an expert session — the host plus every member of
 * every enrolled group. Not a live presence snapshot; whoever *could* be in
 * the room. Drives the host's manage-participants list.
 */
export interface ExpertSessionParticipant {
  userId: string
  displayName: string
  avatarUrl: string | null
  isHost: boolean
  canDrawWhiteboard: boolean
}

// --- profile ---

export async function applyAsExpert(payload: ApplyAsExpertPayload): Promise<ExpertProfile> {
  const res = await client.post<ApiSuccess<ExpertProfile>>('/experts/apply', payload)
  return res.data.data
}

export async function getMyExpertProfile(): Promise<ExpertProfile> {
  const res = await client.get<ApiSuccess<ExpertProfile>>('/experts/me')
  return res.data.data
}

export async function updateMyExpertProfile(payload: UpdateExpertProfilePayload): Promise<ExpertProfile> {
  const res = await client.patch<ApiSuccess<ExpertProfile>>('/experts/me', payload)
  return res.data.data
}

export async function getExpertDirectory(): Promise<ExpertProfile[]> {
  const res = await client.get<ApiSuccess<ExpertProfile[]>>('/experts/directory')
  return res.data.data
}

export async function getExpertPublicProfile(userId: string): Promise<ExpertProfile> {
  const res = await client.get<ApiSuccess<ExpertProfile>>(`/experts/${userId}`)
  return res.data.data
}

// --- sessions ---

export async function createExpertSession(payload: CreateExpertSessionPayload): Promise<ExpertSession> {
  const res = await client.post<ApiSuccess<ExpertSession>>('/expert-sessions', payload)
  return res.data.data
}

export async function getExpertSession(sessionId: string): Promise<ExpertSession> {
  const res = await client.get<ApiSuccess<ExpertSession>>(`/expert-sessions/${sessionId}`)
  return res.data.data
}

export async function listMyExpertSessions(): Promise<ExpertSession[]> {
  const res = await client.get<ApiSuccess<ExpertSession[]>>('/expert-sessions/mine')
  return res.data.data
}

export async function listEnrolledSessionsForGroup(groupId: string): Promise<ExpertSession[]> {
  const res = await client.get<ApiSuccess<ExpertSession[]>>(`/expert-sessions/enrolled?groupId=${groupId}`)
  return res.data.data
}

/** All currently-OPEN sessions across every expert, sorted by startsAt. */
export async function listOpenSessions(): Promise<ExpertSession[]> {
  const res = await client.get<ApiSuccess<ExpertSession[]>>('/expert-sessions/open')
  return res.data.data
}

export async function cancelExpertSession(sessionId: string): Promise<void> {
  await client.post(`/expert-sessions/${sessionId}/cancel`)
}

export async function enrollGroupInSession(sessionId: string, groupId: string): Promise<void> {
  await client.post(`/expert-sessions/${sessionId}/enroll`, { groupId })
}

export async function unenrollGroupFromSession(sessionId: string, groupId: string): Promise<void> {
  await client.post(`/expert-sessions/${sessionId}/unenroll`, { groupId })
}

export async function grantWhiteboardWrite(sessionId: string, userId: string): Promise<void> {
  await client.post(`/expert-sessions/${sessionId}/whiteboard/grant`, { userId })
}

export async function revokeWhiteboardWrite(sessionId: string, userId: string): Promise<void> {
  await client.post(`/expert-sessions/${sessionId}/whiteboard/revoke`, { userId })
}

export async function listExpertSessionParticipants(sessionId: string): Promise<ExpertSessionParticipant[]> {
  const res = await client.get<ApiSuccess<ExpertSessionParticipant[]>>(
    `/expert-sessions/${sessionId}/participants`
  )
  return res.data.data
}

// --- booking requests ---

export async function createBookingRequest(payload: CreateBookingPayload): Promise<BookingRequest> {
  const res = await client.post<ApiSuccess<BookingRequest>>('/expert-bookings', payload)
  return res.data.data
}

export async function listMyBookings(asExpert: boolean): Promise<BookingRequest[]> {
  const res = await client.get<ApiSuccess<BookingRequest[]>>(
    `/expert-bookings/mine?asExpert=${asExpert}`,
  )
  return res.data.data
}

export async function acceptBookingRequest(requestId: string): Promise<BookingRequest> {
  const res = await client.post<ApiSuccess<BookingRequest>>(`/expert-bookings/${requestId}/accept`)
  return res.data.data
}

export async function rejectBookingRequest(requestId: string): Promise<BookingRequest> {
  const res = await client.post<ApiSuccess<BookingRequest>>(`/expert-bookings/${requestId}/reject`)
  return res.data.data
}

export async function withdrawBookingRequest(requestId: string): Promise<BookingRequest> {
  const res = await client.post<ApiSuccess<BookingRequest>>(`/expert-bookings/${requestId}/withdraw`)
  return res.data.data
}
