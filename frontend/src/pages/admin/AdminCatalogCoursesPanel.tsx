import { useEffect, useState } from 'react'
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
import AdminModal from './components/AdminModal'
import AdminTable, { Column } from './components/AdminTable'
import ReasonPromptModal from './components/ReasonPromptModal'

export default function AdminCatalogCoursesPanel({ uni }: { uni: University }) {
  const [items, setItems] = useState<Course[]>([])
  const [q, setQ] = useState('')
  const [opening, setOpening] = useState<Course | null>(null)
  const [creating, setCreating] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      const res = await searchCourses({ universityId: uni.id, q: q || undefined, size: 100 })
      setItems(res.content)
    } catch (e) {
      setErr(String(e))
    }
  }
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uni.id])

  const cols: Column<Course>[] = [
    { header: 'Code', cell: (c) => <span className="font-mono text-xs">{c.code}</span>, width: '100px' },
    { header: 'Name', cell: (c) => c.name },
    { header: 'Credits', cell: (c) => c.creditPoints ?? '—', width: '80px' },
  ]

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap gap-2 items-end">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && load()}
          placeholder="search code or name"
          className="rounded-full border border-line bg-base px-3 py-1.5 text-sm"
        />
        <button onClick={load} className="px-3 py-1.5 rounded-full border border-line text-sm">
          Search
        </button>
        <div className="flex-1" />
        <button
          onClick={() => setCreating(true)}
          className="px-3 py-1.5 rounded-full bg-indigo-600 text-on-brand text-sm"
        >
          + Add course
        </button>
      </div>
      {err && <p className="text-red-600 text-sm">{err}</p>}
      <AdminTable
        columns={cols}
        rows={items}
        keyOf={(c) => c.id}
        onRowClick={(c) => setOpening(c)}
        empty="No courses for this university."
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
      setErr(extractError(e))
    }
  }
  return (
    <AdminModal
      title="Add course"
      onClose={onClose}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 rounded-full border border-line">
            Cancel
          </button>
          <button onClick={submit} className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand">
            Create
          </button>
        </div>
      }
    >
      <div className="grid grid-cols-2 gap-3">
        <Labelled label="Code">
          <input value={code} onChange={(e) => setCode(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label="Credit points">
          <input
            type="number"
            step="0.1"
            value={credits}
            onChange={(e) => setCredits(e.target.value)}
            className={inputCls}
          />
        </Labelled>
      </div>
      <Labelled label="Name">
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label="Description">
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          className={inputCls}
        />
      </Labelled>
      {err && <p className="mt-2 text-sm text-red-600">{err}</p>}
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
  const [detail, setDetail] = useState<CourseDetail | null>(null)
  const [departments, setDepartments] = useState<Department[]>([])
  const [terms, setTerms] = useState<Term[]>([])
  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      const [d, depts, ts] = await Promise.all([
        getCourseDetail(courseId),
        listDepartments(uni.id),
        listTerms(uni.id),
      ])
      setDetail(d)
      setDepartments(depts)
      setTerms(ts)
    } catch (e) {
      setErr(String(e))
    }
  }
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId])

  if (!detail) {
    return (
      <AdminModal title="Course" onClose={onClose}>
        {err ? <p className="text-red-600">{err}</p> : <p>Loading…</p>}
      </AdminModal>
    )
  }
  const c = detail.course
  return (
    <AdminModal
      title={`${c.code} — ${c.name}`}
      onClose={onClose}
      size="lg"
      footer={
        <div className="flex justify-end gap-2">
          <button
            onClick={() => setDeleting(true)}
            className="px-4 py-2 rounded-full border border-red-300 text-red-600"
          >
            Delete course
          </button>
          <button
            onClick={() => setEditing(true)}
            className="px-4 py-2 rounded-full border border-line"
          >
            Edit
          </button>
          <button onClick={onClose} className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand">
            Close
          </button>
        </div>
      }
    >
      <p className="text-sm text-secondary mb-4">{c.description || 'No description.'}</p>

      <h3 className="text-sm font-semibold mb-2">Linked departments</h3>
      <DepartmentLinks
        courseId={c.id}
        links={detail.departmentLinks}
        departments={departments}
        onChanged={() => load()}
      />

      <h3 className="text-sm font-semibold mt-5 mb-2">Offerings</h3>
      <OfferingsPanel
        courseId={c.id}
        offerings={detail.offerings}
        terms={terms}
        onChanged={() => load()}
      />

      {editing && (
        <EditCourseModal
          course={c}
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
          title={`Delete ${c.code}`}
          description="Rejected if any offering still has active groups."
          destructive
          confirmLabel="Delete course"
          onCancel={() => setDeleting(false)}
          onConfirm={async (reason) => {
            await deleteCourse(c.id, reason)
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
      setErr(extractError(e))
    }
  }
  return (
    <AdminModal
      title="Edit course"
      onClose={onClose}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 rounded-full border border-line">
            Cancel
          </button>
          <button onClick={submit} className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand">
            Save
          </button>
        </div>
      }
    >
      <Labelled label="Name">
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label="Credit points">
        <input
          type="number"
          step="0.1"
          value={credits}
          onChange={(e) => setCredits(e.target.value)}
          className={inputCls}
        />
      </Labelled>
      <Labelled label="Description">
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          rows={3}
          className={inputCls}
        />
      </Labelled>
      {err && <p className="mt-2 text-sm text-red-600">{err}</p>}
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
      setErr(extractError(e))
    }
  }
  return (
    <div className="flex flex-col gap-2">
      <ul className="flex flex-wrap gap-2">
        {links.map((l) => {
          const d = departments.find((x) => x.id === l.departmentId)
          return (
            <li
              key={l.departmentId}
              className="flex items-center gap-2 rounded-full border border-line bg-base px-3 py-1 text-xs"
            >
              <span>{d ? `${d.shortCode} — ${d.name}` : l.departmentId}</span>
              {l.primary && <span className="text-indigo-600 font-medium">★ primary</span>}
              <button
                onClick={async () => {
                  await unlinkCourseDepartment(courseId, l.departmentId)
                  onChanged()
                }}
                className="text-red-600 hover:underline"
                aria-label="Unlink"
              >
                ✕
              </button>
            </li>
          )
        })}
        {links.length === 0 && <li className="text-xs text-secondary">No departments linked.</li>}
      </ul>
      <div className="flex flex-wrap gap-2 items-center">
        <select value={pickDept} onChange={(e) => setPickDept(e.target.value)} className={inputCls + ' w-auto'}>
          <option value="">Pick department…</option>
          {departments
            .filter((d) => !links.some((l) => l.departmentId === d.id))
            .map((d) => (
              <option key={d.id} value={d.id}>{d.shortCode} — {d.name}</option>
            ))}
        </select>
        <label className="flex items-center gap-1 text-xs">
          <input type="checkbox" checked={pickPrimary} onChange={(e) => setPickPrimary(e.target.checked)} />
          Primary
        </label>
        <button onClick={add} className="px-3 py-1.5 rounded-full bg-indigo-600 text-on-brand text-xs">
          Add
        </button>
      </div>
      {err && <p className="text-xs text-red-600">{err}</p>}
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
      setErr(extractError(e))
    }
  }
  return (
    <div className="flex flex-col gap-2">
      <ul className="flex flex-col gap-1">
        {offerings.map((o) => {
          const t = terms.find((x) => x.id === o.termId)
          return (
            <li
              key={o.id}
              className="flex items-center justify-between rounded-xl border border-line bg-base px-3 py-1.5 text-sm"
            >
              <span>{t ? `${t.code} — ${t.name}` : o.termId}</span>
              <button
                onClick={() => setDeleting(o.id)}
                className="text-xs text-red-600 hover:underline"
              >
                Delete
              </button>
            </li>
          )
        })}
        {offerings.length === 0 && (
          <li className="text-xs text-secondary">No offerings yet.</li>
        )}
      </ul>
      <div className="flex flex-wrap gap-2 items-center">
        <select value={pickTerm} onChange={(e) => setPickTerm(e.target.value)} className={inputCls + ' w-auto'}>
          <option value="">Pick term…</option>
          {terms
            .filter((t) => !offerings.some((o) => o.termId === t.id))
            .map((t) => (
              <option key={t.id} value={t.id}>{t.code} — {t.name}</option>
            ))}
        </select>
        <button onClick={add} className="px-3 py-1.5 rounded-full bg-indigo-600 text-on-brand text-xs">
          Add offering
        </button>
      </div>
      {err && <p className="text-xs text-red-600">{err}</p>}
      {deleting && (
        <ReasonPromptModal
          title="Delete offering"
          description="Rejected if any groups reference this offering."
          destructive
          confirmLabel="Delete"
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

function Labelled({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block mb-3">
      <span className="text-xs text-secondary">{label}</span>
      <div className="mt-1">{children}</div>
    </label>
  )
}

const inputCls = 'w-full rounded-xl border border-line bg-base px-3 py-2 text-sm'

function extractError(e: unknown): string {
  if (typeof e === 'object' && e !== null) {
    const anyE = e as { response?: { data?: { error?: { code?: string; message?: string } } } }
    return anyE.response?.data?.error?.message ?? anyE.response?.data?.error?.code ?? 'Request failed.'
  }
  return 'Request failed.'
}
