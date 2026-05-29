import client, { ApiSuccess } from './client'

export type CalendarOwnerType = 'GROUP' | 'USER'
export type CalendarEventType =
  | 'STUDY_SESSION'
  | 'EXPERT_SESSION'
  | 'MEETING'
  | 'DEADLINE'
  | 'EXAM'
  | 'ASSIGNMENT'
  | 'REMINDER'
  | 'OTHER'

/**
 * Types the user is allowed to pick in the create-event form. EXPERT_SESSION
 * is intentionally excluded — those events are materialized by the backend
 * when an expert schedules a session, not by group members hand-creating them.
 */
export const EVENT_TYPES: CalendarEventType[] = [
  'STUDY_SESSION', 'MEETING', 'DEADLINE', 'EXAM', 'ASSIGNMENT', 'REMINDER', 'OTHER',
]

export interface CalendarEvent {
  id: string
  ownerType: CalendarOwnerType
  ownerId: string
  createdBy: string
  eventType: CalendarEventType
  description?: string
  startsAt: string
  endsAt: string
  createdAt: string
  updatedAt: string
}

export interface CreateEventPayload {
  ownerType: CalendarOwnerType
  ownerId: string
  eventType: CalendarEventType
  description?: string
  startsAt: string
  endsAt: string
}

export interface UpdateEventPayload {
  eventType?: CalendarEventType
  description?: string
  startsAt?: string
  endsAt?: string
}

export async function listEvents(
  ownerType: CalendarOwnerType,
  ownerId: string,
  from: string,
  to: string,
): Promise<CalendarEvent[]> {
  const res = await client.get<ApiSuccess<CalendarEvent[]>>(
    '/calendars/events',
    { params: { ownerType, ownerId, from, to } },
  )
  return res.data.data
}

export async function getEvent(id: string): Promise<CalendarEvent> {
  const res = await client.get<ApiSuccess<CalendarEvent>>(`/calendars/events/${id}`)
  return res.data.data
}

export async function createEvent(payload: CreateEventPayload): Promise<CalendarEvent> {
  const res = await client.post<ApiSuccess<CalendarEvent>>('/calendars/events', payload)
  return res.data.data
}

export async function updateEvent(id: string, payload: UpdateEventPayload): Promise<CalendarEvent> {
  const res = await client.patch<ApiSuccess<CalendarEvent>>(`/calendars/events/${id}`, payload)
  return res.data.data
}

export async function deleteEvent(id: string): Promise<void> {
  await client.delete(`/calendars/events/${id}`)
}
