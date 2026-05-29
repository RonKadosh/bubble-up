import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
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

function formatRange(startsAt: string | null, endsAt: string | null): string {
  if (!startsAt || !endsAt) return ''
  const s = new Date(startsAt)
  const e = new Date(endsAt)
  return `${s.toLocaleString()} → ${e.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
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

  async function handleCancel(sessionId: string) {
    if (!window.confirm(t('expert.dashboard.confirmCancel'))) return
    try {
      await cancelExpertSession(sessionId)
      await refresh()
    } catch {
      setActionError(t('expert.dashboard.errorCancel'))
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
    <div className="flex-1 overflow-y-auto p-6 sm:p-8">
      <div className="max-w-4xl mx-auto space-y-6">

        {/* Profile snippet */}
        <section className="bg-surface rounded-2xl shadow-themed border border-line p-5 flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <h1 className="text-lg font-bold text-base">{profile?.headline}</h1>
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
          <Link
            to="/expert/profile/edit"
            className="shrink-0 px-3 py-1.5 rounded-full text-xs text-base hover:bg-surface-hover transition border border-line"
          >
            {t('expert.dashboard.editProfile')}
          </Link>
        </section>

        {actionError && (
          <div className="px-4 py-2 text-xs bg-warning/15 text-warning border border-warning/30 rounded-lg">
            {actionError}
            <button onClick={() => setActionError(null)} className="ml-3 underline">dismiss</button>
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
                <li key={r.id} className="bg-surface rounded-xl border border-line p-4 flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="text-sm text-base">
                      {new Date(r.proposedStartsAt).toLocaleString()} → {new Date(r.proposedEndsAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </div>
                    {r.message && <p className="text-sm text-muted mt-1 truncate">{r.message}</p>}
                  </div>
                  <div className="shrink-0 flex gap-2">
                    <button
                      onClick={() => handleReject(r.id)}
                      className="px-3 py-1.5 rounded-full text-xs text-base hover:bg-surface-hover transition border border-line"
                    >
                      {t('expert.dashboard.reject')}
                    </button>
                    <button
                      onClick={() => handleAccept(r.id)}
                      className="px-3 py-1.5 rounded-full bg-primary-600 text-white text-xs font-medium hover:bg-primary-700 transition"
                    >
                      {t('expert.dashboard.accept')}
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Sessions */}
        <section>
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-lg font-bold text-base">{t('expert.dashboard.sessionsHeading')}</h2>
            <button
              type="button"
              onClick={() => setShowSchedule(true)}
              className="px-4 py-2 rounded-full bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 transition"
            >
              {t('expert.dashboard.schedule')}
            </button>
          </div>
          {activeSessions.length === 0 ? (
            <p className="text-sm text-muted">{t('expert.dashboard.noSessions')}</p>
          ) : (
            <ul className="space-y-2">
              {activeSessions.map((s) => (
                <li key={s.id} className="bg-surface rounded-xl border border-line p-4 flex items-start justify-between gap-4">
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
                      <Link
                        to={`/sessions/${s.id}`}
                        className="px-3 py-1.5 rounded-full bg-primary-600 text-white text-xs font-medium hover:bg-primary-700 transition"
                      >
                        {t('expert.dashboard.enter')}
                      </Link>
                    )}
                    <button
                      onClick={() => handleCancel(s.id)}
                      className="px-3 py-1.5 rounded-full text-xs text-base hover:bg-surface-hover transition border border-line"
                    >
                      {t('expert.dashboard.cancel')}
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <ScheduleExpertSessionModal
        open={showSchedule}
        onClose={() => setShowSchedule(false)}
        onCreated={refresh}
      />
    </div>
  )
}
