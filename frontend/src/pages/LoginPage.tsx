import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { GOOGLE_OAUTH_START_URL } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import { BubbleLogo } from '../components/Icons'

/**
 * Single-button sign-in screen. The button is a plain anchor pointing at
 * the Spring Security entry URL — the browser must navigate there directly
 * so Spring can attach the SESSION cookie used during the round-trip to
 * Google. Don't fetch this URL from axios; it won't work.
 */
export default function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const accessToken = useAuthStore((s) => s.accessToken)
  const emailVerified = useAuthStore((s) => s.user?.emailVerified ?? false)
  const [params] = useSearchParams()
  const [error, setError] = useState<string>('')

  // Signed-in users continue wherever they left off: verified users go into
  // the app; first-time academic Google signups continue to the Bubble.up
  // verification screen until they click the email link.
  useEffect(() => {
    if (!accessToken) return
    navigate(emailVerified ? '/dashboard' : '/auth/verify', { replace: true })
  }, [accessToken, emailVerified, navigate])

  // /login?error=<CODE> happens when the OAuth callback redirected us here
  // (e.g. NOT_ACADEMIC_EMAIL after we rejected a non-.ac.il Google email).
  useEffect(() => {
    const code = params.get('error')
    if (!code) return
    setError(errorMessage(code, t))
  }, [params, t])

  function startGoogleSignIn() {
    // Full-page navigate. The backend issues an HTTP 302 to accounts.google.com.
    window.location.assign(GOOGLE_OAUTH_START_URL)
  }

  return (
    <div className="min-h-screen relative overflow-hidden bg-base">
      <div className="absolute inset-0 bg-brand-gradient-soft opacity-40 pointer-events-none" />

      <div className="relative min-h-screen mx-auto max-w-[1200px]">
        <header className="absolute top-4 start-4 tablet:top-6 tablet:start-6 desktop:top-8 desktop:start-8 z-30 flex items-center gap-2.5">
          <div className="ring-iridescent p-[1.5px] rounded-full shadow-themed">
            <div className="w-11 h-11 rounded-full bg-brand-gradient text-on-brand flex items-center justify-center">
              <BubbleLogo className="w-5 h-5" />
            </div>
          </div>
          <span className="text-xl font-bold text-base">{t('brand.name')}</span>
        </header>

        <div className="hidden desktop:block absolute inset-0 pointer-events-none">
          <PhotoBubble src="/images/photo-books.jpg"  alt="" className="top-[15%]  start-[38%] w-[18%]" />
          <PhotoBubble src="/images/photo-group.jpg"  alt="" className="top-[10%] start-[20%]  w-[24%]" />
          <PhotoBubble src="/images/photo-tablet.jpg" alt="" className="top-[40%] start-[14%] w-[18%]" />

          <EmptyBubble className="top-[44%] start-[36%] w-[10%]" />
          <EmptyBubble className="top-[41%] start-[50%] w-[15%]" />

          <EmptyBubble className="top-[60%] start-[44%] w-[8%]"  />
          <EmptyBubble className="top-[62%] start-[66%] w-[7%]"  />
          <EmptyBubble className="top-[64%] start-[35%]  w-[7%]"  />

          <EmptyBubble className="top-[78%] start-[48%] w-[5%]"  />
          <EmptyBubble className="top-[76%] start-[60%] w-[5%]"  />

          <EmptyBubble className="top-[90%] start-[80%] w-[3%]"  />
          <EmptyBubble className="top-[86%] start-[68%] w-[4%]"  />
        </div>

        <main className="relative z-20 min-h-screen flex items-start justify-center desktop:justify-end px-4 tablet:px-6 desktop:pe-4 pt-[14vh] tablet:pt-[12vh] desktop:pt-[14vh] pb-12 tablet:pb-24">
          <div className="w-full max-w-md ring-iridescent p-[2px] rounded-[2.5rem] shadow-themed">
          <div className="bg-surface rounded-[2.5rem] p-6 tablet:p-8 desktop:p-10">
            <h1 className="text-3xl font-bold text-base">
              {t('login.entryHeading', { defaultValue: 'Get started' })}
            </h1>
            <p className="text-sm text-muted mt-2 mb-8">
              {t('login.entrySub', {
                defaultValue: 'Continue with your Israeli academic Google account.',
              })}
            </p>

            {error && (
              <div className="bg-danger-soft text-danger text-sm px-4 py-2.5 rounded-2xl border border-line mb-4">
                {error}
              </div>
            )}

            <button
              type="button"
              onClick={startGoogleSignIn}
              className="w-full bg-white text-[#1f1f1f] border border-line rounded-2xl py-3.5 px-4 flex items-center justify-center gap-3 font-medium text-sm hover:bg-neutral-50 transition shadow-bubble"
            >
              <GoogleGlyph />
              {t('login.entryButton', { defaultValue: 'Continue with Google' })}
            </button>

            <p className="mt-6 text-xs text-muted text-center leading-relaxed">
              {t('login.entryNote', {
                defaultValue:
                  "On your first sign-up, Bubble.up will send a verification link to that academic inbox before access is unlocked.",
              })}
            </p>
          </div>
          </div>
        </main>
      </div>
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

function PhotoBubble({ src, alt, className = '' }: { src: string; alt: string; className?: string }) {
  return (
    <div className={`absolute aspect-square rounded-full ring-iridescent p-[2px] shadow-themed ${className}`}>
      <div className="w-full h-full rounded-full overflow-hidden bg-surface">
        <img src={src} alt={alt} className="w-full h-full object-cover" />
      </div>
    </div>
  )
}

function EmptyBubble({ className = '' }: { className?: string }) {
  return (
    <div className={`absolute aspect-square rounded-full ring-iridescent p-[2px] shadow-themed ${className}`}>
      <div className="w-full h-full rounded-full bg-gradient-to-br from-white/85 via-primary-50/70 to-primary-200/40 backdrop-blur-sm relative overflow-hidden">
        <div className="absolute top-[6%] start-[8%] w-[28%] h-[20%] rounded-full bg-white/60 blur-md" />
      </div>
    </div>
  )
}
