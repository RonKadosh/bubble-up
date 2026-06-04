import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import {
  enrollGroupInSession,
  getExpertDirectory,
  listOpenSessions,
  type ExpertProfile,
  type ExpertSession,
} from '../api/expert'
import { errorCode } from '../api/errors'
import { getGroups, type Group } from '../api/groups'
import { useAuthStore } from '../store/authStore'
import { Card } from '../components/Card'
import { Button } from '../components/Button'
import { Avatar } from '../components/Avatar'
import { formatClock, formatDateTime } from '../i18n/datetime'

/**
 * `/experts` — directory + open-sessions browse view.
 *
 * Two sections stacked:
 *   1. Open sessions across every expert. Group owners pick one of their owned
 *      groups inline and enroll. The session then materializes on the group's
 *      calendar (backend merges enrolled sessions into the group calendar
 *      query) so members can join from the hub.
 *   2. Expert profiles, searchable by headline / bio / tag.
 */
export default function ExpertDirectoryPage() {
  const { t } = useTranslation()
  const me = useAuthStore((s) => s.user)
  const [experts, setExperts] = useState<ExpertProfile[]>([])
  const [sessions, setSessions] = useState<ExpertSession[]>([])
  const [ownedGroups, setOwnedGroups] = useState<Group[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')

  async function refresh() {
    setLoading(true)
    try {
      const [d, s, g] = await Promise.all([
        getExpertDirectory(),
        listOpenSessions(),
        getGroups().catch(() => [] as Group[]),
      ])
      setExperts(d)
      setSessions(s)
      setOwnedGroups(g.filter((x) => me?.id && x.ownerId === me.id))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    refresh()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [me?.id])

  const filteredExperts = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return experts
    return experts.filter((e) =>
      e.headline.toLowerCase().includes(q)
      || (e.bio ?? '').toLowerCase().includes(q)
      || e.expertiseTags.some((tag) => tag.toLowerCase().includes(q))
    )
  }, [experts, query])

  return (
    <div className="flex-1 overflow-y-auto p-6 sm:p-8">
      <div className="max-w-4xl mx-auto space-y-8">
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <div className="flex items-center gap-3">
            <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
            <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
            <h1 className="text-2xl font-bold text-base">{t('expert.directory.title')}</h1>
          </div>
          <Link to="/become-expert" className="text-sm text-link underline">
            {t('expert.directory.becomeLink')}
          </Link>
        </div>

        <section>
          <h2 className="text-lg font-bold text-base mb-3">{t('expert.directory.openSessionsHeading')}</h2>
          {loading ? (
            <p className="text-sm text-muted">{t('common.loading')}</p>
          ) : sessions.length === 0 ? (
            <p className="text-sm text-muted">{t('expert.directory.noOpenSessions')}</p>
          ) : (
            <ul className="space-y-2">
              {sessions.map((s) => (
                <OpenSessionCard
                  key={s.id}
                  session={s}
                  ownedGroups={ownedGroups}
                  onEnrolled={refresh}
                />
              ))}
            </ul>
          )}
        </section>

        <section>
          <h2 className="text-lg font-bold text-base mb-3">{t('expert.directory.expertsHeading')}</h2>
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('expert.directory.searchPlaceholder')}
            className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 mb-4 focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
          {loading ? (
            <p className="text-sm text-muted">{t('common.loading')}</p>
          ) : filteredExperts.length === 0 ? (
            <p className="text-sm text-muted">{t('expert.directory.noMatches')}</p>
          ) : (
            <ul className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {filteredExperts.map((e) => (
                <li key={e.id}>
                  <Link to={`/experts/${e.userId}`} className="block h-full">
                    <Card size="md" interactive className="p-4 h-full">
                      <div className="flex items-start gap-3">
                        <Avatar id={e.userId} name={e.headline} size="md" />
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <h3 className="text-base font-semibold text-base truncate">{e.headline}</h3>
                            {e.verificationStatus === 'VERIFIED' && (
                              <span className="shrink-0 text-xs px-2 py-0.5 rounded-full bg-success/15 text-success">{t('expert.directory.verifiedBadge')}</span>
                            )}
                          </div>
                          {e.bio && <p className="text-sm text-muted line-clamp-2">{e.bio}</p>}
                          {e.expertiseTags.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-2">
                              {e.expertiseTags.slice(0, 5).map((tag) => (
                                <span key={tag} className="text-xs px-2 py-0.5 rounded-full bg-surface-hover text-base">
                                  {tag}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    </Card>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  )
}

interface OpenSessionCardProps {
  session: ExpertSession
  ownedGroups: Group[]
  onEnrolled: () => void
}

function OpenSessionCard({ session, ownedGroups, onEnrolled }: OpenSessionCardProps) {
  const { t } = useTranslation()
  const [pickerOpen, setPickerOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [info, setInfo] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleEnroll(groupId: string) {
    setBusy(true)
    setInfo(null)
    setError(null)
    try {
      await enrollGroupInSession(session.id, groupId)
      setInfo(t('expert.directory.enrolledOk'))
      setPickerOpen(false)
      onEnrolled()
    } catch (err) {
      const code = errorCode(err)
      if (code === 'EXPERT_SESSION_GROUP_ALREADY_ENROLLED') setError(t('expert.directory.errorAlreadyEnrolled'))
      else if (code === 'EXPERT_SESSION_CAPACITY_REACHED') setError(t('expert.directory.errorCapacity'))
      else if (code === 'EXPERT_SESSION_ENROLLMENT_CLOSED') setError(t('expert.directory.errorEnrollmentClosed'))
      else if (code === 'GROUP_SCHEDULE_CONFLICT') setError(t('expert.directory.errorScheduleConflict'))
      else if (code === 'NOT_GROUP_OWNER') setError(t('expert.directory.errorNotGroupOwner'))
      else setError(t('expert.directory.errorEnrollGeneric'))
    } finally {
      setBusy(false)
    }
  }

  const timeLabel = session.startsAt
    ? `${formatDateTime(session.startsAt)} → ${session.endsAt ? formatClock(session.endsAt) : ''}`
    : ''

  return (
    <li>
      <Card size="md" className="p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-sm font-medium text-base truncate">{session.title}</span>
              <Link
                to={`/experts/${session.expertUserId}`}
                className="text-xs text-link underline truncate"
              >
                {session.expertUserId.slice(0, 8)}…
              </Link>
              <span className="text-xs text-muted">
                {t('expert.directory.groupsCount', { count: session.enrolledGroupCount, capacity: session.capacity })}
              </span>
            </div>
            {timeLabel && <div className="text-xs text-muted">{timeLabel}</div>}
          </div>
          <div className="shrink-0">
            {ownedGroups.length === 0 ? (
              <span className="text-xs text-muted">{t('expert.directory.noOwnedGroups')}</span>
            ) : pickerOpen ? (
              <div className="flex flex-col items-end gap-1 max-h-32 overflow-y-auto">
                <span className="text-xs text-muted">{t('expert.directory.pickGroup')}</span>
                {ownedGroups.map((g) => (
                  <Button
                    key={g.id}
                    variant="secondary"
                    size="xs"
                    onClick={() => handleEnroll(g.id)}
                    disabled={busy}
                  >
                    {g.name}
                  </Button>
                ))}
              </div>
            ) : (
              <Button
                size="sm"
                onClick={() => { setError(null); setInfo(null); setPickerOpen(true) }}
              >
                {t('expert.directory.enrollButton')}
              </Button>
            )}
          </div>
        </div>
        {info && <div className="mt-2 text-xs text-success">{info}</div>}
        {error && <div className="mt-2 text-xs text-warning">{error}</div>}
      </Card>
    </li>
  )
}
