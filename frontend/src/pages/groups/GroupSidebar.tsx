import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Group, Visibility } from '../../api/groups'
import {
  Course,
  Department,
  University,
  getCoursesByDepartment,
  getDepartments,
  getUniversities,
} from '../../api/catalog'
import { Avatar } from '../../components/Avatar'
import { Button } from '../../components/Button'

interface CreateGroupInput {
  name: string
  description?: string
  visibility: Visibility
  maxMembers: number
  courseId: string
}

const MIN_MAX_MEMBERS = 4
const MAX_MAX_MEMBERS = 10
const DEFAULT_MAX_MEMBERS = 6

interface GroupSidebarProps {
  groups: Group[]
  selectedId: string | null
  meId: string | null
  /** Per-group unread count for badge rendering. */
  unreadByGroup: Record<string, number>
  /** Group IDs that are live right now (Bubble Room or expert session) — red marker. */
  liveGroupIds: Set<string>
  onSelect: (id: string) => void
  onCreate: (input: CreateGroupInput) => Promise<void>
  /** Drawer-open state below `desktop`. Ignored at desktop+ where the sidebar is always inline. */
  mobileOpen: boolean
  onMobileClose: () => void
  /**
   * Deep-link from elsewhere (dashboard / course page) to open the create form,
   * optionally pre-targeted to a course. Applied once on arrival; the user can
   * still change the selection afterwards. {@code prefillDeptId} is needed for
   * the dept→course cascade to surface {@code prefillCourseId}.
   */
  initialCreate?: { open: boolean; deptId?: string; courseId?: string } | null
  onInitialCreateConsumed?: () => void
}

/**
 * Left rail of the GroupsPage hub: collapsible create-form + list of "bubbles".
 * Owns its own create-form state (`showCreate`, `newName`, …) — pure view state
 * with no reason to live in the parent.
 *
 * Below `desktop` (1200px) this renders as a slide-over drawer anchored to the
 * inline-start edge with a backdrop. At desktop+ it's a normal inline aside.
 */
export function GroupSidebar({ groups, selectedId, meId, unreadByGroup, liveGroupIds, onSelect, onCreate, mobileOpen, onMobileClose, initialCreate, onInitialCreateConsumed }: GroupSidebarProps) {
  const { t } = useTranslation()
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDescription, setNewDescription] = useState('')
  const [newVisibility, setNewVisibility] = useState<Visibility>('PUBLIC')
  const [newMaxMembers, setNewMaxMembers] = useState(DEFAULT_MAX_MEMBERS)
  const [universities, setUniversities] = useState<University[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [selectedDeptId, setSelectedDeptId] = useState<string>('')
  const [selectedCourseId, setSelectedCourseId] = useState<string>('')
  const [catalogError, setCatalogError] = useState('')
  // Course to auto-select once the dept's course list arrives (deep-link prefill).
  const [pendingCourseId, setPendingCourseId] = useState<string>('')

  // Deep-link: open the create form pre-targeted to a course, then tell the
  // parent to clear the navigation state so a refresh/back doesn't re-trigger it.
  useEffect(() => {
    if (!initialCreate?.open) return
    setShowCreate(true)
    if (initialCreate.deptId) setSelectedDeptId(initialCreate.deptId)
    if (initialCreate.courseId) setPendingCourseId(initialCreate.courseId)
    onInitialCreateConsumed?.()
  }, [initialCreate, onInitialCreateConsumed])

  // Once the cascade has loaded the courses for the prefilled dept, select the
  // target course (if it's actually in that department's list).
  useEffect(() => {
    if (!pendingCourseId) return
    if (courses.some((c) => c.id === pendingCourseId)) {
      setSelectedCourseId(pendingCourseId)
      setPendingCourseId('')
    }
  }, [courses, pendingCourseId])

  // Load universities + departments the first time the form opens. v1 only has
  // BGU, so we auto-select the first university and load its departments.
  useEffect(() => {
    if (!showCreate || universities.length > 0) return
    getUniversities()
      .then(async (us) => {
        setUniversities(us)
        if (us.length === 0) return
        const depts = await getDepartments(us[0].id)
        setDepartments(depts)
      })
      .catch(() => setCatalogError(t('groups.error.loadCatalog')))
  }, [showCreate, universities.length, t])

  // Cascade: when the user picks a department, refresh the course list.
  useEffect(() => {
    if (!selectedDeptId) {
      setCourses([])
      setSelectedCourseId('')
      return
    }
    getCoursesByDepartment(selectedDeptId)
      .then(setCourses)
      .catch(() => setCatalogError(t('groups.error.loadCatalog')))
  }, [selectedDeptId, t])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!selectedCourseId) return
    await onCreate({
      name: newName,
      description: newDescription || undefined,
      visibility: newVisibility,
      maxMembers: newMaxMembers,
      courseId: selectedCourseId,
    })
    setNewName('')
    setNewDescription('')
    setNewVisibility('PUBLIC')
    setNewMaxMembers(DEFAULT_MAX_MEMBERS)
    setSelectedDeptId('')
    setSelectedCourseId('')
    setShowCreate(false)
  }

  return (
    <>
      {/* Backdrop — only painted below `desktop` while the drawer is open. */}
      <button
        type="button"
        aria-label={t('common.close')}
        onClick={onMobileClose}
        className={`fixed inset-0 z-30 bg-black/30 desktop:hidden transition-opacity duration-200 ${
          mobileOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'
        }`}
      />

      <aside
        className={`
          flex flex-col bg-surface border-e border-line
          desktop:static desktop:z-auto desktop:w-80 desktop:translate-x-0 desktop:shadow-none
          fixed inset-y-0 start-0 z-40 w-[18rem] max-w-[85vw] shadow-bubble
          transition-transform duration-200 ease-out
          ${mobileOpen ? 'translate-x-0' : '-translate-x-full rtl:translate-x-full desktop:translate-x-0 desktop:rtl:translate-x-0'}
        `}
      >
      <div className="p-4 border-b border-line flex items-center justify-between gap-2">
        <h2 className="font-semibold text-base">{t('groups.sidebarTitle')}</h2>
        <div className="flex items-center gap-1">
          <Button
            variant={showCreate ? 'ghost' : 'primary'}
            size={showCreate ? 'xs' : 'sm'}
            onClick={() => setShowCreate((v) => !v)}
          >
            {showCreate ? t('common.cancel') : t('groups.newBubble')}
          </Button>
          <button
            type="button"
            onClick={onMobileClose}
            aria-label={t('common.close')}
            className="desktop:hidden w-8 h-8 rounded-full text-muted hover:text-base hover:bg-surface-hover transition flex items-center justify-center text-lg leading-none"
          >
            ×
          </button>
        </div>
      </div>

      {showCreate && (
        <form onSubmit={handleSubmit} className="p-3 border-b border-line flex flex-col gap-2 bg-surface-muted">
          <input
            placeholder={t('groups.createForm.bubbleName')}
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
            required
          />
          <input
            placeholder={t('groups.createForm.descriptionOptional')}
            value={newDescription}
            onChange={(e) => setNewDescription(e.target.value)}
            className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
          />
          {universities.length > 0 && (
            <div className="text-xs text-muted px-1">
              {t('groups.createForm.universityLabel')}: <span className="font-semibold">{universities[0].name}</span>
            </div>
          )}
          <select
            value={selectedDeptId}
            onChange={(e) => setSelectedDeptId(e.target.value)}
            className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
            required
          >
            <option value="">{t('groups.createForm.selectDepartment')}</option>
            {departments.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </select>
          <select
            value={selectedCourseId}
            onChange={(e) => setSelectedCourseId(e.target.value)}
            disabled={!selectedDeptId}
            className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400 disabled:opacity-50"
            required
          >
            <option value="">{t('groups.createForm.selectCourse')}</option>
            {courses.map((c) => (
              <option key={c.id} value={c.id}>
                {c.code} — {c.name}
              </option>
            ))}
          </select>
          {catalogError && <p className="text-xs text-danger">{catalogError}</p>}
          <div className="flex gap-3 text-xs">
            <label className="flex items-center gap-1">
              <input
                type="radio"
                checked={newVisibility === 'PUBLIC'}
                onChange={() => setNewVisibility('PUBLIC')}
              />
              {t('groups.createForm.public')}
            </label>
            <label className="flex items-center gap-1">
              <input
                type="radio"
                checked={newVisibility === 'PRIVATE'}
                onChange={() => setNewVisibility('PRIVATE')}
              />
              {t('groups.createForm.private')}
            </label>
          </div>
          <div className="flex items-center justify-between gap-2">
            <label className="text-xs text-muted">{t('groups.createForm.maxGroupSize')}</label>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                variant="secondary"
                size="xs"
                aria-label={t('groups.createForm.maxGroupSizeDecrease')}
                disabled={newMaxMembers <= MIN_MAX_MEMBERS}
                onClick={() => setNewMaxMembers((n) => Math.max(MIN_MAX_MEMBERS, n - 1))}
              >
                −
              </Button>
              <span className="w-6 text-center text-sm font-semibold tabular-nums">{newMaxMembers}</span>
              <Button
                type="button"
                variant="secondary"
                size="xs"
                aria-label={t('groups.createForm.maxGroupSizeIncrease')}
                disabled={newMaxMembers >= MAX_MAX_MEMBERS}
                onClick={() => setNewMaxMembers((n) => Math.min(MAX_MAX_MEMBERS, n + 1))}
              >
                +
              </Button>
            </div>
          </div>
          <Button type="submit" size="sm" className="mt-1 w-full" disabled={!selectedCourseId}>
            {t('groups.createForm.submit')}
          </Button>
        </form>
      )}

      <div className="flex-1 overflow-y-auto">
        {groups.length === 0 && (
          <p className="p-4 text-sm text-muted">
            {t('groups.emptyList')} <span className="font-semibold">{t('groups.newBubble')}</span>.
          </p>
        )}
        {groups.map((g) => {
          const active = g.id === selectedId
          const youAreOwner = meId === g.ownerId
          const unread = unreadByGroup[g.id] ?? 0
          const isLive = liveGroupIds.has(g.id)
          const hasUnread = unread > 0 && !active

          const content = (
            <>
              <div className={`relative ${isLive ? 'avatar-live-red' : hasUnread ? 'avatar-live' : ''}`}>
                <Avatar id={g.id} name={g.name} size="md" />
                {isLive && (
                  <span className="absolute -top-0.5 -end-0.5 flex h-3 w-3" aria-label={t('groups.liveNow')} title={t('groups.liveNow')}>
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-500 opacity-75" />
                    <span className="relative inline-flex rounded-full h-3 w-3 bg-rose-500 ring-2 ring-surface" />
                  </span>
                )}
              </div>
              <div className="flex-1 min-w-0">
                <p className="font-semibold truncate">{g.name}</p>
                <p className={`text-xs truncate ${active ? 'text-secondary' : 'text-muted'}`}>
                  {t('groups.memberLabel', { count: g.memberCount })}
                  {' · '}
                  {youAreOwner
                    ? t('groups.ownerBadge')
                    : g.visibility === 'PRIVATE'
                      ? t('groups.privateBadge')
                      : t('groups.publicBadge')}
                </p>
              </div>
              {unread > 0 && !active && (
                <span className="bg-brand-gradient-strong text-on-brand text-xs font-bold rounded-full min-w-[1.5rem] h-6 px-2 flex items-center justify-center shadow-sm">
                  {unread > 99 ? '99+' : unread}
                </span>
              )}
            </>
          )

          if (active) {
            return (
              <div key={g.id} className="ring-iridescent p-[1.5px] rounded-[2rem] mx-2 my-1 shadow-themed">
                <button
                  onClick={() => onSelect(g.id)}
                  className="w-full flex items-center gap-3 px-3 py-2.5 text-start bg-surface rounded-[calc(2rem-2px)] text-base"
                >
                  {content}
                </button>
              </div>
            )
          }

          return (
            <button
              key={g.id}
              onClick={() => onSelect(g.id)}
              className="w-full flex items-center gap-3 px-3 py-2.5 my-1 mx-2 text-start rounded-2xl transition-all hover:bg-surface-muted text-base bubble-pop"
            >
              {content}
            </button>
          )
        })}
      </div>
    </aside>
    </>
  )
}
