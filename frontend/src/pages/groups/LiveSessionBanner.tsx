import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarEvent } from '../../api/calendar'
import { getRoomForEvent } from '../../api/room'
import { describeError } from '../../api/errors'
import { useTranslation } from 'react-i18next'
import type { TFunction } from 'i18next'

interface Props {
  liveSession: CalendarEvent | null
  onError: (msg: string) => void
}

function relativeStarted(t: TFunction, now: number, startsAt: string): string {
  const time = new Date(startsAt).getTime()
  const min = Math.max(0, Math.floor((now - time) / 60000))
  if (min < 1) return t('room.rel.justNow')
  if (min < 60) return t('room.rel.minAgo', { count: min })
  return t('room.rel.hAgo', { count: Math.floor(min / 60) })
}

/**
 * Top-of-bubble banner shown when a session is currently joinable:
 *   - STUDY_SESSION  (own group event)   : `[startsAt - 15min, endsAt]`
 *   - EXPERT_SESSION (enrolled session)  : `[startsAt -  5min, endsAt]`
 * Hidden when no session is live. One-click Join navigates into the right
 * room — `/rooms/{id}` for a STUDY_SESSION, `/sessions/{id}` for an
 * EXPERT_SESSION (BubbleRoomPage redirects bookmarked EXPERT_SESSION room
 * URLs the same way, this just saves the hop).
 */
export function LiveSessionBanner({ liveSession, onError }: Props) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!liveSession) return
    const id = setInterval(() => setNow(Date.now()), 30000)
    return () => clearInterval(id)
  }, [liveSession])

  if (!liveSession) return null

  const isExpert = liveSession.eventType === 'EXPERT_SESSION'
  const emoji = isExpert ? '🎓' : '🎥'
  const fallback = isExpert ? t('room.liveBanner.fallbackExpert') : t('room.liveBanner.fallback')

  const started = new Date(liveSession.startsAt).getTime() <= now
  const label = started
    ? t('room.liveBanner.started', { when: relativeStarted(t, now, liveSession.startsAt) })
    : t('room.liveBanner.opensIn', {
        count: Math.max(0, Math.ceil((new Date(liveSession.startsAt).getTime() - now) / 60000)),
      })

  async function handleJoin() {
    if (!liveSession) return
    try {
      const room = await getRoomForEvent(liveSession.id)
      if (room.scope === 'EXPERT_SESSION' && room.expertSessionId) {
        navigate(`/sessions/${room.expertSessionId}`)
      } else {
        navigate(`/rooms/${room.id}`)
      }
    } catch (err) {
      onError(describeError(err, t,
        {
          ROOM_NOT_YET_OPEN: 'groups.error.roomNotYetOpen',
          EXPERT_SESSION_NOT_OPEN_FOR_JOIN_YET: 'groups.error.roomNotYetOpen',
          ROOM_ENDED: 'groups.error.roomEnded',
          NOT_GROUP_MEMBER: 'groups.error.notMember',
          FORBIDDEN: 'groups.error.notMember',
          JITSI_NOT_CONFIGURED: 'groups.error.jitsiNotConfigured',
        },
        'groups.error.openRoom'))
    }
  }

  return (
    <div className="flex items-center gap-3 px-3 tablet:px-6 py-2.5 bg-gradient-to-r from-primary-500/15 via-primary-500/10 to-transparent border-b border-line">
      <span className="relative flex h-2.5 w-2.5 shrink-0">
        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-500 opacity-75" />
        <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-rose-500" />
      </span>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold truncate">
          {emoji} {liveSession.description?.trim() || fallback}
        </p>
        <p className="text-xs text-muted truncate">{label}</p>
      </div>
      <button
        type="button"
        onClick={handleJoin}
        className="shrink-0 bg-primary-600 hover:bg-primary-700 text-white text-sm font-semibold px-4 py-2 rounded-full transition-colors bubble-pop"
      >
        {t('room.liveBanner.join')}
      </button>
    </div>
  )
}
