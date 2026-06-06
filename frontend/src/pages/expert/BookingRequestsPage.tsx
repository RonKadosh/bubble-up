import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  acceptBookingRequest,
  listMyBookings,
  rejectBookingRequest,
  withdrawBookingRequest,
  type BookingRequest,
} from '../../api/expert'
import { useAuthStore } from '../../store/authStore'
import { Card } from '../../components/Card'
import { Button } from '../../components/Button'
import { formatClock, formatDateTime } from '../../i18n/datetime'

/**
 * Booking-request inbox / outbox. The same page renders both views because
 * the data shape is identical; we just flip which list and which actions to
 * show based on the {@code asExpert} toggle and the current user's role.
 *
 * Mounted at both /expert/requests (expert side) and /bookings (group-owner
 * outbound). When the user has both roles the toggle is shown.
 */
export default function BookingRequestsPage() {
  const { t } = useTranslation()
  const role = useAuthStore((s) => s.user?.role)
  const isExpert = role === 'EXPERT' || role === 'ADMIN'
  const [asExpert, setAsExpert] = useState(isExpert)
  const [requests, setRequests] = useState<BookingRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  async function refresh() {
    setLoading(true)
    setError(null)
    try {
      setRequests(await listMyBookings(asExpert))
    } catch {
      setError(t('expert.requests.errorLoad'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [asExpert])

  async function withAction(action: (id: string) => Promise<unknown>, id: string) {
    try {
      await action(id)
      await refresh()
    } catch {
      setError(t('expert.requests.errorAction'))
    }
  }

  return (
    <div className="flex-1 overflow-y-auto p-6 sm:p-8">
      <div className="max-w-4xl mx-auto">
        <div className="flex items-center justify-between mb-4 gap-3 flex-wrap">
          <div className="flex items-center gap-3">
            <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
            <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
            <h1 className="text-2xl font-bold text-base">{t('expert.requests.title')}</h1>
          </div>
          {isExpert && (
            <div className="inline-flex rounded-full border border-line overflow-hidden">
              <button
                type="button"
                onClick={() => setAsExpert(true)}
                className={`px-3 py-1.5 text-xs transition ${asExpert ? 'bg-brand-gradient-strong text-on-brand' : 'text-base hover:bg-surface-hover'}`}
              >
                {t('expert.requests.tabInbound')}
              </button>
              <button
                type="button"
                onClick={() => setAsExpert(false)}
                className={`px-3 py-1.5 text-xs transition ${!asExpert ? 'bg-brand-gradient-strong text-on-brand' : 'text-base hover:bg-surface-hover'}`}
              >
                {t('expert.requests.tabOutbound')}
              </button>
            </div>
          )}
        </div>

        {error && (
          <div className="px-4 py-2 text-xs bg-warning/15 text-warning border border-warning/30 rounded-xl mb-3 flex items-center gap-3">
            <span className="flex-1">{error}</span>
            <button onClick={() => setError(null)} className="underline shrink-0">{t('common.close')}</button>
          </div>
        )}

        {loading ? (
          <p className="text-sm text-muted">{t('common.loading')}</p>
        ) : requests.length === 0 ? (
          <p className="text-sm text-muted">{t('expert.requests.empty')}</p>
        ) : (
          <ul className="space-y-2">
            {requests.map((r) => (
              <li key={r.id}>
                <Card size="md" className="p-4 flex items-start justify-between gap-4">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-sm text-base">
                        {formatDateTime(r.proposedStartsAt)} → {formatClock(r.proposedEndsAt)}
                      </span>
                      <span
                        className={`text-xs px-2 py-0.5 rounded-full ${
                          r.status === 'PENDING'
                            ? 'bg-warning/15 text-warning'
                            : r.status === 'ACCEPTED'
                            ? 'bg-success/15 text-success'
                            : 'bg-surface-hover text-muted'
                        }`}
                      >
                        {r.status}
                      </span>
                    </div>
                    {r.message && <p className="text-sm text-muted truncate">{r.message}</p>}
                  </div>
                  <div className="shrink-0 flex gap-2">
                    {r.status === 'PENDING' && asExpert && (
                      <>
                        <Button variant="secondary" size="xs" onClick={() => withAction(rejectBookingRequest, r.id)}>
                          {t('expert.requests.reject')}
                        </Button>
                        <Button size="xs" onClick={() => withAction(acceptBookingRequest, r.id)}>
                          {t('expert.requests.accept')}
                        </Button>
                      </>
                    )}
                    {r.status === 'PENDING' && !asExpert && (
                      <Button variant="secondary" size="xs" onClick={() => withAction(withdrawBookingRequest, r.id)}>
                        {t('expert.requests.withdraw')}
                      </Button>
                    )}
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
