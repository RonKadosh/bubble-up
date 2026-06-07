import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { AuditLogEntry, PageResponse, searchAudit } from '../../api/admin'
import { errorBody } from '../../api/errors'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import AdminTable, { Column } from './components/AdminTable'

export default function AdminAuditTab() {
  const { t, i18n } = useTranslation()
  const [page, setPage] = useState<PageResponse<AuditLogEntry> | null>(null)
  const [action, setAction] = useState('')
  const [targetType, setTargetType] = useState('')
  const [pageIndex, setPageIndex] = useState(0)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      const res = await searchAudit({
        action: action || undefined,
        targetType: targetType || undefined,
        page: pageIndex,
        size: 20,
      })
      setPage(res)
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.audit.errorLoad'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pageIndex])

  const dateFmt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }),
    [i18n.language]
  )

  const columns: Column<AuditLogEntry>[] = useMemo(
    () => [
      {
        header: t('admin.audit.columns.action'),
        cell: (row) => <ActionBadge action={row.action} />,
        width: '180px',
      },
      {
        header: t('admin.audit.columns.actor'),
        cell: (row) => (
          <div className="min-w-0">
            <p className="text-sm text-base truncate" dir="ltr">{row.actorEmail ?? '-'}</p>
            {row.actorId && <p className="text-xs text-muted truncate font-mono">{row.actorId}</p>}
          </div>
        ),
      },
      {
        header: t('admin.audit.columns.target'),
        cell: (row) => (
          <div className="min-w-0">
            <p className="text-sm text-base">{row.targetType}</p>
            {row.targetId && <p className="text-xs text-muted truncate font-mono">{row.targetId}</p>}
          </div>
        ),
      },
      {
        header: t('admin.audit.columns.reason'),
        cell: (row) => <span className="text-sm text-muted line-clamp-2">{row.reason ?? '-'}</span>,
      },
      {
        header: t('admin.audit.columns.created'),
        cell: (row) => <span className="text-xs text-muted">{dateFmt.format(new Date(row.createdAt))}</span>,
        width: '140px',
      },
    ],
    [dateFmt, t]
  )

  return (
    <div className="flex flex-col gap-4">
      <Card size="lg" className="p-4">
        <div className="flex flex-col tablet:flex-row tablet:items-end gap-3">
          <label className="flex flex-col gap-1 tablet:w-64">
            <span className="text-xs font-semibold text-muted">{t('admin.audit.actionLabel')}</span>
            <input
              value={action}
              onChange={(e) => setAction(e.target.value)}
              placeholder="USER_SUSPENDED"
              className="rounded-full border border-line bg-base px-3 py-2 text-sm focus-bubble"
            />
          </label>
          <label className="flex flex-col gap-1 tablet:w-48">
            <span className="text-xs font-semibold text-muted">{t('admin.audit.targetTypeLabel')}</span>
            <input
              value={targetType}
              onChange={(e) => setTargetType(e.target.value)}
              placeholder="USER"
              className="rounded-full border border-line bg-base px-3 py-2 text-sm focus-bubble"
            />
          </label>
          <Button type="button" variant="ghost" size="sm" className="admin-action-button" onClick={() => { setPageIndex(0); load() }}>
            {t('admin.audit.apply')}
          </Button>
        </div>
      </Card>

      {err && <p className="text-danger text-sm">{err}</p>}
      <AdminTable columns={columns} rows={page?.content ?? []} keyOf={(row) => row.id} empty={t('admin.audit.empty')} />

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
            <Button type="button" variant="secondary" size="xs" disabled={page.first} onClick={() => setPageIndex((prev) => Math.max(0, prev - 1))}>
              {t('admin.users.prev')}
            </Button>
            <Button type="button" variant="secondary" size="xs" disabled={page.last} onClick={() => setPageIndex((prev) => prev + 1)}>
              {t('admin.users.next')}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

function ActionBadge({ action }: { action: string }) {
  const tone = action.includes('BANNED') || action.includes('DELETED')
    ? 'bg-danger-soft text-danger'
    : action.includes('SUSPENDED') || action.includes('ARCHIVED')
      ? 'bg-warning-soft text-warning'
      : 'bg-bubble-green-soft text-bubble-green'
  return <span className={`text-xs px-2 py-0.5 rounded-md ${tone}`}>{action}</span>
}
