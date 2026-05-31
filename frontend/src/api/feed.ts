import client, { ApiSuccess } from './client'

/**
 * The dashboard feed: one curated, sectioned digest fetched in a single request
 * (replaces the old client-side fan-out). Sections arrive in priority order and
 * empty sections are omitted. Bounded — no pagination.
 */

export type FeedSectionKey = 'LIVE' | 'UPCOMING' | 'ACTIVITY' | 'DISCOVERY'

export type FeedItemKind =
  | 'liveSession'
  | 'liveGroupRoom'
  | 'upcomingEvent'
  | 'memberJoin'
  | 'memberLeave'
  | 'unread'
  | 'file'
  | 'recommendation'

export type FeedCtaType =
  | 'JOIN_SESSION'
  | 'JOIN_ROOM'
  | 'VIEW_BUBBLE'
  | 'VIEW_EVENT'
  | 'JOIN_BUBBLE'

export interface FeedCta {
  type: FeedCtaType
  targetId?: string
}

export interface FeedItem {
  kind: FeedItemKind
  groupId?: string
  groupName?: string
  /** Universal sort key (occurrence/activity time). Absent for discovery. */
  ts?: string
  title?: string
  subtitle?: string
  startsAt?: string
  endsAt?: string
  eventType?: string
  unreadCount?: number
  matchPercent?: number
  memberCount?: number
  /** Users currently in the live room's video call (liveGroupRoom / liveSession). */
  participantCount?: number
  cta?: FeedCta
}

export interface FeedSection {
  key: FeedSectionKey
  items: FeedItem[]
}

export interface Feed {
  sections: FeedSection[]
}

export async function getFeed(): Promise<Feed> {
  const res = await client.get<ApiSuccess<Feed>>('/feed')
  return res.data.data
}
