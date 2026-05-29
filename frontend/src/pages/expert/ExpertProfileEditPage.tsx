import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { getMyExpertProfile, updateMyExpertProfile } from '../../api/expert'

export default function ExpertProfileEditPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [loading, setLoading] = useState(true)
  const [headline, setHeadline] = useState('')
  const [bio, setBio] = useState('')
  const [tagsRaw, setTagsRaw] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getMyExpertProfile()
      .then((p) => {
        if (cancelled) return
        setHeadline(p.headline)
        setBio(p.bio ?? '')
        setTagsRaw(p.expertiseTags.join(', '))
      })
      .catch(() => navigate('/become-expert', { replace: true }))
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [navigate])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      const tags = tagsRaw.split(',').map((t) => t.trim()).filter(Boolean).slice(0, 20)
      await updateMyExpertProfile({
        headline: headline.trim() || undefined,
        bio: bio.trim(),
        expertiseTags: tags,
      })
      navigate('/expert', { replace: true })
    } catch {
      setError(t('expert.profileEdit.errorSave'))
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return <div className="flex items-center justify-center h-full text-muted text-sm">{t('common.loading')}</div>
  }

  return (
    <div className="flex-1 overflow-y-auto p-8">
      <div className="max-w-2xl mx-auto">
        <h1 className="text-2xl font-bold text-base mb-6">{t('expert.profileEdit.title')}</h1>
        <form onSubmit={handleSubmit} className="bg-surface rounded-2xl shadow-themed border border-line p-6 space-y-4">
          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.profileEdit.headlineLabel')}</span>
            <input
              type="text"
              value={headline}
              onChange={(e) => setHeadline(e.target.value)}
              maxLength={140}
              className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>
          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.profileEdit.bioLabel')}</span>
            <textarea
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              maxLength={2000}
              rows={5}
              className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>
          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.profileEdit.tagsLabel')}</span>
            <input
              type="text"
              value={tagsRaw}
              onChange={(e) => setTagsRaw(e.target.value)}
              placeholder={t('expert.profileEdit.tagsPlaceholder')}
              className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>
          {error && <div className="text-sm text-warning">{error}</div>}
          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={() => navigate('/expert')}
              className="px-4 py-2 rounded-full text-sm text-base hover:bg-surface-hover transition"
            >
              {t('expert.profileEdit.cancel')}
            </button>
            <button
              type="submit"
              disabled={submitting}
              className="px-5 py-2 rounded-full bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 disabled:bg-primary-300 transition"
            >
              {submitting ? t('expert.profileEdit.saving') : t('expert.profileEdit.save')}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
