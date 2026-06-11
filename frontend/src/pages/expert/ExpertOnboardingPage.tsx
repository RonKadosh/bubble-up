import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { applyAsExpert, getMyExpertProfile } from '../../api/expert'
import { errorCode } from '../../api/errors'
import { useAuthStore } from '../../store/authStore'
import { Card } from '../../components/Card'
import { Button } from '../../components/Button'
import { FormField, fieldInputClass } from '../../components/FormField'

/**
 * `/become-expert` — single-screen application form. Verification is admin-gated
 * (see `app.expert.auto-verify`): applying creates a PENDING profile and the
 * user stays a STUDENT until an admin approves. Only an auto-verified response
 * (VERIFIED) promotes the role + opens the Expert hub immediately; a PENDING
 * response shows a "pending review" state instead of routing to /expert (which
 * the RequireExpert guard would bounce, since the role hasn't changed yet).
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
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getMyExpertProfile()
      .then((profile) => {
        if (cancelled) return
        if (profile.verificationStatus === 'VERIFIED') {
          updateUser({ role: 'EXPERT' })
          navigate('/expert', { replace: true })
        } else {
          // PENDING profile already exists — show the waiting state, never bounce.
          setPending(true)
          setChecking(false)
        }
      })
      .catch(() => { if (!cancelled) setChecking(false) })
    return () => { cancelled = true }
  }, [navigate, updateUser])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!headline.trim()) return
    setSubmitting(true)
    setError(null)
    try {
      const tags = tagsRaw
        .split(',')
        .map((tag) => tag.trim())
        .filter((tag) => tag.length > 0)
        .slice(0, 20)
      const profile = await applyAsExpert({ headline: headline.trim(), bio: bio.trim() || undefined, expertiseTags: tags })
      if (profile.verificationStatus === 'VERIFIED') {
        updateUser({ role: 'EXPERT' })
        navigate('/expert', { replace: true })
      } else {
        setPending(true)
      }
    } catch (err) {
      if (errorCode(err) === 'EXPERT_APPLICATION_ALREADY_SUBMITTED') {
        setPending(true)
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

  if (pending) {
    return (
      <div className="flex-1 overflow-y-auto p-8">
        <div className="max-w-2xl mx-auto">
          <Card size="lg" className="p-8 text-center flex flex-col items-center gap-4">
            <div className="w-12 h-12 rounded-full bg-bubble-magenta-soft text-bubble-magenta flex items-center justify-center text-2xl">⏳</div>
            <h1 className="text-xl font-bold text-base">{t('expert.onboarding.pendingTitle')}</h1>
            <p className="text-sm text-muted">{t('expert.onboarding.pendingBody')}</p>
            <Button type="button" size="sm" onClick={() => navigate('/dashboard')}>
              {t('expert.onboarding.pendingBackToDashboard')}
            </Button>
          </Card>
        </div>
      </div>
    )
  }

  return (
    <div className="flex-1 overflow-y-auto p-8">
      <div className="max-w-2xl mx-auto">
        <div className="flex items-center gap-3 mb-2">
          <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
          <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
          <h1 className="text-2xl font-bold text-base">{t('expert.onboarding.title')}</h1>
        </div>
        <p className="text-sm text-muted mb-6 ms-[1.6rem]">{t('expert.onboarding.subtitle')}</p>

        <Card size="lg" className="p-6">
          <form onSubmit={handleSubmit} className="space-y-4">
            <FormField label={t('expert.onboarding.headlineLabel')} required>
              <input
                type="text"
                value={headline}
                onChange={(e) => setHeadline(e.target.value)}
                maxLength={140}
                required
                placeholder={t('expert.onboarding.headlinePlaceholder')}
                className={fieldInputClass}
              />
            </FormField>

            <FormField label={t('expert.onboarding.bioLabel')}>
              <textarea
                value={bio}
                onChange={(e) => setBio(e.target.value)}
                maxLength={2000}
                rows={5}
                placeholder={t('expert.onboarding.bioPlaceholder')}
                className={fieldInputClass}
              />
            </FormField>

            <FormField label={t('expert.onboarding.tagsLabel')} hint={t('expert.onboarding.tagsHint')}>
              <input
                type="text"
                value={tagsRaw}
                onChange={(e) => setTagsRaw(e.target.value)}
                placeholder={t('expert.onboarding.tagsPlaceholder')}
                className={fieldInputClass}
              />
            </FormField>

            {error && <div className="text-sm text-warning">{error}</div>}

            <Button variant="deep"
              type="submit"
              size="sm"
              disabled={submitting || !headline.trim()}
              className="w-full sm:w-auto"
            >
              {submitting ? t('expert.onboarding.submitting') : t('expert.onboarding.submit')}
            </Button>
          </form>
        </Card>
      </div>
    </div>
  )
}
