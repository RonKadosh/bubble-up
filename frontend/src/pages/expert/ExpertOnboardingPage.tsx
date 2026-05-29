import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { applyAsExpert, getMyExpertProfile } from '../../api/expert'
import { errorCode } from '../../api/errors'
import { useAuthStore } from '../../store/authStore'

/**
 * `/become-expert` — single-screen application form. Per v1 scope, the backend
 * auto-verifies on submit (`verificationStatus = VERIFIED`) and promotes the
 * user's role to EXPERT. On success we update the local authStore so the
 * sidebar's "Expert hub" link appears immediately and the RequireExpert route
 * guard accepts the user.
 *
 * If the user already has a profile we redirect to the dashboard — there's no
 * "edit on this page" path; that lives at /expert/profile/edit.
 */
export default function ExpertOnboardingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const updateUser = useAuthStore((s) => s.updateUser)

  const [headline, setHeadline] = useState('')
  const [bio, setBio] = useState('')
  const [tagsRaw, setTagsRaw] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [checking, setChecking] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getMyExpertProfile()
      .then(() => { if (!cancelled) navigate('/expert', { replace: true }) })
      .catch(() => { if (!cancelled) setChecking(false) })
    return () => { cancelled = true }
  }, [navigate])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!headline.trim()) return
    setSubmitting(true)
    setError(null)
    try {
      const tags = tagsRaw
        .split(',')
        .map((t) => t.trim())
        .filter((t) => t.length > 0)
        .slice(0, 20)
      await applyAsExpert({ headline: headline.trim(), bio: bio.trim() || undefined, expertiseTags: tags })
      updateUser({ role: 'EXPERT' })
      navigate('/expert', { replace: true })
    } catch (err) {
      const code = errorCode(err)
      if (code === 'EXPERT_APPLICATION_ALREADY_SUBMITTED') {
        navigate('/expert', { replace: true })
      } else {
        setError(t('expert.onboarding.errorGeneric'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (checking) {
    return (
      <div className="flex items-center justify-center h-full text-muted text-sm">{t('common.loading')}</div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto p-8">
      <div className="max-w-2xl mx-auto">
        <h1 className="text-2xl font-bold text-base mb-2">{t('expert.onboarding.title')}</h1>
        <p className="text-sm text-muted mb-6">{t('expert.onboarding.subtitle')}</p>

        <form onSubmit={handleSubmit} className="bg-surface rounded-2xl shadow-themed border border-line p-6 space-y-4">
          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.onboarding.headlineLabel')} *</span>
            <input
              type="text"
              value={headline}
              onChange={(e) => setHeadline(e.target.value)}
              maxLength={140}
              required
              placeholder={t('expert.onboarding.headlinePlaceholder')}
              className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>

          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.onboarding.bioLabel')}</span>
            <textarea
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              maxLength={2000}
              rows={5}
              placeholder={t('expert.onboarding.bioPlaceholder')}
              className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </label>

          <label className="block">
            <span className="block text-sm font-medium text-base mb-1">{t('expert.onboarding.tagsLabel')}</span>
            <input
              type="text"
              value={tagsRaw}
              onChange={(e) => setTagsRaw(e.target.value)}
              placeholder={t('expert.onboarding.tagsPlaceholder')}
              className="w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            <span className="block text-xs text-muted mt-1">{t('expert.onboarding.tagsHint')}</span>
          </label>

          {error && <div className="text-sm text-warning">{error}</div>}

          <button
            type="submit"
            disabled={submitting || !headline.trim()}
            className="w-full sm:w-auto px-5 py-2 rounded-full bg-primary-600 text-white text-sm font-medium hover:bg-primary-700 disabled:bg-primary-300 transition"
          >
            {submitting ? t('expert.onboarding.submitting') : t('expert.onboarding.submit')}
          </button>
        </form>
      </div>
    </div>
  )
}
