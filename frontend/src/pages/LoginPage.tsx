import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { GOOGLE_OAUTH_START_URL } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import { AuthScene } from '../components/AuthScene'

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
    <AuthScene>
      <h1 className="text-3xl font-bold text-base">{t('login.headingSignIn')}</h1>
      <p className="text-sm text-muted mt-2 mb-8">{t('login.subGoogleOnly')}</p>

      {error && (
        <div className="bg-danger-soft text-danger text-sm px-4 py-2.5 rounded-2xl border border-line mb-4 animate-pop-in">
          {error}
        </div>
      )}

      <button
        type="button"
        onClick={startGoogleSignIn}
        className="w-full bg-white text-[#1f1f1f] border border-line rounded-2xl py-3.5 px-4 flex items-center justify-center gap-3 font-medium text-sm hover:bg-neutral-50 transition shadow-bubble bubble-pop"
      >
        <GoogleGlyph />
        {t('login.signInWithGoogle')}
      </button>

      <p className="mt-6 text-xs text-muted text-center leading-relaxed">
        {t('login.academicOnlyNote')}
      </p>
    </AuthScene>
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
