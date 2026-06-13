import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  enrollGroupInSession,
  getExpertPublicProfile,
  listOpenSessionsForExpert,
  type ExpertProfile,
  type ExpertSession,
} from '../api/expert'
import { errorCode } from '../api/errors'
import { getMyGroups, type Group } from '../api/groups'
import { useAuthStore } from '../store/authStore'
import { RequestBookingModal } from './expert/RequestBookingModal'
import { Card } from '../components/Card'
import { Button } from '../components/Button'
import { Avatar } from '../components/Avatar'
import { PageShell, SectionLabel } from '../components/PageHeader'
import { CalendarIcon, ClockIcon } from '../components/Icons'
import { formatClock, formatDateTime } from '../i18n/datetime'

/**
 * `/experts/:userId` - public expert profile with booking and open-session
 * enrollment actions for group owners.
 */
export default function ExpertPublicProfilePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { userId } = useParams<{ userId: string }>()
  const me = useAuthStore((s) => s.user)
  const [profile, setProfile] = useState<ExpertProfile | null>(null)
  const [sessions, setSessions] = useState<ExpertSession[]>([])
  const [myGroups, setMyGroups] = useState<Group[]>([])
  const [loading, setLoading] = useState(true)
  const [showBooking, setShowBooking] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [actionInfo, setActionInfo] = useState<string | null>(null)

  async function refresh() {
    if (!userId) return
    setLoading(true)
    try {
      const [p, openSessions, groups] = await Promise.all([
        getExpertPublicProfile(userId),
        listOpenSessionsForExpert(userId).catch(() => [] as ExpertSession[]),
        getMyGroups().catch(() => [] as Group[]),
      ])
      setProfile(p)
      setSessions(openSessions)
      setMyGroups(groups.filter((g) => me?.id && g.ownerId === me.id))
    } catch {
      setProfile(null)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [userId, me?.id])

  async function handleEnroll(sessionId: string, groupId: string) {
    setActionError(null)
    setActionInfo(null)
    try {
      await enrollGroupInSession(sessionId, groupId)
      setActionInfo(t('expert.publicProfile.infoEnrolled'))
      await refresh()
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
    <>
      <PageShell
        title={profile.headline}
        subtitle={t('expert.publicProfile.subtitle')}
        titleAfter={profile.verificationStatus === 'VERIFIED' && (
          <span className="text-xs px-2 py-0.5 rounded-full bg-success/15 text-success">
            {t('expert.publicProfile.verifiedBadge')}
          </span>
        )}
        actions={
          <>
            <Button variant="secondary" size="sm" onClick={() => navigate('/experts')}>
              {t('expert.publicProfile.backToDirectory')}
            </Button>
            {viewingSelf ? (
              <Button variant="deep" size="sm" onClick={() => navigate('/expert')}>
                {t('expert.publicProfile.openHub')}
              </Button>
            ) : (
              <Button
                variant="deep"
                size="sm"
                onClick={() => { setActionError(null); setActionInfo(null); setShowBooking(true) }}
                disabled={myGroups.length === 0}
                title={myGroups.length === 0 ? t('expert.publicProfile.needGroupToBook') : ''}
              >
                {t('expert.publicProfile.requestButton')}
              </Button>
            )}
          </>
        }
      >
        <div className="max-w-4xl mx-auto space-y-6">
          <Card size="lg" className="p-5 tablet:p-6">
            <div className="flex items-start gap-4">
              <Avatar id={profile.userId} name={profile.headline} size="lg" ring />
              <div className="flex-1 min-w-0">
                {profile.bio && <p className="text-sm text-base whitespace-pre-wrap leading-relaxed">{profile.bio}</p>}
                {profile.expertiseTags.length > 0 && (
                  <div className="flex flex-wrap gap-1.5 mt-3">
                    {profile.expertiseTags.map((tag) => (
                      <span key={tag} className="text-xs px-2 py-0.5 rounded-full bg-surface-hover text-base">
                        {tag}
                      </span>
                    ))}
                  </div>
                )}
                <div className="mt-4 flex flex-wrap gap-2 text-xs text-muted">
                  <span className="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-surface-muted border border-line">
                    <ClockIcon className="w-3.5 h-3.5" />
                    {t('expert.publicProfile.openSessionCount', { count: sessions.length })}
                  </span>
                </div>
              </div>
            </div>
          </Card>

          {actionInfo && (
            <div className="px-4 py-2 text-xs bg-success/15 text-success border border-success/30 rounded-xl flex items-center gap-3">
              <span className="flex-1">{actionInfo}</span>
              <button onClick={() => setActionInfo(null)} className="underline shrink-0">{t('common.close')}</button>
            </div>
          )}
          {actionError && (
            <div className="px-4 py-2 text-xs bg-warning/15 text-warning border border-warning/30 rounded-xl flex items-center gap-3">
              <span className="flex-1">{actionError}</span>
              <button onClick={() => setActionError(null)} className="underline shrink-0">{t('common.close')}</button>
            </div>
          )}

          <section>
            <SectionLabel className="mb-3">{t('expert.publicProfile.openSessionsHeading')}</SectionLabel>
            {sessions.length === 0 ? (
              <Card size="md" className="p-6">
                <div className="flex flex-col items-center text-center gap-3">
                  <span className="w-11 h-11 rounded-2xl bg-surface-muted text-muted flex items-center justify-center">
                    <CalendarIcon className="w-5 h-5" />
                  </span>
                  <div>
                    <h2 className="text-base font-semibold text-base">{t('expert.publicProfile.noPublicOpenSessionsTitle')}</h2>
                    <p className="text-sm text-muted mt-1">{t('expert.publicProfile.noPublicOpenSessionsBody')}</p>
                  </div>
                  {!viewingSelf && (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => setShowBooking(true)}
                      disabled={myGroups.length === 0}
                    >
                      {t('expert.publicProfile.requestButton')}
                    </Button>
                  )}
                </div>
              </Card>
            ) : (
              <ul className="space-y-2">
                {sessions.map((session) => (
                  <li key={session.id}>
                    <ProfileSessionCard
                      session={session}
                      myGroups={myGroups}
                      onEnroll={(groupId) => handleEnroll(session.id, groupId)}
                    />
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      </PageShell>

      <RequestBookingModal
        open={showBooking}
        expertUserId={profile.userId}
        onClose={() => setShowBooking(false)}
        onSent={() => setActionInfo(t('expert.publicProfile.infoRequestSent'))}
      />
    </>
  )
}

function ProfileSessionCard({
  session,
  myGroups,
  onEnroll,
}: {
  session: ExpertSession
  myGroups: Group[]
  onEnroll: (groupId: string) => void
}) {
  const { t } = useTranslation()
  const [pickerOpen, setPickerOpen] = useState(false)

  const timeLabel = session.startsAt
    ? `${formatDateTime(session.startsAt)} -> ${session.endsAt ? formatClock(session.endsAt) : ''}`
    : ''

  return (
    <Card size="md" className="p-4">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <h3 className="text-sm font-semibold text-base truncate">{session.title}</h3>
            <span className="text-xs px-2 py-0.5 rounded-full bg-success/15 text-success">
              {t('expert.publicProfile.statusOpen')}
            </span>
            <span className="text-xs text-muted">
              {t('expert.publicProfile.groupsCount', { count: session.enrolledGroupCount, capacity: session.capacity })}
            </span>
          </div>
          {timeLabel && (
            <div className="inline-flex items-center gap-1.5 text-xs text-muted">
              <CalendarIcon className="w-3.5 h-3.5" />
              {timeLabel}
            </div>
          )}
          {session.description && <p className="text-sm text-muted mt-2 line-clamp-2">{session.description}</p>}
        </div>
        <div className="shrink-0">
          {myGroups.length === 0 ? (
            <span className="text-xs text-muted">{t('expert.publicProfile.needGroupToBook')}</span>
          ) : pickerOpen ? (
            <div className="flex flex-col items-end gap-1 max-h-32 overflow-y-auto">
              <span className="text-xs text-muted">{t('expert.publicProfile.enrollLabel')}</span>
              {myGroups.map((group) => (
                <Button key={group.id} variant="secondary" size="xs" onClick={() => onEnroll(group.id)}>
                  {group.name}
                </Button>
              ))}
            </div>
          ) : (
            <Button variant="deep" size="sm" onClick={() => setPickerOpen(true)}>
              {t('expert.publicProfile.enrollButton')}
            </Button>
          )}
        </div>
      </div>
    </Card>
  )
}
