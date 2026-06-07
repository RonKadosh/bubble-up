import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  AdminExpert,
  listExperts,
  rejectExpert,
  revokeExpert,
  VerificationStatus,
  verifyExpert,
} from '../../api/admin'
import { errorBody } from '../../api/errors'
import { Button } from '../../components/Button'
import { Card } from '../../components/Card'
import AdminModal from './components/AdminModal'
import ReasonPromptModal from './components/ReasonPromptModal'

const STATUSES: VerificationStatus[] = ['PENDING', 'VERIFIED', 'REJECTED']

/**
 * Admin "Expert Requests" inbox tab. Absorbs the former standalone
 * /admin/experts page into the admin shell so verification lives alongside the
 * other admin tabs. `onChanged` lets the shell refresh the pending-count badge.
 */
export default function AdminExpertRequestsTab({ onChanged }: { onChanged?: () => void }) {
  const { t, i18n } = useTranslation()
  const [status, setStatus] = useState<VerificationStatus>('PENDING')
  const [items, setItems] = useState<AdminExpert[]>([])
  const [opening, setOpening] = useState<AdminExpert | null>(null)
  const [err, setErr] = useState<string | null>(null)

  async function load() {
    try {
      const res = await listExperts(status)
      setItems(res)
      setErr(null)
    } catch (e) {
      setErr(errorBody(e)?.message ?? t('admin.experts.errorLoad'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status])

  const dateFmt = useMemo(
    () => new Intl.DateTimeFormat(i18n.language, { month: 'short', day: 'numeric', year: 'numeric' }),
    [i18n.language]
  )

  return (
    <div className="flex flex-col gap-4">
      <Card size="lg" className="p-2">
        <nav className="flex flex-wrap gap-1">
          {STATUSES.map((item) => (
            <button
              key={item}
              type="button"
              onClick={() => setStatus(item)}
              className={`px-4 py-2 rounded-full text-sm font-medium transition ${
                status === item
                  ? 'bg-bubble-magenta-soft text-bubble-magenta'
                  : 'text-muted hover:bg-surface-hover'
              }`}
            >
              {t(`admin.experts.status.${item}`)}
            </button>
          ))}
        </nav>
      </Card>

      {err && <p className="text-danger text-sm">{err}</p>}

      <ul className="grid grid-cols-1 tablet:grid-cols-2 gap-3">
        {items.map((expert) => (
          <li key={expert.id}>
            <Card size="md" interactive className="p-4 cursor-pointer h-full" onClick={() => setOpening(expert)}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="text-base font-semibold text-base truncate">{expert.headline}</h2>
                  <p className="text-xs text-muted mt-1">
                    {t('admin.experts.userId')}: <span className="font-mono" dir="ltr">{expert.userId}</span>
                  </p>
                </div>
                <StatusBadge status={expert.verificationStatus} />
              </div>
              {expert.expertiseTags.length > 0 && (
                <div className="mt-3 flex flex-wrap gap-1">
                  {expert.expertiseTags.map((tag) => (
                    <span key={tag} className="text-xs rounded-full bg-surface-muted text-secondary px-2 py-0.5">
                      {tag}
                    </span>
                  ))}
                </div>
              )}
              <p className="text-xs text-muted mt-3">
                {t('admin.experts.applied')}: {dateFmt.format(new Date(expert.appliedAt))}
              </p>
              {expert.verifiedAt && (
                <p className="text-xs text-bubble-green mt-1">
                  {t('admin.experts.verifiedAt')}: {dateFmt.format(new Date(expert.verifiedAt))}
                </p>
              )}
            </Card>
          </li>
        ))}
        {items.length === 0 && (
          <li className="col-span-full">
            <Card size="lg" className="p-8 text-center text-muted">
              {t('admin.experts.empty')}
            </Card>
          </li>
        )}
      </ul>

      {opening && (
        <ExpertDetailModal
          expert={opening}
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

function ExpertDetailModal({
  expert,
  onClose,
  onChanged,
}: {
  expert: AdminExpert
  onClose: () => void
  onChanged: () => void
}) {
  const { t, i18n } = useTranslation()
  const [action, setAction] = useState<'verify' | 'reject' | 'revoke' | null>(null)

  return (
    <AdminModal
      title={expert.headline}
      onClose={onClose}
      size="md"
      footer={
        <div className="flex flex-wrap justify-end gap-2">
          {expert.verificationStatus !== 'VERIFIED' && (
            <Button type="button" variant="secondary" size="sm" onClick={() => setAction('verify')}>
              {t('admin.experts.actions.verify')}
            </Button>
          )}
          {expert.verificationStatus === 'VERIFIED' && (
            <Button type="button" variant="secondary" size="sm" onClick={() => setAction('revoke')}>
              {t('admin.experts.actions.revoke')}
            </Button>
          )}
          <Button type="button" variant="danger" size="sm" onClick={() => setAction('reject')}>
            {t('admin.experts.actions.reject')}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={onClose} className="admin-action-button">
            {t('common.close')}
          </Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4">
        <Card size="md" className="p-4">
          <dl className="grid grid-cols-1 tablet:grid-cols-2 gap-3 text-sm">
            <Field label={t('admin.experts.userId')} value={expert.userId} mono />
            <Field label={t('admin.experts.fields.status')} value={t(`admin.experts.status.${expert.verificationStatus}`)} />
            <Field label={t('admin.experts.fields.applied')} value={new Date(expert.appliedAt).toLocaleString(i18n.language)} />
            <Field
              label={t('admin.experts.fields.verifiedAt')}
              value={expert.verifiedAt ? new Date(expert.verifiedAt).toLocaleString(i18n.language) : null}
            />
          </dl>
        </Card>

        <div>
          <h3 className="text-sm font-semibold text-base mb-1">{t('admin.experts.fields.bio')}</h3>
          <p className="text-sm text-muted whitespace-pre-wrap">{expert.bio || t('admin.experts.noBio')}</p>
        </div>

        <div className="flex flex-wrap gap-1">
          {expert.expertiseTags.map((tag) => (
            <span key={tag} className="text-xs rounded-full bg-surface-muted text-secondary px-2 py-0.5">
              {tag}
            </span>
          ))}
          {expert.expertiseTags.length === 0 && <span className="text-sm text-muted">{t('admin.experts.noTags')}</span>}
        </div>
      </div>

      {action && (
        <ReasonPromptModal
          title={t(`admin.experts.prompts.${action}.title`)}
          description={t(`admin.experts.prompts.${action}.description`)}
          destructive={action !== 'verify'}
          confirmLabel={t(`admin.experts.actions.${action}`)}
          onCancel={() => setAction(null)}
          onConfirm={async (reason) => {
            if (action === 'verify') await verifyExpert(expert.userId, reason)
            else if (action === 'reject') await rejectExpert(expert.userId, reason)
            else await revokeExpert(expert.userId, reason)
            setAction(null)
            onChanged()
          }}
        />
      )}
    </AdminModal>
  )
}

function StatusBadge({ status }: { status: VerificationStatus }) {
  const { t } = useTranslation()
  const tone = status === 'VERIFIED'
    ? 'bg-bubble-green-soft text-bubble-green'
    : status === 'REJECTED'
    ? 'bg-danger-soft text-danger'
    : 'bg-surface-muted text-secondary'
  return (
    <span className={`shrink-0 text-xs px-2 py-0.5 rounded-md ${tone}`}>
      {t(`admin.experts.status.${status}`)}
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
