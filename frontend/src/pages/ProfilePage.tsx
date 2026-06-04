import { useEffect, useMemo, useState } from 'react'
import { Navigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import { Avatar } from '../components/Avatar'
import { Card } from '../components/Card'
import { UserProfile, getUserProfile } from '../api/users'
import { errorCode } from '../api/errors'
import { formatDate } from '../i18n/datetime'

/**
 * Read-only view of *another* user's profile (`/profile/:userId`). The current
 * user's own profile (view + edit + avatar) lives in the Settings page; visiting
 * `/profile` — or `/profile/:userId` with your own id — redirects there.
 */
export default function ProfilePage() {
  const { t, i18n } = useTranslation()
  const { userId } = useParams<{ userId?: string }>()
  const me = useAuthStore((s) => s.user)

  const isSelf = !!me && (!userId || userId === me.id)

  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [notVisible, setNotVisible] = useState(false)

  // Load the profile when the target changes (skipped when redirecting self).
  useEffect(() => {
    if (!userId || isSelf) return
    let cancelled = false
    setLoading(true)
    setError('')
    setNotVisible(false)
    getUserProfile(userId)
      .then((p) => { if (!cancelled) setProfile(p) })
      .catch((e) => {
        if (cancelled) return
        if (errorCode(e) === 'PROFILE_NOT_VISIBLE') {
          setNotVisible(true)
        } else {
          setError(t('profile.errorLoad'))
        }
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [userId, isSelf, t])

  const joinedLabel = useMemo(
    () => (profile?.createdAt ? formatDate(profile.createdAt, { year: 'numeric', month: 'short', day: 'numeric' }) : ''),
    [profile?.createdAt, i18n.language],
  )

  // Own profile is edited in Settings — keep a single source of truth.
  if (isSelf) {
    return <Navigate to="/settings" replace />
  }

  if (notVisible) {
    return (
      <div className="flex-1 overflow-y-auto">
        <div className="container-app px-4 tablet:px-6 desktop:px-8 py-12 flex justify-center">
          <Card size="lg" className="px-6 py-10 max-w-md text-center shadow-bubble">
            <p className="text-base font-semibold">{t('profile.cannotView')}</p>
          </Card>
        </div>
      </div>
    )
  }

  if (loading || !profile) {
    return (
      <div className="flex-1 overflow-y-auto">
        <div className="container-app px-4 tablet:px-6 desktop:px-8 py-8">
          {error
            ? <p className="text-danger text-sm">{error}</p>
            : <p className="text-muted text-sm">{t('common.loading')}</p>}
        </div>
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="container-app px-4 tablet:px-6 desktop:px-8 py-6 tablet:py-8">
        <header className="mb-6">
          <div className="flex items-center gap-3 mb-1">
            <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
            <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
            <h1 className="text-2xl font-bold text-base">{t('profile.titleOther')}</h1>
          </div>
        </header>

        <Card size="lg" className="p-5 tablet:p-6 shadow-bubble max-w-2xl">
          <div className="flex items-start gap-4 tablet:gap-6 flex-wrap">
            <div className="shrink-0 flex flex-col items-center gap-2">
              <Avatar
                id={profile.userId}
                name={profile.displayName}
                imageUrl={profile.avatarUrl}
                size="lg"
                ring
                className="!w-20 !h-20"
              />
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <h2 className="text-xl font-bold text-base break-words">{profile.displayName}</h2>
                <span className="text-xs px-2 py-0.5 rounded-md bg-surface-muted text-secondary">
                  {profile.role}
                </span>
              </div>
              <p className="text-sm text-muted mt-0.5">
                {t('profile.joinedAt', { date: joinedLabel })}
              </p>
              <p className="text-sm text-secondary mt-4 whitespace-pre-wrap break-words">
                {profile.bio || t('profile.noBio')}
              </p>
              <p className="text-sm text-secondary mt-4">
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
            </div>
          </div>
        </Card>
      </div>
    </div>
  )
}
