import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AdminGroup,
  AdminGroupDetail,
  activateGroup,
  archiveGroup,
  deleteGroup,
  getGroupDetail,
  PageResponse,
  searchGroups,
} from '../../api/admin'
import { errorBody } from '../../api/errors'
import { Avatar } from '../../components/Avatar'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import AdminModal from './components/AdminModal'
import AdminTable, { Column } from './components/AdminTable'
import ReasonPromptModal from './components/ReasonPromptModal'

export default function AdminGroupsTab() {
  const { t, i18n } = useTranslation()
  const [q, setQ] = useState('')
  const [page, setPage] = useState<PageResponse<AdminGroup> | null>(null)
  const [pageIndex, setPageIndex] = useState(0)
  const [opening, setOpening] = useState<AdminGroup | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      const res = await searchGroups({ q: q || undefined, page: pageIndex, size: 20 })
      setPage(res)
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.groups.errorLoad'))
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

  const cols: Column<AdminGroup>[] = useMemo(
    () => [
      {
        header: t('admin.groups.columns.name'),
        cell: (group) => (
          <div className="min-w-0">
            <p className="font-medium text-base truncate">{group.name}</p>
            {group.description && <p className="text-xs text-muted truncate">{group.description}</p>}
          </div>
        ),
      },
      {
        header: t('admin.groups.columns.visibility'),
        cell: (group) => <VisibilityBadge visibility={group.visibility} />,
        width: '120px',
      },
      {
        header: t('admin.groups.columns.status'),
        cell: (group) => <StatusBadge status={group.status} />,
        width: '120px',
      },
      {
        header: t('admin.groups.columns.members'),
        cell: (group) => <span className="font-semibold text-base">{group.memberCount}</span>,
        width: '100px',
      },
      {
        header: t('admin.groups.columns.created'),
        cell: (group) => <span className="text-xs text-muted">{dateFmt.format(new Date(group.createdAt))}</span>,
        width: '140px',
      },
    ],
    [dateFmt, t]
  )

  return (
    <div className="flex flex-col gap-4">
      <Card size="lg" className="p-4">
        <div className="flex flex-col tablet:flex-row tablet:items-end gap-3">
          <label className="flex flex-col gap-1 flex-1 min-w-0">
            <span className="text-xs font-semibold text-muted">{t('admin.groups.searchLabel')}</span>
            <input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && (setPageIndex(0), load())}
              placeholder={t('admin.groups.searchPlaceholder')}
              className="rounded-full border border-line bg-base px-3 py-2 text-sm focus-bubble"
            />
          </label>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="admin-action-button"
            onClick={() => { setPageIndex(0); load() }}
          >
            {t('admin.groups.search')}
          </Button>
        </div>
      </Card>

      {err && <p className="text-danger text-sm">{err}</p>}

      <AdminTable
        columns={cols}
        rows={page?.content ?? []}
        keyOf={(group) => group.id}
        onRowClick={(group) => setOpening(group)}
        empty={t('admin.groups.empty')}
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
  const { t, i18n } = useTranslation()
  const [detail, setDetail] = useState<AdminGroupDetail | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [archiving, setArchiving] = useState(false)
  const [activating, setActivating] = useState(false)
  const [err, setErr] = useState<string | null>(null)

  useEffect(() => {
    getGroupDetail(group.id)
      .then((res) => {
        setDetail(res)
        setErr(null)
      })
      .catch((e) => setErr(errorBody(e)?.message ?? t('admin.groups.errorDetail')))
  }, [group.id, t])

  return (
    <AdminModal
      title={t('admin.groups.detailTitle', { name: group.name })}
      onClose={onClose}
      size="lg"
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          {detail?.group.status === 'ACTIVE' ? (
            <Button type="button" variant="secondary" size="sm" onClick={() => setArchiving(true)}>
              {t('admin.groups.archiveGroup')}
            </Button>
          ) : (
            <Button type="button" variant="secondary" size="sm" onClick={() => setActivating(true)}>
              {t('admin.groups.activateGroup')}
            </Button>
          )}
          <Button type="button" variant="danger" size="sm" onClick={() => setDeleting(true)}>
            {t('admin.groups.deleteGroup')}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={onClose} className="admin-action-button">
            {t('common.close')}
          </Button>
        </div>
      }
    >
      {err && <p className="text-danger text-sm">{err}</p>}
      {!detail ? (
        <p className="text-sm text-muted">{t('common.loading')}</p>
      ) : (
        <>
          <dl className="grid grid-cols-1 tablet:grid-cols-2 gap-3 text-sm mb-5">
            <Field label={t('admin.groups.fields.visibility')} value={t(`admin.groups.visibility.${detail.group.visibility}`)} />
            <Field label={t('admin.groups.fields.status')} value={t(`admin.groups.status.${detail.group.status}`)} />
            <Field label={t('admin.groups.fields.members')} value={String(detail.group.memberCount)} />
            <Field label={t('admin.groups.fields.offering')} value={detail.group.offeringId} mono />
            <Field label={t('admin.groups.fields.course')} value={detail.group.courseId} mono />
            <Field label={t('admin.groups.fields.created')} value={new Date(detail.group.createdAt).toLocaleString(i18n.language)} />
            <Field label={t('admin.groups.fields.createdBy')} value={detail.group.createdBy} mono />
          </dl>

          <Card size="md" className="p-4">
            <h3 className="text-sm font-semibold text-base mb-3">
              {t('admin.groups.membersTitle', { count: detail.members.length })}
            </h3>
            <ul className="flex flex-col gap-2 max-h-72 overflow-y-auto">
              {detail.members.map((member) => (
                <li
                  key={member.userId}
                  className="flex items-center justify-between gap-3 text-sm border border-line rounded-2xl px-3 py-2 bg-base"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <Avatar id={member.userId} name={member.displayName || member.email || member.userId} size="sm" ring={member.role === 'OWNER'} />
                    <div className="min-w-0">
                      <p className="font-medium truncate">{member.displayName ?? '-'}</p>
                      <p className="text-xs text-muted truncate" dir="ltr">{member.email ?? member.userId}</p>
                    </div>
                  </div>
                  <span className="shrink-0 text-xs rounded-md bg-surface-muted text-secondary px-2 py-0.5">
                    {t(`admin.groups.memberRoles.${member.role}`)}
                  </span>
                </li>
              ))}
              {detail.members.length === 0 && <li className="text-sm text-muted">{t('admin.groups.noMembers')}</li>}
            </ul>
          </Card>
        </>
      )}
      {deleting && (
        <ReasonPromptModal
          title={t('admin.groups.deleteTitle', { name: group.name })}
          description={t('admin.groups.deleteDescription')}
          destructive
          confirmLabel={t('common.delete')}
          onCancel={() => setDeleting(false)}
          onConfirm={async (reason) => {
            await deleteGroup(group.id, reason)
            onDeleted()
          }}
        />
      )}
      {archiving && (
        <ReasonPromptModal
          title={t('admin.groups.archiveTitle', { name: group.name })}
          description={t('admin.groups.archiveDescription')}
          confirmLabel={t('admin.groups.archiveGroup')}
          onCancel={() => setArchiving(false)}
          onConfirm={async (reason) => {
            await archiveGroup(group.id, reason)
            onDeleted()
          }}
        />
      )}
      {activating && (
        <ReasonPromptModal
          title={t('admin.groups.activateTitle', { name: group.name })}
          description={t('admin.groups.activateDescription')}
          confirmLabel={t('admin.groups.activateGroup')}
          onCancel={() => setActivating(false)}
          onConfirm={async (reason) => {
            await activateGroup(group.id, reason)
            onDeleted()
          }}
        />
      )}
    </AdminModal>
  )
}

function VisibilityBadge({ visibility }: { visibility: AdminGroup['visibility'] }) {
  const { t } = useTranslation()
  const tone = visibility === 'PUBLIC'
    ? 'bg-bubble-green-soft text-bubble-green'
    : 'bg-bubble-magenta-soft text-bubble-magenta'
  return (
    <span className={`text-xs px-2 py-0.5 rounded-md ${tone}`}>
      {t(`admin.groups.visibility.${visibility}`)}
    </span>
  )
}

function StatusBadge({ status }: { status: AdminGroup['status'] }) {
  const { t } = useTranslation()
  const tone = status === 'ACTIVE'
    ? 'bg-bubble-green-soft text-bubble-green'
    : status === 'ARCHIVED'
      ? 'bg-warning-soft text-warning'
      : 'bg-danger-soft text-danger'
  return (
    <span className={`text-xs px-2 py-0.5 rounded-md ${tone}`}>
      {t(`admin.groups.status.${status}`)}
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
