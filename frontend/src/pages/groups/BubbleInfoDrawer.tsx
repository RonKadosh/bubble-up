import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Group, GroupMember } from '../../api/groups'
import { PresenceEntry } from '../../api/presence'
import { Avatar } from '../../components/Avatar'
import { Button } from '../../components/Button'
import { useUserCardStore } from '../../store/userCardStore'
import { formatRelative } from './timeFormat'

interface BubbleInfoDrawerProps {
  open: boolean
  group: Group
  members: GroupMember[]
  presence: Record<string, PresenceEntry>
  me: string | null
  isOwner: boolean
  isMember: boolean
  onClose: () => void
  onAdd: (userId: string) => void
  onRemove: (userId: string) => void
  onTransfer: (userId: string) => void
  onLeave: () => void
  onDelete: () => void
}

/**
 * "Bubble Info" — a slide-down overlay anchored to the top of the content
 * region (under the {@link MembersStrip}), floating over the bento grid with a
 * dim backdrop. Replaces the old centered ManageMembersModal and consolidates
 * member info (presence + last seen for everyone), owner controls (add by id /
 * remove / transfer), and the Leave / Pop actions in one place.
 *
 * Always mounted while a bubble is selected so the slide animation plays on
 * close; `open` drives the translate. Mirrors the GroupSidebar drawer pattern,
 * vertical instead of horizontal — translate-y is RTL-agnostic.
 */
export function BubbleInfoDrawer({
  open, group, members, presence, me, isOwner, isMember,
  onClose, onAdd, onRemove, onTransfer, onLeave, onDelete,
}: BubbleInfoDrawerProps) {
  const { t } = useTranslation()
  const openUserCard = useUserCardStore((s) => s.open)
  const [newMemberId, setNewMemberId] = useState('')
  const full = group.memberCount >= group.maxMembers

  // me first, then online, then by name — same order as the strip.
  const sorted = [...members].sort((a, b) => {
    if (a.userId === me) return -1
    if (b.userId === me) return 1
    const aOn = !!presence[a.userId]?.online
    const bOn = !!presence[b.userId]?.online
    if (aOn !== bOn) return aOn ? -1 : 1
    return (a.displayName ?? '').localeCompare(b.displayName ?? '')
  })

  return (
    <>
      {/* Backdrop — dims the bento only (the content region), not the header/strip. */}
      <button
        type="button"
        aria-label={t('common.close')}
        tabIndex={open ? 0 : -1}
        onClick={onClose}
        className={`absolute inset-0 z-30 bg-black/30 transition-opacity duration-200 ${
          open ? 'opacity-100' : 'opacity-0 pointer-events-none'
        }`}
      />

      <div
        role="dialog"
        aria-modal="true"
        aria-label={t('groups.info.title')}
        className={`absolute inset-x-0 top-0 z-40 max-h-[85%] overflow-y-auto bg-surface border-b border-line shadow-bubble rounded-b-3xl transition-transform duration-200 ease-out ${
          open ? 'translate-y-0' : '-translate-y-full pointer-events-none'
        }`}
      >
        <div className="sticky top-0 bg-surface px-4 tablet:px-6 py-3 border-b border-line flex items-center justify-between">
          <h3 className="font-semibold">{t('groups.info.title')}</h3>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('common.close')}
            className="text-muted hover:text-secondary text-xl leading-none"
          >
            ×
          </button>
        </div>

        <div className="p-4 tablet:px-6 flex flex-col gap-4">
          {/* Info line: name + visibility + description. */}
          <div className="min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="font-semibold truncate">{group.name}</span>
              <span className={`text-xs px-2 py-0.5 rounded-md ${
                group.visibility === 'PUBLIC'
                  ? 'bg-primary-100 text-primary-700'
                  : 'bg-amber-100 text-amber-800'
              }`}>
                {group.visibility === 'PUBLIC' ? t('groups.publicBadge') : t('groups.privateBadge')}
              </span>
              <span className="text-xs px-2 py-0.5 rounded-md bg-surface-muted text-secondary tabular-nums">
                {t('groups.capacity', { count: group.memberCount, max: group.maxMembers })}
              </span>
            </div>
            {group.description && (
              <p className="text-sm text-secondary mt-1">{group.description}</p>
            )}
          </div>

          {/* Roster — presence + last seen for everyone; owner-only row actions. */}
          <ul className="flex flex-col gap-1 text-sm">
            {sorted.length === 0 && (
              <li className="text-muted">{t('groups.members.empty')}</li>
            )}
            {sorted.map((m) => {
              const p = presence[m.userId]
              const online = !!p?.online
              const statusLabel = online
                ? t('groups.members.onlineNow')
                : p?.lastSeenAt
                  ? t('groups.members.lastSeen', { rel: formatRelative(p.lastSeenAt, t) })
                  : t('groups.members.lastSeenUnknown')
              return (
                <li key={m.userId} className="flex justify-between items-center py-2 bg-surface rounded-xl px-3 border border-line">
                  <button
                    type="button"
                    onClick={() => openUserCard(m.userId)}
                    className="flex items-center gap-3 min-w-0 flex-1 text-start hover:bg-surface-hover -mx-1 px-1 py-1 rounded-lg transition"
                  >
                    <div className="relative shrink-0">
                      <Avatar id={m.userId} name={m.displayName ?? '?'} imageUrl={m.avatarUrl} size="sm" />
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
                  </button>
                  {isOwner && m.userId !== me && (
                    <div className="flex gap-1.5 shrink-0 ms-2">
                      <Button variant="cell" size="xs" onClick={() => onTransfer(m.userId)}>{t('groups.members.makeOwner')}</Button>
                      <Button variant="danger" size="xs" onClick={() => onRemove(m.userId)}>{t('groups.members.remove')}</Button>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>

          {/* Add member by id — owner only. Disabled once the bubble is full. */}
          {isOwner && (
            full ? (
              <p className="text-xs text-muted">{t('groups.members.fullHint', { max: group.maxMembers })}</p>
            ) : (
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  if (!newMemberId.trim()) return
                  onAdd(newMemberId.trim())
                  setNewMemberId('')
                }}
                className="flex gap-2"
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
            )
          )}

          {/* Leave (any member, incl. owner) + Pop (owner). */}
          {isMember && (
            <div className="flex items-center justify-end gap-2 border-t border-line pt-4">
              <Button
                variant="secondary"
                size="sm"
                onClick={onLeave}
                title={isOwner ? t('groups.header.leaveOwnerTitle') : undefined}
              >
                {t('groups.info.leaveBubble')}
              </Button>
              {isOwner && (
                <Button variant="danger" size="sm" onClick={onDelete} title={t('groups.header.popTitle')} className="glow-danger">
                  {t('groups.header.pop')}
                </Button>
              )}
            </div>
          )}
        </div>
      </div>
    </>
  )
}
