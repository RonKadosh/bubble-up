import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useUserCardStore } from '../store/userCardStore'
import { useAuthStore } from '../store/authStore'
import { UserProfile, getUserProfile } from '../api/users'
import { Group, addMember, getInvitableGroups } from '../api/groups'
import { describeError, errorCode } from '../api/errors'
import { formatDate } from '../i18n/datetime'
import { Avatar } from './Avatar'
import { Button } from './Button'

/**
 * App-wide read-only profile popup. Mounted once at the Layout root; driven by
 * {@link useUserCardStore}. Clicking any avatar/name in chat or the Bubble Info
 * drawer pops this card instead of routing to `/profile/:userId`, so it never
 * breaks the flow of the surrounding page. Editing your own profile still lives
 * on the full `/profile` page, reachable via the "Edit" shortcut here.
 */
export function UserProfileCard() {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const userId = useUserCardStore((s) => s.userId)
  const close = useUserCardStore((s) => s.close)
  const me = useAuthStore((s) => s.user)

  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(false)
  const [notVisible, setNotVisible] = useState(false)
  const [failed, setFailed] = useState(false)

  // "Invite to Bubble": lazy-loaded list of the viewer's owned bubbles the target
  // can be added to. Opened on demand so the card stays cheap for the common case.
  const [inviteOpen, setInviteOpen] = useState(false)
  const [invitable, setInvitable] = useState<Group[] | null>(null)
  const [inviteLoading, setInviteLoading] = useState(false)
  const [invitedIds, setInvitedIds] = useState<Set<string>>(new Set())
  const [inviteError, setInviteError] = useState('')

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    setProfile(null)
    setNotVisible(false)
    setFailed(false)
    setLoading(true)
    setInviteOpen(false)
    setInvitable(null)
    setInvitedIds(new Set())
    setInviteError('')
    getUserProfile(userId)
      .then((p) => { if (!cancelled) setProfile(p) })
      .catch((e) => {
        if (cancelled) return
        if (errorCode(e) === 'PROFILE_NOT_VISIBLE') setNotVisible(true)
        else setFailed(true)
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [userId])

  // Close on Escape.
  useEffect(() => {
    if (!userId) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close() }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [userId, close])

  const joinedLabel = useMemo(
    () => (profile?.createdAt ? formatDate(profile.createdAt, { year: 'numeric', month: 'short', day: 'numeric' }) : ''),
    [profile?.createdAt, i18n.language],
  )

  async function handleOpenInvite() {
    if (!userId) return
    setInviteOpen(true)
    if (invitable !== null) return   // already loaded once
    setInviteLoading(true)
    setInviteError('')
    try {
      setInvitable(await getInvitableGroups(userId))
    } catch (e) {
      setInviteError(describeError(e, t, {}, 'profile.invite.loadError'))
      setInvitable([])
    } finally {
      setInviteLoading(false)
    }
  }

  async function handleInvite(groupId: string) {
    if (!userId) return
    setInviteError('')
    try {
      await addMember(groupId, userId)
      setInvitedIds((s) => new Set(s).add(groupId))
    } catch (e) {
      setInviteError(describeError(e, t, {
        GROUP_IS_FULL: 'profile.invite.full',
        ALREADY_GROUP_MEMBER: 'profile.invite.already',
        NOT_ENROLLED_IN_COURSE: 'profile.invite.notEnrolled',
      }, 'profile.invite.error'))
    }
  }

  if (!userId) return null

  const isSelf = !!me && me.id === userId

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4 animate-fade-in" onClick={close}>
      <div
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
        className="bg-surface rounded-3xl shadow-bubble animate-pop-in border border-line w-full max-w-sm overflow-hidden"
      >
        <div className="flex justify-end px-3 pt-3">
          <button
            type="button"
            onClick={close}
            aria-label={t('common.close')}
            className="text-muted hover:text-secondary text-xl leading-none"
          >
            ×
          </button>
        </div>

        <div className="px-6 pb-6 -mt-1">
          {loading && (
            <p className="text-sm text-muted py-8 text-center">{t('common.loading')}</p>
          )}
          {notVisible && (
            <p className="text-sm font-semibold py-8 text-center">{t('profile.cannotView')}</p>
          )}
          {failed && (
            <p className="text-sm text-danger py-8 text-center">{t('profile.errorLoad')}</p>
          )}
          {profile && (
            <div className="flex flex-col items-center text-center">
              <Avatar
                id={profile.userId}
                name={profile.displayName}
                imageUrl={profile.avatarUrl}
                size="lg"
                ring
                className="!w-20 !h-20"
              />
              <div className="flex items-center gap-2 mt-3 flex-wrap justify-center">
                <h2 className="text-lg font-bold text-base break-words">{profile.displayName}</h2>
                <span className="text-xs px-2 py-0.5 rounded-md bg-surface-muted text-secondary">{profile.role}</span>
              </div>
              <p className="text-xs text-muted mt-1">{t('profile.joinedAt', { date: joinedLabel })}</p>
              {profile.bio && (
                <p className="text-sm text-secondary mt-3 whitespace-pre-wrap break-words">{profile.bio}</p>
              )}
              <p className="text-sm text-secondary mt-3">
                <span className="text-muted">{t('profile.affiliationLabel')}: </span>
                {profile.universityName || profile.departmentName || profile.enrollmentYear != null
                  ? [
                      profile.universityName,
                      profile.departmentName,
                      profile.enrollmentYear != null
                        ? `${t('profile.enrollmentYearLabel')} ${profile.enrollmentYear}`
                        : null,
                    ].filter(Boolean).join(' · ')
                  : t('profile.noAffiliation')}
              </p>
              {isSelf && (
                <Button
                  size="sm"
                  variant="secondary"
                  className="mt-4"
                  onClick={() => { close(); navigate('/profile') }}
                >
                  {t('common.edit')}
                </Button>
              )}

              {/* Invite to one of my Bubbles — only for other users. */}
              {!isSelf && (
                <div className="mt-4 w-full">
                  {!inviteOpen ? (
                    <Button size="sm" onClick={handleOpenInvite}>
                      {t('profile.invite.button')}
                    </Button>
                  ) : (
                    <div className="w-full text-start border border-line rounded-2xl p-3">
                      <p className="text-xs font-semibold text-secondary mb-2">{t('profile.invite.title')}</p>
                      {inviteLoading && <p className="text-xs text-muted">{t('common.loading')}</p>}
                      {!inviteLoading && invitable && invitable.length === 0 && (
                        <p className="text-xs text-muted">{t('profile.invite.none')}</p>
                      )}
                      <ul className="flex flex-col gap-1.5 max-h-44 overflow-y-auto">
                        {invitable?.map((g) => {
                          const invited = invitedIds.has(g.id)
                          return (
                            <li key={g.id} className="flex items-center gap-2">
                              <Avatar id={g.id} name={g.name} imageUrl={g.imageUrl} size="sm" />
                              <span className="text-sm font-medium truncate flex-1">{g.name}</span>
                              {invited ? (
                                <span className="text-xs text-success font-semibold shrink-0">{t('profile.invite.invited')}</span>
                              ) : (
                                <Button variant="cell" size="xs" onClick={() => handleInvite(g.id)} className="shrink-0">
                                  {t('profile.invite.add')}
                                </Button>
                              )}
                            </li>
                          )
                        })}
                      </ul>
                      {inviteError && <p className="text-xs text-danger mt-2">{inviteError}</p>}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
