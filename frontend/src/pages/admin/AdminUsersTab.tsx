import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AdminUser,
  banUser,
  changeUserRole,
  PageResponse,
  reactivateUser,
  searchUsers,
  suspendUser,
  UserRole,
  UserStatus,
} from '../../api/admin'
import { errorBody } from '../../api/errors'
import { Avatar } from '../../components/Avatar'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import AdminTable, { Column } from './components/AdminTable'
import AdminModal from './components/AdminModal'
import ReasonPromptModal from './components/ReasonPromptModal'

const ROLES: UserRole[] = ['STUDENT', 'EXPERT', 'ADMIN']
const STATUSES: UserStatus[] = ['ACTIVE', 'SUSPENDED', 'BANNED']

export default function AdminUsersTab() {
  const { t, i18n } = useTranslation()
  const [page, setPage] = useState<PageResponse<AdminUser> | null>(null)
  const [q, setQ] = useState('')
  const [role, setRole] = useState<UserRole | ''>('')
  const [status, setStatus] = useState<UserStatus | ''>('')
  const [pageIndex, setPageIndex] = useState(0)
  const [selected, setSelected] = useState<AdminUser | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function load() {
    try {
      const res = await searchUsers({
        q: q || undefined,
        role: role || undefined,
        status: status || undefined,
        page: pageIndex,
        size: 20,
      })
      setPage(res)
      setError(null)
    } catch (e) {
      setError(errorBody(e)?.message ?? t('admin.users.errorLoad'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex])

  const dateFmt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric', year: 'numeric' }),
    [i18n.language]
  )

  const columns: Column<AdminUser>[] = useMemo(
    () => [
      {
        header: t('admin.users.columns.person'),
        cell: (user) => (
          <div className="flex items-center gap-3 min-w-0">
            <Avatar id={user.id} name={user.displayName || user.email} size="sm" ring={user.role === 'ADMIN'} />
            <div className="min-w-0">
              <p className="font-medium text-base truncate">{user.displayName}</p>
              <p className="text-xs text-muted truncate" dir="ltr">{user.email}</p>
            </div>
          </div>
        ),
      },
      {
        header: t('admin.users.columns.role'),
        cell: (user) => <RoleBadge role={user.role} />,
      },
      {
        header: t('admin.users.columns.status'),
        cell: (user) => <StatusBadge user={user} />,
      },
      {
        header: t('admin.users.columns.affiliation'),
        cell: (user) => (
          <span className="text-xs text-muted">
            {user.enrollmentYear ?? t('admin.users.noAffiliation')}
          </span>
        ),
      },
      {
        header: t('admin.users.columns.joined'),
        cell: (user) => <span className="text-xs text-muted">{dateFmt.format(new Date(user.createdAt))}</span>,
      },
    ],
    [dateFmt, t]
  )

  return (
    <div className="flex flex-col gap-4">
      <Card size="lg" className="p-4">
        <div className="flex flex-col tablet:flex-row tablet:items-end gap-3">
          <label className="flex flex-col gap-1 flex-1 min-w-0">
            <span className="text-xs font-semibold text-muted">{t('admin.users.searchLabel')}</span>
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && (setPageIndex(0), load())}
              placeholder={t('admin.users.searchPlaceholder')}
              className="rounded-full border border-line bg-base px-3 py-2 text-sm focus-bubble"
            />
          </label>
          <label className="flex flex-col gap-1 tablet:w-48">
            <span className="text-xs font-semibold text-muted">{t('admin.users.roleLabel')}</span>
            <select
              value={role}
              onChange={(e) => setRole(e.target.value as UserRole | '')}
              className="rounded-full border border-line bg-base px-3 py-2 text-sm focus-bubble"
            >
              <option value="">{t('admin.users.anyRole')}</option>
              {ROLES.map((item) => (
                <option key={item} value={item}>{t(`admin.roles.${item}`)}</option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-1 tablet:w-48">
            <span className="text-xs font-semibold text-muted">{t('admin.users.statusLabel')}</span>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as UserStatus | '')}
              className="rounded-full border border-line bg-base px-3 py-2 text-sm focus-bubble"
            >
              <option value="">{t('admin.users.anyStatus')}</option>
              {STATUSES.map((item) => (
                <option key={item} value={item}>{t(`admin.users.status.${item}`)}</option>
              ))}
            </select>
          </label>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="admin-action-button"
            onClick={() => { setPageIndex(0); load() }}
          >
            {t('admin.users.apply')}
          </Button>
        </div>
      </Card>

      {error && <p className="text-danger text-sm">{error}</p>}

      <AdminTable
        columns={columns}
        rows={page?.content ?? []}
        keyOf={(user) => user.id}
        onRowClick={(user) => setSelected(user)}
        empty={t('admin.users.empty')}
      />

      {page && (
        <div className="flex flex-col tablet:flex-row tablet:items-center tablet:justify-between gap-3 text-sm text-muted">
          <span>
            {t('admin.users.paginationSummary', {
              total: page.totalElements,
              page: page.currentPage + 1,
              pages: Math.max(1, page.totalPages),
            })}
          </span>
          <div className="flex gap-2">
            <Button
              type="button"
              variant="secondary"
              size="xs"
              disabled={page.first}
              onClick={() => setPageIndex((prev) => Math.max(0, prev - 1))}
            >
              {t('admin.users.prev')}
            </Button>
            <Button
              type="button"
              variant="secondary"
              size="xs"
              disabled={page.last}
              onClick={() => setPageIndex((prev) => prev + 1)}
            >
              {t('admin.users.next')}
            </Button>
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
  onChanged: (user: AdminUser) => void
}) {
  const { t, i18n } = useTranslation()
  const [newRole, setNewRole] = useState<UserRole>(user.role)
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [moderating, setModerating] = useState<'suspend' | 'ban' | 'reactivate' | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function submit() {
    if (newRole === user.role) {
      onClose()
      return
    }
    if (!reason.trim()) {
      setErr(t('admin.reason.required'))
      return
    }
    setSubmitting(true)
    setErr(null)
    try {
      const updated = await changeUserRole(user.id, newRole, reason.trim())
      onChanged(updated)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.users.errorRoleChange'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AdminModal
      title={t('admin.users.detailTitle', { email: user.email })}
      onClose={onClose}
      size="md"
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          {user.status === 'ACTIVE' && (
            <>
              <Button type="button" variant="secondary" size="sm" onClick={() => setModerating('suspend')} disabled={submitting}>
                {t('admin.users.actions.suspend')}
              </Button>
              <Button type="button" variant="danger" size="sm" onClick={() => setModerating('ban')} disabled={submitting}>
                {t('admin.users.actions.ban')}
              </Button>
            </>
          )}
          {user.status !== 'ACTIVE' && (
            <Button type="button" variant="secondary" size="sm" onClick={() => setModerating('reactivate')} disabled={submitting}>
              {t('admin.users.actions.reactivate')}
            </Button>
          )}
          <Button type="button" variant="ghost" size="sm" onClick={onClose} disabled={submitting}>
            {t('common.close')}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={submitting}
            onClick={submit}
            className="admin-action-button"
          >
            {submitting ? t('admin.reason.submitting') : t('common.save')}
          </Button>
        </div>
      }
    >
      <div className="flex items-center gap-3 mb-5">
        <Avatar id={user.id} name={user.displayName || user.email} size="md" ring={user.role === 'ADMIN'} />
        <div className="min-w-0">
          <p className="font-semibold text-base truncate">{user.displayName}</p>
          <p className="text-xs text-muted truncate" dir="ltr">{user.email}</p>
        </div>
      </div>

      <dl className="grid grid-cols-1 tablet:grid-cols-2 gap-3 text-sm mb-5">
        <Field label={t('admin.users.fields.role')} value={t(`admin.roles.${user.role}`)} />
        <Field label={t('admin.users.fields.status')} value={t(`admin.users.status.${user.status}`)} />
        <Field label={t('admin.users.fields.created')} value={new Date(user.createdAt).toLocaleString(i18n.language)} />
        <Field label={t('admin.users.fields.enrollmentYear')} value={user.enrollmentYear?.toString() ?? null} />
        <Field label={t('admin.users.fields.suspendedUntil')} value={user.suspendedUntil ? new Date(user.suspendedUntil).toLocaleString(i18n.language) : null} />
        <Field label={t('admin.users.fields.id')} value={user.id} mono />
      </dl>
      {user.statusReason && (
        <div className="mb-5 rounded-2xl border border-line bg-surface-muted px-4 py-3">
          <p className="text-xs text-muted">{t('admin.users.fields.statusReason')}</p>
          <p className="text-sm text-base mt-1">{user.statusReason}</p>
        </div>
      )}

      <Card size="md" className="p-4">
        <h3 className="text-sm font-semibold text-base mb-3">{t('admin.users.roleChangeTitle')}</h3>
        <label className="block mb-3">
          <span className="text-sm font-medium text-base">{t('admin.users.roleLabel')}</span>
          <select
            value={newRole}
            onChange={(e) => setNewRole(e.target.value as UserRole)}
            className="mt-1 w-full rounded-2xl border border-line bg-base px-3 py-2 text-sm focus-bubble"
          >
            {ROLES.map((item) => (
              <option key={item} value={item}>{t(`admin.roles.${item}`)}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="text-sm font-medium text-base">{t('admin.reason.label')}</span>
          <textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={3}
            placeholder={t('admin.users.reasonPlaceholder')}
            className="mt-1 w-full rounded-2xl border border-line bg-base px-3 py-2 text-sm focus-bubble"
          />
        </label>
        {err && <p className="mt-2 text-sm text-danger">{err}</p>}
      </Card>
      {moderating === 'suspend' && (
        <SuspendUserModal
          user={user}
          onCancel={() => setModerating(null)}
          onDone={(updated) => {
            setModerating(null)
            onChanged(updated)
          }}
        />
      )}
      {moderating === 'ban' && (
        <ReasonPromptModal
          title={t('admin.users.prompts.banTitle', { name: user.displayName })}
          description={t('admin.users.prompts.banDescription')}
          destructive
          confirmLabel={t('admin.users.actions.ban')}
          onCancel={() => setModerating(null)}
          onConfirm={async (reason) => {
            const updated = await banUser(user.id, reason)
            setModerating(null)
            onChanged(updated)
          }}
        />
      )}
      {moderating === 'reactivate' && (
        <ReasonPromptModal
          title={t('admin.users.prompts.reactivateTitle', { name: user.displayName })}
          description={t('admin.users.prompts.reactivateDescription')}
          confirmLabel={t('admin.users.actions.reactivate')}
          onCancel={() => setModerating(null)}
          onConfirm={async (reason) => {
            const updated = await reactivateUser(user.id, reason)
            setModerating(null)
            onChanged(updated)
          }}
        />
      )}
    </AdminModal>
  )
}

function SuspendUserModal({
  user,
  onCancel,
  onDone,
}: {
  user: AdminUser
  onCancel: () => void
  onDone: (user: AdminUser) => void
}) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  const [until, setUntil] = useState('')
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
      const updated = await suspendUser(user.id, reason.trim(), until ? new Date(until).toISOString() : undefined)
      onDone(updated)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.users.errorModerate'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AdminModal
      title={t('admin.users.prompts.suspendTitle', { name: user.displayName })}
      onClose={onCancel}
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={onCancel} disabled={submitting}>
            {t('common.cancel')}
          </Button>
          <Button type="button" variant="secondary" size="sm" onClick={submit} disabled={submitting}>
            {submitting ? t('admin.reason.submitting') : t('admin.users.actions.suspend')}
          </Button>
        </div>
      }
    >
      <label className="block mb-3">
        <span className="text-sm font-medium text-base">{t('admin.users.fields.suspendedUntil')}</span>
        <input
          type="datetime-local"
          value={until}
          onChange={(e) => setUntil(e.target.value)}
          className="mt-1 w-full rounded-2xl border border-line bg-base px-3 py-2 text-sm focus-bubble"
        />
      </label>
      <label className="block">
        <span className="text-sm font-medium text-base">{t('admin.reason.label')}</span>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          placeholder={t('admin.reason.placeholder')}
          className="mt-1 w-full rounded-2xl border border-line bg-base px-3 py-2 text-sm focus-bubble"
        />
      </label>
      {err && <p className="mt-2 text-sm text-danger">{err}</p>}
    </AdminModal>
  )
}

function RoleBadge({ role }: { role: UserRole }) {
  const { t } = useTranslation()
  const tone = role === 'ADMIN'
    ? 'bg-bubble-magenta-soft text-bubble-magenta'
    : role === 'EXPERT'
    ? 'bg-bubble-green-soft text-bubble-green'
    : 'bg-surface-muted text-secondary'
  return (
    <span className={`text-xs px-2 py-0.5 rounded-md ${tone}`}>
      {t(`admin.roles.${role}`)}
    </span>
  )
}

function StatusBadge({ user }: { user: AdminUser }) {
  const { t, i18n } = useTranslation()
  const tone = user.status === 'ACTIVE'
    ? 'bg-bubble-green-soft text-bubble-green'
    : user.status === 'SUSPENDED'
      ? 'bg-warning-soft text-warning'
      : 'bg-danger-soft text-danger'
  return (
    <span className={`text-xs px-2 py-0.5 rounded-md ${tone}`}>
      {t(`admin.users.status.${user.status}`)}
      {user.suspendedUntil && (
        <span className="ms-1 text-[10px] opacity-80">
          {new Date(user.suspendedUntil).toLocaleDateString(i18n.language)}
        </span>
      )}
    </span>
  )
}

function Field({ label, value, mono }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className={`text-sm text-base ${mono ? 'font-mono text-xs break-all' : ''}`}>{value ?? '-'}</dd>
    </div>
  )
}
