import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createExpertSession } from '../../api/expert'
import { errorCode } from '../../api/errors'

interface Props {
  open: boolean
  onClose: () => void
  onCreated: () => void
}

/**
 * Modal owned by ExpertDashboardPage. Posts to /api/expert-sessions and tells
 * the parent to refresh on success. The backend creates the calendar event +
 * room atomically — we don't expose any of that here.
 */
export function ScheduleExpertSessionModal({ open, onClose, onCreated }: Props) {
  const { t } = useTranslation()
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [startLocal, setStartLocal] = useState('')
  const [endLocal, setEndLocal] = useState('')
  const [capacity, setCapacity] = useState(3)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!open) return null

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!title.trim() || !startLocal || !endLocal) return
    setSubmitting(true)
    setError(null)
    try {
      // `datetime-local` produces "YYYY-MM-DDTHH:mm" without a timezone; treat
      // as local time and let `new Date(...)` apply the user's offset before
      // converting to ISO (which is UTC) for the wire payload.
      await createExpertSession({
        title: title.trim(),
        description: description.trim() || undefined,
        startsAt: new Date(startLocal).toISOString(),
        endsAt: new Date(endLocal).toISOString(),
        capacity,
      })
      onCreated()
      onClose()
      // reset
      setTitle('')
      setDescription('')
      setStartLocal('')
      setEndLocal('')
      setCapacity(3)
    } catch (err) {
      const code = errorCode(err)
      if (code === 'INVALID_EVENT_TIME_RANGE') {
        setError(t('expert.schedule.errorBadRange'))
      } else if (code === 'EXPERT_NOT_VERIFIED') {
        setError(t('expert.schedule.errorNotVerified'))
      } else {
        setError(t('expert.schedule.errorGeneric'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      onClick={onClose}
    >
      <div
        className="bg-surface rounded-2xl shadow-bubble border border-line p-6 w-full max-w-lg"
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className="text-lg font-bold text-base mb-4">{t('expert.schedule.title')}</h2>
        <form onSubmit={handleSubmit} className="space-y-3">
          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.schedule.titleLabel')} *</span>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              maxLength={140}
              required
              className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>
          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.schedule.descriptionLabel')}</span>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              maxLength={2000}
              rows={3}
              className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="block">
              <span className="block text-sm font-medium text-base mb-1">{t('expert.schedule.startLabel')} *</span>
              <input
                type="datetime-local"
                value={startLocal}
                onChange={(e) => setStartLocal(e.target.value)}
                required
                className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </label>
            <label className="block">
              <span className="block text-sm font-medium text-base mb-1">{t('expert.schedule.endLabel')} *</span>
              <input
                type="datetime-local"
                value={endLocal}
                onChange={(e) => setEndLocal(e.target.value)}
                required
                className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </label>
          </div>
          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.schedule.capacityLabel')} *</span>
            <input
              type="number"
              min={1}
              max={50}
              value={capacity}
              onChange={(e) => setCapacity(parseInt(e.target.value, 10) || 1)}
              required
              className="w-32 border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>
          {error && <div className="text-sm text-warning">{error}</div>}
          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-full text-sm text-base hover:bg-surface-hover transition"
            >
              {t('expert.schedule.cancel')}
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-5 py-2 rounded-full bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 disabled:bg-primary-300 transition"
            >
              {submitting ? t('expert.schedule.creating') : t('expert.schedule.create')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
