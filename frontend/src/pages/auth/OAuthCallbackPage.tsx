import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../../store/authStore'
import { BubbleLogo } from '../../components/Icons'

/**
 * Landing page after Spring Security finishes the Google OAuth round-trip.
 *
 * Success arrives as /auth/callback#accessToken=...&refreshToken=... plus the
 * AuthResponse user summary. Failures arrive as /auth/callback?error=<CODE>.
 */
export default function OAuthCallbackPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const setAuth = useAuthStore((s) => s.setAuth)
  const [status, setStatus] = useState<'working' | 'failed'>('working')

  useEffect(() => {
    const errorCode = params.get('error')
    if (errorCode) {
      navigate(`/login?error=${encodeURIComponent(errorCode)}`, { replace: true })
      return
    }

    const fragment = window.location.hash.startsWith('#')
      ? window.location.hash.slice(1)
      : window.location.hash
    const fragmentParams = new URLSearchParams(fragment)

    const accessToken = fragmentParams.get('accessToken') ?? ''
    const refreshToken = fragmentParams.get('refreshToken') ?? ''
    const userId = fragmentParams.get('userId') ?? ''
    const email = fragmentParams.get('email') ?? ''
    const role = fragmentParams.get('role') ?? ''
    const displayName = fragmentParams.get('displayName') ?? ''
    const avatarUrl = fragmentParams.get('avatarUrl') || null
    const emailVerified = fragmentParams.get('emailVerified') === 'true'

    if (!accessToken || !refreshToken) {
      navigate('/login', { replace: true })
      return
    }
    if (!userId || !email || !role || !displayName) {
      window.history.replaceState({}, '', '/auth/callback')
      setStatus('failed')
      return
    }

    window.history.replaceState({}, '', '/auth/callback')
    setAuth(accessToken, refreshToken, {
      id: userId,
      email,
      role,
      displayName,
      avatarUrl,
      emailVerified,
    })
    navigate(emailVerified ? '/dashboard' : '/auth/verify', { replace: true })
  }, [navigate, params, setAuth])

  return (
    <div className="min-h-screen bg-base flex items-center justify-center px-4">
      <div className="max-w-sm w-full bg-surface rounded-3xl shadow-themed p-8 text-center">
        <div className="mx-auto w-12 h-12 rounded-full bg-brand-gradient text-on-brand flex items-center justify-center mb-4">
          <BubbleLogo className="w-6 h-6" />
        </div>
        {status === 'working' ? (
          <>
            <h1 className="text-lg font-semibold text-base">{t('oauth.signingYouIn')}</h1>
            <p className="text-sm text-muted mt-2">{t('oauth.takesAMoment')}</p>
          </>
        ) : (
          <>
            <h1 className="text-lg font-semibold text-base">{t('oauth.somethingWentWrong')}</h1>
            <p className="text-sm text-muted mt-2">{t('oauth.tryAgain')}</p>
            <button
              onClick={() => navigate('/login', { replace: true })}
              className="mt-6 inline-block text-primary-600 font-medium hover:underline"
            >
              {t('oauth.backToSignIn')}
            </button>
          </>
        )}
      </div>
    </div>
  )
}
