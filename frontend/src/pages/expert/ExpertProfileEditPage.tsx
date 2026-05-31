import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { getMyExpertProfile, updateMyExpertProfile } from '../../api/expert'
import { Card } from '../../components/Card'
import { Button } from '../../components/Button'

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
        <div className="flex items-center gap-3 mb-6">
          <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
          <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
          <h1 className="text-2xl font-bold text-base">{t('expert.profileEdit.title')}</h1>
        </div>
        <Card size="lg" className="p-6">
        <form onSubmit={handleSubmit} className="space-y-4">
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
            <Button type="button" variant="ghost" size="sm" onClick={() => navigate('/expert')}>
              {t('expert.profileEdit.cancel')}
            </Button>
            <Button type="submit" size="sm" disabled={submitting}>
              {submitting ? t('expert.profileEdit.saving') : t('expert.profileEdit.save')}
            </Button>
          </div>
        </form>
        </Card>
      </div>
    </div>
  )
}
