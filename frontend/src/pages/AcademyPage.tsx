import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Card } from '../components/Card'
import { Button, IconButton } from '../components/Button'
import { PageShell, SectionLabel } from '../components/PageHeader'
import { gradientFor } from '../components/Avatar'
import {
  AtomIcon,
  BeakerIcon,
  BookIcon,
  BulbIcon,
  CapIcon,
  ChevronIcon,
  CloseIcon,
  CodeIcon,
  GlobeIcon,
  LeafIcon,
  PaletteIcon,
  ScaleIcon,
  SigmaIcon,
  TrendIcon,
} from '../components/Icons'
import { ConfirmDialog } from '../components/ConfirmDialog'
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
import { describeError } from '../api/errors'
import { useOnboardingStore } from '../store/onboardingStore'
import { useViewportStore } from '../store/viewportStore'

const TERM_ALL = '__all__'

/** Phone browse drill-down steps: Departments → Courses → Course detail. */
type MobileStep = 'departments' | 'courses' | 'detail'

export default function AcademyPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const isPhone = useViewportStore((s) => s.tier === 'phone')

  const [university, setUniversity] = useState<University | null>(null)
  const [departments, setDepartments] = useState<Department[]>([])
  const [terms, setTerms] = useState<Term[]>([])
  const [currentTerm, setCurrentTerm] = useState<Term | null>(null)
  const [selectedDeptId, setSelectedDeptId] = useState<string | null>(null)
  const [selectedTermId, setSelectedTermId] = useState<string>(TERM_ALL)
  const [courses, setCourses] = useState<Course[]>([])
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null)
  // Phone-only: which of the three browse panes is on screen (the drill-down).
  const [mobileStep, setMobileStep] = useState<MobileStep>('departments')
  const [course, setCourse] = useState<Course | null>(null)
  const [offerings, setOfferings] = useState<Offering[]>([])

  const [myEnrollments, setMyEnrollments] = useState<Enrollment[]>([])
  const [enrollBusy, setEnrollBusy] = useState(false)
  /** enrollment id awaiting unenroll confirmation, or null. */
  const [pendingUnenroll, setPendingUnenroll] = useState<string | null>(null)

  const [loadingShell, setLoadingShell] = useState(true)
  const [loadingCourses, setLoadingCourses] = useState(false)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

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
    setActionError(null)
    try {
      await enrollInCourse(courseId)
      await refreshEnrollments()
      // Refresh onboarding so the "enroll" arrival callout flips to done.
      void useOnboardingStore.getState().refresh()
    } catch (e) {
      setActionError(describeError(e, t, {
        ENROLLMENT_NO_CURRENT_OFFERING: 'academy.error.noCurrentOffering',
        CURRENT_TERM_NOT_FOUND: 'academy.error.noCurrentTerm',
        USER_AFFILIATION_REQUIRED: 'academy.error.affiliationRequired',
      }, 'academy.error.enrollGeneric'))
    } finally {
      setEnrollBusy(false)
    }
  }

  async function performUnenroll(enrollmentId: string) {
    setEnrollBusy(true)
    setActionError(null)
    try {
      await unenrollApi(enrollmentId)
      await refreshEnrollments()
    } catch (e) {
      setActionError(describeError(e, t, {}, 'academy.error.unenrollGeneric'))
    } finally {
      setEnrollBusy(false)
      setPendingUnenroll(null)
    }
  }

  return (
    <>
      <PageShell
        title={t('academy.title')}
        subtitle={t('academy.subtitle')}
        titleAfter={university && (
          <span className="ms-1 text-xs font-medium px-2 py-0.5 rounded-md bg-surface-muted text-secondary border border-line">
            {university.shortCode}
          </span>
        )}
        actions={
          <>
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
          </>
        }
      >
        {error && (
          <p className="mb-3 text-sm text-danger">{error}</p>
        )}

        {actionError && (
          <div className="mb-4 px-4 py-2 text-xs bg-warning-soft text-warning border border-warning-soft rounded-xl flex items-center gap-3">
            <span className="flex-1">{actionError}</span>
            <button onClick={() => setActionError(null)} className="underline shrink-0">
              {t('common.close')}
            </button>
          </div>
        )}

        <MyCoursesSection
          enrollments={myEnrollments}
          currentTerm={currentTerm}
          onOpenCourse={(id) => navigate(`/courses/${id}`)}
          onUnenroll={(id) => setPendingUnenroll(id)}
          busy={enrollBusy}
        />

        <section className="mt-6">
          <SectionLabel className="mb-2">
            {t('academy.browseHeading')}
          </SectionLabel>
          {(() => {
            const departmentsBody = loadingShell ? (
              <PaneSkeleton />
            ) : departments.length === 0 ? (
              <PaneEmpty label={t('academy.empty.departments')} />
            ) : (
              <ul className="flex flex-col gap-1">
                {departments.map((d) => (
                  <li key={d.id}>
                    <RowButton
                      selected={selectedDeptId === d.id}
                      onClick={() => { setSelectedDeptId(d.id); setMobileStep('courses') }}
                    >
                      <div className="font-medium truncate">{d.name}</div>
                      <div className="text-xs text-muted truncate">{d.shortCode}</div>
                    </RowButton>
                  </li>
                ))}
              </ul>
            )

            const coursesBody = !selectedDeptId ? (
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
                      onClick={() => { setSelectedCourseId(c.id); setMobileStep('detail') }}
                    >
                      <div className="text-xs font-mono text-muted">{c.code}</div>
                      <div className="font-medium truncate">{c.name}</div>
                      {enrolledCourseIds.has(c.id) && (
                        <span className="inline-flex items-center gap-1 mt-1 text-[10px] font-bold uppercase tracking-wide px-1.5 py-0.5 rounded-md bg-bubble-green-soft text-bubble-green">
                          <span className="w-1.5 h-1.5 rounded-full bg-success" />
                          {t('academy.enrolledBadge')}
                        </span>
                      )}
                    </RowButton>
                  </li>
                ))}
              </ul>
            )

            const detailBody = !selectedCourseId ? (
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
                onUnenroll={(eid) => setPendingUnenroll(eid)}
                onOpenCourse={() => navigate(`/courses/${course.id}`)}
              />
            )

            // Phone: one pane at a time with a back chevron — no three-way squash.
            if (isPhone) {
              const selectedDept = departments.find((d) => d.id === selectedDeptId) ?? null
              return (
                <div className="h-[clamp(26rem,64vh,42rem)] min-h-0">
                  {mobileStep === 'departments' && (
                    <Pane title={t('academy.column.departments')}>{departmentsBody}</Pane>
                  )}
                  {mobileStep === 'courses' && (
                    <Pane
                      title={selectedDept?.name ?? t('academy.column.courses')}
                      onBack={() => setMobileStep('departments')}
                      backLabel={t('academy.column.departments')}
                    >
                      {coursesBody}
                    </Pane>
                  )}
                  {mobileStep === 'detail' && (
                    <Pane
                      title={course?.name ?? t('academy.column.detail')}
                      onBack={() => setMobileStep('courses')}
                      backLabel={selectedDept?.name ?? t('academy.column.courses')}
                    >
                      {detailBody}
                    </Pane>
                  )}
                </div>
              )
            }

            return (
              <div className="h-[clamp(28rem,60vh,40rem)] grid grid-cols-1 tablet:grid-cols-[16rem_minmax(0,20rem)_minmax(0,1fr)] gap-3 min-h-0">
                <Pane title={t('academy.column.departments')}>{departmentsBody}</Pane>
                <Pane title={t('academy.column.courses')}>{coursesBody}</Pane>
                <Pane title={t('academy.column.detail')}>{detailBody}</Pane>
              </div>
            )
          })()}
        </section>
      </PageShell>

      <ConfirmDialog
        open={pendingUnenroll !== null}
        title={t('academy.confirmUnenroll.title')}
        body={t('academy.confirmUnenroll.body')}
        confirmLabel={t('academy.confirmUnenroll.confirm')}
        cancelLabel={t('common.cancel')}
        busy={enrollBusy}
        onConfirm={() => pendingUnenroll && performUnenroll(pendingUnenroll)}
        onClose={() => setPendingUnenroll(null)}
      />
    </>
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
  const { t } = useTranslation()
  const scrollerRef = useRef<HTMLUListElement>(null)
  const [canPrev, setCanPrev] = useState(false)
  const [canNext, setCanNext] = useState(false)

  const updateArrows = useCallback(() => {
    const el = scrollerRef.current
    if (!el) return
    const max = el.scrollWidth - el.clientWidth
    const pos = Math.abs(el.scrollLeft)
    setCanPrev(pos > 4)
    setCanNext(pos < max - 4)
  }, [])

  useEffect(() => {
    updateArrows()
    window.addEventListener('resize', updateArrows)
    return () => window.removeEventListener('resize', updateArrows)
  }, [updateArrows, enrollments.length])

  function scrollByDir(dir: 1 | -1) {
    const el = scrollerRef.current
    if (!el) return
    const rtl = getComputedStyle(el).direction === 'rtl'
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
    el.scrollBy({
      left: el.clientWidth * 0.8 * dir * (rtl ? -1 : 1),
      behavior: reduce ? 'auto' : 'smooth',
    })
  }

  const showArrows = canPrev || canNext

  return (
    <section>
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-baseline gap-2 min-w-0">
          <h2 className="text-base font-bold text-base">{t('academy.myCourses.title')}</h2>
          {currentTerm && (
            <span className="text-xs text-muted truncate">· {currentTerm.name} {currentTerm.academicYear}</span>
          )}
        </div>
        {showArrows && (
          <div className="hidden tablet:flex items-center gap-1.5 shrink-0">
            <IconButton
              size="sm"
              variant="secondary"
              onClick={() => scrollByDir(-1)}
              disabled={!canPrev}
              aria-label={t('academy.myCourses.scrollPrev')}
            >
              <ChevronIcon className="w-4 h-4 rtl:rotate-180" />
            </IconButton>
            <IconButton
              size="sm"
              variant="secondary"
              onClick={() => scrollByDir(1)}
              disabled={!canNext}
              aria-label={t('academy.myCourses.scrollNext')}
            >
              <ChevronIcon className="w-4 h-4 rotate-180 rtl:rotate-0" />
            </IconButton>
          </div>
        )}
      </div>
      {enrollments.length === 0 ? (
        <p className="text-sm text-muted">{t('academy.myCourses.empty')}</p>
      ) : (
        <ul
          ref={scrollerRef}
          onScroll={updateArrows}
          className="flex gap-3 overflow-x-auto snap-x snap-mandatory no-scrollbar -mx-2 px-2 -my-2 py-3"
        >
          {enrollments.map((e) => (
            <li key={e.id} className="snap-start shrink-0 w-[15rem] tablet:w-64">
              <Card size="md" interactive className="p-4 flex flex-col h-full">
                <div className="flex items-start gap-3">
                  <CourseGlyph id={e.courseId ?? e.id} label={e.courseName ?? e.courseCode ?? ''} />
                  <button
                    type="button"
                    onClick={() => e.courseId && onOpenCourse(e.courseId)}
                    disabled={!e.courseId}
                    className="text-start flex-1 min-w-0"
                  >
                    <div className="text-xs font-mono text-muted">{e.courseCode ?? '—'}</div>
                    <div className="font-semibold text-base leading-snug line-clamp-2">{e.courseName ?? '—'}</div>
                    {e.termCode && (
                      <span className="inline-block mt-1.5 text-[10px] font-medium px-1.5 py-0.5 rounded-md bg-surface-muted text-secondary border border-line">
                        {e.termCode}
                      </span>
                    )}
                  </button>
                  <IconButton
                    size="sm"
                    onClick={() => onUnenroll(e.id)}
                    disabled={busy}
                    aria-label={t('academy.myCourses.unenrollAria')}
                    className="text-muted hover:text-danger shrink-0"
                  >
                    <CloseIcon className="w-4 h-4" />
                  </IconButton>
                </div>
                <div className="mt-3 flex justify-end">
                  <Button
                    size="xs"
                    onClick={() => e.courseId && onOpenCourse(e.courseId)}
                    disabled={!e.courseId}
                  >
                    {t('academy.myCourses.openCourse')}
                  </Button>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/**
 * A course "tile": brand-gradient square (stable per course id) stamped with a
 * subject glyph instead of a meaningless code initial ("3" for course 372…).
 * The glyph is matched from the course name when we recognise the subject, and
 * otherwise falls back to a stable pick so each course keeps the same face.
 */
type GlyphIcon = React.ComponentType<{ className?: string }>

const SUBJECT_GLYPHS: Array<[RegExp, GlyphIcon]> = [
  [/math|calcul|algebra|linear|geometr|statist|probab|discrete/i, SigmaIcon],
  [/program|comput|software|algorithm|data struct|coding|cyber|web|network/i, CodeIcon],
  [/physic|mechanic|quantum|electro|thermo|optic/i, AtomIcon],
  [/chem|organic|molecul|reaction/i, BeakerIcon],
  [/bio|genet|ecolog|life scien|botan|zoolog|medic|anatom/i, LeafIcon],
  [/econ|financ|account|market|business|manage|entrepre/i, TrendIcon],
  [/law|legal|justice|constitut|ethic/i, ScaleIcon],
  [/art|design|paint|sculpt|visual|music|architect/i, PaletteIcon],
  [/histor|philosoph|literat|languag|linguist|writ|cultur/i, BookIcon],
  [/geograph|geolog|environ|climat|earth|astronom/i, GlobeIcon],
  [/psych|cognit|neuro|social|educat/i, BulbIcon],
]

const FALLBACK_GLYPHS: GlyphIcon[] = [CapIcon, AtomIcon, BookIcon, SigmaIcon, GlobeIcon, BulbIcon]

function glyphFor(label: string, id: string): GlyphIcon {
  const text = label.toLowerCase()
  for (const [re, Icon] of SUBJECT_GLYPHS) if (re.test(text)) return Icon
  let h = 0
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) | 0
  return FALLBACK_GLYPHS[Math.abs(h) % FALLBACK_GLYPHS.length]
}

function CourseGlyph({ id, label }: { id: string; label: string }) {
  const Icon = glyphFor(label, id)
  return (
    <div
      className={`w-12 h-12 rounded-2xl ${gradientFor(id)} ring-on-brand flex items-center justify-center text-white shrink-0 shadow-sm`}
      aria-hidden="true"
    >
      <Icon className="w-6 h-6" />
    </div>
  )
}

function Pane({
  title,
  children,
  onBack,
  backLabel,
}: {
  title: string
  children: React.ReactNode
  /** Phone drill-down: when set, the header shows a back chevron. */
  onBack?: () => void
  backLabel?: string
}) {
  return (
    <Card size="lg" className="flex flex-col min-h-0 h-full">
      <div className="px-4 py-3 border-b border-line shrink-0 flex items-center gap-2">
        {onBack && (
          <button
            type="button"
            onClick={onBack}
            aria-label={backLabel}
            className="shrink-0 -ms-1.5 grid place-items-center w-7 h-7 rounded-full text-primary-600 hover:bg-surface-muted transition bubble-pop"
          >
            <ChevronIcon className="w-4 h-4 rtl:rotate-180" />
          </button>
        )}
        <h2 className="text-sm font-semibold text-secondary truncate">{title}</h2>
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
  if (selected) {
    return (
      <div className="ring-iridescent p-[1.5px] rounded-xl shadow-themed">
        <button
          type="button"
          onClick={onClick}
          className="w-full text-start px-3 py-2 bg-surface rounded-[calc(0.9rem-2px)] text-base"
        >
          {children}
        </button>
      </div>
    )
  }
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full text-start px-3 py-2 rounded-xl border border-transparent bubble-pop transition hover:bg-surface-muted"
    >
      {children}
    </button>
  )
}

function PaneEmpty({ label }: { label: string }) {
  return (
    <div className="h-full flex flex-col items-center justify-center gap-2.5 text-sm text-muted px-6 text-center">
      <span className="w-10 h-10 rounded-2xl bg-surface-muted text-muted flex items-center justify-center">
        <CapIcon className="w-5 h-5" />
      </span>
      <span className="max-w-[24ch] leading-relaxed">{label}</span>
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
            <Button size="sm" onClick={onOpenCourse}>
              {t('academy.detail.openCourse')}
            </Button>
            <Button
              variant="danger"
              size="sm"
              onClick={() => enrollment && onUnenroll(enrollment.id)}
              disabled={enrollBusy}
            >
              {t('academy.detail.unenroll')}
            </Button>
          </>
        ) : hasCurrentOffering ? (
          <Button variant="deep" size="sm" onClick={onEnroll} disabled={enrollBusy}>
            {enrollBusy
              ? t('academy.detail.enrolling')
              : currentTerm
              ? t('academy.detail.enrollForTerm', { code: currentTerm.code })
              : t('academy.detail.enroll')}
          </Button>
        ) : (
          <span className="text-xs text-muted px-3 py-1.5 rounded-full border border-line bg-surface-muted">
            {t('academy.detail.notOffered')}
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
