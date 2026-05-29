import { useEffect, useState } from 'react'
import {
  AdminGroup,
  AdminGroupDetail,
  deleteGroup,
  getGroupDetail,
  PageResponse,
  searchGroups,
} from '../../api/admin'
import AdminModal from './components/AdminModal'
import AdminTable, { Column } from './components/AdminTable'
import ReasonPromptModal from './components/ReasonPromptModal'

export default function AdminGroupsTab() {
  const [q, setQ] = useState('')
  const [page, setPage] = useState<PageResponse<AdminGroup> | null>(null)
  const [pageIndex, setPageIndex] = useState(0)
  const [opening, setOpening] = useState<AdminGroup | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      setPage(await searchGroups({ q: q || undefined, page: pageIndex, size: 20 }))
    } catch (e) {
      setErr(String(e))
    }
  }
  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex])

  const cols: Column<AdminGroup>[] = [
    { header: 'Name', cell: (g) => g.name },
    { header: 'Visibility', cell: (g) => g.visibility },
    { header: 'Members', cell: (g) => g.memberCount, width: '100px' },
    {
      header: 'Created',
      cell: (g) => <span className="text-xs text-secondary">{new Date(g.createdAt).toLocaleDateString()}</span>,
    },
  ]

  return (
    <div className="flex flex-col gap-3">
      <div className="flex gap-2 items-end">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && (setPageIndex(0), load())}
          placeholder="search by name"
          className="rounded-full border border-line bg-base px-3 py-1.5 text-sm"
        />
        <button onClick={() => { setPageIndex(0); load() }} className="px-3 py-1.5 rounded-full bg-indigo-600 text-on-brand text-sm">
          Search
        </button>
      </div>
      {err && <p className="text-red-600 text-sm">{err}</p>}
      <AdminTable
        columns={cols}
        rows={page?.content ?? []}
        keyOf={(g) => g.id}
        onRowClick={(g) => setOpening(g)}
        empty="No groups."
      />
      {page && (
        <div className="flex justify-between text-sm text-secondary">
          <span>{page.totalElements} total</span>
          <div className="flex gap-2">
            <button
              disabled={page.first}
              onClick={() => setPageIndex((p) => Math.max(0, p - 1))}
              className="px-3 py-1 rounded-full border border-line disabled:opacity-40"
            >
              ‹ Prev
            </button>
            <button
              disabled={page.last}
              onClick={() => setPageIndex((p) => p + 1)}
              className="px-3 py-1 rounded-full border border-line disabled:opacity-40"
            >
              Next ›
            </button>
          </div>
        </div>
      )}
      {opening && (
        <GroupDetailModal
          group={opening}
          onClose={() => setOpening(null)}
          onDeleted={() => {
            setOpening(null)
            load()
          }}
        />
      )}
    </div>
  )
}

function GroupDetailModal({
  group,
  onClose,
  onDeleted,
}: {
  group: AdminGroup
  onClose: () => void
  onDeleted: () => void
}) {
  const [detail, setDetail] = useState<AdminGroupDetail | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  useEffect(() => {
    getGroupDetail(group.id).then(setDetail).catch((e) => setErr(String(e)))
  }, [group.id])
  return (
    <AdminModal
      title={`Group · ${group.name}`}
      onClose={onClose}
      size="lg"
      footer={
        <div className="flex justify-end gap-2">
          <button
            onClick={() => setDeleting(true)}
            className="px-4 py-2 rounded-full border border-red-300 text-red-600"
          >
            Delete group
          </button>
          <button onClick={onClose} className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand">
            Close
          </button>
        </div>
      }
    >
      {err && <p className="text-red-600 text-sm">{err}</p>}
      {!detail ? (
        <p>Loading…</p>
      ) : (
        <>
          <dl className="grid grid-cols-2 gap-3 text-sm mb-4">
            <Field label="Visibility" value={detail.group.visibility} />
            <Field label="Members" value={String(detail.group.memberCount)} />
            <Field label="Offering" value={detail.group.offeringId} mono />
            <Field label="Course" value={detail.group.courseId} mono />
            <Field label="Created" value={new Date(detail.group.createdAt).toLocaleString()} />
            <Field label="Created by" value={detail.group.createdBy} mono />
          </dl>
          <h3 className="text-sm font-semibold mb-2">Members ({detail.members.length})</h3>
          <ul className="flex flex-col gap-1 max-h-64 overflow-y-auto">
            {detail.members.map((m) => (
              <li
                key={m.userId}
                className="flex items-center justify-between text-sm border border-line rounded-xl px-3 py-1.5"
              >
                <div>
                  <div className="font-medium">{m.displayName ?? '—'}</div>
                  <div className="text-xs text-secondary">{m.email ?? m.userId}</div>
                </div>
                <span className="text-xs rounded-full border border-line px-2 py-0.5">{m.role}</span>
              </li>
            ))}
          </ul>
        </>
      )}
      {deleting && (
        <ReasonPromptModal
          title={`Delete ${group.name}`}
          description="Cascades through members, files, calendar, chat. Cannot be undone."
          destructive
          confirmLabel="Delete"
          onCancel={() => setDeleting(false)}
          onConfirm={async (reason) => {
            await deleteGroup(group.id, reason)
            onDeleted()
          }}
        />
      )}
    </AdminModal>
  )
}

function Field({ label, value, mono }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-secondary">{label}</dt>
      <dd className={`text-sm text-base ${mono ? 'font-mono text-xs break-all' : ''}`}>{value ?? '—'}</dd>
    </div>
  )
}
