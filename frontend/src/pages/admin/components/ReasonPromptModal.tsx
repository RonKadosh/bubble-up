import { useState } from 'react'
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
  confirmLabel = 'Confirm',
  destructive,
  onCancel,
  onConfirm,
}: Props) {
  const [reason, setReason] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    if (!reason.trim()) {
      setError('A reason is required.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await onConfirm(reason.trim())
    } catch (e) {
      setError(extractErrorMessage(e))
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
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className="px-4 py-2 rounded-full border border-line text-base hover:bg-surface-hover"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting}
            className={`px-4 py-2 rounded-full text-on-brand font-medium ${
              destructive ? 'bg-red-600 hover:bg-red-700' : 'bg-indigo-600 hover:bg-indigo-700'
            } disabled:opacity-60`}
          >
            {submitting ? '…' : confirmLabel}
          </button>
        </div>
      }
    >
      {description && <p className="text-sm text-secondary mb-3">{description}</p>}
      <label className="block">
        <span className="text-sm font-medium text-base">Reason</span>
        <textarea
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          autoFocus
          className="mt-1 w-full rounded-xl border border-line bg-base px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
          placeholder="Why are you doing this?"
        />
      </label>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
    </AdminModal>
  )
}

function extractErrorMessage(e: unknown): string {
  if (typeof e === 'object' && e !== null) {
    const anyE = e as { response?: { data?: { error?: { code?: string; message?: string } } } }
    return anyE.response?.data?.error?.message ?? anyE.response?.data?.error?.code ?? 'Request failed.'
  }
  return 'Request failed.'
}
