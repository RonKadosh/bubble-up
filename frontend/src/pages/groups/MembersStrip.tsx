import { useTranslation } from 'react-i18next'
import { GroupMember } from '../../api/groups'
import { PresenceEntry } from '../../api/presence'
import { Avatar } from '../../components/Avatar'
import { ChevronIcon } from '../../components/Icons'
import { formatRelative } from './timeFormat'

interface MembersStripProps {
  members: GroupMember[]
  presence: Record<string, PresenceEntry>
  me: string | null
  isOwner: boolean
  /** Opens the Bubble Info drawer. The whole strip is the trigger. */
  onOpenInfo: () => void
}

/**
 * Slim horizontal row of member chips (avatar + presence dot + name) shown
 * above the bento. The entire strip is a single button that opens the
 * {@link BubbleInfoDrawer} — per-member profile links live inside that drawer,
 * so the strip stays one clean click target.
 *
 * Order: me first, then online, then by display name.
 */
export function MembersStrip({ members, presence, me, isOwner, onOpenInfo }: MembersStripProps) {
  const { t } = useTranslation()

  if (members.length === 0 && !isOwner) return null

  const sorted = [...members].sort((a, b) => {
    if (a.userId === me) return -1
    if (b.userId === me) return 1
    const aOn = !!presence[a.userId]?.online
    const bOn = !!presence[b.userId]?.online
    if (aOn !== bOn) return aOn ? -1 : 1
    return (a.displayName ?? '').localeCompare(b.displayName ?? '')
  })

  return (
    <button
      type="button"
      onClick={onOpenInfo}
      aria-haspopup="dialog"
      aria-label={t('groups.info.openStripAria')}
      className="shrink-0 w-full bg-surface border-b border-line px-3 tablet:px-4 py-2 flex items-center gap-2 text-start hover:bg-surface-hover transition"
    >
      <ul className="flex items-center gap-1.5 overflow-hidden flex-1 min-w-0">
        {sorted.map((m) => {
          const p = presence[m.userId]
          const online = !!p?.online
          const statusLabel = online
            ? t('groups.members.onlineNow')
            : p?.lastSeenAt
              ? t('groups.members.lastSeen', { rel: formatRelative(p.lastSeenAt, t) })
              : t('groups.members.lastSeenUnknown')
          const name = m.displayName ?? `${m.userId.slice(0, 8)}…`
          return (
            <li key={m.userId} className="shrink-0 flex items-center gap-2 ps-1 pe-3 py-1">
              <div className="relative shrink-0">
                <Avatar id={m.userId} name={name} imageUrl={m.avatarUrl} size="sm" />
                <span
                  className={`absolute -bottom-0.5 -end-0.5 w-2.5 h-2.5 rounded-full border-2 border-surface ${online ? 'bg-success' : 'bg-line'}`}
                  aria-label={statusLabel}
                />
              </div>
              <span className="text-sm font-medium truncate max-w-[8rem]">{name}</span>
            </li>
          )
        })}
      </ul>
      <span className="shrink-0 text-muted text-xs flex items-center gap-1" aria-hidden="true">
        {t('groups.info.title')}
        <ChevronIcon className="w-4 h-4 -rotate-90" />
      </span>
    </button>
  )
}
