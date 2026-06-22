import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { startDemo } from '../api/demo'
import { useAuthStore } from '../store/authStore'
import { useLanguageStore } from '../store/languageStore'
import { useOnboardingStore } from '../store/onboardingStore'
import { useTourStore } from '../store/tourStore'
import { useBentoLayoutStore } from '../store/bentoLayoutStore'
import { useQuizPromptStore } from '../store/quizPromptStore'
import { useViewportStore } from '../store/viewportStore'
import { Button } from '../components/Button'
import { BubbleField } from '../components/BubbleField'
import { BubbleLoader } from '../components/BubbleLoader'
import { BubbleLogo } from '../components/Icons'

/**
 * Public, no-login entry to the interactive demo. "Start demo" builds a fresh
 * isolated world server-side, auto-logs the guest in, hydrates onboarding (so the
 * hub renders instead of the wizard), kicks off the guided tour, and lands on the
 * hub. Only mounted in the demo build (DEMO_MODE).
 *
 * Visual language mirrors LoginPage (brand-gradient shell, top bar, BubbleField,
 * iridescent card, footer) so the public face of the product stays consistent.
 */
export default function DemoLandingPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)
  const refreshOnboarding = useOnboardingStore((s) => s.refresh)
  const startTour = useTourStore((s) => s.start)
  const isPhone = useViewportStore((s) => s.tier === 'phone')
  const lang = useLanguageStore((s) => s.lang)
  const setLang = useLanguageStore((s) => s.setLang)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(false)

  // The demo opens in Hebrew (RTL); the toggle lets the visitor switch to English
  // before starting. The chosen language rides Accept-Language on /demo/start (so the
  // world is seeded in it) and drives the whole UI. Demo build only — the global
  // languageStore default stays English for the production app.
  useEffect(() => {
    setLang('he')
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

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
    <div className="relative flex min-h-screen flex-col overflow-x-hidden bg-base text-base">
      <div className="pointer-events-none absolute inset-0 z-0 bg-brand-gradient-soft opacity-30 dark:opacity-20" />

      {/* Top bar — mirrors LoginPage. */}
      <header className="sticky top-0 z-30 border-b border-line bg-base/80 backdrop-blur-md">
        <div className="mx-auto flex h-16 w-full max-w-[1200px] items-center px-4 tablet:px-6 desktop:px-8">
          <div className="flex items-center gap-2.5">
            <div className="ring-iridescent rounded-full p-[1.5px] shadow-themed">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-gradient text-on-brand">
                <BubbleLogo className="h-5 w-5" />
              </div>
            </div>
            <span className="font-heading text-xl font-bold text-base">{t('brand.name')}</span>
          </div>
        </div>
      </header>

      {/* Hero: drifting bubble field + the start-demo card, vertically centred. */}
      <main className="relative z-10 flex w-full flex-grow items-center justify-center">
        <BubbleField />
        <div className="relative z-10 mx-auto w-full max-w-lg px-4 py-10 tablet:py-12">
          <div className="ring-iridescent animate-rise-in rounded-[2.5rem] p-[2px] shadow-bubble">
            <div className="bubble-surface relative overflow-hidden rounded-[calc(2.5rem-2px)] p-6 text-center tablet:p-10">
              <BubbleLogo className="mx-auto mb-6 h-16 w-16" />
              <h1 className="font-heading text-3xl font-extrabold text-base">{t('demo.landing.title')}</h1>
              <p className="mt-3 text-secondary">{t('demo.landing.subtitle')}</p>
              <p className="mt-2 text-sm text-muted">{t('demo.landing.blurb')}</p>

              {/* Desktop recommendation — prominent on phones, a soft hint elsewhere. */}
              <p
                className={
                  isPhone
                    ? 'mt-6 rounded-2xl border border-line bg-surface-hover px-4 py-2.5 text-sm font-medium text-secondary'
                    : 'mt-6 text-xs text-muted'
                }
              >
                {t('demo.landing.desktopNote')}
              </p>

              {/* Language choice — the world is seeded in the picked language. Hidden once
                  the seed is underway. */}
              {!loading && (
                <div className="mt-6 flex flex-col items-center gap-2">
                  <span className="text-xs text-muted">{t('demo.landing.langPrompt')}</span>
                  <div className="inline-flex rounded-full border border-line bg-surface-hover p-1">
                    {(['he', 'en'] as const).map((code) => (
                      <button
                        key={code}
                        type="button"
                        onClick={() => setLang(code)}
                        className={`rounded-full px-5 py-1.5 text-sm font-semibold transition-colors ${
                          lang === code
                            ? 'bg-brand-gradient text-on-brand shadow-themed'
                            : 'text-secondary hover:text-base'
                        }`}
                      >
                        {code === 'he' ? 'עברית' : 'English'}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* While the world seeds (a few seconds), swap the CTA for the soap-bubble
                  loader + a reassuring note so the click clearly registered. */}
              {loading ? (
                <div className="mt-6 flex flex-col items-center gap-3" role="status" aria-live="polite">
                  <BubbleLoader size={56} />
                  <p className="text-sm font-medium text-secondary">{t('demo.landing.preparing')}</p>
                </div>
              ) : (
                <Button variant="deep" size="lg" className="mt-6 w-full" onClick={handleStart}>
                  {t('demo.landing.cta')}
                </Button>
              )}

              {error && <p className="mt-4 text-sm text-danger">{t('demo.landing.error')}</p>}
              <p className="mt-6 text-xs text-muted">{t('demo.landing.disclaimer')}</p>
            </div>
          </div>
        </div>
      </main>

      <footer className="relative z-10 border-t border-line bg-base/90 backdrop-blur-md">
        <div className="mx-auto w-full max-w-[1200px] px-4 py-6 text-center desktop:px-8">
          <p className="text-xs text-muted">{t('login.footerRights')}</p>
        </div>
      </footer>
    </div>
  )
}
