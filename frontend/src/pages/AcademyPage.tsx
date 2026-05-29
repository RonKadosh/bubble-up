import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Card } from '../components/Card'
import {
  Course,
  Department,
  Offering,
  Term,
  University,
  getCourse,
  getCoursesByDepartment,
  getCurrentTerm,
  getDepartments,
  getOfferingsForCourse,
  getTerms,
  getUniversities,
} from '../api/catalog'
import {
  Enrollment,
  enrollInCourse,
  listMyCurrentEnrollments,
  unenroll as unenrollApi,
} from '../api/enrollment'
import { errorCode } from '../api/errors'

const TERM_ALL = '__all__'

export default function AcademyPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [university, setUniversity] = useState<University | null>(null)
  const [departments, setDepartments] = useState<Department[]>([])
  const [terms, setTerms] = useState<Term[]>([])
  const [currentTerm, setCurrentTerm] = useState<Term | null>(null)
  const [selectedDeptId, setSelectedDeptId] = useState<string | null>(null)
  const [selectedTermId, setSelectedTermId] = useState<string>(TERM_ALL)
  const [courses, setCourses] = useState<Course[]>([])
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null)
  const [course, setCourse] = useState<Course | null>(null)
  const [offerings, setOfferings] = useState<Offering[]>([])

  const [myEnrollments, setMyEnrollments] = useState<Enrollment[]>([])
  const [enrollBusy, setEnrollBusy] = useState(false)

  const [loadingShell, setLoadingShell] = useState(true)
  const [loadingCourses, setLoadingCourses] = useState(false)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const termsById = useMemo(() => {
    const m = new Map<string, Term>()
    terms.forEach((tm) => m.set(tm.id, tm))
    return m
  }, [terms])

  const deptsById = useMemo(() => {
    const m = new Map<string, Department>()
    departments.forEach((d) => m.set(d.id, d))
    return m
  }, [departments])

  /** courseId → enrollment row for the current term. */
  const enrolledCourseIds = useMemo(() => {
    const m = new Map<string, Enrollment>()
    myEnrollments.forEach((e) => { if (e.courseId) m.set(e.courseId, e) })
    return m
  }, [myEnrollments])

  async function refreshEnrollments() {
    try {
      const list = await listMyCurrentEnrollments()
      setMyEnrollments(list)
    } catch {
      // silent — enrollment list isn't critical to the browse flow
    }
  }

  useEffect(() => {
    let cancelled = false
    setLoadingShell(true)
    setError(null)
    ;(async () => {
      try {
        const unis = await getUniversities()
        if (cancelled) return
        if (unis.length === 0) {
          setLoadingShell(false)
          return
        }
        const uni = unis[0]
        setUniversity(uni)
        const [depts, termList, ct, enrollments] = await Promise.all([
          getDepartments(uni.id),
          getTerms(uni.id),
          getCurrentTerm(uni.id),
          listMyCurrentEnrollments().catch(() => [] as Enrollment[]),
        ])
        if (cancelled) return
        setDepartments(depts)
        setTerms(termList)
        setCurrentTerm(ct)
        setSelectedTermId(ct?.id ?? TERM_ALL)
        setMyEnrollments(enrollments)
      } catch {
        if (!cancelled) setError(t('academy.error.load'))
      } finally {
        if (!cancelled) setLoadingShell(false)
      }
    })()
    return () => { cancelled = true }
  }, [t])

  useEffect(() => {
    if (!selectedDeptId) {
      setCourses([])
      setSelectedCourseId(null)
      return
    }
    let cancelled = false
    setLoadingCourses(true)
    setError(null)
    ;(async () => {
      try {
        const termArg = selectedTermId === TERM_ALL ? undefined : selectedTermId
        const list = await getCoursesByDepartment(selectedDeptId, termArg)
        if (cancelled) return
        setCourses(list)
        setSelectedCourseId(null)
      } catch {
        if (!cancelled) setError(t('academy.error.load'))
      } finally {
        if (!cancelled) setLoadingCourses(false)
      }
    })()
    return () => { cancelled = true }
  }, [selectedDeptId, selectedTermId, t])

  useEffect(() => {
    if (!selectedCourseId) {
      setCourse(null)
      setOfferings([])
      return
    }
    let cancelled = false
    setLoadingDetail(true)
    ;(async () => {
      try {
        const [c, offs] = await Promise.all([
          getCourse(selectedCourseId),
          getOfferingsForCourse(selectedCourseId),
        ])
        if (cancelled) return
        setCourse(c)
        setOfferings(offs)
      } catch {
        if (!cancelled) setError(t('academy.error.load'))
      } finally {
        if (!cancelled) setLoadingDetail(false)
      }
    })()
    return () => { cancelled = true }
  }, [selectedCourseId, t])

  async function handleEnroll(courseId: string) {
    setEnrollBusy(true)
    try {
      await enrollInCourse(courseId)
      await refreshEnrollments()
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
      setEnrollBusy(false)
    }
  }

  async function handleUnenroll(enrollmentId: string) {
    if (!confirm('Unenroll from this course?')) return
    setEnrollBusy(true)
    try {
      await unenrollApi(enrollmentId)
      await refreshEnrollments()
    } catch {
      alert('Could not unenroll. Please try again.')
    } finally {
      setEnrollBusy(false)
    }
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
      <header className="px-4 tablet:px-6 desktop:px-8 pt-6 tablet:pt-8 pb-4 shrink-0">
        <div className="flex items-center gap-3 mb-1">
          <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
          <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
          <h1 className="text-2xl font-bold text-base">{t('academy.title')}</h1>
          {university && (
            <span className="ms-2 text-xs px-2 py-0.5 rounded-md bg-surface-muted text-secondary border border-line">
              {university.shortCode}
            </span>
          )}
        </div>
        <p className="text-sm text-muted ms-[1.6rem]">{t('academy.subtitle')}</p>

        <div className="mt-4 ms-[1.6rem] flex items-center gap-2 flex-wrap">
          <label className="text-xs text-muted" htmlFor="academy-term">
            {t('academy.term.label')}
          </label>
          <select
            id="academy-term"
            value={selectedTermId}
            onChange={(e) => setSelectedTermId(e.target.value)}
            disabled={loadingShell || terms.length === 0}
            className="border border-line bg-surface rounded-xl px-3 py-1.5 text-sm focus:outline-none focus:border-primary-400 disabled:opacity-50"
          >
            <option value={TERM_ALL}>{t('academy.term.all')}</option>
            {terms.map((tm) => (
              <option key={tm.id} value={tm.id}>
                {tm.name} · {tm.academicYear}
              </option>
            ))}
          </select>
        </div>
      </header>

      <div className="flex-1 min-h-0 px-4 tablet:px-6 desktop:px-8 pb-6 tablet:pb-8 overflow-y-auto">
        {error && (
          <p className="mb-3 text-sm text-danger">{error}</p>
        )}

        <MyCoursesSection
          enrollments={myEnrollments}
          currentTerm={currentTerm}
          onOpenCourse={(id) => navigate(`/courses/${id}`)}
          onUnenroll={handleUnenroll}
          busy={enrollBusy}
        />

        <section className="mt-4">
          <h2 className="text-sm font-semibold text-secondary mb-2 ms-[1.6rem]">
            Browse & enroll
          </h2>
          <div className="h-[42rem] tablet:h-[36rem] grid grid-cols-1 tablet:grid-cols-[16rem_minmax(0,20rem)_minmax(0,1fr)] gap-3 min-h-0">
            <Pane title={t('academy.column.departments')}>
              {loadingShell ? (
                <PaneSkeleton />
              ) : departments.length === 0 ? (
                <PaneEmpty label={t('academy.empty.departments')} />
              ) : (
                <ul className="flex flex-col gap-1">
                  {departments.map((d) => (
                    <li key={d.id}>
                      <RowButton
                        selected={selectedDeptId === d.id}
                        onClick={() => setSelectedDeptId(d.id)}
                      >
                        <div className="font-medium truncate">{d.name}</div>
                        <div className="text-xs text-muted truncate">{d.shortCode}</div>
                      </RowButton>
                    </li>
                  ))}
                </ul>
              )}
            </Pane>

            <Pane title={t('academy.column.courses')}>
              {!selectedDeptId ? (
                <PaneEmpty label={t('academy.empty.courses')} />
              ) : loadingCourses ? (
                <PaneSkeleton />
              ) : courses.length === 0 ? (
                <PaneEmpty label={t('academy.empty.courses')} />
              ) : (
                <ul className="flex flex-col gap-1">
                  {courses.map((c) => (
                    <li key={c.id}>
                      <RowButton
                        selected={selectedCourseId === c.id}
                        onClick={() => setSelectedCourseId(c.id)}
                      >
                        <div className="text-xs font-mono text-muted">{c.code}</div>
                        <div className="font-medium truncate">{c.name}</div>
                        {enrolledCourseIds.has(c.id) && (
                          <div className="text-[10px] text-indigo-600 font-medium mt-0.5">
                            ENROLLED
                          </div>
                        )}
                      </RowButton>
                    </li>
                  ))}
                </ul>
              )}
            </Pane>

            <Pane title={t('academy.column.detail')}>
              {!selectedCourseId ? (
                <PaneEmpty label={t('academy.empty.detail')} />
              ) : loadingDetail || !course ? (
                <PaneSkeleton />
              ) : (
                <CourseDetail
                  course={course}
                  offerings={offerings}
                  termsById={termsById}
                  deptsById={deptsById}
                  currentTerm={currentTerm}
                  enrollment={enrolledCourseIds.get(course.id) ?? null}
                  enrollBusy={enrollBusy}
                  onEnroll={() => handleEnroll(course.id)}
                  onUnenroll={(eid) => handleUnenroll(eid)}
                  onOpenCourse={() => navigate(`/courses/${course.id}`)}
                />
              )}
            </Pane>
          </div>
        </section>
      </div>
    </div>
  )
}

function MyCoursesSection({
  enrollments,
  currentTerm,
  onOpenCourse,
  onUnenroll,
  busy,
}: {
  enrollments: Enrollment[]
  currentTerm: Term | null
  onOpenCourse: (courseId: string) => void
  onUnenroll: (enrollmentId: string) => void
  busy: boolean
}) {
  return (
    <section className="ms-[1.6rem]">
      <div className="flex items-baseline gap-2 mb-2">
        <h2 className="text-sm font-semibold text-secondary">My Courses</h2>
        {currentTerm && (
          <span className="text-xs text-muted">· {currentTerm.name} {currentTerm.academicYear}</span>
        )}
      </div>
      {enrollments.length === 0 ? (
        <p className="text-sm text-muted mb-3">No courses yet — enroll below to start.</p>
      ) : (
        <ul className="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-3 gap-2 mb-3">
          {enrollments.map((e) => (
            <li key={e.id}
              className="rounded-2xl border border-line bg-surface p-3 flex flex-col"
            >
              <div className="flex items-start justify-between gap-2">
                <button
                  type="button"
                  onClick={() => e.courseId && onOpenCourse(e.courseId)}
                  disabled={!e.courseId}
                  className="text-start flex-1 min-w-0"
                >
                  <div className="text-xs font-mono text-muted">{e.courseCode ?? '—'}</div>
                  <div className="font-medium text-base truncate">{e.courseName ?? '—'}</div>
                </button>
                <button
                  type="button"
                  onClick={() => onUnenroll(e.id)}
                  disabled={busy}
                  className="text-xs text-danger hover:underline shrink-0"
                  aria-label="Unenroll"
                >
                  ✕
                </button>
              </div>
              <div className="mt-2 flex justify-end">
                <button
                  type="button"
                  onClick={() => e.courseId && onOpenCourse(e.courseId)}
                  disabled={!e.courseId}
                  className="text-xs px-3 py-1 rounded-full bg-indigo-600 text-on-brand"
                >
                  Open course →
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function Pane({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card size="lg" className="flex flex-col min-h-0 h-full">
      <div className="px-4 py-3 border-b border-line shrink-0">
        <h2 className="text-sm font-semibold text-secondary">{title}</h2>
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto p-2">{children}</div>
    </Card>
  )
}

function RowButton({
  selected,
  onClick,
  children,
}: {
  selected: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`w-full text-start px-3 py-2 rounded-xl transition border ${
        selected
          ? 'bg-primary-50 text-primary-700 border-primary-100'
          : 'bg-transparent border-transparent hover:bg-surface-muted'
      }`}
    >
      {children}
    </button>
  )
}

function PaneEmpty({ label }: { label: string }) {
  return (
    <div className="h-full flex items-center justify-center text-sm text-muted px-4 text-center">
      {label}
    </div>
  )
}

function PaneSkeleton() {
  return (
    <div className="flex flex-col gap-2 p-2 animate-pulse">
      <div className="h-9 bg-surface-muted rounded-xl" />
      <div className="h-9 bg-surface-muted rounded-xl w-5/6" />
      <div className="h-9 bg-surface-muted rounded-xl w-4/6" />
    </div>
  )
}

function CourseDetail({
  course,
  offerings,
  termsById,
  deptsById,
  currentTerm,
  enrollment,
  enrollBusy,
  onEnroll,
  onUnenroll,
  onOpenCourse,
}: {
  course: Course
  offerings: Offering[]
  termsById: Map<string, Term>
  deptsById: Map<string, Department>
  currentTerm: Term | null
  enrollment: Enrollment | null
  enrollBusy: boolean
  onEnroll: () => void
  onUnenroll: (enrollmentId: string) => void
  onOpenCourse: () => void
}) {
  const { t } = useTranslation()
  const sortedOfferings = useMemo(() => {
    return [...offerings].sort((a, b) => {
      const ta = termsById.get(a.termId)
      const tb = termsById.get(b.termId)
      if (!ta || !tb) return 0
      return ta.startsOn < tb.startsOn ? -1 : ta.startsOn > tb.startsOn ? 1 : 0
    })
  }, [offerings, termsById])

  const hasCurrentOffering = useMemo(
    () => currentTerm != null && offerings.some((o) => o.termId === currentTerm.id),
    [currentTerm, offerings]
  )
  const isEnrolled = enrollment != null

  return (
    <div className="p-3 flex flex-col gap-4">
      <div>
        <div className="text-xs font-mono text-muted">{course.code}</div>
        <h3 className="text-lg font-bold text-base mt-0.5">{course.name}</h3>
      </div>

      <div className="flex flex-wrap gap-2">
        {isEnrolled ? (
          <>
            <button
              type="button"
              onClick={onOpenCourse}
              className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand text-sm font-medium"
            >
              Open course →
            </button>
            <button
              type="button"
              onClick={() => enrollment && onUnenroll(enrollment.id)}
              disabled={enrollBusy}
              className="px-4 py-2 rounded-full border border-line text-sm text-danger hover:bg-surface-muted disabled:opacity-60"
            >
              Unenroll
            </button>
          </>
        ) : hasCurrentOffering ? (
          <button
            type="button"
            onClick={onEnroll}
            disabled={enrollBusy}
            className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand text-sm font-medium disabled:opacity-60"
          >
            {enrollBusy ? 'Enrolling…' : `Enroll${currentTerm ? ` for ${currentTerm.code}` : ''}`}
          </button>
        ) : (
          <span className="text-xs text-muted px-2 py-1 rounded-full border border-line bg-surface-muted">
            Not offered this term
          </span>
        )}
      </div>

      {course.creditPoints !== null && (
        <DetailRow label={t('academy.detail.credits')}>
          <span className="text-sm font-medium">{course.creditPoints}</span>
        </DetailRow>
      )}

      <DetailRow label={t('academy.detail.departments')}>
        <div className="flex flex-wrap gap-1.5">
          {course.departmentIds.length === 0 && (
            <span className="text-sm text-muted">—</span>
          )}
          {course.departmentIds.map((id) => {
            const d = deptsById.get(id)
            const label = d ? d.shortCode : id.slice(0, 8)
            return (
              <span
                key={id}
                className="text-xs px-2 py-0.5 rounded-md bg-surface-muted text-secondary border border-line"
                title={d?.name}
              >
                {label}
              </span>
            )
          })}
        </div>
      </DetailRow>

      <DetailRow label={t('academy.detail.offerings')}>
        {sortedOfferings.length === 0 ? (
          <span className="text-sm text-muted">—</span>
        ) : (
          <ul className="flex flex-col gap-1">
            {sortedOfferings.map((o) => {
              const tm = termsById.get(o.termId)
              return (
                <li key={o.id} className="text-sm">
                  {tm ? (
                    <span>
                      <span className="font-medium">{tm.name}</span>
                      <span className="text-xs text-muted ms-2">{tm.academicYear}</span>
                    </span>
                  ) : (
                    <span className="font-mono text-xs">{o.termCode}</span>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </DetailRow>

      {course.description && (
        <DetailRow label={t('academy.detail.description')}>
          <p className="text-sm text-base whitespace-pre-wrap leading-relaxed">{course.description}</p>
        </DetailRow>
      )}
    </div>
  )
}

function DetailRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wide text-muted mb-1">{label}</div>
      {children}
    </div>
  )
}
