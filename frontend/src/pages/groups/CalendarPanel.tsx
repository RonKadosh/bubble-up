import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  CalendarEvent,
  CalendarEventType,
  EVENT_TYPES,
  createEvent,
  deleteEvent,
  listEvents,
  updateEvent,
} from '../../api/calendar'
import { sendLinkMessage } from '../../api/chat'
import { describeError } from '../../api/errors'
import { getRoomForEvent } from '../../api/room'
import { Button, IconButton } from '../../components/Button'
import {
  TYPE_COLORS,
  buildMonthGrid,
  fmtRange,
  fromLocalInput,
  monthRange,
  toLocalDateStr,
  toLocalInput,
} from './calendarFormat'

interface CalendarPanelProps {
  groupId: string
  meId: string | null
  isOwner: boolean
  /** Whether the current user is a member of this group. Non-members can't share to chat. */
  isMember: boolean
  /** Default chat room id for this group; null if not yet loaded. */
  chatRoomId: string | null
  onError: (msg: string) => void
  /** Called after a successful share so the parent can refresh rooms / unread counts. */
  onShared: () => void
  /** True when this panel is *not* the bento focus (i.e. minimized) — switches to AgendaView. */
  compact?: boolean
}

/**
 * Routes to AgendaView (compact) or MonthGridView (maximized) based on the
 * bento focus flag from the parent. Both sub-views own their own data fetch.
 */
export function CalendarPanel(props: CalendarPanelProps) {
  if (props.compact) return <AgendaView {...props} />
  return <MonthGridView {...props} />
}

// ---------------------------------------------------------------------------
// AgendaView — compact: rolling next-30-days, read-only
// ---------------------------------------------------------------------------

const AGENDA_DAYS = 30
const AGENDA_MAX_VISIBLE = 5

function AgendaView({ groupId, isMember, onError }: CalendarPanelProps) {
  const { t, i18n } = useTranslation()
  const [events, setEvents] = useState<CalendarEvent[]>([])

  useEffect(() => {
    if (!isMember) { setEvents([]); return }
    let cancelled = false
    const now = Date.now()
    const fromIso = new Date(now).toISOString()
    const toIso = new Date(now + AGENDA_DAYS * 24 * 60 * 60 * 1000).toISOString()
    listEvents('GROUP', groupId, fromIso, toIso)
      .then((evts) => {
        if (cancelled) return
        setEvents([...evts].sort((a, b) => a.startsAt.localeCompare(b.startsAt)))
      })
      .catch((e) => {
        if (cancelled) return
        onError(describeError(e, t,
          { NOT_GROUP_MEMBER: 'groups.error.notMember' },
          'groups.error.loadEvents'))
        setEvents([])
      })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, isMember])

  const visible = events.slice(0, AGENDA_MAX_VISIBLE)
  const hiddenCount = Math.max(0, events.length - AGENDA_MAX_VISIBLE)
  const weekdayFmt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { weekday: 'short' }),
    [i18n.language],
  )

  return (
    <div className="flex-1 flex flex-col overflow-y-auto p-3 gap-2">
      {events.length === 0 ? (
        <p className="text-sm text-muted">{t('groups.calendar.noUpcoming')}</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {visible.map((ev) => {
            const start = new Date(ev.startsAt)
            return (
              <li key={ev.id} className="flex items-center gap-3 bg-surface border border-line rounded-2xl px-3 py-2">
                <div className="flex flex-col items-center justify-center bg-surface-muted text-secondary rounded-xl px-2 py-1 min-w-[3rem] shrink-0">
                  <span className="text-[10px] uppercase tracking-wide">{weekdayFmt.format(start)}</span>
                  <span className="text-base font-bold leading-none">{start.getDate()}</span>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className={`text-[10px] px-2 py-0.5 rounded-md ${TYPE_COLORS[ev.eventType]}`}>
                      {ev.eventType.replace('_', ' ')}
                    </span>
                    <span className="text-xs text-muted">{fmtRange(ev.startsAt, ev.endsAt)}</span>
                  </div>
                  {ev.description && (
                    <p className="text-sm text-secondary mt-0.5 truncate">{ev.description}</p>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
      {hiddenCount > 0 && (
        <p className="text-xs text-muted text-center mt-1">
          {t('groups.calendar.moreEvents', { count: hiddenCount })}
        </p>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// MonthGridView — maximized: toolbar + 6×7 grid + modal
// ---------------------------------------------------------------------------

type ModalState =
  | { mode: 'create'; initialDateStr: string }
  | { mode: 'edit'; event: CalendarEvent }

function MonthGridView({ groupId, meId, isOwner, isMember, chatRoomId, onError, onShared }: CalendarPanelProps) {
  const { t, i18n } = useTranslation()
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth())
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [modalState, setModalState] = useState<ModalState | null>(null)

  async function reload() {
    if (!isMember) { setEvents([]); return }
    const { from, to } = monthRange(year, month)
    try {
      setEvents(await listEvents('GROUP', groupId, from.toISOString(), to.toISOString()))
    } catch (e) {
      onError(describeError(e, t,
        { NOT_GROUP_MEMBER: 'groups.error.notMember' },
        'groups.error.loadEvents'))
      setEvents([])
    }
  }

  useEffect(() => {
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, year, month, isMember])

  function shiftMonth(delta: number) {
    let m = month + delta
    let y = year
    while (m < 0) { m += 12; y -= 1 }
    while (m > 11) { m -= 12; y += 1 }
    setYear(y); setMonth(m)
  }

  function goToToday() {
    const today = new Date()
    setYear(today.getFullYear())
    setMonth(today.getMonth())
  }

  const cells = useMemo(() => buildMonthGrid(year, month), [year, month])

  const eventsByDateStr = useMemo(() => {
    const map: Record<string, CalendarEvent[]> = {}
    for (const ev of events) {
      const k = toLocalDateStr(new Date(ev.startsAt))
      ;(map[k] ??= []).push(ev)
    }
    for (const arr of Object.values(map)) {
      arr.sort((a, b) => a.startsAt.localeCompare(b.startsAt))
    }
    return map
  }, [events])

  const monthLabel = useMemo(
    () => new Date(Date.UTC(year, month, 1)).toLocaleDateString(i18n.language, {
      month: 'long', year: 'numeric', timeZone: 'UTC',
    }),
    [year, month, i18n.language],
  )

  // Weekday names from a known Sunday (Jan 7 2024) — localized per current language.
  const weekdayLabels = useMemo(() => {
    const fmt = new Intl.DateTimeFormat(i18n.language, { weekday: 'short' })
    return Array.from({ length: 7 }, (_, i) => fmt.format(new Date(2024, 0, 7 + i)))
  }, [i18n.language])

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      <div className="flex items-center justify-between bg-surface border-b border-line px-4 py-2.5 gap-2">
        <div className="flex items-center gap-1">
          <IconButton variant="cell" size="sm" onClick={() => shiftMonth(-1)} aria-label={t('groups.calendar.prevMonth')}>‹</IconButton>
          <span className="font-semibold text-sm text-base px-2">{monthLabel}</span>
          <IconButton variant="cell" size="sm" onClick={() => shiftMonth(1)} aria-label={t('groups.calendar.nextMonth')}>›</IconButton>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="xs" onClick={goToToday}>{t('groups.calendar.today')}</Button>
          <Button
            variant="cell"
            size="sm"
            onClick={() => setModalState({ mode: 'create', initialDateStr: toLocalDateStr(new Date()) })}
          >
            {t('groups.calendar.newEvent')}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-7 bg-surface-muted/40 border-b border-line shrink-0">
        {weekdayLabels.map((label, i) => (
          <div key={i} className="text-center text-[10px] font-bold uppercase tracking-wide text-muted py-1.5">
            {label}
          </div>
        ))}
      </div>

      <div className="flex-1 min-h-0 grid grid-cols-7 grid-rows-6 gap-px bg-line p-px overflow-hidden">
        {cells.map((cell) => {
          const dayEvents = eventsByDateStr[cell.dateStr] ?? []
          return (
            <button
              key={cell.dateStr}
              type="button"
              onClick={() => setModalState({ mode: 'create', initialDateStr: cell.dateStr })}
              className={`bg-surface min-h-0 p-1.5 flex flex-col items-stretch text-start overflow-hidden hover:bg-surface-muted/60 transition-colors ${
                cell.isCurrentMonth ? '' : 'text-muted bg-surface-muted/40'
              }`}
            >
              <span className={`text-xs font-semibold self-start mb-1 px-1.5 py-0.5 rounded-md ${
                cell.isToday ? 'bg-primary-500 text-white' : ''
              }`}>{cell.date.getDate()}</span>
              <div className="flex-1 min-h-0 overflow-y-auto flex flex-col gap-0.5">
                {dayEvents.map((ev) => (
                  <span
                    key={ev.id}
                    onClick={(e) => { e.stopPropagation(); setModalState({ mode: 'edit', event: ev }) }}
                    className={`text-[10px] leading-tight px-1.5 py-0.5 rounded truncate cursor-pointer ${TYPE_COLORS[ev.eventType]}`}
                    title={`${fmtRange(ev.startsAt, ev.endsAt)}${ev.description ? ' — ' + ev.description : ''}`}
                  >
                    {ev.description?.trim() || ev.eventType.replace('_', ' ')}
                  </span>
                ))}
              </div>
            </button>
          )
        })}
      </div>

      {modalState && (
        <EventModal
          groupId={groupId}
          meId={meId}
          isOwner={isOwner}
          isMember={isMember}
          chatRoomId={chatRoomId}
          mode={modalState.mode}
          initialEvent={modalState.mode === 'edit' ? modalState.event : undefined}
          initialDateStr={modalState.mode === 'create' ? modalState.initialDateStr : undefined}
          onClose={() => setModalState(null)}
          onSaved={() => { setModalState(null); reload() }}
          onError={onError}
          onShared={onShared}
        />
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// EventModal — shared create/edit overlay
// ---------------------------------------------------------------------------

interface EventModalProps {
  groupId: string
  meId: string | null
  isOwner: boolean
  isMember: boolean
  chatRoomId: string | null
  mode: 'create' | 'edit'
  initialEvent?: CalendarEvent
  initialDateStr?: string
  onClose: () => void
  onSaved: () => void
  onError: (msg: string) => void
  onShared: () => void
}

function EventModal({
  groupId, meId, isOwner, isMember, chatRoomId,
  mode, initialEvent, initialDateStr,
  onClose, onSaved, onError, onShared,
}: EventModalProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const initialState = useMemo(() => {
    if (mode === 'edit' && initialEvent) {
      return {
        eventType: initialEvent.eventType,
        description: initialEvent.description ?? '',
        startsAt: toLocalInput(initialEvent.startsAt),
        endsAt: toLocalInput(initialEvent.endsAt),
      }
    }
    const dateStr = initialDateStr ?? toLocalDateStr(new Date())
    return {
      eventType: 'STUDY_SESSION' as CalendarEventType,
      description: '',
      startsAt: `${dateStr}T09:00`,
      endsAt: `${dateStr}T10:00`,
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const [eventType, setEventType] = useState<CalendarEventType>(initialState.eventType)
  const [description, setDescription] = useState(initialState.description)
  const [startsAt, setStartsAt] = useState(initialState.startsAt)
  const [endsAt, setEndsAt] = useState(initialState.endsAt)

  const isEdit = mode === 'edit' && !!initialEvent
  const canMutate = isEdit && initialEvent ? (meId === initialEvent.createdBy || isOwner) : false
  // Once an event's start time has passed it's fixated — the backend rejects edits
  // (EVENT_ALREADY_STARTED), so we lock the form to match.
  const isStarted = isEdit && initialEvent ? new Date(initialEvent.startsAt).getTime() <= Date.now() : false
  // Can't pick a start in the past; mirrors the backend EVENT_STARTS_IN_PAST guard.
  const minStart = toLocalInput(new Date().toISOString())

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (isStarted) return
    try {
      if (mode === 'edit' && initialEvent) {
        await updateEvent(initialEvent.id, {
          eventType,
          description: description || undefined,
          startsAt: fromLocalInput(startsAt),
          endsAt: fromLocalInput(endsAt),
        })
      } else {
        await createEvent({
          ownerType: 'GROUP',
          ownerId: groupId,
          eventType,
          description: description || undefined,
          startsAt: fromLocalInput(startsAt),
          endsAt: fromLocalInput(endsAt),
        })
      }
      onSaved()
    } catch (err) {
      onError(describeError(err, t,
        {
          INVALID_EVENT_TIME_RANGE: 'groups.error.invalidTimeRange',
          EVENT_STARTS_IN_PAST: 'groups.error.eventInPast',
          EVENT_ALREADY_STARTED: 'groups.error.eventLocked',
          NOT_GROUP_MEMBER: 'groups.error.notMember',
          NOT_EVENT_AUTHOR_OR_OWNER: 'groups.error.notEventAuthor',
          GROUP_SCHEDULE_CONFLICT: 'groups.error.scheduleConflictExpert',
        },
        'groups.error.saveEvent'))
    }
  }

  async function handleDelete() {
    if (mode !== 'edit' || !initialEvent) return
    if (!confirm(t('groups.calendar.confirmDelete'))) return
    try {
      await deleteEvent(initialEvent.id)
      onSaved()
    } catch (err) {
      onError(describeError(err, t,
        {
          NOT_EVENT_AUTHOR_OR_OWNER: 'groups.error.notEventAuthorDelete',
          EVENT_ALREADY_STARTED: 'groups.error.eventLocked',
        },
        'groups.error.deleteEvent'))
    }
  }

  async function handleShareToChat() {
    if (mode !== 'edit' || !initialEvent || !chatRoomId) return
    try {
      await sendLinkMessage(chatRoomId, 'CALENDAR_EVENT', initialEvent.id)
      onShared()
      onClose()
    } catch {
      onError(t('groups.error.shareToChat'))
    }
  }

  async function handleEnterRoom() {
    if (mode !== 'edit' || !initialEvent) return
    try {
      const room = await getRoomForEvent(initialEvent.id)
      if (room.scope === 'EXPERT_SESSION' && room.expertSessionId) {
        navigate(`/sessions/${room.expertSessionId}`)
      } else {
        navigate(`/rooms/${room.id}`)
      }
    } catch (err) {
      onError(describeError(err, t,
        {
          ROOM_NOT_FOUND: 'groups.error.roomNotFound',
          ROOM_NOT_YET_OPEN: 'groups.error.roomNotYetOpen',
          ROOM_ENDED: 'groups.error.roomEnded',
          NOT_GROUP_MEMBER: 'groups.error.notMember',
          JITSI_NOT_CONFIGURED: 'groups.error.jitsiNotConfigured',
        },
        'groups.error.openRoom'))
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-3 tablet:p-4" onClick={onClose}>
      <form
        onSubmit={handleSubmit}
        className="bg-surface rounded-3xl shadow-bubble w-full max-w-[28rem] max-h-[80vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center justify-between">
          <h3 className="font-semibold">
            {isEdit ? t('groups.calendar.editEvent') : t('groups.calendar.newEventHeading')}
          </h3>
          <button type="button" onClick={onClose} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-2">
          {isStarted && (
            <p className="text-xs text-muted bg-surface-muted rounded-xl px-3 py-2">
              {t('groups.calendar.lockedNote')}
            </p>
          )}
          <select
            value={eventType}
            onChange={(e) => setEventType(e.target.value as CalendarEventType)}
            disabled={isStarted}
            className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400 disabled:opacity-60"
          >
            {EVENT_TYPES.map((et) => (
              <option key={et} value={et}>{et.replace('_', ' ')}</option>
            ))}
          </select>
          <div className="flex gap-2 text-sm">
            <label className="flex flex-col flex-1">
              <span className="text-xs text-muted">{t('groups.calendar.start')}</span>
              <input
                type="datetime-local"
                value={startsAt}
                onChange={(e) => setStartsAt(e.target.value)}
                min={minStart}
                disabled={isStarted}
                className="border border-line bg-surface rounded-xl px-3 py-2 focus:outline-none focus:border-primary-400 disabled:opacity-60"
                required
              />
            </label>
            <label className="flex flex-col flex-1">
              <span className="text-xs text-muted">{t('groups.calendar.end')}</span>
              <input
                type="datetime-local"
                value={endsAt}
                onChange={(e) => setEndsAt(e.target.value)}
                min={startsAt || minStart}
                disabled={isStarted}
                className="border border-line bg-surface rounded-xl px-3 py-2 focus:outline-none focus:border-primary-400 disabled:opacity-60"
                required
              />
            </label>
          </div>
          <textarea
            placeholder={t('groups.calendar.descriptionPlaceholder')}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            disabled={isStarted}
            className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400 disabled:opacity-60"
            rows={3}
          />
        </div>

        <div className="px-4 py-3 border-t border-line flex flex-wrap items-center gap-2 justify-end">
          {isEdit && isMember && eventType === 'STUDY_SESSION' && initialEvent && (() => {
            const now = Date.now()
            const opensAt = new Date(initialEvent.startsAt).getTime() - 15 * 60_000
            const endsAtMs = new Date(initialEvent.endsAt).getTime()
            if (now > endsAtMs) {
              return (
                <Button type="button" size="sm" disabled title="This session has ended">
                  Ended
                </Button>
              )
            }
            if (now < opensAt) {
              const mins = Math.max(1, Math.ceil((opensAt - now) / 60_000))
              return (
                <Button type="button" size="sm" disabled title="Room opens 15 min before the session starts">
                  Opens in {mins} min
                </Button>
              )
            }
            return (
              <Button type="button" size="sm" onClick={handleEnterRoom}>
                Enter Room
              </Button>
            )
          })()}
          {isEdit && isMember && chatRoomId && (
            <Button type="button" variant="cell" size="sm" onClick={handleShareToChat}>
              {t('groups.calendar.shareToChat')}
            </Button>
          )}
          {canMutate && !isStarted && (
            <Button type="button" variant="danger" size="sm" onClick={handleDelete}>
              {t('common.delete')}
            </Button>
          )}
          <div className="flex-1" />
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          {!isStarted && (
            <Button type="submit" size="sm">
              {isEdit ? t('common.save') : t('common.create')}
            </Button>
          )}
        </div>
      </form>
    </div>
  )
}
