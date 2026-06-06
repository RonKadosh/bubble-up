import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Course, getCourse } from '../api/catalog'
import { enrollInCourse } from '../api/enrollment'
import { describeError, errorCode } from '../api/errors'
import { getGroupsByCourse, Group, GroupsByCourseFilters, Visibility, joinGroup } from '../api/groups'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { Avatar } from '../components/Avatar'
import { ArrowLeftIcon, LockIcon, PeopleIcon, SearchIcon, SparkleIcon } from '../components/Icons'

type LoadState =
  | { kind: 'loading' }
  | { kind: 'gated'; course: Course }
  | { kind: 'ready'; course: Course; groups: Group[] }
  | { kind: 'error'; message: string }

export default function CoursePage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { t } = useTranslation()

  const [state, setState] = useState<LoadState>({ kind: 'loading' })
  const [enrolling, setEnrolling] = useState(false)
  const [filters, setFilters] = useState<GroupsByCourseFilters>({})
  const [joinedGroupIds, setJoinedGroupIds] = useState<Set<string>>(new Set())
  const [actionError, setActionError] = useState<string | null>(null)

  async function load(courseId: string, withFilters: GroupsByCourseFilters) {
    try {
      const course = await getCourse(courseId)
      try {
        const groups = await getGroupsByCourse(courseId, withFilters)
        setState({ kind: 'ready', course, groups })
      } catch (e: unknown) {
        if (errorCode(e) === 'NOT_ENROLLED_IN_COURSE') {
          setState({ kind: 'gated', course })
        } else {
          throw e
        }
      }
    } catch {
      setState({ kind: 'error', message: t('course.error.load') })
    }
  }

  useEffect(() => {
    if (!id) return
    setState({ kind: 'loading' })
    load(id, filters)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id])

  useEffect(() => {
    if (!id || state.kind !== 'ready') return
    load(id, filters)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filters.q, filters.visibility, filters.joinedOnly])

  async function handleEnroll() {
    if (!id) return
    setEnrolling(true)
    setActionError(null)
    try {
      await enrollInCourse(id)
      await load(id, filters)
    } catch (e) {
      setActionError(describeError(e, t, {
        ENROLLMENT_NO_CURRENT_OFFERING: 'course.error.noCurrentOffering',
        CURRENT_TERM_NOT_FOUND: 'course.error.noCurrentTerm',
        USER_AFFILIATION_REQUIRED: 'course.error.affiliationRequired',
      }, 'course.error.enrollGeneric'))
    } finally {
      setEnrolling(false)
    }
  }

  async function handleJoin(groupId: string) {
    setActionError(null)
    try {
      await joinGroup(groupId)
      setJoinedGroupIds((s) => new Set(s).add(groupId))
    } catch (e) {
      setActionError(describeError(e, t, {}, 'course.error.join'))
    }
  }

  function onBack() {
    navigate('/academy')
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
      <div className="shrink-0 px-4 tablet:px-6 desktop:px-8 pt-5 tablet:pt-6">
        <Button variant="ghost" size="sm" onClick={onBack} leftIcon={<ArrowLeftIcon className="w-4 h-4 rtl:rotate-180" />}>
          {t('course.back')}
        </Button>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto px-4 tablet:px-6 desktop:px-8 pb-8 pt-4">
        <div className="mx-auto w-full max-w-4xl flex flex-col gap-5">
          {state.kind === 'loading' && <CourseSkeleton />}

          {state.kind === 'error' && (
            <ErrorCard message={state.message} onRetry={() => id && load(id, filters)} />
          )}

          {(state.kind === 'gated' || state.kind === 'ready') && (
            <CourseHero course={state.course} enrolled={state.kind === 'ready'} />
          )}

          {actionError && (
            <div
              role="alert"
              className="px-4 py-2.5 text-sm bg-warning-soft text-warning border border-warning-soft rounded-2xl flex items-center gap-3"
            >
              <span className="flex-1">{actionError}</span>
              <button onClick={() => setActionError(null)} className="underline shrink-0 text-xs">
                {t('common.close')}
              </button>
            </div>
          )}

          {state.kind === 'gated' && (
            <GatedCard enrolling={enrolling} onEnroll={handleEnroll} />
          )}

          {state.kind === 'ready' && (
            <>
              <InfoCard />
              <GroupsSection
                groups={state.groups}
                filters={filters}
                onFilters={setFilters}
                joinedGroupIds={joinedGroupIds}
                onJoin={handleJoin}
                onOpenBubbles={() => navigate('/groups')}
                onStartFirst={() => navigate('/groups', {
                  state: {
                    openCreate: true,
                    courseId: state.course.id,
                    deptId: state.course.departmentIds[0],
                  },
                })}
              />
            </>
          )}
        </div>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Hero — the one committed brand moment on the page.                 */
/* ------------------------------------------------------------------ */

function CourseHero({ course, enrolled }: { course: Course; enrolled: boolean }) {
  const { t } = useTranslation()
  return (
    <header className="relative overflow-hidden rounded-3xl bg-brand-gradient-soft ring-on-brand p-6 tablet:p-8">
      {/* faint bubble specular, top-end corner */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute -top-10 -end-10 w-44 h-44 rounded-full opacity-50 blur-2xl"
        style={{ background: 'radial-gradient(circle, rgba(255,255,255,0.7), transparent 70%)' }}
      />
      <div className="relative flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <div className="font-mono text-sm text-on-brand opacity-70 tracking-wide">{course.code}</div>
          <h1 className="mt-1 text-2xl tablet:text-3xl font-bold text-on-brand text-balance leading-tight">
            {course.name}
          </h1>
          {course.description && (
            <p className="mt-3 text-sm text-on-brand opacity-80 leading-relaxed max-w-[65ch] text-pretty">
              {course.description}
            </p>
          )}
          {course.creditPoints !== null && (
            <div className="mt-4 inline-flex items-center gap-1.5 rounded-full bg-white/35 ring-on-brand px-3 py-1 text-xs font-semibold text-on-brand">
              {t('course.credits', { count: course.creditPoints })}
            </div>
          )}
        </div>

        {enrolled && (
          <span className="shrink-0 inline-flex items-center gap-1.5 rounded-full bg-white/45 px-3 py-1.5 text-xs font-bold text-on-brand shadow-sm">
            <span className="w-2 h-2 rounded-full bg-success" />
            {t('course.enrolledBadge')}
          </span>
        )}
      </div>
    </header>
  )
}

/* ------------------------------------------------------------------ */
/* Gated — enrollment wall, redesigned as an invitation, not a stop.  */
/* ------------------------------------------------------------------ */

function GatedCard({ enrolling, onEnroll }: { enrolling: boolean; onEnroll: () => void }) {
  const { t } = useTranslation()
  return (
    <div className="ring-iridescent p-[1.5px] rounded-3xl shadow-bubble">
      <div className="rounded-[calc(1.5rem-2px)] bg-surface px-6 py-10 text-center flex flex-col items-center">
        <div className="w-14 h-14 rounded-full bg-brand-gradient-strong text-on-brand flex items-center justify-center ring-on-brand">
          <LockIcon className="w-6 h-6" />
        </div>
        <h2 className="mt-5 text-xl font-bold text-base text-balance">{t('course.gated.title')}</h2>
        <p className="mt-2 text-sm text-secondary leading-relaxed max-w-[48ch]">
          {t('course.gated.body')}
        </p>
        <Button size="lg" className="mt-6" onClick={onEnroll} disabled={enrolling}>
          {enrolling ? t('course.gated.enrolling') : t('course.gated.enroll')}
        </Button>
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Course info — honest "soon" placeholder, not a shouting SOON.      */
/* ------------------------------------------------------------------ */

function InfoCard() {
  const { t } = useTranslation()
  return (
    <Card size="lg" className="p-5 flex items-start gap-4">
      <div className="shrink-0 w-10 h-10 rounded-2xl bg-bubble-magenta-soft text-bubble-magenta flex items-center justify-center">
        <SparkleIcon className="w-5 h-5" />
      </div>
      <div className="min-w-0">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-base">{t('course.info.title')}</h3>
          <span className="text-[10px] font-bold uppercase tracking-wide px-1.5 py-0.5 rounded-md bg-bubble-magenta-soft text-bubble-magenta">
            {t('course.info.badge')}
          </span>
        </div>
        <p className="text-sm text-secondary mt-1 leading-relaxed">{t('course.info.body')}</p>
      </div>
    </Card>
  )
}

/* ------------------------------------------------------------------ */
/* Study groups — dense, scannable bubble cards with live filters.    */
/* ------------------------------------------------------------------ */

function GroupsSection({
  groups,
  filters,
  onFilters,
  joinedGroupIds,
  onJoin,
  onOpenBubbles,
  onStartFirst,
}: {
  groups: Group[]
  filters: GroupsByCourseFilters
  onFilters: (update: (f: GroupsByCourseFilters) => GroupsByCourseFilters) => void
  joinedGroupIds: Set<string>
  onJoin: (groupId: string) => void
  onOpenBubbles: () => void
  onStartFirst: () => void
}) {
  const { t } = useTranslation()
  const filtersActive =
    !!filters.q || !!filters.visibility || !!filters.joinedOnly

  return (
    <section>
      <div className="flex flex-wrap items-end justify-between gap-3 mb-3">
        <div>
          <h2 className="text-lg font-bold text-base flex items-center gap-2">
            <PeopleIcon className="w-5 h-5 text-primary-500" />
            {t('course.groups.title')}
          </h2>
          <p className="text-xs text-muted mt-0.5">
            {t('course.groups.subtitle', { count: groups.length })}
          </p>
        </div>
      </div>

      {/* Filter bar */}
      <div className="flex flex-wrap items-center gap-2 mb-4">
        <div className="relative flex-1 min-w-[12rem]">
          <SearchIcon className="absolute start-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted pointer-events-none" />
          <input
            value={filters.q ?? ''}
            onChange={(e) => onFilters((f) => ({ ...f, q: e.target.value || undefined }))}
            placeholder={t('course.groups.search')}
            className="w-full ps-9 pe-3 py-2 text-sm rounded-xl border border-line bg-surface focus-bubble"
          />
        </div>
        <select
          value={filters.visibility ?? ''}
          onChange={(e) =>
            onFilters((f) => ({
              ...f,
              visibility: (e.target.value || undefined) as Visibility | undefined,
            }))
          }
          className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
        >
          <option value="">{t('course.groups.allVisibilities')}</option>
          <option value="PUBLIC">{t('course.groups.public')}</option>
          <option value="PRIVATE">{t('course.groups.private')}</option>
        </select>
        <label
          className={`inline-flex items-center gap-2 text-sm px-3 py-2 rounded-xl border cursor-pointer bubble-pop transition ${
            filters.joinedOnly
              ? 'border-primary-400 bg-primary-50 text-primary-700'
              : 'border-line text-secondary hover:bg-surface-muted'
          }`}
        >
          <input
            type="checkbox"
            checked={filters.joinedOnly ?? false}
            onChange={(e) => onFilters((f) => ({ ...f, joinedOnly: e.target.checked || undefined }))}
            className="accent-primary-500"
          />
          {t('course.groups.joinedOnly')}
        </label>
      </div>

      {groups.length === 0 ? (
        <GroupsEmpty filtered={filtersActive} onStartFirst={onStartFirst} />
      ) : (
        <ul className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
          {groups.map((g) => (
            <li key={g.id}>
              <GroupRow
                group={g}
                joined={joinedGroupIds.has(g.id)}
                onJoin={() => onJoin(g.id)}
                onOpen={onOpenBubbles}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function GroupRow({
  group,
  joined,
  onJoin,
  onOpen,
}: {
  group: Group
  joined: boolean
  onJoin: () => void
  onOpen: () => void
}) {
  const { t } = useTranslation()
  return (
    <Card size="md" className="p-4 flex items-center gap-3.5 h-full">
      <Avatar id={group.id} name={group.name} size="lg" ring={joined} />
      <div className="min-w-0 flex-1">
        <div className="font-semibold text-base truncate">{group.name}</div>
        <div className="flex items-center gap-2 mt-1">
          <span className="inline-flex items-center gap-1 text-xs text-muted">
            <PeopleIcon className="w-3.5 h-3.5" />
            {t('course.groups.members', { count: group.memberCount })}
          </span>
          <VisibilityChip visibility={group.visibility} />
        </div>
      </div>
      {joined ? (
        <Button variant="secondary" size="xs" onClick={onOpen} className="shrink-0">
          {t('course.groups.open')}
        </Button>
      ) : (
        <Button size="xs" onClick={onJoin} className="shrink-0">
          {t('course.groups.join')}
        </Button>
      )}
    </Card>
  )
}

function VisibilityChip({ visibility }: { visibility: Visibility }) {
  const { t } = useTranslation()
  const isPublic = visibility === 'PUBLIC'
  return (
    <span
      className={`text-[10px] font-semibold uppercase tracking-wide px-1.5 py-0.5 rounded-md border ${
        isPublic
          ? 'bg-bubble-green-soft text-bubble-green border-bubble-green'
          : 'bg-surface-muted text-secondary border-line'
      }`}
    >
      {isPublic ? t('course.groups.public') : t('course.groups.private')}
    </span>
  )
}

function GroupsEmpty({ filtered, onStartFirst }: { filtered: boolean; onStartFirst: () => void }) {
  const { t } = useTranslation()
  return (
    <Card size="lg" className="px-6 py-10 text-center flex flex-col items-center">
      <div className="w-12 h-12 rounded-2xl bg-surface-muted text-muted flex items-center justify-center">
        <PeopleIcon className="w-6 h-6" />
      </div>
      <h3 className="mt-4 text-sm font-semibold text-base">{t('course.groups.emptyTitle')}</h3>
      <p className="mt-1 text-sm text-muted max-w-[42ch]">
        {filtered ? t('course.groups.emptyFiltered') : t('course.groups.emptyBody')}
      </p>
      {/* Enrolled (this view is only reached when enrolled) → offer to start the
          first Bubble. Hidden under an active filter, where "empty" just means no match. */}
      {!filtered && (
        <Button size="sm" className="mt-4" onClick={onStartFirst}>
          {t('course.groups.startFirst')}
        </Button>
      )}
    </Card>
  )
}

/* ------------------------------------------------------------------ */
/* Loading & error states                                             */
/* ------------------------------------------------------------------ */

function CourseSkeleton() {
  return (
    <div className="flex flex-col gap-5 animate-pulse">
      <div className="h-44 rounded-3xl bg-surface-muted" />
      <div className="h-20 rounded-3xl bg-surface-muted" />
      <div className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
        <div className="h-20 rounded-2xl bg-surface-muted" />
        <div className="h-20 rounded-2xl bg-surface-muted" />
        <div className="h-20 rounded-2xl bg-surface-muted" />
        <div className="h-20 rounded-2xl bg-surface-muted" />
      </div>
    </div>
  )
}

function ErrorCard({ message, onRetry }: { message: string; onRetry: () => void }) {
  const { t } = useTranslation()
  return (
    <Card size="lg" className="px-6 py-10 text-center flex flex-col items-center">
      <p className="text-sm text-danger">{message}</p>
      <Button variant="secondary" size="sm" className="mt-4" onClick={onRetry}>
        {t('common.retry')}
      </Button>
    </Card>
  )
}
