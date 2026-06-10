import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createEvent } from '../../api/calendar'
import { describeError } from '../../api/errors'
import { Button } from '../../components/Button'
import { fromLocalInput, toLocalInput } from './calendarFormat'

interface Props {
  groupId: string
  groupName: string
  onClose: () => void
  /** @param opensNow true when the session is joinable immediately (start within the 15-min open window). */
  onScheduled: (opensNow: boolean) => void
  onError: (msg: string) => void
}

/**
 * Light wrapper around the calendar event create flow, presented as a
 * room-first dialog. The underlying record is a STUDY_SESSION calendar event;
 * the backend auto-creates the Room.
 */
export function ScheduleRoomModal({ groupId, groupName, onClose, onScheduled, onError }: Props) {
  const { t } = useTranslation()

  // Default start = next quarter hour from now; duration = 1h.
  const defaultStart = useMemo(() => {
    const d = new Date()
    d.setSeconds(0, 0)
    const minutes = d.getMinutes()
    d.setMinutes(minutes - (minutes % 15) + 15)
    return d
  }, [])

  const [startsAtInput, setStartsAtInput] = useState(toLocalInput(defaultStart.toISOString()))
  const [durationMinutes, setDurationMinutes] = useState(60)
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (submitting) return
    setSubmitting(true)
    try {
      const startsAtIso = fromLocalInput(startsAtInput)
      const endsAtIso = new Date(new Date(startsAtIso).getTime() + durationMinutes * 60_000).toISOString()
      await createEvent({
        ownerType: 'GROUP',
        ownerId: groupId,
        eventType: 'STUDY_SESSION',
        description: description.trim() || t('room.schedule.defaultDescription', { groupName }),
        startsAt: startsAtIso,
        endsAt: endsAtIso,
      })
      // STUDY_SESSIONs open 15 min before start — within that window it's live right away.
      const opensNow = Date.now() >= new Date(startsAtIso).getTime() - 15 * 60_000
      onScheduled(opensNow)
      onClose()
    } catch (err) {
      onError(describeError(err, t,
        {
          INVALID_EVENT_TIME_RANGE: 'groups.error.invalidTimeRange',
          EVENT_STARTS_IN_PAST: 'groups.error.eventInPast',
          NOT_GROUP_MEMBER: 'groups.error.notMember',
          GROUP_SCHEDULE_CONFLICT: 'groups.error.scheduleConflictLive',
        },
        'groups.error.saveEvent'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-3 tablet:p-4 animate-fade-in" onClick={onClose}>
      <form
        onSubmit={handleSubmit}
        className="bg-surface rounded-3xl shadow-bubble animate-pop-in w-full max-w-[28rem] max-h-[80vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center justify-between">
          <h3 className="font-semibold">{t('room.schedule.title')}</h3>
          <button type="button" onClick={onClose} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-xs text-muted">{t('room.schedule.start')}</span>
            <input
              type="datetime-local"
              value={startsAtInput}
              onChange={(e) => setStartsAtInput(e.target.value)}
              min={toLocalInput(new Date().toISOString())}
              className="border border-line bg-surface rounded-xl px-3 py-2 focus:outline-none focus:border-primary-400"
              required
            />
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-xs text-muted">{t('room.schedule.duration')}</span>
            <select
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(parseInt(e.target.value, 10))}
              className="border border-line bg-surface rounded-xl px-3 py-2 focus:outline-none focus:border-primary-400"
            >
              <option value={30}>{t('room.schedule.duration30')}</option>
              <option value={45}>{t('room.schedule.duration45')}</option>
              <option value={60}>{t('room.schedule.duration60')}</option>
              <option value={90}>{t('room.schedule.duration90')}</option>
              <option value={120}>{t('room.schedule.duration120')}</option>
            </select>
          </label>

          <label className="flex flex-col gap-1 text-sm">
            <span className="text-xs text-muted">{t('groups.calendar.descriptionPlaceholder')}</span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              placeholder={t('room.schedule.defaultDescription', { groupName })}
              className="border border-line bg-surface rounded-xl px-3 py-2 focus:outline-none focus:border-primary-400"
            />
          </label>

          <p className="text-xs text-muted">
            {t('room.schedule.explainer')}
          </p>
        </div>

        <div className="px-4 py-3 border-t border-line flex items-center justify-end gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button variant="deep" type="submit" size="sm" disabled={submitting}>
            {submitting ? t('room.schedule.submitting') : t('room.schedule.submit')}
          </Button>
        </div>
      </form>
    </div>
  )
}
