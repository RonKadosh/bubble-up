import { useEffect, useMemo, useState } from 'react'
import {
  AdminUser,
  changeUserRole,
  PageResponse,
  searchUsers,
  UserRole,
} from '../../api/admin'
import AdminTable, { Column } from './components/AdminTable'
import AdminModal from './components/AdminModal'

const ROLES: UserRole[] = ['STUDENT', 'EXPERT', 'ADMIN']

export default function AdminUsersTab() {
  const [page, setPage] = useState<PageResponse<AdminUser> | null>(null)
  const [q, setQ] = useState('')
  const [role, setRole] = useState<UserRole | ''>('')
  const [pageIndex, setPageIndex] = useState(0)
  const [selected, setSelected] = useState<AdminUser | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    try {
      const res = await searchUsers({
        q: q || undefined,
        role: role || undefined,
        page: pageIndex,
        size: 20,
      })
      setPage(res)
    } catch (e) {
      setError(String(e))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex])

  const columns: Column<AdminUser>[] = useMemo(
    () => [
      { header: 'Email', cell: (u) => <span className="font-medium">{u.email}</span> },
      { header: 'Display name', cell: (u) => u.displayName },
      {
        header: 'Role',
        cell: (u) => <span className="text-xs px-2 py-0.5 rounded-full bg-base/60 border border-line">{u.role}</span>,
      },
      {
        header: 'Joined',
        cell: (u) => <span className="text-xs text-secondary">{new Date(u.createdAt).toLocaleDateString()}</span>,
      },
    ],
    []
  )

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap gap-2 items-end">
        <label className="flex flex-col">
          <span className="text-xs text-secondary">Search</span>
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && (setPageIndex(0), load())}
            placeholder="email or display name"
            className="rounded-full border border-line bg-base px-3 py-1.5 text-sm"
          />
        </label>
        <label className="flex flex-col">
          <span className="text-xs text-secondary">Role</span>
          <select
            value={role}
            onChange={(e) => setRole(e.target.value as UserRole | '')}
            className="rounded-full border border-line bg-base px-3 py-1.5 text-sm"
          >
            <option value="">Any</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>{r}</option>
            ))}
          </select>
        </label>
        <button
          type="button"
          onClick={() => { setPageIndex(0); load() }}
          className="px-4 py-1.5 rounded-full bg-indigo-600 text-on-brand text-sm hover:bg-indigo-700"
        >
          Apply
        </button>
      </div>

      {error && <p className="text-red-600 text-sm">{error}</p>}

      <AdminTable
        columns={columns}
        rows={page?.content ?? []}
        keyOf={(u) => u.id}
        onRowClick={(u) => setSelected(u)}
        empty="No users match."
      />

      {page && (
        <div className="flex items-center justify-between text-sm text-secondary">
          <span>{page.totalElements} total · page {page.currentPage + 1}/{Math.max(1, page.totalPages)}</span>
          <div className="flex gap-2">
            <button
              type="button"
              disabled={page.first}
              onClick={() => setPageIndex((p) => Math.max(0, p - 1))}
              className="px-3 py-1 rounded-full border border-line hover:bg-surface-hover disabled:opacity-40"
            >
              ‹ Prev
            </button>
            <button
              type="button"
              disabled={page.last}
              onClick={() => setPageIndex((p) => p + 1)}
              className="px-3 py-1 rounded-full border border-line hover:bg-surface-hover disabled:opacity-40"
            >
              Next ›
            </button>
          </div>
        </div>
      )}

      {selected && (
        <UserDetailModal
          user={selected}
          onClose={() => setSelected(null)}
          onChanged={(updated) => {
            setSelected(updated)
            load()
          }}
        />
      )}
    </div>
  )
}

function UserDetailModal({
  user,
  onClose,
  onChanged,
}: {
  user: AdminUser
  onClose: () => void
  onChanged: (u: AdminUser) => void
}) {
  const [newRole, setNewRole] = useState<UserRole>(user.role)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  async function submit() {
    if (newRole === user.role) {
      onClose()
      return
    }
    if (!reason.trim()) {
      setErr('Reason is required.')
      return
    }
    setSubmitting(true)
    setErr(null)
    try {
      const updated = await changeUserRole(user.id, newRole, reason.trim())
      onChanged(updated)
    } catch (e) {
      setErr(extractError(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AdminModal
      title={`User · ${user.email}`}
      onClose={onClose}
      size="md"
      footer={
        <div className="flex justify-end gap-2">
          <button onClick={onClose} className="px-4 py-2 rounded-full border border-line">
            Close
          </button>
          <button
            disabled={submitting}
            onClick={submit}
            className="px-4 py-2 rounded-full bg-indigo-600 text-on-brand disabled:opacity-60"
          >
            {submitting ? '…' : 'Save'}
          </button>
        </div>
      }
    >
      <dl className="grid grid-cols-2 gap-3 text-sm mb-4">
        <Field label="Display name" value={user.displayName} />
        <Field label="Email" value={user.email} />
        <Field label="Created" value={new Date(user.createdAt).toLocaleString()} />
        <Field label="ID" value={user.id} mono />
      </dl>
      <label className="block mb-3">
        <span className="text-sm font-medium">Role</span>
        <select
          value={newRole}
          onChange={(e) => setNewRole(e.target.value as UserRole)}
          className="mt-1 w-full rounded-xl border border-line bg-base px-3 py-2 text-sm"
        >
          {ROLES.map((r) => (
            <option key={r} value={r}>{r}</option>
          ))}
        </select>
      </label>
      <label className="block">
        <span className="text-sm font-medium">Reason</span>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={2}
          placeholder="Required when changing role"
          className="mt-1 w-full rounded-xl border border-line bg-base px-3 py-2 text-sm"
        />
      </label>
      {err && <p className="mt-2 text-sm text-red-600">{err}</p>}
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

function extractError(e: unknown): string {
  if (typeof e === 'object' && e !== null) {
    const anyE = e as { response?: { data?: { error?: { code?: string; message?: string } } } }
    return anyE.response?.data?.error?.message ?? anyE.response?.data?.error?.code ?? 'Request failed.'
  }
  return 'Request failed.'
}
