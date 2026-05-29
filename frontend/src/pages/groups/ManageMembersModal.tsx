import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { GroupMember } from '../../api/groups'
import { PresenceEntry } from '../../api/presence'
import { Avatar } from '../../components/Avatar'
import { Button } from '../../components/Button'
import { formatRelative } from './timeFormat'

interface Props {
  members: GroupMember[]
  presence: Record<string, PresenceEntry>
  me: string | null
  onAdd: (userId: string) => void
  onRemove: (userId: string) => void
  onTransfer: (userId: string) => void
  onClose: () => void
}

/**
 * Owner-only roster editor: invite by UUID, remove members, transfer ownership.
 * Opened from [[MembersStrip]] so the strip itself stays read-only and tidy.
 */
export function ManageMembersModal({ members, presence, me, onAdd, onRemove, onTransfer, onClose }: Props) {
  const { t } = useTranslation()
  const [newMemberId, setNewMemberId] = useState('')

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-3 tablet:p-4" onClick={onClose}>
      <div
        className="bg-surface rounded-3xl shadow-bubble w-full max-w-xl max-h-[80vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center justify-between">
          <h3 className="font-semibold">{t('groups.members.modalTitle')}</h3>
          <button type="button" onClick={onClose} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          <ul className="flex flex-col gap-1 text-sm">
            {members.length === 0 && (
              <li className="text-muted">{t('groups.members.empty')}</li>
            )}
            {members.map((m) => {
              const p = presence[m.userId]
              const online = !!p?.online
              const statusLabel = online
                ? t('groups.members.onlineNow')
                : p?.lastSeenAt
                  ? t('groups.members.lastSeen', { rel: formatRelative(p.lastSeenAt, t) })
                  : t('groups.members.lastSeenUnknown')
              return (
                <li key={m.userId} className="flex justify-between items-center py-2 bg-surface rounded-xl px-3 border border-line">
                  <Link
                    to={`/profile/${m.userId}`}
                    className="flex items-center gap-3 min-w-0 flex-1 hover:bg-surface-hover -mx-1 px-1 py-1 rounded-lg transition"
                  >
                    <div className="relative shrink-0">
                      <Avatar
                        id={m.userId}
                        name={m.displayName ?? '?'}
                        imageUrl={m.avatarUrl}
                        size="sm"
                      />
                      <span
                        className={`absolute -bottom-0.5 -end-0.5 w-2.5 h-2.5 rounded-full border-2 border-surface ${online ? 'bg-success' : 'bg-line'}`}
                        title={statusLabel}
                        aria-label={statusLabel}
                      />
                    </div>
                    <span className="flex flex-col min-w-0">
                      <span className="flex items-center gap-1.5 min-w-0">
                        <span className="font-semibold text-base truncate">
                          {m.displayName ?? `${m.userId.slice(0, 8)}…`}
                        </span>
                        {m.userId === me && (
                          <span className="text-xs text-primary-600 shrink-0">{t('groups.members.youSuffix')}</span>
                        )}
                        <span className={`text-xs px-2 py-0.5 rounded-md shrink-0 ${
                          m.role === 'OWNER' ? 'bg-primary-100 text-primary-700' : 'bg-surface-muted text-secondary'
                        }`}>
                          {m.role}
                        </span>
                      </span>
                      <span className={`text-xs truncate ${online ? 'text-success' : 'text-muted'}`}>{statusLabel}</span>
                    </span>
                  </Link>
                  {m.userId !== me && (
                    <div className="flex gap-1.5 shrink-0 ms-2">
                      <Button variant="cell" size="xs" onClick={() => onTransfer(m.userId)}>{t('groups.members.makeOwner')}</Button>
                      <Button variant="danger" size="xs" onClick={() => onRemove(m.userId)}>{t('groups.members.remove')}</Button>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>

          <form
            onSubmit={(e) => {
              e.preventDefault()
              if (!newMemberId.trim()) return
              onAdd(newMemberId.trim())
              setNewMemberId('')
            }}
            className="flex gap-2 mt-4"
          >
            <input
              placeholder={t('groups.members.addPlaceholder')}
              value={newMemberId}
              onChange={(e) => setNewMemberId(e.target.value)}
              className="border border-line rounded-full px-4 py-2 text-sm flex-1 font-mono bg-surface focus:outline-none focus:border-primary-400"
              dir="ltr"
            />
            <Button type="submit" variant="cell" size="sm">{t('groups.members.addButton')}</Button>
          </form>
        </div>
      </div>
    </div>
  )
}
