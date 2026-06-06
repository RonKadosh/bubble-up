import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import {
  enrollGroupInSession,
  getExpertPublicProfile,
  listMyExpertSessions,
  type ExpertProfile,
  type ExpertSession,
} from '../api/expert'
import { errorCode } from '../api/errors'
import { getGroups, type Group } from '../api/groups'
import { useAuthStore } from '../store/authStore'
import { RequestBookingModal } from './expert/RequestBookingModal'
import { Card } from '../components/Card'
import { Button } from '../components/Button'
import { Avatar } from '../components/Avatar'
import { formatDateTime } from '../i18n/datetime'

/**
 * `/experts/:userId` — public view of an expert: headline / bio / tags + the
 * list of open sessions (anyone they're hosting). Group owners can either
 * enroll one of their groups into an open session or send a booking request
 * for a custom time.
 *
 * The "open sessions" list is filtered client-side from {@code listMyExpertSessions}
 * via a workaround: that endpoint returns the caller's own sessions, so to get
 * another expert's sessions we'd need a public endpoint. For v1 we just don't
 * surface the list here unless you're looking at yourself — instead a "Request
 * a booking" CTA is always the primary action. (Known gap: add
 * GET /api/experts/{userId}/sessions to make the open list browseable.)
 */
export default function ExpertPublicProfilePage() {
  const { t } = useTranslation()
  const { userId } = useParams<{ userId: string }>()
  const me = useAuthStore((s) => s.user)
  const [profile, setProfile] = useState<ExpertProfile | null>(null)
  const [ownSessions, setOwnSessions] = useState<ExpertSession[] | null>(null)
  const [myGroups, setMyGroups] = useState<Group[]>([])
  const [loading, setLoading] = useState(true)
  const [showBooking, setShowBooking] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionInfo, setActionInfo] = useState<string | null>(null)

  useEffect(() => {
    if (!userId) return
    let cancelled = false
    setLoading(true)
    getExpertPublicProfile(userId)
      .then((p) => { if (!cancelled) setProfile(p) })
      .catch(() => { /* surfaced via profile === null */ })
      .finally(() => { if (!cancelled) setLoading(false) })
    // If we're viewing our own profile, show our own session list inline.
    if (me?.id === userId) {
      listMyExpertSessions().then((s) => { if (!cancelled) setOwnSessions(s) }).catch(() => {})
    }
    getGroups().then((all) => {
      if (cancelled) return
      setMyGroups(all.filter((g) => me?.id && g.ownerId === me.id))
    }).catch(() => {})
    return () => { cancelled = true }
  }, [userId, me?.id])

  async function handleEnroll(sessionId: string, groupId: string) {
    setActionError(null)
    setActionInfo(null)
    try {
      await enrollGroupInSession(sessionId, groupId)
      setActionInfo(t('expert.publicProfile.infoEnrolled'))
    } catch (err) {
      const code = errorCode(err)
      if (code === 'EXPERT_SESSION_GROUP_ALREADY_ENROLLED') setActionError(t('expert.publicProfile.errorAlreadyEnrolled'))
      else if (code === 'EXPERT_SESSION_CAPACITY_REACHED') setActionError(t('expert.publicProfile.errorCapacity'))
      else if (code === 'EXPERT_SESSION_ENROLLMENT_CLOSED') setActionError(t('expert.publicProfile.errorEnrollmentClosed'))
      else if (code === 'GROUP_SCHEDULE_CONFLICT') setActionError(t('expert.publicProfile.errorScheduleConflict'))
      else if (code === 'NOT_GROUP_OWNER') setActionError(t('expert.publicProfile.errorNotGroupOwner'))
      else setActionError(t('expert.publicProfile.errorEnrollGeneric'))
    }
  }

  if (loading) {
    return <div className="flex items-center justify-center h-full text-muted text-sm">{t('common.loading')}</div>
  }

  if (!profile) {
    return (
      <div className="flex flex-col items-center justify-center h-full p-8 gap-3">
        <p className="text-base">{t('expert.publicProfile.notFound')}</p>
        <Link to="/experts" className="text-sm text-link underline">{t('expert.publicProfile.backToDirectory')}</Link>
      </div>
    )
  }

  const viewingSelf = me?.id === profile.userId

  return (
    <div className="flex-1 overflow-y-auto p-6 sm:p-8">
      <div className="max-w-3xl mx-auto space-y-6">
        <Card size="lg" className="p-6">
          <div className="flex items-start gap-4">
            <Avatar id={profile.userId} name={profile.headline} size="lg" ring />
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1 flex-wrap">
                <h1 className="text-2xl font-bold text-base">{profile.headline}</h1>
                {profile.verificationStatus === 'VERIFIED' && (
                  <span className="text-xs px-2 py-0.5 rounded-full bg-success/15 text-success">{t('expert.publicProfile.verifiedBadge')}</span>
                )}
              </div>
              {profile.bio && <p className="text-sm text-base mt-2 whitespace-pre-wrap">{profile.bio}</p>}
              {profile.expertiseTags.length > 0 && (
                <div className="flex flex-wrap gap-1 mt-3">
                  {profile.expertiseTags.map((tag) => (
                    <span key={tag} className="text-xs px-2 py-0.5 rounded-full bg-surface-hover text-base">
                      {tag}
                    </span>
                  ))}
                </div>
              )}

              {!viewingSelf && (
                <div className="mt-5 flex flex-wrap gap-2">
                  <Button
                    size="sm"
                    onClick={() => { setActionError(null); setActionInfo(null); setShowBooking(true) }}
                    disabled={myGroups.length === 0}
                    title={myGroups.length === 0 ? t('expert.publicProfile.needGroupToBook') : ''}
                  >
                    {t('expert.publicProfile.requestButton')}
                  </Button>
                </div>
              )}

              {actionInfo && <div className="mt-3 text-sm text-success">{actionInfo}</div>}
              {actionError && <div className="mt-3 text-sm text-warning">{actionError}</div>}
            </div>
          </div>
        </Card>

        {viewingSelf && ownSessions && (
          <section>
            <h2 className="text-lg font-bold text-base mb-2">{t('expert.publicProfile.yourOpenSessions')}</h2>
            {ownSessions.filter((s) => s.status === 'OPEN').length === 0 ? (
              <p className="text-sm text-muted">{t('expert.publicProfile.noOpenSessions')}</p>
            ) : (
              <ul className="space-y-2">
                {ownSessions.filter((s) => s.status === 'OPEN').map((s) => (
                  <li key={s.id}>
                    <Card size="md" className="p-4">
                      <div className="text-sm font-medium text-base">{s.title}</div>
                      {s.startsAt && (
                        <div className="text-xs text-muted">
                          {formatDateTime(s.startsAt)} · {t('expert.publicProfile.groupsCount', { count: s.enrolledGroupCount, capacity: s.capacity })}
                        </div>
                      )}
                      {myGroups.length > 0 && (
                        <div className="mt-2 flex flex-wrap items-center gap-1">
                          <span className="text-xs text-muted me-1">{t('expert.publicProfile.enrollLabel')}</span>
                          {myGroups.map((g) => (
                            <Button
                              key={g.id}
                              variant="secondary"
                              size="xs"
                              onClick={() => handleEnroll(s.id, g.id)}
                            >
                              {g.name}
                            </Button>
                          ))}
                        </div>
                      )}
                    </Card>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </div>

      <RequestBookingModal
        open={showBooking}
        expertUserId={profile.userId}
        onClose={() => setShowBooking(false)}
        onSent={() => setActionInfo(t('expert.publicProfile.infoRequestSent'))}
      />
    </div>
  )
}
