import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import type { BubbleRoom } from '../../api/room'
import { useActiveRoomStore } from '../../store/activeRoomStore'
import { fmtRelative } from './timeFormat'

interface Props {
  room: BubbleRoom
}

export function RoomHeader({ room }: Props) {
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
        <h1 className="text-base font-semibold truncate">{t('room.title')}</h1>
        {timeLabel && <span className="text-xs text-muted truncate">{timeLabel}</span>}
      </div>
      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="sm"
          onClick={() => navigate('/groups')}
          title={t('room.openBubbleTitle')}
        >
          {t('room.openBubble')}
        </Button>
        <Button
          variant="danger"
          size="sm"
          onClick={() => {
            useActiveRoomStore.getState().clearActive()
            navigate('/groups')
          }}
        >
          {t('room.leave')}
        </Button>
      </div>
    </div>
  )
}
