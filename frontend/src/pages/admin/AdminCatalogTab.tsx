import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  createDepartment,
  createTerm,
  archiveTermGroups,
  createUniversity,
  deleteDepartment,
  deleteTerm,
  deleteUniversity,
  Department,
  listDepartments,
  listTerms,
  listUniversities,
  rolloverTerm,
  TermKind,
  University,
} from '../../api/admin'
import { errorBody } from '../../api/errors'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import AdminCatalogCoursesPanel from './AdminCatalogCoursesPanel'
import AdminModal from './components/AdminModal'
import ReasonPromptModal from './components/ReasonPromptModal'

type SubTab = 'departments' | 'terms' | 'courses'

export default function AdminCatalogTab() {
  const { t } = useTranslation()
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
      setError(null)
    } catch (e) {
      setError(errorBody(e)?.message ?? t('admin.catalog.errorUniversities'))
    }
  }

  useEffect(() => {
    loadUnis()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="flex flex-col tablet:flex-row gap-4">
      <Card size="lg" className="tablet:w-72 shrink-0 p-3">
        <div className="flex items-center justify-between gap-3 mb-3">
          <div>
            <h3 className="text-sm font-semibold text-base">{t('admin.catalog.universities')}</h3>
            <p className="text-xs text-muted">{t('admin.catalog.universityCount', { count: unis.length })}</p>
          </div>
          <Button type="button" variant="ghost" size="xs" className="admin-action-button" onClick={() => setCreatingUni(true)}>
            {t('admin.catalog.addUniversity')}
          </Button>
        </div>

        <ul className="flex flex-col gap-1">
          {unis.map((uni) => (
            <li key={uni.id}>
              <button
                type="button"
                onClick={() => setSelectedUni(uni)}
                className={`w-full text-start px-3 py-2 rounded-2xl text-sm transition ${
                  selectedUni?.id === uni.id
                    ? 'bg-bubble-magenta-soft text-base'
                    : 'hover:bg-surface-hover text-base'
                }`}
              >
                <div className="font-semibold">{uni.shortCode}</div>
                <div className="text-xs text-muted truncate">{uni.name}</div>
              </button>
            </li>
          ))}
          {unis.length === 0 && <li className="text-sm text-muted p-3">{t('admin.catalog.emptyUniversities')}</li>}
        </ul>

        {selectedUni && (
          <Button
            type="button"
            variant="danger"
            size="xs"
            className="mt-3 w-full"
            onClick={() => setDeletingUni(selectedUni)}
          >
            {t('admin.catalog.deleteSelectedUniversity')}
          </Button>
        )}
        {error && <p className="mt-3 text-xs text-danger">{error}</p>}
      </Card>

      <section className="flex-1 min-w-0">
        {selectedUni ? (
          <div className="flex flex-col gap-4">
            <Card size="lg" className="p-4">
              <div className="flex flex-col desktop:flex-row desktop:items-center desktop:justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="text-lg font-semibold text-base truncate">{selectedUni.name}</h2>
                  <p className="text-xs text-muted">
                    {selectedUni.shortCode} · {selectedUni.country}
                  </p>
                </div>
                <nav className="flex flex-wrap gap-1">
                  {(['departments', 'terms', 'courses'] as SubTab[]).map((item) => (
                    <button
                      key={item}
                      type="button"
                      onClick={() => setSubTab(item)}
                      className={`px-3 py-1.5 rounded-full text-sm font-medium ${
                        subTab === item
                          ? 'bg-bubble-magenta-soft text-bubble-magenta'
                          : 'text-muted hover:bg-surface-hover'
                      }`}
                    >
                      {t(`admin.catalog.tabs.${item}`)}
                    </button>
                  ))}
                </nav>
              </div>
            </Card>

            {subTab === 'departments' && <DepartmentsPanel uni={selectedUni} />}
            {subTab === 'terms' && <TermsPanel uni={selectedUni} />}
            {subTab === 'courses' && <AdminCatalogCoursesPanel uni={selectedUni} />}
          </div>
        ) : (
          <Card size="lg" className="p-8 text-center text-muted">
            {t('admin.catalog.selectUniversity')}
          </Card>
        )}
      </section>

      {creatingUni && (
        <CreateUniversityModal
          onClose={() => setCreatingUni(false)}
          onCreated={(uni) => {
            setUnis((prev) => [...prev, uni])
            setSelectedUni(uni)
            setCreatingUni(false)
          }}
        />
      )}
      {deletingUni && (
        <ReasonPromptModal
          title={t('admin.catalog.deleteUniversityTitle', { name: deletingUni.name })}
          description={t('admin.catalog.deleteUniversityDescription')}
          destructive
          confirmLabel={t('common.delete')}
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
  onCreated: (uni: University) => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [shortCode, setShortCode] = useState('')
  const [country, setCountry] = useState('US')
  const [err, setErr] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit() {
    setSubmitting(true)
    setErr(null)
    try {
      const uni = await createUniversity({ name, shortCode, country })
      onCreated(uni)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.errorCreateUniversity'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AdminModal
      title={t('admin.catalog.addUniversity')}
      onClose={onClose}
      size="sm"
      footer={<ModalActions onCancel={onClose} onSubmit={submit} submitting={submitting} submitLabel={t('common.create')} />}
    >
      <Labelled label={t('admin.catalog.fields.name')}>
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label={t('admin.catalog.fields.shortCode')}>
        <input value={shortCode} onChange={(e) => setShortCode(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label={t('admin.catalog.fields.country')}>
        <input
          value={country}
          onChange={(e) => setCountry(e.target.value.toUpperCase().slice(0, 2))}
          className={inputCls}
        />
      </Labelled>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
  )
}

function DepartmentsPanel({ uni }: { uni: University }) {
  const { t } = useTranslation()
  const [items, setItems] = useState<Department[]>([])
  const [showCreate, setShowCreate] = useState(false)
  const [deleting, setDeleting] = useState<Department | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      setItems(await listDepartments(uni.id))
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.errorDepartments'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uni.id])

  return (
    <div className="flex flex-col gap-3">
      <div className="flex justify-end">
        <Button type="button" variant="ghost" size="sm" className="admin-action-button" onClick={() => setShowCreate(true)}>
          {t('admin.catalog.addDepartment')}
        </Button>
      </div>
      {err && <p className="text-danger text-sm">{err}</p>}
      <ul className="grid grid-cols-1 tablet:grid-cols-2 gap-2">
        {items.map((department) => (
          <li key={department.id}>
            <Card size="md" className="p-3 flex justify-between items-center gap-3">
              <div className="min-w-0">
                <div className="font-medium text-base truncate">{department.name}</div>
                <div className="text-xs text-muted">{department.shortCode}</div>
              </div>
              <Button type="button" variant="danger" size="xs" onClick={() => setDeleting(department)}>
                {t('common.delete')}
              </Button>
            </Card>
          </li>
        ))}
        {items.length === 0 && (
          <li className="col-span-full">
            <Card size="lg" className="p-6 text-center text-muted">{t('admin.catalog.emptyDepartments')}</Card>
          </li>
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
          title={t('admin.catalog.deleteDepartmentTitle', { name: deleting.name })}
          description={t('admin.catalog.deleteDepartmentDescription')}
          destructive
          confirmLabel={t('common.delete')}
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
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [shortCode, setShortCode] = useState('')
  const [err, setErr] = useState<string | null>(null)

  async function submit() {
    setErr(null)
    try {
      await createDepartment(uni.id, { name, shortCode })
      onCreated()
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.errorCreateDepartment'))
    }
  }

  return (
    <AdminModal
      title={t('admin.catalog.addDepartment')}
      onClose={onClose}
      size="sm"
      footer={<ModalActions onCancel={onClose} onSubmit={submit} submitLabel={t('common.create')} />}
    >
      <Labelled label={t('admin.catalog.fields.name')}>
        <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
      </Labelled>
      <Labelled label={t('admin.catalog.fields.shortCode')}>
        <input value={shortCode} onChange={(e) => setShortCode(e.target.value)} className={inputCls} />
      </Labelled>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
  )
}

function TermsPanel({ uni }: { uni: University }) {
  const { t, i18n } = useTranslation()
  const [items, setItems] = useState<Awaited<ReturnType<typeof listTerms>>>([])
  const [showCreate, setShowCreate] = useState(false)
  const [deleting, setDeleting] = useState<(typeof items)[number] | null>(null)
  const [archiving, setArchiving] = useState<(typeof items)[number] | null>(null)
  const [rollingOver, setRollingOver] = useState<(typeof items)[number] | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      setItems(await listTerms(uni.id))
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.errorTerms'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [uni.id])

  return (
    <div className="flex flex-col gap-3">
      <div className="flex justify-end">
        <Button type="button" variant="ghost" size="sm" className="admin-action-button" onClick={() => setShowCreate(true)}>
          {t('admin.catalog.addTerm')}
        </Button>
      </div>
      {err && <p className="text-danger text-sm">{err}</p>}
      <ul className="flex flex-col gap-2">
        {items.map((term) => (
          <li key={term.id}>
            <Card size="md" className="p-3 flex justify-between items-center gap-3">
              <div className="min-w-0">
                <div className="font-medium text-base truncate">
                  {term.name} <span className="text-xs text-muted">({term.code})</span>
                </div>
                <div className="text-xs text-muted">
                  {t(`admin.catalog.termKinds.${term.kind}`)} {term.academicYear} ·{' '}
                  {new Date(term.startsOn).toLocaleDateString(i18n.language)} - {new Date(term.endsOn).toLocaleDateString(i18n.language)}
                </div>
              </div>
              <div className="shrink-0 flex flex-wrap justify-end gap-2">
                <Button type="button" variant="ghost" size="xs" className="admin-action-button" onClick={() => setRollingOver(term)}>
                  {t('admin.catalog.startNextTerm')}
                </Button>
                <Button type="button" variant="secondary" size="xs" onClick={() => setArchiving(term)}>
                  {t('admin.catalog.archiveTermGroups')}
                </Button>
                <Button type="button" variant="danger" size="xs" onClick={() => setDeleting(term)}>
                  {t('common.delete')}
                </Button>
              </div>
            </Card>
          </li>
        ))}
        {items.length === 0 && (
          <li>
            <Card size="lg" className="p-6 text-center text-muted">{t('admin.catalog.emptyTerms')}</Card>
          </li>
        )}
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
          title={t('admin.catalog.deleteTermTitle', { name: deleting.name })}
          description={t('admin.catalog.deleteTermDescription')}
          destructive
          confirmLabel={t('common.delete')}
          onCancel={() => setDeleting(null)}
          onConfirm={async (reason) => {
            await deleteTerm(deleting.id, reason)
            setDeleting(null)
            load()
          }}
        />
      )}
      {archiving && (
        <ReasonPromptModal
          title={t('admin.catalog.archiveTermGroupsTitle', { name: archiving.name })}
          description={t('admin.catalog.archiveTermGroupsDescription')}
          confirmLabel={t('admin.catalog.archiveTermGroups')}
          onCancel={() => setArchiving(null)}
          onConfirm={async (reason) => {
            const res = await archiveTermGroups(archiving.id, reason)
            setArchiving(null)
            setErr(t('admin.catalog.archiveTermGroupsResult', { count: res.affectedGroups }))
            load()
          }}
        />
      )}
      {rollingOver && (
        <RolloverTermModal
          source={rollingOver}
          onClose={() => setRollingOver(null)}
          onDone={(result) => {
            setRollingOver(null)
            setErr(t('admin.catalog.rolloverResult', {
              offerings: result.copiedOfferings,
              groups: result.archivedGroups,
            }))
            load()
          }}
        />
      )}
    </div>
  )
}

function RolloverTermModal({
  source,
  onClose,
  onDone,
}: {
  source: Awaited<ReturnType<typeof listTerms>>[number]
  onClose: () => void
  onDone: (result: Awaited<ReturnType<typeof rolloverTerm>>) => void
}) {
  const { t } = useTranslation()
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [kind, setKind] = useState<TermKind>(source.kind)
  const [year, setYear] = useState(source.academicYear)
  const [startsOn, setStartsOn] = useState('')
  const [endsOn, setEndsOn] = useState('')
  const [reason, setReason] = useState('')
  const [err, setErr] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function submit() {
    if (!reason.trim()) {
      setErr(t('admin.reason.required'))
      return
    }
    setSubmitting(true)
    setErr(null)
    try {
      const result = await rolloverTerm(source.id, {
        code,
        name,
        kind,
        academicYear: year,
        startsOn,
        endsOn,
        reason: reason.trim(),
      })
      onDone(result)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.catalog.errorRollover'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AdminModal
      title={t('admin.catalog.rolloverTitle', { name: source.name })}
      onClose={onClose}
      size="md"
      footer={<ModalActions onCancel={onClose} onSubmit={submit} submitting={submitting} submitLabel={t('admin.catalog.startNextTerm')} />}
    >
      <p className="text-sm text-muted mb-4">{t('admin.catalog.rolloverDescription')}</p>
      <div className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
        <Labelled label={t('admin.catalog.fields.termCode')}>
          <input value={code} onChange={(e) => setCode(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label={t('admin.catalog.fields.name')}>
          <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label={t('admin.catalog.fields.kind')}>
          <select value={kind} onChange={(e) => setKind(e.target.value as TermKind)} className={inputCls}>
            {(['FALL', 'SPRING', 'SUMMER', 'WINTER', 'OTHER'] as TermKind[]).map((item) => (
              <option key={item} value={item}>{t(`admin.catalog.termKinds.${item}`)}</option>
            ))}
          </select>
        </Labelled>
        <Labelled label={t('admin.catalog.fields.academicYear')}>
          <input type="number" value={year} onChange={(e) => setYear(parseInt(e.target.value || '0', 10))} className={inputCls} />
        </Labelled>
        <Labelled label={t('admin.catalog.fields.startsOn')}>
          <input type="date" value={startsOn} onChange={(e) => setStartsOn(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label={t('admin.catalog.fields.endsOn')}>
          <input type="date" value={endsOn} onChange={(e) => setEndsOn(e.target.value)} className={inputCls} />
        </Labelled>
      </div>
      <Labelled label={t('admin.reason.label')}>
        <textarea value={reason} onChange={(e) => setReason(e.target.value)} rows={3} className={`${inputCls} rounded-2xl`} />
      </Labelled>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
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
  const { t } = useTranslation()
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
      setErr(errorBody(e)?.message ?? t('admin.catalog.errorCreateTerm'))
    }
  }

  return (
    <AdminModal
      title={t('admin.catalog.addTerm')}
      onClose={onClose}
      size="md"
      footer={<ModalActions onCancel={onClose} onSubmit={submit} submitLabel={t('common.create')} />}
    >
      <div className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
        <Labelled label={t('admin.catalog.fields.termCode')}>
          <input value={code} onChange={(e) => setCode(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label={t('admin.catalog.fields.name')}>
          <input value={name} onChange={(e) => setName(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label={t('admin.catalog.fields.kind')}>
          <select value={kind} onChange={(e) => setKind(e.target.value as TermKind)} className={inputCls}>
            {(['FALL', 'SPRING', 'SUMMER', 'WINTER', 'OTHER'] as TermKind[]).map((item) => (
              <option key={item} value={item}>{t(`admin.catalog.termKinds.${item}`)}</option>
            ))}
          </select>
        </Labelled>
        <Labelled label={t('admin.catalog.fields.academicYear')}>
          <input
            type="number"
            value={year}
            onChange={(e) => setYear(parseInt(e.target.value || '0', 10))}
            className={inputCls}
          />
        </Labelled>
        <Labelled label={t('admin.catalog.fields.startsOn')}>
          <input type="date" value={startsOn} onChange={(e) => setStartsOn(e.target.value)} className={inputCls} />
        </Labelled>
        <Labelled label={t('admin.catalog.fields.endsOn')}>
          <input type="date" value={endsOn} onChange={(e) => setEndsOn(e.target.value)} className={inputCls} />
        </Labelled>
      </div>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
  )
}

function ModalActions({
  onCancel,
  onSubmit,
  submitLabel,
  submitting,
}: {
  onCancel: () => void
  onSubmit: () => void
  submitLabel: string
  submitting?: boolean
}) {
  const { t } = useTranslation()
  return (
    <div className="flex justify-end gap-2">
      <Button type="button" variant="ghost" size="sm" onClick={onCancel} disabled={submitting}>
        {t('common.cancel')}
      </Button>
      <Button type="button" variant="ghost" size="sm" className="admin-action-button" onClick={onSubmit} disabled={submitting}>
        {submitting ? t('admin.reason.submitting') : submitLabel}
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
