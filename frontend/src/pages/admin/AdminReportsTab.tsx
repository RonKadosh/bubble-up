import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AdminReport,
  dismissReport,
  fetchReportImageUrl,
  listReports,
  PageResponse,
  ReportStatus,
  resolveReport,
} from '../../api/admin'
import { errorBody } from '../../api/errors'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import AdminModal from './components/AdminModal'
import AdminTable, { Column } from './components/AdminTable'
import ReasonPromptModal from './components/ReasonPromptModal'

const STATUSES: ReportStatus[] = ['PENDING', 'RESOLVED', 'DISMISSED']

/** Admin "Reports" inbox tab — entity-agnostic user reports with optional screenshots. */
export default function AdminReportsTab({ onChanged }: { onChanged?: () => void }) {
  const { t, i18n } = useTranslation()
  const [status, setStatus] = useState<ReportStatus>('PENDING')
  const [page, setPage] = useState<PageResponse<AdminReport> | null>(null)
  const [pageIndex, setPageIndex] = useState(0)
  const [opening, setOpening] = useState<AdminReport | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      const res = await listReports(status, pageIndex, 20)
      setPage(res)
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.reports.errorLoad'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, pageIndex])

  const dateFmt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }),
    [i18n.language]
  )

  const columns: Column<AdminReport>[] = useMemo(
    () => [
      {
        header: t('admin.reports.columns.category'),
        cell: (row) => <CategoryBadge category={row.category} />,
        width: '150px',
      },
      {
        header: t('admin.reports.columns.subject'),
        cell: (row) => (
          <div className="min-w-0">
            <p className="text-sm text-base truncate">{row.subject}</p>
            <p className="text-xs text-muted line-clamp-1">{row.description}</p>
          </div>
        ),
      },
      {
        header: t('admin.reports.columns.reporter'),
        cell: (row) => <span className="text-xs text-muted font-mono truncate" dir="ltr">{row.reporterUserId}</span>,
        width: '160px',
      },
      {
        header: t('admin.reports.columns.created'),
        cell: (row) => (
          <span className="text-xs text-muted">
            {dateFmt.format(new Date(row.createdAt))}
            {row.hasAttachment && <span className="ms-1" title={t('admin.reports.hasAttachment')}>📎</span>}
          </span>
        ),
        width: '150px',
      },
    ],
    [dateFmt, t]
  )

  return (
    <div className="flex flex-col gap-4">
      <Card size="lg" className="p-2">
        <nav className="flex flex-wrap gap-1">
          {STATUSES.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => { setStatus(item); setPageIndex(0) }}
              className={`px-4 py-2 rounded-full text-sm font-medium transition ${
                status === item
                  ? 'bg-bubble-magenta-soft text-bubble-magenta'
                  : 'text-muted hover:bg-surface-hover'
              }`}
            >
              {t(`admin.reports.status.${item}`)}
            </button>
          ))}
        </nav>
      </Card>

      {err && <p className="text-danger text-sm">{err}</p>}

      <AdminTable
        columns={columns}
        rows={page?.content ?? []}
        keyOf={(row) => row.id}
        onRowClick={(row) => setOpening(row)}
        empty={t('admin.reports.empty')}
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
            <Button type="button" variant="secondary" size="xs" disabled={page.first} onClick={() => setPageIndex((p) => Math.max(0, p - 1))}>
              {t('admin.users.prev')}
            </Button>
            <Button type="button" variant="secondary" size="xs" disabled={page.last} onClick={() => setPageIndex((p) => p + 1)}>
              {t('admin.users.next')}
            </Button>
          </div>
        </div>
      )}

      {opening && (
        <ReportDetailModal
          report={opening}
          onClose={() => setOpening(null)}
          onChanged={() => {
            setOpening(null)
            load()
            onChanged?.()
          }}
        />
      )}
    </div>
  )
}

function ReportDetailModal({
  report,
  onClose,
  onChanged,
}: {
  report: AdminReport
  onClose: () => void
  onChanged: () => void
}) {
  const { t, i18n } = useTranslation()
  const [action, setAction] = useState<'resolve' | 'dismiss' | null>(null)
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const isPending = report.status === 'PENDING'

  useEffect(() => {
    if (!report.hasAttachment) return
    let url: string | null = null
    let cancelled = false
    fetchReportImageUrl(report.id)
      .then((u) => { if (!cancelled) { url = u; setImageUrl(u) } })
      .catch(() => { /* attachment best-effort; ignore */ })
    return () => {
      cancelled = true
      if (url) URL.revokeObjectURL(url)
    }
  }, [report.id, report.hasAttachment])

  return (
    <AdminModal
      title={report.subject}
      onClose={onClose}
      size="md"
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          {isPending && (
            <>
              <Button type="button" variant="secondary" size="sm" onClick={() => setAction('resolve')}>
                {t('admin.reports.actions.resolve')}
              </Button>
              <Button type="button" variant="danger" size="sm" onClick={() => setAction('dismiss')}>
                {t('admin.reports.actions.dismiss')}
              </Button>
            </>
          )}
          <Button type="button" variant="ghost" size="sm" onClick={onClose} className="admin-action-button">
            {t('common.close')}
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        <Card size="md" className="p-4">
          <dl className="grid grid-cols-1 tablet:grid-cols-2 gap-3 text-sm">
            <Field label={t('admin.reports.fields.category')} value={t(`report.category.${report.category}`)} />
            <Field label={t('admin.reports.fields.status')} value={t(`admin.reports.status.${report.status}`)} />
            <Field label={t('admin.reports.fields.reporter')} value={report.reporterUserId} mono />
            <Field label={t('admin.reports.fields.created')} value={new Date(report.createdAt).toLocaleString(i18n.language)} />
            {report.resolutionNote && (
              <Field label={t('admin.reports.fields.resolutionNote')} value={report.resolutionNote} />
            )}
          </dl>
        </Card>

        <div>
          <h3 className="text-sm font-semibold text-base mb-1">{t('admin.reports.fields.description')}</h3>
          <p className="text-sm text-muted whitespace-pre-wrap">{report.description}</p>
        </div>

        {report.hasAttachment && (
          <div>
            <h3 className="text-sm font-semibold text-base mb-1">{t('admin.reports.fields.attachment')}</h3>
            {imageUrl ? (
              <a href={imageUrl} target="_blank" rel="noreferrer">
                <img src={imageUrl} alt={t('admin.reports.fields.attachment')} className="max-h-64 rounded-xl border border-line" />
              </a>
            ) : (
              <p className="text-sm text-muted">{t('common.loading')}</p>
            )}
          </div>
        )}
      </div>

      {action && (
        <ReasonPromptModal
          title={t(`admin.reports.prompts.${action}.title`)}
          description={t(`admin.reports.prompts.${action}.description`)}
          destructive={action === 'dismiss'}
          confirmLabel={t(`admin.reports.actions.${action}`)}
          onCancel={() => setAction(null)}
          onConfirm={async (note) => {
            if (action === 'resolve') await resolveReport(report.id, note)
            else await dismissReport(report.id, note)
            setAction(null)
            onChanged()
          }}
        />
      )}
    </AdminModal>
  )
}

function CategoryBadge({ category }: { category: AdminReport['category'] }) {
  const { t } = useTranslation()
  const tone = category === 'ABUSE' || category === 'HARASSMENT' || category === 'SAFETY'
    ? 'bg-danger-soft text-danger'
    : category === 'SPAM'
    ? 'bg-warning-soft text-warning'
    : 'bg-surface-muted text-secondary'
  return <span className={`text-xs px-2 py-0.5 rounded-md ${tone}`}>{t(`report.category.${category}`)}</span>
}

function Field({ label, value, mono }: { label: string; value: string | null; mono?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className={`text-sm text-base ${mono ? 'font-mono text-xs break-all' : ''}`}>{value ?? '-'}</dd>
    </div>
  )
}
