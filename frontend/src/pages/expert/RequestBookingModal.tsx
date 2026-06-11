import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createBookingRequest } from '../../api/expert'
import { errorCode } from '../../api/errors'
import { getMyGroups, type Group } from '../../api/groups'
import { useAuthStore } from '../../store/authStore'
import { Button } from '../../components/Button'

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
    getMyGroups()
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
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-3 tablet:p-4 animate-fade-in"
      onClick={onClose}
    >
      <div
        className="bg-surface rounded-3xl shadow-bubble animate-pop-in border border-line w-full max-w-lg max-h-[85vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-5 py-3 border-b border-line flex items-center justify-between shrink-0">
          <h3 className="font-semibold text-base">{t('expert.booking.title')}</h3>
          <button type="button" onClick={onClose} aria-label={t('common.close')} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>

        {loadingGroups ? (
          <p className="text-sm text-muted p-5">{t('expert.booking.loadingGroups')}</p>
        ) : groups.length === 0 ? (
          <p className="text-sm text-muted p-5">{t('expert.booking.needsGroupOwner')}</p>
        ) : (
          <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-5 space-y-3">
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
              <Button type="button" variant="ghost" size="sm" onClick={onClose}>
                {t('expert.booking.cancel')}
              </Button>
              <Button variant="deep" type="submit" size="sm" disabled={submitting}>
                {submitting ? t('expert.booking.sending') : t('expert.booking.send')}
              </Button>
            </div>
          </form>
        )}
      </div>
    </div>
  )
}
