import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Group,
  GroupCandidate,
  GroupMember,
  Visibility,
  deleteGroupImage,
  getGroupCandidates,
  updateGroup,
  uploadGroupImage,
} from '../../api/groups'
import { PresenceEntry } from '../../api/presence'
import { describeError } from '../../api/errors'
import { Avatar } from '../../components/Avatar'
import { Button } from '../../components/Button'
import { CameraIcon, ChevronIcon, SearchIcon } from '../../components/Icons'
import { useUserCardStore } from '../../store/userCardStore'
import { MatchFeedbackControl } from './MatchFeedbackControl'
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
  /** Called after the owner edits name/description/visibility or the cover image,
   *  so the parent can replace the group in its list (sidebar/header/avatar refresh). */
  onUpdated: (group: Group) => void
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
  onClose, onAdd, onRemove, onTransfer, onLeave, onDelete, onUpdated,
}: BubbleInfoDrawerProps) {
  const { t } = useTranslation()
  const openUserCard = useUserCardStore((s) => s.open)
  const full = group.memberCount >= group.maxMembers

  // Close on Escape while open — pairs with the backdrop click and the toggle
  // openers so the drawer is dismissable from anywhere.
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  // Owner "Manage Bubble" menu — one expandable panel open at a time.
  type Panel = 'name' | 'desc' | 'visibility' | 'add' | 'remove' | 'transfer' | 'pop'
  const [panel, setPanel] = useState<Panel | null>(null)
  const [saving, setSaving] = useState(false)
  const [formName, setFormName] = useState('')
  const [formDescription, setFormDescription] = useState('')
  const [editError, setEditError] = useState('')
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const [imageUploading, setImageUploading] = useState(false)

  // Reset the open panel whenever the drawer closes, so it reopens clean.
  useEffect(() => { if (!open) setPanel(null) }, [open])

  function togglePanel(p: Panel) {
    setEditError('')
    setPanel((cur) => {
      const next = cur === p ? null : p
      if (next === 'name') setFormName(group.name)
      if (next === 'desc') setFormDescription(group.description ?? '')
      return next
    })
  }

  async function runUpdate(payload: { name?: string; description?: string; visibility?: Visibility }) {
    setSaving(true)
    setEditError('')
    try {
      onUpdated(await updateGroup(group.id, payload))
      setPanel(null)
    } catch (err) {
      setEditError(describeError(err, t, {}, 'groups.error.updateGroup'))
    } finally {
      setSaving(false)
    }
  }

  async function handleImageFile(file: File) {
    setEditError('')
    setImageUploading(true)
    try {
      onUpdated(await uploadGroupImage(group.id, file))
    } catch (err) {
      setEditError(describeError(err, t, {
        GROUP_IMAGE_TYPE_NOT_ALLOWED: 'groups.error.imageType',
        GROUP_IMAGE_TOO_LARGE: 'groups.error.imageTooLarge',
      }, 'groups.error.imageGeneric'))
    } finally {
      setImageUploading(false)
    }
  }

  async function handleImageDelete() {
    setEditError('')
    setImageUploading(true)
    try {
      onUpdated(await deleteGroupImage(group.id))
    } catch (err) {
      setEditError(describeError(err, t, {}, 'groups.error.imageGeneric'))
    } finally {
      setImageUploading(false)
    }
  }

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
          {/* Identity block: cover image + name / visibility / description.
              Owner gets inline image upload and an Edit form for the text fields. */}
          <div className="flex gap-3 items-start min-w-0">
            {/* Cover image — owner taps to upload (camera badge); members see it plain. */}
            {isOwner ? (
              <>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept="image/png,image/jpeg,image/webp,image/gif"
                  className="hidden"
                  onChange={(e) => {
                    const f = e.target.files?.[0]
                    if (f) handleImageFile(f)
                    e.target.value = ''
                  }}
                />
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  disabled={imageUploading}
                  aria-label={t('groups.info.changeImage')}
                  title={t('groups.info.changeImage')}
                  className="group relative shrink-0 rounded-full bubble-pop focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-300 disabled:opacity-70 disabled:cursor-wait"
                >
                  <Avatar id={group.id} name={group.name} imageUrl={group.imageUrl} size="lg" />
                  <span className="absolute -bottom-0.5 -end-0.5 grid place-items-center w-6 h-6 rounded-full bg-primary-500 text-white border-2 border-surface shadow-sm">
                    {imageUploading ? (
                      <span className="block w-3 h-3 rounded-full border-2 border-white/40 border-t-white animate-spin" aria-hidden />
                    ) : (
                      <CameraIcon className="w-3 h-3" />
                    )}
                  </span>
                </button>
              </>
            ) : (
              <Avatar id={group.id} name={group.name} imageUrl={group.imageUrl} size="lg" className="shrink-0" />
            )}

            <div className="min-w-0 flex-1">
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
                <p className="text-sm text-secondary mt-1 whitespace-pre-wrap break-words">{group.description}</p>
              )}
              {/* "Is this Bubble a fit?" — on phone the header is just the icon, so
                  the match-feedback control that desktop shows inline lives here. */}
              {!isOwner && (
                <div className="mt-2">
                  <MatchFeedbackControl key={group.id} groupId={group.id} />
                </div>
              )}
              {imageUploading && (
                <p className="text-xs text-muted mt-1">{t('groups.info.imageUploading')}</p>
              )}
              {isOwner && group.imageUrl && !imageUploading && (
                <button
                  type="button"
                  onClick={handleImageDelete}
                  className="text-xs text-muted hover:text-danger mt-1"
                >
                  {t('groups.info.removeImage')}
                </button>
              )}
            </div>
          </div>

          {/* Roster — display only; tap a member to open their profile card. Owner
              operations live in the management menu below, not inline per row, so
              names never get squashed by action buttons. */}
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
                <li key={m.userId}>
                  <button
                    type="button"
                    onClick={() => openUserCard(m.userId)}
                    className="w-full flex items-center gap-3 py-2 px-3 bg-surface rounded-xl border border-line text-start hover:bg-surface-hover transition"
                  >
                    <div className="relative shrink-0">
                      <Avatar id={m.userId} name={m.displayName ?? '?'} imageUrl={m.avatarUrl} size="sm" />
                      <span
                        className={`absolute -bottom-0.5 -end-0.5 w-2.5 h-2.5 rounded-full border-2 border-surface ${online ? 'bg-success' : 'bg-line'}`}
                        title={statusLabel}
                        aria-label={statusLabel}
                      />
                    </div>
                    <span className="flex flex-col min-w-0 flex-1">
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
                </li>
              )
            })}
          </ul>

          {/* Owner management menu — every owner operation as a clearly-labelled,
              expandable row. One panel open at a time (accordion). */}
          {isOwner && (
            <div className="flex flex-col gap-1.5 border-t border-line pt-3">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted mb-0.5">
                {t('groups.info.manageTitle')}
              </p>

              <ManageRow label={t('groups.info.manage.editName')} active={panel === 'name'} onClick={() => togglePanel('name')} />
              {panel === 'name' && (
                <div className="px-1 pb-1 flex flex-col gap-2">
                  <input
                    value={formName}
                    onChange={(e) => setFormName(e.target.value)}
                    maxLength={100}
                    placeholder={t('groups.createForm.bubbleName')}
                    className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
                  />
                  <div className="flex gap-2">
                    <Button size="xs" disabled={saving || !formName.trim()} onClick={() => runUpdate({ name: formName.trim() })}>
                      {saving ? t('profile.savingButton') : t('profile.saveButton')}
                    </Button>
                    <Button variant="ghost" size="xs" onClick={() => setPanel(null)} disabled={saving}>{t('common.cancel')}</Button>
                  </div>
                </div>
              )}

              <ManageRow label={t('groups.info.manage.editDesc')} active={panel === 'desc'} onClick={() => togglePanel('desc')} />
              {panel === 'desc' && (
                <div className="px-1 pb-1 flex flex-col gap-2">
                  <textarea
                    value={formDescription}
                    onChange={(e) => setFormDescription(e.target.value)}
                    maxLength={500}
                    rows={3}
                    placeholder={t('groups.createForm.descriptionOptional')}
                    className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400 resize-y min-h-[4rem]"
                  />
                  <div className="flex gap-2">
                    <Button size="xs" disabled={saving} onClick={() => runUpdate({ description: formDescription.trim() })}>
                      {saving ? t('profile.savingButton') : t('profile.saveButton')}
                    </Button>
                    <Button variant="ghost" size="xs" onClick={() => setPanel(null)} disabled={saving}>{t('common.cancel')}</Button>
                  </div>
                </div>
              )}

              <ManageRow label={t('groups.info.manage.visibility')} active={panel === 'visibility'} onClick={() => togglePanel('visibility')} />
              {panel === 'visibility' && (
                <div className="px-1 pb-1 flex gap-2">
                  {(['PUBLIC', 'PRIVATE'] as Visibility[]).map((v) => (
                    <Button
                      key={v}
                      size="xs"
                      variant={group.visibility === v ? 'primary' : 'secondary'}
                      disabled={saving}
                      onClick={() => (group.visibility === v ? setPanel(null) : runUpdate({ visibility: v }))}
                    >
                      {v === 'PUBLIC' ? t('groups.createForm.public') : t('groups.createForm.private')}
                    </Button>
                  ))}
                </div>
              )}

              <ManageRow label={t('groups.members.addLabel')} active={panel === 'add'} onClick={() => togglePanel('add')} />
              {panel === 'add' && (
                full ? (
                  <p className="text-xs text-muted px-1 pb-1">{t('groups.members.fullHint', { max: group.maxMembers })}</p>
                ) : (
                  <div className="px-1 pb-1">
                    <AddMemberSearch groupId={group.id} memberCount={group.memberCount} onAdd={onAdd} />
                  </div>
                )
              )}

              <ManageRow label={t('groups.info.manage.removeMember')} active={panel === 'remove'} onClick={() => togglePanel('remove')} />
              {panel === 'remove' && (
                <MemberActionList
                  members={sorted.filter((m) => m.userId !== me)}
                  actionLabel={t('groups.members.remove')}
                  danger
                  onAction={onRemove}
                  emptyLabel={t('groups.info.manage.noOthers')}
                />
              )}

              <ManageRow label={t('groups.info.manage.transfer')} active={panel === 'transfer'} onClick={() => togglePanel('transfer')} />
              {panel === 'transfer' && (
                <MemberActionList
                  members={sorted.filter((m) => m.userId !== me)}
                  actionLabel={t('groups.members.makeOwner')}
                  onAction={onTransfer}
                  emptyLabel={t('groups.info.manage.noOthers')}
                />
              )}

              <ManageRow label={t('groups.header.pop')} danger active={panel === 'pop'} onClick={() => togglePanel('pop')} />
              {panel === 'pop' && (
                <div className="px-1 pb-1">
                  <div className="flex flex-col gap-2 rounded-xl border border-danger-soft bg-danger-soft p-3">
                    <p className="text-xs text-danger font-medium">{t('groups.info.manage.popConfirm')}</p>
                    {group.memberCount > 1 && (
                      <p className="text-xs text-muted">{t('groups.info.manage.popNotEmpty')}</p>
                    )}
                    <div className="flex gap-2">
                      <Button variant="danger" size="xs" className="glow-danger" onClick={onDelete}>
                        {t('groups.info.manage.popConfirmButton')}
                      </Button>
                      <Button variant="ghost" size="xs" onClick={() => setPanel(null)}>{t('common.cancel')}</Button>
                    </div>
                  </div>
                </div>
              )}

              {editError && <p className="text-xs text-danger">{editError}</p>}
            </div>
          )}

          {/* Leave — any member, including the owner (owner must transfer/empty first). */}
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
            </div>
          )}
        </div>
      </div>
    </>
  )
}

// ---------------------------------------------------------------------------
// Owner management menu primitives.
// ---------------------------------------------------------------------------

/** One expandable row in the management menu. Chevron points down collapsed, up open. */
function ManageRow({ label, active, danger, onClick }: {
  label: string
  active: boolean
  danger?: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-expanded={active}
      className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl border text-sm text-start bubble-pop transition ${
        danger ? 'border-danger-soft text-danger hover:bg-danger-soft' : 'border-line hover:bg-surface-muted'
      } ${active ? 'bg-surface-muted' : 'bg-surface'}`}
    >
      <span className="font-medium">{label}</span>
      <ChevronIcon className={`w-4 h-4 transition-transform ${active ? 'rotate-90' : '-rotate-90'}`} />
    </button>
  )
}

/** Member list with one action per row — backs Remove and Transfer-ownership. */
function MemberActionList({ members, actionLabel, danger, onAction, emptyLabel }: {
  members: GroupMember[]
  actionLabel: string
  danger?: boolean
  onAction: (userId: string) => void
  emptyLabel: string
}) {
  if (members.length === 0) {
    return <p className="text-xs text-muted px-1 pb-1">{emptyLabel}</p>
  }
  return (
    <ul className="px-1 pb-1 flex flex-col gap-1 max-h-56 overflow-y-auto">
      {members.map((m) => (
        <li key={m.userId} className="flex items-center gap-2 py-1.5 px-2 rounded-xl border border-line bg-surface">
          <Avatar id={m.userId} name={m.displayName ?? '?'} imageUrl={m.avatarUrl} size="sm" />
          <span className="font-medium text-sm truncate flex-1">{m.displayName ?? `${m.userId.slice(0, 8)}…`}</span>
          <Button variant={danger ? 'danger' : 'cell'} size="xs" onClick={() => onAction(m.userId)}>{actionLabel}</Button>
        </li>
      ))}
    </ul>
  )
}

// ---------------------------------------------------------------------------
// Owner-only member search: type a name, pick from the Bubble's enrolled
// students (offering-scoped). Replaces the old paste-a-UUID box. Re-fetches
// whenever the query or `memberCount` changes (so a just-added member drops out).
// ---------------------------------------------------------------------------

interface AddMemberSearchProps {
  groupId: string
  memberCount: number
  onAdd: (userId: string) => void
}

function AddMemberSearch({ groupId, memberCount, onAdd }: AddMemberSearchProps) {
  const { t } = useTranslation()
  const openUserCard = useUserCardStore((s) => s.open)
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<GroupCandidate[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  // Debounced search. Re-runs on query or membership change. The empty query
  // returns the first page of enrolled non-members so the picker is useful on focus.
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    const handle = setTimeout(() => {
      getGroupCandidates(groupId, query.trim() || undefined)
        .then((rows) => { if (!cancelled) { setCandidates(rows); setError('') } })
        .catch((e) => { if (!cancelled) setError(describeError(e, t, {}, 'groups.error.loadCandidates')) })
        .finally(() => { if (!cancelled) setLoading(false) })
    }, 250)
    return () => { cancelled = true; clearTimeout(handle) }
  }, [groupId, query, memberCount, t])

  return (
    <div className="flex flex-col gap-2">
      <label className="text-xs font-semibold text-secondary">{t('groups.members.addLabel')}</label>
      <div className="relative">
        <SearchIcon className="absolute inset-y-0 start-3 my-auto w-4 h-4 text-muted pointer-events-none" />
        <input
          placeholder={t('groups.members.searchPlaceholder')}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="border border-line rounded-full ps-9 pe-4 py-2 text-sm w-full bg-surface focus:outline-none focus:border-primary-400"
        />
      </div>
      {error && <p className="text-xs text-danger">{error}</p>}
      <ul className="flex flex-col gap-1 max-h-56 overflow-y-auto">
        {!loading && candidates.length === 0 && !error && (
          <li className="text-xs text-muted px-1 py-2">
            {query.trim() ? t('groups.members.noMatches') : t('groups.members.noCandidates')}
          </li>
        )}
        {candidates.map((c) => (
          <li key={c.userId} className="flex items-center gap-2 py-1.5 px-2 rounded-xl border border-line bg-surface">
            <button
              type="button"
              onClick={() => openUserCard(c.userId)}
              className="flex items-center gap-2 min-w-0 flex-1 text-start"
            >
              <Avatar id={c.userId} name={c.displayName ?? '?'} imageUrl={c.avatarUrl} size="sm" />
              <span className="font-medium text-sm truncate">
                {c.displayName ?? `${c.userId.slice(0, 8)}…`}
              </span>
            </button>
            <Button variant="cell" size="xs" onClick={() => onAdd(c.userId)}>
              {t('groups.members.addButton')}
            </Button>
          </li>
        ))}
      </ul>
    </div>
  )
}
