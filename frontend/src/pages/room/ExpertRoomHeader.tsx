import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import { useActiveRoomStore } from '../../store/activeRoomStore'
import type { BubbleRoom } from '../../api/room'
import type { ExpertSession } from '../../api/expert'
import { fmtRelative } from './timeFormat'

interface Props {
  session: ExpertSession
  room: BubbleRoom
  /** Live count of users currently in the video call. */
  inCall?: number
  /**
   * The Bubble the user entered this session from (via a chat/calendar link or a
   * feed CTA). When set, the header offers "Open Bubble" (juggle back to the hub
   * while the call stays alive as a PiP) and Leave returns there instead of the
   * expert directory. Null when entered context-free (e.g. a direct link).
   */
  fromGroupId?: string | null
}

/**
 * Header for the expert-session room — visually parallel to {@link RoomHeader}.
 * When the user arrived from a Bubble it mirrors {@link RoomHeader}'s juggle:
 * "Open Bubble" pops back to the hub (call persists as a PiP) and Leave returns
 * to the Bubble. Without an originating Bubble it falls back to the expert's
 * public profile / the directory.
 */
export function ExpertRoomHeader({ session, room, inCall = 0, fromGroupId = null }: Props) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30000)
    return () => clearInterval(id)
  }, [])

  const startsAt = room.startsAt ? new Date(room.startsAt).getTime() : null
  const endsAt = room.endsAt ? new Date(room.endsAt).getTime() : null
  let timeLabel = ''
  if (startsAt && endsAt) {
    if (now < startsAt) timeLabel = t('room.status.starts', { when: fmtRelative(t, now, room.startsAt) })
    else if (now > endsAt) timeLabel = t('room.status.ended', { when: fmtRelative(t, now, room.endsAt) })
    else timeLabel = t('room.status.liveEnds', { when: fmtRelative(t, now, room.endsAt) })
  }

  return (
    <div className="flex items-center justify-between gap-3 px-4 py-3 bg-surface border-b border-line">
      <div className="flex flex-col min-w-0">
        <div className="flex items-center gap-2 min-w-0">
          <h1 className="text-base font-semibold truncate">{session.title}</h1>
          {inCall > 0 && (
            <span className="shrink-0 inline-flex items-center gap-1 text-xs font-semibold text-rose-600 bg-rose-500/10 rounded-full px-2 py-0.5">
              <span className="inline-flex h-1.5 w-1.5 rounded-full bg-rose-500" />
              {t('room.inCall', { count: inCall })}
            </span>
          )}
        </div>
        {timeLabel && <span className="text-xs text-muted truncate">{timeLabel}</span>}
      </div>
      <div className="flex items-center gap-2">
        {fromGroupId ? (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate('/groups')}
            title={t('room.openBubbleTitle')}
          >
            {t('room.openBubble')}
          </Button>
        ) : (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(`/experts/${session.expertUserId}`)}
            title={t('expertRoom.openProfileTitle')}
          >
            {t('expertRoom.openProfile')}
          </Button>
        )}
        <Button
          variant="danger"
          size="sm"
          onClick={() => {
            // Actually end the call — clear the active room so PersistentVideo
            // disposes the iframe instead of shrinking it to a floating PiP.
            useActiveRoomStore.getState().clearActive()
            // Return to the Bubble we came from when we know it; otherwise the directory.
            navigate(fromGroupId ? '/groups' : '/experts')
          }}
        >
          {t('room.leave')}
        </Button>
      </div>
    </div>
  )
}
