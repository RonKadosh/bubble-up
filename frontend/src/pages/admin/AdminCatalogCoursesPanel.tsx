import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Course,
  CourseDetail,
  createCourse,
  createOffering,
  Department,
  deleteCourse,
  deleteOffering,
  getCourseDetail,
  linkCourseDepartment,
  listDepartments,
  listTerms,
  searchCourses,
  Term,
  unlinkCourseDepartment,
  University,
  updateCourse,
} from '../../api/admin'
import { errorBody } from '../../api/errors'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import AdminModal from './components/AdminModal'
import AdminTable, { Column } from './components/AdminTable'
import ReasonPromptModal from './components/ReasonPromptModal'

export default function AdminCatalogCoursesPanel({ uni }: { uni: University }) {
  const { t } = useTranslation()
  const [items, setItems] = useState<Course[]>([])
  const [q, setQ] = useState('')
  const [opening, setOpening] = useState<Course | null>(null)
  const [creating, setCreating] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      const res = await searchCourses({ universityId: uni.id, q: q || undefined, size: 100 })
      setItems(res.content)
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.courses.errorLoad'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uni.id])

  const cols: Column<Course>[] = useMemo(
    () => [
      { header: t('admin.catalog.courses.columns.code'), cell: (course) => <span className="font-mono text-xs">{course.code}</span>, width: '100px' },
      {
        header: t('admin.catalog.courses.columns.name'),
        cell: (course) => (
          <div className="min-w-0">
            <p className="font-medium text-base truncate">{course.name}</p>
            {course.description && <p className="text-xs text-muted truncate">{course.description}</p>}
          </div>
        ),
      },
      {
        header: t('admin.catalog.courses.columns.credits'),
        cell: (course) => course.creditPoints ?? '-',
        width: '90px',
      },
    ],
    [t]
  )

  return (
    <div className="flex flex-col gap-3">
      <Card size="lg" className="p-4">
        <div className="flex flex-col tablet:flex-row tablet:items-end gap-3">
          <label className="flex flex-col gap-1 flex-1 min-w-0">
            <span className="text-xs font-semibold text-muted">{t('admin.catalog.courses.searchLabel')}</span>
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && load()}
              placeholder={t('admin.catalog.courses.searchPlaceholder')}
              className="rounded-full border border-line bg-base px-3 py-2 text-sm focus-bubble"
            />
          </label>
          <Button type="button" variant="secondary" size="sm" onClick={load}>
            {t('admin.catalog.courses.search')}
          </Button>
          <Button type="button" variant="ghost" size="sm" className="admin-action-button" onClick={() => setCreating(true)}>
            {t('admin.catalog.courses.add')}
          </Button>
        </div>
      </Card>

      {err && <p className="text-danger text-sm">{err}</p>}

      <AdminTable
        columns={cols}
        rows={items}
        keyOf={(course) => course.id}
        onRowClick={(course) => setOpening(course)}
        empty={t('admin.catalog.courses.empty')}
      />

      {opening && (
        <CourseDetailModal
          uni={uni}
          courseId={opening.id}
          onClose={() => setOpening(null)}
          onChanged={() => load()}
        />
      )}
      {creating && (
        <CreateCourseModal
          uni={uni}
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            load()
          }}
        />
      )}
    </div>
  )
}

function CreateCourseModal({
  uni,
  onClose,
  onCreated,
}: {
  uni: University
  onClose: () => void
  onCreated: () => void
}) {
  const { t } = useTranslation()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [credits, setCredits] = useState('')
  const [description, setDescription] = useState('')
  const [err, setErr] = useState<string | null>(null)

  async function submit() {
    setErr(null)
    try {
      await createCourse({
        universityId: uni.id,
        code,
        name,
        creditPoints: credits ? parseFloat(credits) : undefined,
        description: description || undefined,
      })
      onCreated()
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.courses.errorCreate'))
    }
  }

  return (
    <AdminModal
      title={t('admin.catalog.courses.add')}
      onClose={onClose}
      size="md"
      footer={<ModalActions onCancel={onClose} onSubmit={submit} submitLabel={t('common.create')} />}
    >
      <div className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
        <Labelled label={t('admin.catalog.courses.fields.code')}>
          <input value={code} onChange={(e) => setCode(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label={t('admin.catalog.courses.fields.credits')}>
          <input
            type="number"
            step="0.1"
            value={credits}
            onChange={(e) => setCredits(e.target.value)}
            className={inputCls}
          />
        </Labelled>
      </div>
      <Labelled label={t('admin.catalog.courses.fields.name')}>
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label={t('admin.catalog.courses.fields.description')}>
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className={inputCls} />
      </Labelled>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
  )
}

function CourseDetailModal({
  uni,
  courseId,
  onClose,
  onChanged,
}: {
  uni: University
  courseId: string
  onClose: () => void
  onChanged: () => void
}) {
  const { t } = useTranslation()
  const [detail, setDetail] = useState<CourseDetail | null>(null)
  const [departments, setDepartments] = useState<Department[]>([])
  const [terms, setTerms] = useState<Term[]>([])
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      const [courseDetail, depts, termList] = await Promise.all([
        getCourseDetail(courseId),
        listDepartments(uni.id),
        listTerms(uni.id),
      ])
      setDetail(courseDetail)
      setDepartments(depts)
      setTerms(termList)
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.courses.errorDetail'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId])

  if (!detail) {
    return (
      <AdminModal title={t('admin.catalog.courses.course')} onClose={onClose}>
        {err ? <p className="text-danger">{err}</p> : <p className="text-sm text-muted">{t('common.loading')}</p>}
      </AdminModal>
    )
  }

  const course = detail.course
  return (
    <AdminModal
      title={`${course.code} - ${course.name}`}
      onClose={onClose}
      size="lg"
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="danger" size="sm" onClick={() => setDeleting(true)}>
            {t('admin.catalog.courses.deleteCourse')}
          </Button>
          <Button type="button" variant="secondary" size="sm" onClick={() => setEditing(true)}>
            {t('common.edit')}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={onClose} className="admin-action-button">
            {t('common.close')}
          </Button>
        </div>
      }
    >
      <p className="text-sm text-muted mb-4 whitespace-pre-wrap">
        {course.description || t('admin.catalog.courses.noDescription')}
      </p>

      <Card size="md" className="p-4 mb-4">
        <h3 className="text-sm font-semibold text-base mb-3">{t('admin.catalog.courses.linkedDepartments')}</h3>
        <DepartmentLinks
          courseId={course.id}
          links={detail.departmentLinks}
          departments={departments}
          onChanged={() => load()}
        />
      </Card>

      <Card size="md" className="p-4">
        <h3 className="text-sm font-semibold text-base mb-3">{t('admin.catalog.courses.offerings')}</h3>
        <OfferingsPanel
          courseId={course.id}
          offerings={detail.offerings}
          terms={terms}
          onChanged={() => load()}
        />
      </Card>

      {editing && (
        <EditCourseModal
          course={course}
          onClose={() => setEditing(false)}
          onSaved={() => {
            setEditing(false)
            load()
            onChanged()
          }}
        />
      )}
      {deleting && (
        <ReasonPromptModal
          title={t('admin.catalog.courses.deleteTitle', { code: course.code })}
          description={t('admin.catalog.courses.deleteDescription')}
          destructive
          confirmLabel={t('admin.catalog.courses.deleteCourse')}
          onCancel={() => setDeleting(false)}
          onConfirm={async (reason) => {
            await deleteCourse(course.id, reason)
            setDeleting(false)
            onClose()
            onChanged()
          }}
        />
      )}
    </AdminModal>
  )
}

function EditCourseModal({
  course,
  onClose,
  onSaved,
}: {
  course: Course
  onClose: () => void
  onSaved: () => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState(course.name)
  const [description, setDescription] = useState(course.description ?? '')
  const [credits, setCredits] = useState(course.creditPoints?.toString() ?? '')
  const [err, setErr] = useState<string | null>(null)

  async function submit() {
    setErr(null)
    try {
      await updateCourse(course.id, {
        name,
        description,
        creditPoints: credits ? parseFloat(credits) : undefined,
      })
      onSaved()
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.courses.errorUpdate'))
    }
  }

  return (
    <AdminModal
      title={t('admin.catalog.courses.edit')}
      onClose={onClose}
      size="md"
      footer={<ModalActions onCancel={onClose} onSubmit={submit} submitLabel={t('common.save')} />}
    >
      <Labelled label={t('admin.catalog.courses.fields.name')}>
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label={t('admin.catalog.courses.fields.credits')}>
        <input
          type="number"
          step="0.1"
          value={credits}
          onChange={(e) => setCredits(e.target.value)}
          className={inputCls}
        />
      </Labelled>
      <Labelled label={t('admin.catalog.courses.fields.description')}>
        <textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={3} className={inputCls} />
      </Labelled>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
  )
}

function DepartmentLinks({
  courseId,
  links,
  departments,
  onChanged,
}: {
  courseId: string
  links: { departmentId: string; primary: boolean }[]
  departments: Department[]
  onChanged: () => void
}) {
  const { t } = useTranslation()
  const [pickDept, setPickDept] = useState<string>('')
  const [pickPrimary, setPickPrimary] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function add() {
    setErr(null)
    if (!pickDept) return
    try {
      await linkCourseDepartment(courseId, pickDept, pickPrimary)
      setPickDept('')
      setPickPrimary(false)
      onChanged()
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.courses.errorLinkDepartment'))
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-wrap gap-2">
        {links.map((link) => {
          const department = departments.find((item) => item.id === link.departmentId)
          return (
            <li
              key={link.departmentId}
              className="flex items-center gap-2 rounded-full border border-line bg-base px-3 py-1 text-xs"
            >
              <span>{department ? `${department.shortCode} - ${department.name}` : link.departmentId}</span>
              {link.primary && <span className="font-semibold text-bubble-green">{t('admin.catalog.courses.primary')}</span>}
              <button
                type="button"
                onClick={async () => {
                  await unlinkCourseDepartment(courseId, link.departmentId)
                  onChanged()
                }}
                className="text-danger hover:underline"
                aria-label={t('admin.catalog.courses.unlink')}
              >
                x
              </button>
            </li>
          )
        })}
        {links.length === 0 && <li className="text-xs text-muted">{t('admin.catalog.courses.noDepartmentsLinked')}</li>}
      </ul>

      <div className="flex flex-wrap gap-2 items-center">
        <select value={pickDept} onChange={(e) => setPickDept(e.target.value)} className={`${inputCls} w-auto min-w-48`}>
          <option value="">{t('admin.catalog.courses.pickDepartment')}</option>
          {departments
            .filter((department) => !links.some((link) => link.departmentId === department.id))
            .map((department) => (
              <option key={department.id} value={department.id}>{department.shortCode} - {department.name}</option>
            ))}
        </select>
        <label className="flex items-center gap-2 text-xs text-muted">
          <input type="checkbox" checked={pickPrimary} onChange={(e) => setPickPrimary(e.target.checked)} />
          {t('admin.catalog.courses.primary')}
        </label>
        <Button type="button" variant="ghost" size="xs" className="admin-action-button" onClick={add}>
          {t('common.add')}
        </Button>
      </div>
      {err && <p className="text-xs text-danger">{err}</p>}
    </div>
  )
}

function OfferingsPanel({
  courseId,
  offerings,
  terms,
  onChanged,
}: {
  courseId: string
  offerings: { id: string; termId: string }[]
  terms: Term[]
  onChanged: () => void
}) {
  const { t } = useTranslation()
  const [pickTerm, setPickTerm] = useState<string>('')
  const [deleting, setDeleting] = useState<string | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function add() {
    if (!pickTerm) return
    setErr(null)
    try {
      await createOffering(courseId, pickTerm)
      setPickTerm('')
      onChanged()
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.courses.errorCreateOffering'))
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <ul className="flex flex-col gap-2">
        {offerings.map((offering) => {
          const term = terms.find((item) => item.id === offering.termId)
          return (
            <li
              key={offering.id}
              className="flex items-center justify-between gap-3 rounded-2xl border border-line bg-base px-3 py-2 text-sm"
            >
              <span>{term ? `${term.code} - ${term.name}` : offering.termId}</span>
              <Button type="button" variant="danger" size="xs" onClick={() => setDeleting(offering.id)}>
                {t('common.delete')}
              </Button>
            </li>
          )
        })}
        {offerings.length === 0 && <li className="text-xs text-muted">{t('admin.catalog.courses.noOfferings')}</li>}
      </ul>

      <div className="flex flex-wrap gap-2 items-center">
        <select value={pickTerm} onChange={(e) => setPickTerm(e.target.value)} className={`${inputCls} w-auto min-w-48`}>
          <option value="">{t('admin.catalog.courses.pickTerm')}</option>
          {terms
            .filter((term) => !offerings.some((offering) => offering.termId === term.id))
            .map((term) => (
              <option key={term.id} value={term.id}>{term.code} - {term.name}</option>
            ))}
        </select>
        <Button type="button" variant="ghost" size="xs" className="admin-action-button" onClick={add}>
          {t('admin.catalog.courses.addOffering')}
        </Button>
      </div>
      {err && <p className="text-xs text-danger">{err}</p>}
      {deleting && (
        <ReasonPromptModal
          title={t('admin.catalog.courses.deleteOfferingTitle')}
          description={t('admin.catalog.courses.deleteOfferingDescription')}
          destructive
          confirmLabel={t('common.delete')}
          onCancel={() => setDeleting(null)}
          onConfirm={async (reason) => {
            await deleteOffering(deleting, reason)
            setDeleting(null)
            onChanged()
          }}
        />
      )}
    </div>
  )
}

function ModalActions({
  onCancel,
  onSubmit,
  submitLabel,
}: {
  onCancel: () => void
  onSubmit: () => void
  submitLabel: string
}) {
  const { t } = useTranslation()
  return (
    <div className="flex justify-end gap-2">
      <Button type="button" variant="ghost" size="sm" onClick={onCancel}>
        {t('common.cancel')}
      </Button>
      <Button type="button" variant="ghost" size="sm" className="admin-action-button" onClick={onSubmit}>
        {submitLabel}
      </Button>
    </div>
  )
}

function Labelled({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block mb-3">
      <span className="text-xs font-semibold text-muted">{label}</span>
      <div className="mt-1">{children}</div>
    </label>
  )
}

const inputCls = 'w-full rounded-2xl border border-line bg-base px-3 py-2 text-sm focus-bubble'
