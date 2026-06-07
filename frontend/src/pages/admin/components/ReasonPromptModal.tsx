import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../../../components/Button'
import { errorBody } from '../../../api/errors'
import AdminModal from './AdminModal'

interface Props {
  title: string
  description?: string
  confirmLabel?: string
  destructive?: boolean
  onCancel: () => void
  onConfirm: (reason: string) => Promise<void> | void
}

export default function ReasonPromptModal({
  title,
  description,
  confirmLabel,
  destructive,
  onCancel,
  onConfirm,
}: Props) {
  const { t } = useTranslation()
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    if (!reason.trim()) {
      setError(t('admin.reason.required'))
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await onConfirm(reason.trim())
    } catch (e) {
      setError(errorBody(e)?.message ?? t('admin.reason.requestFailed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AdminModal
      title={title}
      onClose={onCancel}
      size="sm"
      footer={
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={onCancel}
            disabled={submitting}
          >
            {t('common.cancel')}
          </Button>
          <Button
            type="button"
            variant={destructive ? 'danger' : 'primary'}
            size="sm"
            onClick={handleConfirm}
            disabled={submitting}
          >
            {submitting ? t('admin.reason.submitting') : confirmLabel ?? t('admin.reason.confirm')}
          </Button>
        </div>
      }
    >
      {description && <p className="text-sm text-muted mb-3">{description}</p>}
      <label className="block">
        <span className="text-sm font-medium text-base">{t('admin.reason.label')}</span>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          autoFocus
          className="mt-1 w-full rounded-2xl border border-line bg-base px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-300"
          placeholder={t('admin.reason.placeholder')}
        />
      </label>
      {error && <p className="mt-2 text-sm text-danger">{error}</p>}
    </AdminModal>
  )
}
