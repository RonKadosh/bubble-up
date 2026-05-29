import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createBookingRequest } from '../../api/expert'
import { errorCode } from '../../api/errors'
import { getGroups, type Group } from '../../api/groups'
import { useAuthStore } from '../../store/authStore'

interface Props {
  open: boolean
  expertUserId: string
  onClose: () => void
  onSent: () => void
}

/**
 * Modal for group owners to send a private booking request to an expert.
 * Only the groups the caller owns are eligible — backend enforces it, we
 * pre-filter here to avoid showing groups the request would fail for.
 */
export function RequestBookingModal({ open, expertUserId, onClose, onSent }: Props) {
  const { t } = useTranslation()
  const me = useAuthStore((s) => s.user)
  const [groups, setGroups] = useState<Group[]>([])
  const [groupId, setGroupId] = useState('')
  const [startLocal, setStartLocal] = useState('')
  const [endLocal, setEndLocal] = useState('')
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [loadingGroups, setLoadingGroups] = useState(true)

  useEffect(() => {
    if (!open) return
    setLoadingGroups(true)
    getGroups()
      .then((all) => {
        const owned = all.filter((g) => me?.id && g.ownerId === me.id)
        setGroups(owned)
        if (owned.length > 0) setGroupId(owned[0].id)
      })
      .finally(() => setLoadingGroups(false))
  }, [open, me?.id])

  if (!open) return null

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!groupId || !startLocal || !endLocal) return
    setSubmitting(true)
    setError(null)
    try {
      await createBookingRequest({
        expertUserId,
        groupId,
        proposedStartsAt: new Date(startLocal).toISOString(),
        proposedEndsAt: new Date(endLocal).toISOString(),
        message: message.trim() || undefined,
      })
      onSent()
      onClose()
      setMessage('')
      setStartLocal('')
      setEndLocal('')
    } catch (err) {
      const code = errorCode(err)
      if (code === 'INVALID_EVENT_TIME_RANGE') setError(t('expert.booking.errorBadRange'))
      else if (code === 'NOT_GROUP_OWNER') setError(t('expert.booking.errorNotGroupOwner'))
      else if (code === 'EXPERT_NOT_VERIFIED') setError(t('expert.booking.errorNotVerified'))
      else setError(t('expert.booking.errorGeneric'))
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
        <h2 className="text-lg font-bold text-base mb-4">{t('expert.booking.title')}</h2>

        {loadingGroups ? (
          <p className="text-sm text-muted">{t('expert.booking.loadingGroups')}</p>
        ) : groups.length === 0 ? (
          <p className="text-sm text-muted">{t('expert.booking.needsGroupOwner')}</p>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-3">
            <label className="block">
              <span className="block text-sm font-medium text-base mb-1">{t('expert.booking.groupLabel')} *</span>
              <select
                value={groupId}
                onChange={(e) => setGroupId(e.target.value)}
                required
                className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.name}</option>
                ))}
              </select>
            </label>
            <div className="grid grid-cols-2 gap-3">
              <label className="block">
                <span className="block text-sm font-medium text-base mb-1">{t('expert.booking.startLabel')} *</span>
                <input
                  type="datetime-local"
                  value={startLocal}
                  onChange={(e) => setStartLocal(e.target.value)}
                  required
                  className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
                />
              </label>
              <label className="block">
                <span className="block text-sm font-medium text-base mb-1">{t('expert.booking.endLabel')} *</span>
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
              <span className="block text-sm font-medium text-base mb-1">{t('expert.booking.messageLabel')}</span>
              <textarea
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                maxLength={500}
                rows={3}
                placeholder={t('expert.booking.messagePlaceholder')}
                className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </label>
            {error && <div className="text-sm text-warning">{error}</div>}
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 rounded-full text-sm text-base hover:bg-surface-hover transition"
              >
                {t('expert.booking.cancel')}
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="px-5 py-2 rounded-full bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 disabled:bg-primary-300 transition"
              >
                {submitting ? t('expert.booking.sending') : t('expert.booking.send')}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
