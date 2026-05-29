/**
 * Shared formatters + constants for calendar events.
 *
 * Used by both `CalendarPanel` (month nav + event form) and `ChatPanel`'s
 * nested `CalendarLinkCard` (which renders the same date range + event-type pill
 * inside a chat link bubble). Two callers in the same feature folder = co-locate.
 */
import { CalendarEventType } from '../../api/calendar'

/** Tailwind badge classes per event type. */
export const TYPE_COLORS: Record<CalendarEventType, string> = {
  STUDY_SESSION: 'bg-primary-100 text-primary-700',
  // EXPERT_SESSION events are surfaced on a group's calendar via enrollment,
  // not by group members directly. Distinct purple-ish hue keeps them legible
  // against the other categories at a glance.
  EXPERT_SESSION: 'bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-200',
  MEETING: 'bg-primary-50 text-primary-700',
  DEADLINE: 'bg-danger-soft text-danger',
  EXAM: 'bg-amber-100 text-amber-800',
  ASSIGNMENT: 'bg-primary-200 text-primary-800',
  REMINDER: 'bg-surface-muted text-secondary',
  OTHER: 'bg-surface-muted text-secondary',
}

/** UTC [start, end) range covering the given month — used to fetch events for one month. */
export function monthRange(year: number, month0: number): { from: Date; to: Date } {
  return {
    from: new Date(Date.UTC(year, month0, 1)),
    to: new Date(Date.UTC(year, month0 + 1, 1)),
  }
}

/** Human label for an event's start/end pair. Collapses same-day ranges to one date line. */
export function fmtRange(start: string, end: string): string {
  const s = new Date(start)
  const e = new Date(end)
  const dateFmt: Intl.DateTimeFormatOptions = { weekday: 'short', month: 'short', day: 'numeric' }
  const timeFmt: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' }
  const sameDay = s.toDateString() === e.toDateString()
  if (sameDay) {
    return `${s.toLocaleDateString(undefined, dateFmt)} · ${s.toLocaleTimeString(undefined, timeFmt)} – ${e.toLocaleTimeString(undefined, timeFmt)}`
  }
  return `${s.toLocaleString(undefined, { ...dateFmt, ...timeFmt })} – ${e.toLocaleString(undefined, { ...dateFmt, ...timeFmt })}`
}

/** ISO → `<input type="datetime-local">` value (local-time, no timezone suffix). */
export function toLocalInput(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** `<input type="datetime-local">` value → ISO string. */
export function fromLocalInput(local: string): string {
  return new Date(local).toISOString()
}

export interface MonthGridCell {
  date: Date
  dateStr: string
  isCurrentMonth: boolean
  isToday: boolean
}

/**
 * 42-cell (6 weeks × 7 days) grid for the given month, padded with prev/next
 * month days so the grid always starts on Sunday and ends on Saturday.
 *
 * Local time (not UTC): day cells are user-facing — an event "today" should
 * appear in today's local cell. The month-bound fetch still uses `monthRange()`
 * (UTC) for the API call; this grid is just the visual frame around those events.
 */
export function buildMonthGrid(year: number, month0: number): MonthGridCell[] {
  const firstDayIndex = new Date(year, month0, 1).getDay()
  const daysInMonth = new Date(year, month0 + 1, 0).getDate()
  const daysInPrev = new Date(year, month0, 0).getDate()
  const today = new Date()

  const cells: MonthGridCell[] = []
  for (let i = firstDayIndex - 1; i >= 0; i--) {
    const d = new Date(year, month0 - 1, daysInPrev - i)
    cells.push({ date: d, dateStr: toLocalDateStr(d), isCurrentMonth: false, isToday: isSameYMD(d, today) })
  }
  for (let i = 1; i <= daysInMonth; i++) {
    const d = new Date(year, month0, i)
    cells.push({ date: d, dateStr: toLocalDateStr(d), isCurrentMonth: true, isToday: isSameYMD(d, today) })
  }
  let nextDay = 1
  while (cells.length < 42) {
    const d = new Date(year, month0 + 1, nextDay++)
    cells.push({ date: d, dateStr: toLocalDateStr(d), isCurrentMonth: false, isToday: isSameYMD(d, today) })
  }
  return cells
}

function isSameYMD(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear()
      && a.getMonth() === b.getMonth()
      && a.getDate() === b.getDate()
}

/** Local-time 'YYYY-MM-DD' from a Date — matches the `dateStr` the grid produces. */
export function toLocalDateStr(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
