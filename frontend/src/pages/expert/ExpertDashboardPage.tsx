import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import {
  acceptBookingRequest,
  cancelExpertSession,
  getMyExpertProfile,
  listMyBookings,
  listMyExpertSessions,
  rejectBookingRequest,
  type BookingRequest,
  type ExpertProfile,
  type ExpertSession,
} from '../../api/expert'
import { ScheduleExpertSessionModal } from './ScheduleExpertSessionModal'
import { Card } from '../../components/Card'
import { Button } from '../../components/Button'
import { PageShell } from '../../components/PageHeader'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { formatClock, formatDateTime } from '../../i18n/datetime'

function formatRange(startsAt: string | null, endsAt: string | null): string {
  if (!startsAt || !endsAt) return ''
  return `${formatDateTime(startsAt)} → ${formatClock(endsAt)}`
}

/**
 * `/expert` — host's home. Three sections: profile snippet, upcoming sessions
 * (with Schedule CTA), and inbound booking requests with accept/reject. No
 * tabs — the page is short enough to scroll vertically and avoids hiding the
 * actions behind tab switches.
 */
export default function ExpertDashboardPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [profile, setProfile] = useState<ExpertProfile | null>(null)
  const [sessions, setSessions] = useState<ExpertSession[]>([])
  const [requests, setRequests] = useState<BookingRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [showSchedule, setShowSchedule] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  /** session id awaiting cancel confirmation, or null. */
  const [pendingCancel, setPendingCancel] = useState<string | null>(null)
  const [cancelBusy, setCancelBusy] = useState(false)

  async function refresh() {
    try {
      const [p, s, r] = await Promise.all([
        getMyExpertProfile(),
        listMyExpertSessions(),
        listMyBookings(true),
      ])
      setProfile(p)
      setSessions(s)
      setRequests(r)
    } catch {
      // If we can't load the profile, send to onboarding.
      navigate('/become-expert', { replace: true })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function performCancel(sessionId: string) {
    setCancelBusy(true)
    try {
      await cancelExpertSession(sessionId)
      await refresh()
    } catch {
      setActionError(t('expert.dashboard.errorCancel'))
    } finally {
      setCancelBusy(false)
      setPendingCancel(null)
    }
  }

  async function handleAccept(requestId: string) {
    try {
      await acceptBookingRequest(requestId)
      await refresh()
    } catch {
      setActionError(t('expert.dashboard.errorAccept'))
    }
  }

  async function handleReject(requestId: string) {
    try {
      await rejectBookingRequest(requestId)
      await refresh()
    } catch {
      setActionError(t('expert.dashboard.errorReject'))
    }
  }

  if (loading) {
    return <div className="flex items-center justify-center h-full text-muted text-sm">{t('common.loading')}</div>
  }

  const pendingRequests = requests.filter((r) => r.status === 'PENDING')
  const activeSessions = sessions.filter((s) => s.status !== 'CANCELLED' && s.status !== 'ENDED')

  return (
    <>
      <PageShell title={t('expert.dashboard.title')} subtitle={t('expert.dashboard.subtitle')}>
        <div className="space-y-6">

        {/* Profile snippet */}
        <Card size="md" className="p-5 flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h2 className="text-lg font-bold text-base">{profile?.headline}</h2>
              <span
                className={`text-xs px-2 py-0.5 rounded-full ${
                  profile?.verificationStatus === 'VERIFIED'
                    ? 'bg-success/15 text-success'
                    : 'bg-warning/15 text-warning'
                }`}
              >
                {profile?.verificationStatus}
              </span>
            </div>
            {profile?.bio && <p className="text-sm text-muted">{profile.bio}</p>}
            {profile && profile.expertiseTags.length > 0 && (
              <div className="flex flex-wrap gap-1 mt-2">
                {profile.expertiseTags.map((tag) => (
                  <span key={tag} className="text-xs px-2 py-0.5 rounded-full bg-surface-hover text-base">
                    {tag}
                  </span>
                ))}
              </div>
            )}
          </div>
          <Button
            variant="secondary"
            size="xs"
            className="shrink-0"
            onClick={() => navigate('/expert/profile/edit')}
          >
            {t('expert.dashboard.editProfile')}
          </Button>
        </Card>

        {actionError && (
          <div className="px-4 py-2 text-xs bg-warning/15 text-warning border border-warning/30 rounded-xl flex items-center gap-3">
            <span className="flex-1">{actionError}</span>
            <button onClick={() => setActionError(null)} className="underline shrink-0">{t('common.close')}</button>
          </div>
        )}

        {/* Booking requests */}
        <section>
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-lg font-bold text-base">
              {t('expert.dashboard.bookingsHeading')} {pendingRequests.length > 0 && <span className="text-sm text-warning">({pendingRequests.length})</span>}
            </h2>
          </div>
          {pendingRequests.length === 0 ? (
            <p className="text-sm text-muted">{t('expert.dashboard.noPendingRequests')}</p>
          ) : (
            <ul className="space-y-2">
              {pendingRequests.map((r) => (
                <li key={r.id}>
                  <Card size="md" className="p-4 flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="text-sm text-base">
                        {formatRange(r.proposedStartsAt, r.proposedEndsAt)}
                      </div>
                      {r.message && <p className="text-sm text-muted mt-1 truncate">{r.message}</p>}
                    </div>
                    <div className="shrink-0 flex gap-2">
                      <Button variant="secondary" size="xs" onClick={() => handleReject(r.id)}>
                        {t('expert.dashboard.reject')}
                      </Button>
                      <Button size="xs" onClick={() => handleAccept(r.id)}>
                        {t('expert.dashboard.accept')}
                      </Button>
                    </div>
                  </Card>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Sessions */}
        <section>
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-lg font-bold text-base">{t('expert.dashboard.sessionsHeading')}</h2>
            <Button size="sm" onClick={() => setShowSchedule(true)}>
              {t('expert.dashboard.schedule')}
            </Button>
          </div>
          {activeSessions.length === 0 ? (
            <p className="text-sm text-muted">{t('expert.dashboard.noSessions')}</p>
          ) : (
            <ul className="space-y-2">
              {activeSessions.map((s) => (
                <li key={s.id}>
                  <Card size="md" className="p-4 flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-medium text-base truncate">{s.title}</span>
                        <span
                          className={`text-xs px-2 py-0.5 rounded-full ${
                            s.status === 'OPEN'
                              ? 'bg-success/15 text-success'
                              : s.status === 'FULL'
                              ? 'bg-primary-600/15 text-primary-600'
                              : 'bg-surface-hover text-muted'
                          }`}
                        >
                          {s.status}
                        </span>
                        <span className="text-xs text-muted">
                          {t('expert.dashboard.groupsCount', { count: s.enrolledGroupCount, capacity: s.capacity })}
                        </span>
                      </div>
                      <div className="text-xs text-muted">{formatRange(s.startsAt, s.endsAt)}</div>
                    </div>
                    <div className="shrink-0 flex gap-2">
                      {s.roomId && (
                        <Button size="xs" onClick={() => navigate(`/sessions/${s.id}`)}>
                          {t('expert.dashboard.enter')}
                        </Button>
                      )}
                      <Button variant="secondary" size="xs" onClick={() => setPendingCancel(s.id)}>
                        {t('expert.dashboard.cancel')}
                      </Button>
                    </div>
                  </Card>
                </li>
              ))}
            </ul>
          )}
        </section>
        </div>
      </PageShell>

      <ScheduleExpertSessionModal
        open={showSchedule}
        onClose={() => setShowSchedule(false)}
        onCreated={refresh}
      />

      <ConfirmDialog
        open={pendingCancel !== null}
        title={t('expert.dashboard.confirmCancelTitle')}
        body={t('expert.dashboard.confirmCancel')}
        confirmLabel={t('expert.dashboard.confirmCancelConfirm')}
        cancelLabel={t('common.cancel')}
        busy={cancelBusy}
        onConfirm={() => pendingCancel && performCancel(pendingCancel)}
        onClose={() => setPendingCancel(null)}
      />
    </>
  )
}
