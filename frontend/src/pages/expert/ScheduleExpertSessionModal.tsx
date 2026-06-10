import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createExpertSession } from '../../api/expert'
import { errorCode } from '../../api/errors'
import { Button } from '../../components/Button'

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
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-3 tablet:p-4 animate-fade-in"
      onClick={onClose}
    >
      <div
        className="bg-surface rounded-3xl shadow-bubble animate-pop-in border border-line w-full max-w-lg max-h-[85vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-5 py-3 border-b border-line flex items-center justify-between shrink-0">
          <h3 className="font-semibold text-base">{t('expert.schedule.title')}</h3>
          <button type="button" onClick={onClose} aria-label={t('common.close')} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>
        <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-5 space-y-3">
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
            <Button type="button" variant="ghost" size="sm" onClick={onClose}>
              {t('expert.schedule.cancel')}
            </Button>
            <Button variant="deep" type="submit" size="sm" disabled={submitting}>
              {submitting ? t('expert.schedule.creating') : t('expert.schedule.create')}
            </Button>
          </div>
        </form>
      </div>
    </div>
  )
}
