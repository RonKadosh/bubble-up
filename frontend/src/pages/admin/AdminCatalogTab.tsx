import { useEffect, useState } from 'react'
import {
  createDepartment,
  createTerm,
  createUniversity,
  deleteDepartment,
  deleteTerm,
  deleteUniversity,
  Department,
  listDepartments,
  listTerms,
  listUniversities,
  TermKind,
  University,
} from '../../api/admin'
import AdminCatalogCoursesPanel from './AdminCatalogCoursesPanel'
import AdminModal from './components/AdminModal'
import ReasonPromptModal from './components/ReasonPromptModal'

type SubTab = 'departments' | 'terms' | 'courses'

export default function AdminCatalogTab() {
  const [unis, setUnis] = useState<University[]>([])
  const [selectedUni, setSelectedUni] = useState<University | null>(null)
  const [subTab, setSubTab] = useState<SubTab>('departments')
  const [creatingUni, setCreatingUni] = useState(false)
  const [deletingUni, setDeletingUni] = useState<University | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function loadUnis() {
    try {
      const list = await listUniversities()
      setUnis(list)
      if (!selectedUni && list.length > 0) setSelectedUni(list[0])
    } catch (e) {
      setError(String(e))
    }
  }

  useEffect(() => {
    loadUnis()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="flex flex-col tablet:flex-row gap-4">
      <aside className="tablet:w-64 shrink-0 rounded-2xl border border-line bg-surface p-3">
        <div className="flex items-center justify-between mb-2">
          <h3 className="text-sm font-semibold">Universities</h3>
          <button
            type="button"
            onClick={() => setCreatingUni(true)}
            className="text-xs rounded-full px-2 py-1 bg-indigo-600 text-on-brand"
          >
            + Add
          </button>
        </div>
        <ul className="flex flex-col gap-1">
          {unis.map((u) => (
            <li key={u.id}>
              <button
                type="button"
                onClick={() => setSelectedUni(u)}
                className={`w-full text-start px-3 py-2 rounded-xl text-sm transition ${
                  selectedUni?.id === u.id
                    ? 'bg-indigo-100 text-indigo-800'
                    : 'hover:bg-surface-hover'
                }`}
              >
                <div className="font-medium">{u.shortCode}</div>
                <div className="text-xs text-secondary">{u.name}</div>
              </button>
            </li>
          ))}
          {unis.length === 0 && <li className="text-sm text-secondary">No universities yet.</li>}
        </ul>
        {selectedUni && (
          <button
            type="button"
            onClick={() => setDeletingUni(selectedUni)}
            className="mt-3 w-full text-xs text-red-600 hover:underline"
          >
            Delete selected university…
          </button>
        )}
        {error && <p className="mt-3 text-xs text-red-600">{error}</p>}
      </aside>

      <section className="flex-1 min-w-0">
        {selectedUni ? (
          <>
            <div className="mb-3 flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold">{selectedUni.name}</h2>
                <p className="text-xs text-secondary">
                  {selectedUni.shortCode} · {selectedUni.country}
                </p>
              </div>
              <nav className="flex gap-1">
                {(['departments', 'terms', 'courses'] as SubTab[]).map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setSubTab(s)}
                    className={`px-3 py-1.5 rounded-full text-sm ${
                      subTab === s ? 'bg-indigo-600 text-on-brand' : 'border border-line text-base'
                    }`}
                  >
                    {s[0].toUpperCase() + s.slice(1)}
                  </button>
                ))}
              </nav>
            </div>
            {subTab === 'departments' && <DepartmentsPanel uni={selectedUni} />}
            {subTab === 'terms' && <TermsPanel uni={selectedUni} />}
            {subTab === 'courses' && <AdminCatalogCoursesPanel uni={selectedUni} />}
          </>
        ) : (
          <div className="p-6 rounded-2xl border border-line bg-surface text-center text-secondary">
            Select or add a university to begin.
          </div>
        )}
      </section>

      {creatingUni && (
        <CreateUniversityModal
          onClose={() => setCreatingUni(false)}
          onCreated={(u) => {
            setUnis((prev) => [...prev, u])
            setSelectedUni(u)
            setCreatingUni(false)
          }}
        />
      )}
      {deletingUni && (
        <ReasonPromptModal
          title={`Delete ${deletingUni.name}`}
          description="Rejected if any departments, terms, or courses still exist under it."
          destructive
          confirmLabel="Delete"
          onCancel={() => setDeletingUni(null)}
          onConfirm={async (reason) => {
            await deleteUniversity(deletingUni.id, reason)
            setDeletingUni(null)
            setSelectedUni(null)
            await loadUnis()
          }}
        />
      )}
    </div>
  )
}

function CreateUniversityModal({
  onClose,
  onCreated,
}: {
  onClose: () => void
  onCreated: (u: University) => void
}) {
  const [name, setName] = useState('')
  const [shortCode, setShortCode] = useState('')
  const [country, setCountry] = useState('US')
  const [err, setErr] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  async function submit() {
    setSubmitting(true)
    setErr(null)
    try {
      const u = await createUniversity({ name, shortCode, country })
      onCreated(u)
    } catch (e) {
      setErr(extractError(e))
    } finally {
      setSubmitting(false)
    }
  }
  return (
    <AdminModal
      title="Add university"
      onClose={onClose}
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 rounded-full border border-line">
            Cancel
          </button>
          <button
            onClick={submit}
            disabled={submitting}
            className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand disabled:opacity-60"
          >
            Create
          </button>
        </div>
      }
    >
      <Labelled label="Name">
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label="Short code (max 16)">
        <input value={shortCode} onChange={(e) => setShortCode(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label="Country (ISO-2)">
        <input
          value={country}
          onChange={(e) => setCountry(e.target.value.toUpperCase().slice(0, 2))}
          className={inputCls}
        />
      </Labelled>
      {err && <p className="mt-2 text-sm text-red-600">{err}</p>}
    </AdminModal>
  )
}

function DepartmentsPanel({ uni }: { uni: University }) {
  const [items, setItems] = useState<Department[]>([])
  const [showCreate, setShowCreate] = useState(false)
  const [deleting, setDeleting] = useState<Department | null>(null)
  const [err, setErr] = useState<string | null>(null)
  async function load() {
    try {
      setItems(await listDepartments(uni.id))
    } catch (e) {
      setErr(String(e))
    }
  }
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uni.id])
  return (
    <div className="flex flex-col gap-3">
      <div className="flex justify-end">
        <button
          onClick={() => setShowCreate(true)}
          className="px-3 py-1.5 rounded-full bg-indigo-600 text-on-brand text-sm"
        >
          + Add department
        </button>
      </div>
      {err && <p className="text-red-600 text-sm">{err}</p>}
      <ul className="grid grid-cols-1 tablet:grid-cols-2 gap-2">
        {items.map((d) => (
          <li
            key={d.id}
            className="rounded-2xl border border-line bg-surface p-3 flex justify-between items-center"
          >
            <div>
              <div className="font-medium">{d.name}</div>
              <div className="text-xs text-secondary">{d.shortCode}</div>
            </div>
            <button
              onClick={() => setDeleting(d)}
              className="text-xs text-red-600 hover:underline"
            >
              Delete
            </button>
          </li>
        ))}
        {items.length === 0 && (
          <li className="text-sm text-secondary col-span-full">No departments yet.</li>
        )}
      </ul>
      {showCreate && (
        <CreateDepartmentModal
          uni={uni}
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false)
            load()
          }}
        />
      )}
      {deleting && (
        <ReasonPromptModal
          title={`Delete ${deleting.name}`}
          description="Rejected if courses are linked to this department."
          destructive
          confirmLabel="Delete"
          onCancel={() => setDeleting(null)}
          onConfirm={async (reason) => {
            await deleteDepartment(deleting.id, reason)
            setDeleting(null)
            load()
          }}
        />
      )}
    </div>
  )
}

function CreateDepartmentModal({
  uni,
  onClose,
  onCreated,
}: {
  uni: University
  onClose: () => void
  onCreated: () => void
}) {
  const [name, setName] = useState('')
  const [shortCode, setShortCode] = useState('')
  const [err, setErr] = useState<string | null>(null)
  async function submit() {
    setErr(null)
    try {
      await createDepartment(uni.id, { name, shortCode })
      onCreated()
    } catch (e) {
      setErr(extractError(e))
    }
  }
  return (
    <AdminModal
      title="Add department"
      onClose={onClose}
      size="sm"
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
      <Labelled label="Name">
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label="Short code">
        <input value={shortCode} onChange={(e) => setShortCode(e.target.value)} className={inputCls} />
      </Labelled>
      {err && <p className="mt-2 text-sm text-red-600">{err}</p>}
    </AdminModal>
  )
}

function TermsPanel({ uni }: { uni: University }) {
  const [items, setItems] = useState<Awaited<ReturnType<typeof listTerms>>>([])
  const [showCreate, setShowCreate] = useState(false)
  const [deleting, setDeleting] = useState<(typeof items)[number] | null>(null)
  const [err, setErr] = useState<string | null>(null)
  async function load() {
    try {
      setItems(await listTerms(uni.id))
    } catch (e) {
      setErr(String(e))
    }
  }
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uni.id])
  return (
    <div className="flex flex-col gap-3">
      <div className="flex justify-end">
        <button
          onClick={() => setShowCreate(true)}
          className="px-3 py-1.5 rounded-full bg-indigo-600 text-on-brand text-sm"
        >
          + Add term
        </button>
      </div>
      {err && <p className="text-red-600 text-sm">{err}</p>}
      <ul className="flex flex-col gap-2">
        {items.map((t) => (
          <li
            key={t.id}
            className="rounded-2xl border border-line bg-surface p-3 flex justify-between items-center"
          >
            <div>
              <div className="font-medium">{t.name} <span className="text-xs text-secondary">({t.code})</span></div>
              <div className="text-xs text-secondary">
                {t.kind} {t.academicYear} · {t.startsOn} → {t.endsOn}
              </div>
            </div>
            <button
              onClick={() => setDeleting(t)}
              className="text-xs text-red-600 hover:underline"
            >
              Delete
            </button>
          </li>
        ))}
        {items.length === 0 && <li className="text-sm text-secondary">No terms yet.</li>}
      </ul>
      {showCreate && (
        <CreateTermModal
          uni={uni}
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false)
            load()
          }}
        />
      )}
      {deleting && (
        <ReasonPromptModal
          title={`Delete ${deleting.name}`}
          description="Rejected if any offerings reference this term."
          destructive
          confirmLabel="Delete"
          onCancel={() => setDeleting(null)}
          onConfirm={async (reason) => {
            await deleteTerm(deleting.id, reason)
            setDeleting(null)
            load()
          }}
        />
      )}
    </div>
  )
}

function CreateTermModal({
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
  const [kind, setKind] = useState<TermKind>('FALL')
  const [year, setYear] = useState(new Date().getFullYear())
  const [startsOn, setStartsOn] = useState('')
  const [endsOn, setEndsOn] = useState('')
  const [err, setErr] = useState<string | null>(null)
  async function submit() {
    setErr(null)
    try {
      await createTerm(uni.id, { code, name, kind, academicYear: year, startsOn, endsOn })
      onCreated()
    } catch (e) {
      setErr(extractError(e))
    }
  }
  return (
    <AdminModal
      title="Add term"
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
        <Labelled label="Code (e.g. FA26)">
          <input value={code} onChange={(e) => setCode(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label="Name">
          <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label="Kind">
          <select value={kind} onChange={(e) => setKind(e.target.value as TermKind)} className={inputCls}>
            {(['FALL', 'SPRING', 'SUMMER', 'WINTER', 'OTHER'] as TermKind[]).map((k) => (
              <option key={k} value={k}>{k}</option>
            ))}
          </select>
        </Labelled>
        <Labelled label="Academic year">
          <input
            type="number"
            value={year}
            onChange={(e) => setYear(parseInt(e.target.value || '0', 10))}
            className={inputCls}
          />
        </Labelled>
        <Labelled label="Starts on">
          <input type="date" value={startsOn} onChange={(e) => setStartsOn(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label="Ends on">
          <input type="date" value={endsOn} onChange={(e) => setEndsOn(e.target.value)} className={inputCls} />
        </Labelled>
      </div>
      {err && <p className="mt-2 text-sm text-red-600">{err}</p>}
    </AdminModal>
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
