/**
 * Centralized, locale-aware datetime formatting.
 *
 * Every date/time the user sees should format through here so it respects the
 * app's chosen language (`i18n.language`) rather than the browser locale. The
 * functions read `i18n.language` from the singleton at call time; components that
 * render dates already call `useTranslation()`, which re-renders on
 * `languageChanged`, so the active language is picked up on the next render.
 *
 * All formatters parse an ISO-8601 string (e.g. the backend's UTC `Instant`) and
 * return "" for unparseable input so callers can render unconditionally.
 */
import i18n from './index'

function parse(iso: string): Date | null {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? null : d
}

/** Hour + minute, locale-aware (e.g. "2:32 PM" or "14:32"). */
export function formatClock(iso: string): string {
  const d = parse(iso)
  if (!d) return ''
  return d.toLocaleTimeString(i18n.language, { hour: '2-digit', minute: '2-digit' })
}

/** Full date + time (e.g. "Jun 4, 2026, 2:32 PM"). For hover/tap reveal. */
export function formatDateTime(iso: string): string {
  const d = parse(iso)
  if (!d) return ''
  return d.toLocaleString(i18n.language, { dateStyle: 'medium', timeStyle: 'short' })
}

/** Date only, locale-aware. Pass `opts` to override the default medium style. */
export function formatDate(iso: string, opts: Intl.DateTimeFormatOptions = { dateStyle: 'medium' }): string {
  const d = parse(iso)
  if (!d) return ''
  return d.toLocaleDateString(i18n.language, opts)
}

/**
 * Human label for a start/(optional)end pair. Collapses same-day ranges to one date line:
 *   no end  → "Wed, Jun 4 · 2:32 PM"
 *   same day → "Wed, Jun 4 · 2:32 PM – 3:30 PM"
 *   spanning → "Wed, Jun 4, 2:32 PM – Thu, Jun 5, 9:00 AM"
 */
export function formatRange(startIso: string, endIso?: string): string {
  const s = parse(startIso)
  if (!s) return ''
  const lang = i18n.language
  const dateFmt: Intl.DateTimeFormatOptions = { weekday: 'short', month: 'short', day: 'numeric' }
  const timeFmt: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' }
  const start = `${s.toLocaleDateString(lang, dateFmt)} · ${s.toLocaleTimeString(lang, timeFmt)}`
  const e = endIso ? parse(endIso) : null
  if (!e) return start
  if (s.toDateString() === e.toDateString()) {
    return `${start} – ${e.toLocaleTimeString(lang, timeFmt)}`
  }
  return `${s.toLocaleString(lang, { ...dateFmt, ...timeFmt })} – ${e.toLocaleString(lang, { ...dateFmt, ...timeFmt })}`
}
