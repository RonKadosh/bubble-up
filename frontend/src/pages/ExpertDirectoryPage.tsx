import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import {
  enrollGroupInSession,
  getExpertDirectory,
  listOpenSessions,
  type ExpertProfile,
  type ExpertSession,
} from '../api/expert'
import { errorCode } from '../api/errors'
import { getMyGroups, type Group } from '../api/groups'
import { useAuthStore } from '../store/authStore'
import { Card } from '../components/Card'
import { Button } from '../components/Button'
import { Avatar } from '../components/Avatar'
import { PageShell, SectionLabel } from '../components/PageHeader'
import { Tabs } from '../components/Tabs'
import { CalendarIcon, CapIcon, ClockIcon, SearchIcon } from '../components/Icons'
import { RequestBookingModal } from './expert/RequestBookingModal'
import { formatClock, formatDateTime } from '../i18n/datetime'

type DirectoryTab = 'sessions' | 'experts'
type ExpertSort = 'recommended' | 'sessions' | 'newest' | 'az'

/**
 * `/experts` - marketplace-style browse surface for expert help.
 *
 * The page stays read-oriented: sessions and verified experts are fetched once
 * from the expert API, then search, tag chips, and sort are derived locally.
 * Booking/enrollment still flows through the existing modal and session
 * command endpoints.
 */
export default function ExpertDirectoryPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const me = useAuthStore((s) => s.user)
  const [activeTab, setActiveTab] = useState<DirectoryTab>('sessions')
  const [experts, setExperts] = useState<ExpertProfile[]>([])
  const [sessions, setSessions] = useState<ExpertSession[]>([])
  const [ownedGroups, setOwnedGroups] = useState<Group[]>([])
  const [loading, setLoading] = useState(true)
  const [query, setQuery] = useState('')
  const [selectedTag, setSelectedTag] = useState<string | null>(null)
  const [sort, setSort] = useState<ExpertSort>('recommended')
  const [bookingExpert, setBookingExpert] = useState<ExpertProfile | null>(null)
  const [actionInfo, setActionInfo] = useState<string | null>(null)

  async function refresh() {
    setLoading(true)
    try {
      const [d, s, g] = await Promise.all([
        getExpertDirectory(),
        listOpenSessions(),
        getMyGroups().catch(() => [] as Group[]),
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

  const expertsByUserId = useMemo(() => {
    const m = new Map<string, ExpertProfile>()
    experts.forEach((expert) => m.set(expert.userId, expert))
    return m
  }, [experts])

  const sessionCountByExpert = useMemo(() => {
    const m = new Map<string, number>()
    sessions.forEach((session) => m.set(session.expertUserId, (m.get(session.expertUserId) ?? 0) + 1))
    return m
  }, [sessions])

  const topTags = useMemo(() => {
    const counts = new Map<string, number>()
    experts.forEach((expert) => {
      expert.expertiseTags.forEach((tag) => counts.set(tag, (counts.get(tag) ?? 0) + 1))
    })
    return [...counts.entries()]
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
      .slice(0, 10)
      .map(([tag]) => tag)
  }, [experts])

  const filteredExperts = useMemo(() => {
    const q = query.trim().toLowerCase()
    const rows = experts.filter((expert) => {
      const matchesQuery = !q
        || expert.headline.toLowerCase().includes(q)
        || (expert.bio ?? '').toLowerCase().includes(q)
        || expert.expertiseTags.some((tag) => tag.toLowerCase().includes(q))
      const matchesTag = !selectedTag || expert.expertiseTags.includes(selectedTag)
      return matchesQuery && matchesTag
    })

    return [...rows].sort((a, b) => {
      if (sort === 'az') return a.headline.localeCompare(b.headline)
      if (sort === 'newest') {
        return new Date(b.verifiedAt ?? b.appliedAt).getTime() - new Date(a.verifiedAt ?? a.appliedAt).getTime()
      }
      if (sort === 'sessions') {
        return (sessionCountByExpert.get(b.userId) ?? 0) - (sessionCountByExpert.get(a.userId) ?? 0)
      }
      const sessionDelta = (sessionCountByExpert.get(b.userId) ?? 0) - (sessionCountByExpert.get(a.userId) ?? 0)
      if (sessionDelta !== 0) return sessionDelta
      return new Date(b.verifiedAt ?? b.appliedAt).getTime() - new Date(a.verifiedAt ?? a.appliedAt).getTime()
    })
  }, [experts, query, selectedTag, sessionCountByExpert, sort])

  const filteredSessions = useMemo(() => {
    const q = query.trim().toLowerCase()
    return sessions.filter((session) => {
      const expert = expertsByUserId.get(session.expertUserId)
      const matchesQuery = !q
        || session.title.toLowerCase().includes(q)
        || (session.description ?? '').toLowerCase().includes(q)
        || (expert?.headline ?? '').toLowerCase().includes(q)
        || expert?.expertiseTags.some((tag) => tag.toLowerCase().includes(q))
      const matchesTag = !selectedTag || expert?.expertiseTags.includes(selectedTag)
      return matchesQuery && matchesTag
    })
  }, [expertsByUserId, query, selectedTag, sessions])

  const featuredExperts = filteredExperts.slice(0, 3)
  const hasFilters = query.trim().length > 0 || selectedTag !== null

  return (
    <>
      <PageShell
        title={t('expert.directory.title')}
        subtitle={t('expert.directory.subtitle')}
        actions={
          <>
            <Button variant="secondary" size="sm" onClick={() => navigate('/bookings')}>
              {t('expert.directory.bookingsLink')}
            </Button>
            <Button variant="deep" size="sm" onClick={() => navigate('/become-expert')}>
              {t('expert.directory.becomeButton')}
            </Button>
          </>
        }
        tabs={
          <Tabs
            active={activeTab}
            onChange={(key) => setActiveTab(key as DirectoryTab)}
            items={[
              { key: 'sessions', label: t('expert.directory.tabs.sessions'), badge: sessions.length },
              { key: 'experts', label: t('expert.directory.tabs.experts'), badge: experts.length },
            ]}
          />
        }
      >
        <div className="space-y-6">
          <Card size="md" className="p-4 tablet:p-5">
            <div className="flex flex-col gap-4">
              <div className="grid grid-cols-1 tablet:grid-cols-[minmax(0,1fr)_12rem] gap-3">
                <div className="relative">
                  <SearchIcon className="absolute start-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted pointer-events-none" />
                  <input
                    type="search"
                    value={query}
                    onChange={(e) => setQuery(e.target.value)}
                    placeholder={t('expert.directory.searchPlaceholder')}
                    className="w-full ps-9 pe-3 py-2 text-sm rounded-xl border border-line bg-surface focus-bubble"
                  />
                </div>
                {activeTab === 'experts' ? (
                  <select
                    value={sort}
                    onChange={(e) => setSort(e.target.value as ExpertSort)}
                    className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
                  >
                    <option value="recommended">{t('expert.directory.sort.recommended')}</option>
                    <option value="sessions">{t('expert.directory.sort.sessions')}</option>
                    <option value="newest">{t('expert.directory.sort.newest')}</option>
                    <option value="az">{t('expert.directory.sort.az')}</option>
                  </select>
                ) : (
                  <Button variant="secondary" size="sm" onClick={() => setActiveTab('experts')}>
                    {t('expert.directory.requestHelp')}
                  </Button>
                )}
              </div>

              {topTags.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  <TagChip active={selectedTag === null} onClick={() => setSelectedTag(null)}>
                    {t('expert.directory.allTags')}
                  </TagChip>
                  {topTags.map((tag) => (
                    <TagChip key={tag} active={selectedTag === tag} onClick={() => setSelectedTag(tag)}>
                      {tag}
                    </TagChip>
                  ))}
                </div>
              )}
            </div>
          </Card>

          {actionInfo && (
            <div className="px-4 py-2 text-xs bg-success/15 text-success border border-success/30 rounded-xl flex items-center gap-3">
              <span className="flex-1">{actionInfo}</span>
              <button type="button" onClick={() => setActionInfo(null)} className="underline shrink-0">
                {t('common.close')}
              </button>
            </div>
          )}

          {activeTab === 'sessions' ? (
            <section>
              <div className="flex items-center justify-between gap-3 mb-3">
                <SectionLabel>{t('expert.directory.openSessionsHeading')}</SectionLabel>
                <span className="text-xs text-muted">
                  {t('expert.directory.resultCount', { count: filteredSessions.length })}
                </span>
              </div>

              {loading ? (
                <ListSkeleton />
              ) : filteredSessions.length === 0 ? (
                <EmptyState
                  icon={<CalendarIcon className="w-5 h-5" />}
                  title={hasFilters ? t('expert.directory.noFilteredSessionsTitle') : t('expert.directory.noOpenSessionsTitle')}
                  body={hasFilters ? t('expert.directory.noFilteredSessionsBody') : t('expert.directory.noOpenSessionsBody')}
                  actionLabel={t('expert.directory.browseExperts')}
                  onAction={() => setActiveTab('experts')}
                />
              ) : (
                <ul className="grid grid-cols-1 desktop:grid-cols-2 gap-3">
                  {filteredSessions.map((session) => (
                    <OpenSessionCard
                      key={session.id}
                      session={session}
                      expert={expertsByUserId.get(session.expertUserId) ?? null}
                      ownedGroups={ownedGroups}
                      onEnrolled={refresh}
                    />
                  ))}
                </ul>
              )}
            </section>
          ) : (
            <div className="space-y-6">
              {featuredExperts.length > 0 && (
                <section>
                  <SectionLabel className="mb-3">{t('expert.directory.featuredHeading')}</SectionLabel>
                  <ul className="grid grid-cols-1 tablet:grid-cols-3 gap-3">
                    {featuredExperts.map((expert) => (
                      <li key={expert.id}>
                        <ExpertCard
                          expert={expert}
                          openSessionCount={sessionCountByExpert.get(expert.userId) ?? 0}
                          compact
                          canBook={ownedGroups.length > 0}
                          onBook={() => setBookingExpert(expert)}
                        />
                      </li>
                    ))}
                  </ul>
                </section>
              )}

              <section>
                <div className="flex items-center justify-between gap-3 mb-3">
                  <SectionLabel>{t('expert.directory.expertsHeading')}</SectionLabel>
                  <span className="text-xs text-muted">
                    {t('expert.directory.resultCount', { count: filteredExperts.length })}
                  </span>
                </div>

                {loading ? (
                  <ListSkeleton />
                ) : filteredExperts.length === 0 ? (
                  <EmptyState
                    icon={<CapIcon className="w-5 h-5" />}
                    title={hasFilters ? t('expert.directory.noMatchesTitle') : t('expert.directory.noExpertsTitle')}
                    body={hasFilters ? t('expert.directory.noMatchesBody') : t('expert.directory.noExpertsBody')}
                    actionLabel={hasFilters ? t('expert.directory.clearFilters') : t('expert.directory.becomeButton')}
                    onAction={() => {
                      if (hasFilters) {
                        setQuery('')
                        setSelectedTag(null)
                      } else {
                        navigate('/become-expert')
                      }
                    }}
                  />
                ) : (
                  <ul className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
                    {filteredExperts.map((expert) => (
                      <li key={expert.id}>
                        <ExpertCard
                          expert={expert}
                          openSessionCount={sessionCountByExpert.get(expert.userId) ?? 0}
                          canBook={ownedGroups.length > 0}
                          onBook={() => setBookingExpert(expert)}
                        />
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </div>
          )}
        </div>
      </PageShell>

      {bookingExpert && (
        <RequestBookingModal
          open
          expertUserId={bookingExpert.userId}
          onClose={() => setBookingExpert(null)}
          onSent={() => setActionInfo(t('expert.directory.requestSent'))}
        />
      )}
    </>
  )
}

function TagChip({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`px-3 py-1.5 text-xs rounded-full border transition ${
        active
          ? 'bg-brand-gradient-strong border-transparent text-on-brand shadow-themed font-semibold'
          : 'bg-surface border-line text-secondary hover:text-base hover:border-line-strong'
      }`}
    >
      {children}
    </button>
  )
}

interface ExpertCardProps {
  expert: ExpertProfile
  openSessionCount: number
  canBook: boolean
  compact?: boolean
  onBook: () => void
}

function ExpertCard({ expert, openSessionCount, canBook, compact = false, onBook }: ExpertCardProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <Card size="md" interactive className="p-4 h-full">
      <div className="flex flex-col h-full gap-4">
        <Link to={`/experts/${expert.userId}`} className="flex items-start gap-3 min-w-0">
          <Avatar id={expert.userId} name={expert.headline} size={compact ? 'md' : 'lg'} ring={!compact} />
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <h3 className="text-base font-semibold text-base truncate">{expert.headline}</h3>
              {expert.verificationStatus === 'VERIFIED' && (
                <span className="shrink-0 text-xs px-2 py-0.5 rounded-full bg-success/15 text-success">
                  {t('expert.directory.verifiedBadge')}
                </span>
              )}
            </div>
            {expert.bio && <p className={`text-sm text-muted ${compact ? 'line-clamp-2' : 'line-clamp-3'}`}>{expert.bio}</p>}
          </div>
        </Link>

        {expert.expertiseTags.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {expert.expertiseTags.slice(0, compact ? 3 : 5).map((tag) => (
              <span key={tag} className="text-xs px-2 py-0.5 rounded-full bg-surface-hover text-base">
                {tag}
              </span>
            ))}
          </div>
        )}

        <div className="mt-auto flex flex-wrap items-center justify-between gap-2">
          <span className="inline-flex items-center gap-1.5 text-xs text-muted">
            <ClockIcon className="w-3.5 h-3.5" />
            {t('expert.directory.openSessionCount', { count: openSessionCount })}
          </span>
          <div className="flex gap-2">
            <Button variant="secondary" size="xs" onClick={onBook} disabled={!canBook} title={!canBook ? t('expert.directory.noOwnedGroupsShort') : ''}>
              {t('expert.directory.requestButton')}
            </Button>
            <Button size="xs" onClick={() => navigate(`/experts/${expert.userId}`)}>
              {t('expert.directory.viewProfile')}
            </Button>
          </div>
        </div>
      </div>
    </Card>
  )
}

interface OpenSessionCardProps {
  session: ExpertSession
  expert: ExpertProfile | null
  ownedGroups: Group[]
  onEnrolled: () => void
}

function OpenSessionCard({ session, expert, ownedGroups, onEnrolled }: OpenSessionCardProps) {
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
    ? `${formatDateTime(session.startsAt)} -> ${session.endsAt ? formatClock(session.endsAt) : ''}`
    : ''

  return (
    <li>
      <Card size="md" className="p-4 h-full">
        <div className="flex flex-col h-full gap-4">
          <div className="flex items-start justify-between gap-3">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 mb-1">
                <h3 className="text-base font-semibold text-base truncate">{session.title}</h3>
                <span className="text-xs px-2 py-0.5 rounded-full bg-success/15 text-success">
                  {t('expert.directory.statusOpen')}
                </span>
              </div>
              {expert ? (
                <Link to={`/experts/${session.expertUserId}`} className="text-xs text-link underline">
                  {expert.headline}
                </Link>
              ) : (
                <span className="text-xs text-muted">{session.expertUserId.slice(0, 8)}...</span>
              )}
            </div>
            <span className="shrink-0 text-xs text-muted">
              {t('expert.directory.groupsCount', { count: session.enrolledGroupCount, capacity: session.capacity })}
            </span>
          </div>

          {timeLabel && (
            <div className="inline-flex items-center gap-1.5 text-xs text-muted">
              <CalendarIcon className="w-3.5 h-3.5" />
              {timeLabel}
            </div>
          )}
          {session.description && <p className="text-sm text-muted line-clamp-2">{session.description}</p>}
          {expert && expert.expertiseTags.length > 0 && (
            <div className="flex flex-wrap gap-1">
              {expert.expertiseTags.slice(0, 4).map((tag) => (
                <span key={tag} className="text-xs px-2 py-0.5 rounded-full bg-surface-hover text-base">
                  {tag}
                </span>
              ))}
            </div>
          )}

          <div className="mt-auto flex justify-end">
            {ownedGroups.length === 0 ? (
              <span className="text-xs text-muted">{t('expert.directory.noOwnedGroups')}</span>
            ) : pickerOpen ? (
              <div className="flex flex-col items-end gap-1 max-h-32 overflow-y-auto">
                <span className="text-xs text-muted">{t('expert.directory.pickGroup')}</span>
                {ownedGroups.map((group) => (
                  <Button
                    key={group.id}
                    variant="secondary"
                    size="xs"
                    onClick={() => handleEnroll(group.id)}
                    disabled={busy}
                  >
                    {group.name}
                  </Button>
                ))}
              </div>
            ) : (
              <Button
                variant="deep"
                size="sm"
                onClick={() => { setError(null); setInfo(null); setPickerOpen(true) }}
              >
                {t('expert.directory.enrollButton')}
              </Button>
            )}
          </div>

          {info && <div className="text-xs text-success">{info}</div>}
          {error && <div className="text-xs text-warning">{error}</div>}
        </div>
      </Card>
    </li>
  )
}

function EmptyState({
  icon,
  title,
  body,
  actionLabel,
  onAction,
}: {
  icon: React.ReactNode
  title: string
  body: string
  actionLabel: string
  onAction: () => void
}) {
  return (
    <Card size="md" className="p-6">
      <div className="flex flex-col items-center text-center gap-3">
        <span className="w-11 h-11 rounded-2xl bg-surface-muted text-muted flex items-center justify-center">
          {icon}
        </span>
        <div>
          <h3 className="text-base font-semibold text-base">{title}</h3>
          <p className="text-sm text-muted mt-1 max-w-md">{body}</p>
        </div>
        <Button variant="secondary" size="sm" onClick={onAction}>
          {actionLabel}
        </Button>
      </div>
    </Card>
  )
}

function ListSkeleton() {
  return (
    <div className="grid grid-cols-1 tablet:grid-cols-2 gap-3 animate-pulse">
      {[0, 1, 2, 3].map((item) => (
        <Card key={item} size="md" className="p-4">
          <div className="h-5 bg-surface-muted rounded-xl w-2/3 mb-3" />
          <div className="h-4 bg-surface-muted rounded-xl w-full mb-2" />
          <div className="h-4 bg-surface-muted rounded-xl w-4/6" />
        </Card>
      ))}
    </div>
  )
}
