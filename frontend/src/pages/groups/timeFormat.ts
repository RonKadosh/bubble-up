/**
 * Relative-time formatter for presence ("5m ago", "just now"). Returns only the
 * relative phrase — the caller wraps it (e.g. "last seen {{rel}}"). Resolution buckets:
 *   <45s   → "just now"
 *   <60m   → "{n}m ago"
 *   <24h   → "{n}h ago"
 *   <7d    → "{n}d ago"
 *   else   → "on <locale date>"
 */
import type { TFunction } from 'i18next'

export function formatRelative(iso: string, t: TFunction): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return t('groups.members.relJustNow')
  const diffSec = Math.max(0, Math.floor((Date.now() - then) / 1000))

  if (diffSec < 45) return t('groups.members.relJustNow')
  if (diffSec < 60 * 60) return t('groups.members.relMinutes', { count: Math.floor(diffSec / 60) })
  if (diffSec < 60 * 60 * 24) return t('groups.members.relHours', { count: Math.floor(diffSec / 3600) })
  if (diffSec < 60 * 60 * 24 * 7) return t('groups.members.relDays', { count: Math.floor(diffSec / 86400) })
  return t('groups.members.relOn', { date: new Date(iso).toLocaleDateString() })
}
