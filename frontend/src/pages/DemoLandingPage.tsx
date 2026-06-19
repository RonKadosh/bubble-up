import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { startDemo } from '../api/demo'
import { useAuthStore } from '../store/authStore'
import { useOnboardingStore } from '../store/onboardingStore'
import { useTourStore } from '../store/tourStore'
import { useBentoLayoutStore } from '../store/bentoLayoutStore'
import { useQuizPromptStore } from '../store/quizPromptStore'
import { Button } from '../components/Button'
import { BubbleLogo } from '../components/Icons'

/**
 * Public, no-login entry to the interactive demo. "Start demo" builds a fresh
 * isolated world server-side, auto-logs the guest in, hydrates onboarding (so the
 * hub renders instead of the wizard), kicks off the guided tour, and lands on the
 * hub. Only mounted in the demo build (DEMO_MODE).
 */
export default function DemoLandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const refreshOnboarding = useOnboardingStore((s) => s.refresh)
  const startTour = useTourStore((s) => s.start)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)

  async function handleStart() {
    if (loading) return
    setLoading(true)
    setError(false)
    try {
      const result = await startDemo()
      setAuth(result.accessToken, result.refreshToken, result.user)
      // First Bubble the visitor opens should land on a maximized Chat.
      useBentoLayoutStore.getState().setFocused('chat')
      // Hold the quiz back from the start; the tour releases it at its matching phase.
      useQuizPromptStore.getState().setSuppressed(true)
      // Pull the guest's onboarding state (seeded as finished) so the hub gate
      // and QuizPrompt treat them as onboarded.
      await refreshOnboarding()
      if (result.startTour) startTour(result.starterGroupId)
      navigate('/groups', { replace: true, state: { home: true } })
    } catch {
      setError(true)
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-app p-6">
      <div className="w-full max-w-lg text-center bg-surface rounded-[2rem] shadow-bubble border border-line p-8 tablet:p-12 animate-pop-in">
        <BubbleLogo className="w-16 h-16 mx-auto mb-6" />
        <h1 className="text-3xl font-extrabold text-base mb-3">{t('demo.landing.title')}</h1>
        <p className="text-secondary mb-2">{t('demo.landing.subtitle')}</p>
        <p className="text-sm text-muted mb-8">{t('demo.landing.blurb')}</p>

        <Button
          variant="deep"
          size="lg"
          className="w-full"
          onClick={handleStart}
          disabled={loading}
        >
          {loading ? t('demo.landing.starting') : t('demo.landing.cta')}
        </Button>

        {error && <p className="mt-4 text-sm text-danger">{t('demo.landing.error')}</p>}
        <p className="mt-6 text-xs text-muted">{t('demo.landing.disclaimer')}</p>
      </div>
    </div>
  )
}
