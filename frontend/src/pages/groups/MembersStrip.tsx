import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { GroupMember } from '../../api/groups'
import { PresenceEntry } from '../../api/presence'
import { Avatar } from '../../components/Avatar'
import { formatRelative } from './timeFormat'

interface MembersStripProps {
  members: GroupMember[]
  presence: Record<string, PresenceEntry>
  me: string | null
  isOwner: boolean
  onManage: () => void
}

/**
 * Slim horizontal row of member chips (avatar + presence dot + name) shown
 * above the bento. Owners get a "Manage" button that opens [[ManageMembersModal]].
 *
 * Order: me first, then online, then by display name. Keeps the eye on people
 * who matter for the current session and lets the rest scroll horizontally.
 */
export function MembersStrip({ members, presence, me, isOwner, onManage }: MembersStripProps) {
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
    <div className="shrink-0 bg-surface border-b border-line px-3 tablet:px-4 py-2 flex items-center gap-2">
      <ul className="flex items-center gap-1.5 overflow-x-auto flex-1 min-w-0">
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
            <li key={m.userId} className="shrink-0">
              <Link
                to={`/profile/${m.userId}`}
                title={`${name} — ${statusLabel}`}
                className="flex items-center gap-2 ps-1 pe-3 py-1 rounded-full hover:bg-surface-hover transition"
              >
                <div className="relative shrink-0">
                  <Avatar
                    id={m.userId}
                    name={name}
                    imageUrl={m.avatarUrl}
                    size="sm"
                  />
                  <span
                    className={`absolute -bottom-0.5 -end-0.5 w-2.5 h-2.5 rounded-full border-2 border-surface ${online ? 'bg-success' : 'bg-line'}`}
                    aria-label={statusLabel}
                  />
                </div>
                <span className="text-sm font-medium truncate max-w-[8rem]">{name}</span>
              </Link>
            </li>
          )
        })}
      </ul>
      {isOwner && (
        <button
          type="button"
          onClick={onManage}
          className="shrink-0 text-xs font-medium px-3 py-1.5 rounded-full bg-surface-muted hover:bg-surface-hover text-secondary transition"
        >
          {t('groups.members.manage')}
        </button>
      )}
    </div>
  )
}
