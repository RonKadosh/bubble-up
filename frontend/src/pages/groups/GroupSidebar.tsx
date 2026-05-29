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
  courseId: string
}

interface GroupSidebarProps {
  groups: Group[]
  selectedId: string | null
  meId: string | null
  /** Per-group unread count for badge rendering. */
  unreadByGroup: Record<string, number>
  onSelect: (id: string) => void
  onCreate: (input: CreateGroupInput) => Promise<void>
  /** Drawer-open state below `desktop`. Ignored at desktop+ where the sidebar is always inline. */
  mobileOpen: boolean
  onMobileClose: () => void
}

/**
 * Left rail of the GroupsPage hub: collapsible create-form + list of "bubbles".
 * Owns its own create-form state (`showCreate`, `newName`, …) — pure view state
 * with no reason to live in the parent.
 *
 * Below `desktop` (1200px) this renders as a slide-over drawer anchored to the
 * inline-start edge with a backdrop. At desktop+ it's a normal inline aside.
 */
export function GroupSidebar({ groups, selectedId, meId, unreadByGroup, onSelect, onCreate, mobileOpen, onMobileClose }: GroupSidebarProps) {
  const { t } = useTranslation()
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDescription, setNewDescription] = useState('')
  const [newVisibility, setNewVisibility] = useState<Visibility>('PUBLIC')
  const [universities, setUniversities] = useState<University[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [courses, setCourses] = useState<Course[]>([])
  const [selectedDeptId, setSelectedDeptId] = useState<string>('')
  const [selectedCourseId, setSelectedCourseId] = useState<string>('')
  const [catalogError, setCatalogError] = useState('')

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
      courseId: selectedCourseId,
    })
    setNewName('')
    setNewDescription('')
    setNewVisibility('PUBLIC')
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
          ${mobileOpen ? 'translate-x-0' : '-translate-x-full desktop:translate-x-0'}
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
          const hasLive = unread > 0 && !active

          const content = (
            <>
              <div className={hasLive ? 'avatar-live' : ''}>
                <Avatar id={g.id} name={g.name} size="md" />
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
