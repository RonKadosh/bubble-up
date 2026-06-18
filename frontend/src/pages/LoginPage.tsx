import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { GOOGLE_OAUTH_START_URL } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import { Button } from '../components/Button'
import { BubbleField } from '../components/BubbleField'
import { BubbleLogo, ArrowRightIcon } from '../components/Icons'

/**
 * Public landing page + sign-in. A real product page (top bar → hero with the
 * sign-in card → "try the demo" call-out → footer), rendered in the Bubble.up
 * visual language (iridescent card, brand gradients, pill buttons). Google
 * OAuth is the only sign-in path — the old password form has been removed.
 *
 * The Google button is a plain anchor-style full-page navigate to the Spring
 * Security entry URL — the browser must go there directly so Spring can attach
 * the SESSION cookie used during the round-trip to Google. Don't fetch it from
 * axios; it won't work.
 */
export default function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const accessToken = useAuthStore((s) => s.accessToken)
  const [params] = useSearchParams()
  const [error, setError] = useState<string>('')

  // The real demo is being built by a teammate; for now the CTA flashes a
  // self-contained "coming soon" notice (the global Toaster isn't mounted on
  // this public route). Swap showDemoNotice for a navigate/link when it lands.
  const [demoNotice, setDemoNotice] = useState<boolean>(false)
  const demoTimer = useRef<number | null>(null)

  // Already signed in? Go straight into the app.
  useEffect(() => {
    if (!accessToken) return
    navigate('/dashboard', { replace: true })
  }, [accessToken, navigate])

  // /login?error=<CODE> happens when the OAuth callback redirected us here
  // (e.g. NOT_ACADEMIC_EMAIL after we rejected a non-.ac.il Google email).
  useEffect(() => {
    const code = params.get('error')
    if (!code) return
    setError(errorMessage(code, t))
  }, [params, t])

  // Clear any pending notice timer on unmount.
  useEffect(() => () => { if (demoTimer.current) window.clearTimeout(demoTimer.current) }, [])

  function startGoogleSignIn() {
    // Full-page navigate. The backend issues an HTTP 302 to accounts.google.com.
    window.location.assign(GOOGLE_OAUTH_START_URL)
  }

  function showDemoNotice() {
    setDemoNotice(true)
    if (demoTimer.current) window.clearTimeout(demoTimer.current)
    demoTimer.current = window.setTimeout(() => setDemoNotice(false), 3600)
  }

  return (
    <div className="relative flex min-h-screen flex-col overflow-x-hidden bg-base text-base">
      <div className="pointer-events-none absolute inset-0 z-0 bg-brand-gradient-soft opacity-30 dark:opacity-20" />

      {/* Top bar */}
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

      {/* Hero: drifting bubble field + copy + sign-in card. The whole sign-in
          surface lives in a single viewport — main fills the space between the
          header and footer and vertically centres its content, so the footer
          stays in view without scrolling. The demo call-out is folded into the
          hero copy (no separate below-the-fold band). */}
      <main className="relative z-10 flex w-full flex-grow items-center">
        <BubbleField />
        <div className="relative z-10 mx-auto flex w-full max-w-[1200px] flex-col items-center gap-10 px-4 py-10 tablet:py-12 desktop:flex-row desktop:items-center desktop:justify-between desktop:gap-16 desktop:px-8 desktop:py-12">
          <div className="w-full space-y-6 text-center desktop:max-w-[46%] desktop:text-start">
            <h1 className="font-heading text-4xl font-extrabold leading-tight text-base tablet:text-5xl desktop:text-6xl">
              {t('login.heroTitle')}{' '}
              <span className="text-primary-600">{t('login.heroTitleAccent')}</span>
            </h1>
            <p className="mx-auto max-w-md text-base leading-relaxed text-secondary tablet:text-lg desktop:mx-0">
              {t('login.heroSubtitle')}
            </p>

            {/* Demo call-out, folded into the hero so the page stays one screen. */}
            <div className="flex flex-col items-center gap-3 desktop:items-start">
              <Button
                variant="secondary"
                size="lg"
                onClick={showDemoNotice}
                aria-label={t('login.demoCta')}
                rightIcon={<ArrowRightIcon className="h-5 w-5 rtl:rotate-180" />}
              >
                {t('login.demoCta')}
              </Button>
              {demoNotice && (
                <p role="status" className="animate-pop-in rounded-full bg-surface-hover px-4 py-2 text-sm text-secondary">
                  {t('login.demoComingSoon')}
                </p>
              )}
            </div>
          </div>

          <div className="w-full max-w-md">
            <div className="ring-iridescent animate-rise-in rounded-[2.5rem] p-[2px] shadow-bubble">
              <div className="bubble-surface relative overflow-hidden rounded-[calc(2.5rem-2px)] p-6 tablet:p-8 desktop:p-10">
                <h2 className="text-2xl font-bold text-base">{t('login.headingSignIn')}</h2>
                <p className="mb-8 mt-2 text-sm text-muted">{t('login.subGoogleOnly')}</p>

                {error && (
                  <div
                    role="alert"
                    className="mb-4 animate-pop-in rounded-2xl border border-line bg-danger-soft px-4 py-2.5 text-sm text-danger"
                  >
                    {error}
                  </div>
                )}

                <button
                  type="button"
                  onClick={startGoogleSignIn}
                  className="flex w-full items-center justify-center gap-3 rounded-2xl border border-line bg-white px-4 py-3.5 text-sm font-medium text-[#1f1f1f] shadow-bubble bubble-pop transition hover:bg-surface-hover"
                >
                  <GoogleGlyph />
                  {t('login.signInWithGoogle')}
                </button>

                <p className="mt-6 text-center text-xs leading-relaxed text-muted">
                  {t('login.academicOnlyNote')}
                </p>
              </div>
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

/**
 * Map a backend OAuth error code (from the /login?error=... query string
 * the OAuth2LoginSuccessHandler.buildErrorRedirect writes) to a friendly
 * Hebrew/English string for the user.
 */
function errorMessage(code: string, t: (k: string) => string): string {
  switch (code) {
    case 'SESSION_EXPIRED':         return t('login.errorSessionExpired')
    case 'NOT_ACADEMIC_EMAIL':       return t('login.errorNotAcademic')
    case 'OAUTH_EMAIL_UNVERIFIED':   return t('login.errorGoogleUnverified')
    case 'OAUTH_EMAIL_MISSING':      return t('login.errorGoogleNoEmail')
    case 'ACCOUNT_BANNED':           return t('login.errorBanned')
    case 'ACCOUNT_SUSPENDED':        return t('login.errorSuspended')
    case 'OAUTH_FAILED':             return t('login.errorOAuthCancelled')
    default:                         return t('login.errorSignInGeneric')
  }
}

/** Google's official multi-color "G" mark. Inline SVG so we don't need
 * a brand asset at build time. */
function GoogleGlyph() {
  return (
    <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true">
      <path fill="#FFC107" d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8a12 12 0 1 1 0-24c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z"/>
      <path fill="#FF3D00" d="m6.306 14.691 6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z"/>
      <path fill="#4CAF50" d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238C29.211 35.091 26.715 36 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z"/>
      <path fill="#1976D2" d="M43.611 20.083H42V20H24v8h11.303a12.04 12.04 0 0 1-4.087 5.571l.001-.001 6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z"/>
    </svg>
  )
}
