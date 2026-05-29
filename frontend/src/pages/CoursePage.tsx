import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Course, getCourse } from '../api/catalog'
import { enrollInCourse } from '../api/enrollment'
import { errorCode } from '../api/errors'
import { getGroupsByCourse, Group, GroupsByCourseFilters, Visibility, joinGroup } from '../api/groups'

type LoadState =
  | { kind: 'loading' }
  | { kind: 'gated'; course: Course }
  | { kind: 'ready'; course: Course; groups: Group[] }
  | { kind: 'error'; message: string }

export default function CoursePage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [state, setState] = useState<LoadState>({ kind: 'loading' })
  const [enrolling, setEnrolling] = useState(false)
  const [filters, setFilters] = useState<GroupsByCourseFilters>({})
  const [joinedGroupIds, setJoinedGroupIds] = useState<Set<string>>(new Set())

  async function load(courseId: string, withFilters: GroupsByCourseFilters) {
    try {
      const course = await getCourse(courseId)
      try {
        const groups = await getGroupsByCourse(courseId, withFilters)
        // Track which groups the user is already in for the "Joined" badge.
        // We can't tell from the Group shape directly, so re-fetch /me on filter
        // changes is wasteful; instead drive it from local set seeded on first load.
        setState({ kind: 'ready', course, groups })
      } catch (e: unknown) {
        if (errorCode(e) === 'NOT_ENROLLED_IN_COURSE') {
          setState({ kind: 'gated', course })
        } else {
          throw e
        }
      }
    } catch (e: unknown) {
      setState({ kind: 'error', message: String(e) })
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
    try {
      await enrollInCourse(id)
      await load(id, filters)
    } catch (e) {
      const code = errorCode(e)
      alert(
        code === 'ENROLLMENT_NO_CURRENT_OFFERING'
          ? 'This course is not offered in the current term.'
          : code === 'CURRENT_TERM_NOT_FOUND'
          ? 'No active term right now — try again later.'
          : code === 'USER_AFFILIATION_REQUIRED'
          ? 'Set your university and department in your profile first.'
          : 'Could not enroll. Please try again.'
      )
    } finally {
      setEnrolling(false)
    }
  }

  async function handleJoin(groupId: string) {
    try {
      await joinGroup(groupId)
      setJoinedGroupIds((s) => new Set(s).add(groupId))
    } catch {
      alert('Could not join the group.')
    }
  }

  if (state.kind === 'loading') {
    return <CourseShell><p className="text-secondary">Loading…</p></CourseShell>
  }
  if (state.kind === 'error') {
    return <CourseShell><p className="text-red-600">{state.message}</p></CourseShell>
  }

  if (state.kind === 'gated') {
    return (
      <CourseShell>
        <CourseHeader course={state.course} onBack={() => navigate('/academy')} />
        <div className="rounded-2xl border border-line bg-surface p-6 text-center max-w-xl mx-auto">
          <h2 className="text-lg font-semibold text-base">Enroll to view this course</h2>
          <p className="text-sm text-secondary mt-1">
            Course pages — including study groups and accumulative info — are visible to
            enrolled students only.
          </p>
          <button
            type="button"
            onClick={handleEnroll}
            disabled={enrolling}
            className="mt-4 px-5 py-2 rounded-full bg-indigo-600 text-on-brand font-medium hover:bg-indigo-700 disabled:opacity-60"
          >
            {enrolling ? 'Enrolling…' : 'Enroll for current term'}
          </button>
        </div>
      </CourseShell>
    )
  }

  return (
    <CourseShell>
      <CourseHeader course={state.course} onBack={() => navigate('/academy')} />

      <section className="rounded-2xl border border-line bg-surface p-5 mb-4">
        <h3 className="text-sm font-semibold text-base">Course info</h3>
        <p className="text-sm text-secondary mt-1">
          Detailed course information (syllabus, materials, instructors) is coming{' '}
          <span className="font-semibold text-indigo-600">SOON</span>.
        </p>
      </section>

      <section className="rounded-2xl border border-line bg-surface p-5">
        <header className="flex items-center justify-between mb-3 gap-2 flex-wrap">
          <h3 className="text-sm font-semibold text-base">Study groups · this term</h3>
          <div className="flex flex-wrap gap-2 items-center">
            <input
              value={filters.q ?? ''}
              onChange={(e) => setFilters((f) => ({ ...f, q: e.target.value || undefined }))}
              placeholder="search by name"
              className="rounded-full border border-line bg-base px-3 py-1.5 text-sm"
            />
            <select
              value={filters.visibility ?? ''}
              onChange={(e) =>
                setFilters((f) => ({
                  ...f,
                  visibility: (e.target.value || undefined) as Visibility | undefined,
                }))
              }
              className="rounded-full border border-line bg-base px-3 py-1.5 text-sm"
            >
              <option value="">All visibilities</option>
              <option value="PUBLIC">Public</option>
              <option value="PRIVATE">Private</option>
            </select>
            <label className="flex items-center gap-1 text-xs text-secondary">
              <input
                type="checkbox"
                checked={filters.joinedOnly ?? false}
                onChange={(e) =>
                  setFilters((f) => ({ ...f, joinedOnly: e.target.checked || undefined }))
                }
              />
              Joined only
            </label>
          </div>
        </header>
        <ul className="flex flex-col gap-2">
          {state.groups.map((g) => {
            const justJoined = joinedGroupIds.has(g.id)
            return (
              <li
                key={g.id}
                className="flex items-center justify-between rounded-xl border border-line bg-base px-4 py-2"
              >
                <div>
                  <div className="font-medium text-base">{g.name}</div>
                  <div className="text-xs text-secondary">
                    {g.memberCount} member{g.memberCount === 1 ? '' : 's'} · {g.visibility}
                  </div>
                </div>
                {justJoined ? (
                  <button
                    type="button"
                    onClick={() => navigate('/groups')}
                    className="px-3 py-1 rounded-full bg-indigo-600 text-on-brand text-xs"
                  >
                    Open in My Bubbles
                  </button>
                ) : (
                  <button
                    type="button"
                    onClick={() => handleJoin(g.id)}
                    className="px-3 py-1 rounded-full border border-line text-xs hover:bg-surface-hover"
                  >
                    Join
                  </button>
                )}
              </li>
            )
          })}
          {state.groups.length === 0 && (
            <li className="text-sm text-secondary text-center py-6">
              No groups yet — be the first to create one from the My Bubbles hub.
            </li>
          )}
        </ul>
      </section>
    </CourseShell>
  )
}

function CourseShell({ children }: { children: React.ReactNode }) {
  return <div className="p-6 flex flex-col gap-4 overflow-y-auto h-full">{children}</div>
}

function CourseHeader({ course, onBack }: { course: Course; onBack: () => void }) {
  return (
    <header className="flex items-start justify-between gap-2 mb-2">
      <div>
        <button
          type="button"
          onClick={onBack}
          className="text-xs text-secondary hover:text-base"
        >
          ← Academy
        </button>
        <h1 className="text-xl font-semibold text-base mt-1">
          <span className="font-mono text-sm text-secondary">{course.code}</span> {course.name}
        </h1>
        {course.description && (
          <p className="text-sm text-secondary mt-1 max-w-3xl">{course.description}</p>
        )}
      </div>
    </header>
  )
}
