import type { TFunction } from 'i18next'
import { formatClock } from '../../i18n/datetime'

/**
 * Renders a target ISO time relative to {@code now} using the shared
 * {@code room.rel.*} i18n bundle. Returns "" if {@code target} is null so
 * callers can render conditionally without a guard.
 */
export function fmtRelative(t: TFunction, now: number, target: string | null): string {
  if (!target) return ''
  const time = new Date(target).getTime()
  const diffMs = time - now
  const absMin = Math.round(Math.abs(diffMs) / 60000)
  if (diffMs > 0) {
    if (absMin < 60) return t('room.rel.in', { count: absMin })
    return t('room.rel.at', { time: formatClock(target) })
  }
  if (absMin < 1) return t('room.rel.justNow')
  if (absMin < 60) return t('room.rel.minAgo', { count: absMin })
  const h = Math.round(absMin / 60)
  return t('room.rel.hAgo', { count: h })
}
