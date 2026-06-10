import { useEffect, useState } from 'react'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  confirmVerificationEmail,
  requestVerificationEmail,
} from '../../api/auth'
import { errorBody, errorCode } from '../../api/errors'
import { useAuthStore } from '../../store/authStore'
import { BubbleLogo } from '../../components/Icons'

type Mode = 'sending' | 'sent' | 'redeeming' | 'redeemed' | 'error'
const autoVerificationRequests = new Map<string, Promise<void>>()

/**
 * Verification landing page for Bubble.up's first-signup email step.
 *
 * - `/auth/verify` without a token shows a live "check your inbox" screen for
 *   the current signed-in user and auto-sends the verification email once.
 * - `/auth/verify?token=...` redeems the link from the email.
 */
export default function VerifyEmailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const updateUser = useAuthStore((s) => s.updateUser)
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const accessToken = useAuthStore((s) => s.accessToken)
  const user = useAuthStore((s) => s.user)

  const tokenFromUrl = params.get('token')
  const [mode, setMode] = useState<Mode>(tokenFromUrl ? 'redeeming' : 'sending')
  const [error, setError] = useState('')

  useEffect(() => {
    if (!tokenFromUrl) return
    void (async () => {
      try {
        const res = await confirmVerificationEmail(tokenFromUrl)
        clearVerificationMarker(user?.id)
        if (accessToken) {
          updateUser({ emailVerified: res.emailVerified, email: res.email })
        }
        setMode('redeemed')
        setTimeout(
          () => navigate(accessToken ? '/dashboard' : '/login', { replace: true }),
          1500,
        )
      } catch (e) {
        setError(describeVerifyError(e, t))
        setMode('error')
      }
    })()
  }, [tokenFromUrl, updateUser, navigate, t, accessToken, user?.id])

  useEffect(() => {
    if (tokenFromUrl) return
    if (!accessToken || !user) return
    if (user.emailVerified) {
      navigate('/dashboard', { replace: true })
      return
    }
    const marker = verificationMarker(user.id)
    if (window.sessionStorage.getItem(marker) === 'sent') {
      setMode('sent')
      return
    }
    void sendVerificationEmail(true)
  }, [tokenFromUrl, accessToken, user, navigate])

  if (!tokenFromUrl && (!accessToken || !user)) {
    return <Navigate to="/login" replace />
  }

  async function sendVerificationEmail(isAutoSend = false) {
    if (!user) return
    const marker = verificationMarker(user.id)
    const inFlight = isAutoSend ? autoVerificationRequests.get(marker) : undefined
    if (inFlight) {
      setError('')
      setMode('sending')
      try {
        await inFlight
        setMode('sent')
      } catch (err) {
        setError(describeRequestError(err, t))
        setMode('error')
      }
      return
    }

    setError('')
    setMode('sending')
    const request = requestVerificationEmail(user.email)
    if (isAutoSend) autoVerificationRequests.set(marker, request)
    try {
      await request
      window.sessionStorage.setItem(marker, 'sent')
      setMode('sent')
    } catch (err) {
      if (isRestartRequired(err)) {
        clearVerificationMarker(user.id)
        clearAuth()
        navigate('/login?error=SESSION_EXPIRED', { replace: true })
        return
      }
      setError(describeRequestError(err, t))
      setMode('error')
    } finally {
      if (isAutoSend) autoVerificationRequests.delete(marker)
    }
  }

  return (
    <div className="min-h-screen bg-base flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-surface rounded-3xl shadow-themed p-8">
        <div className="mx-auto w-12 h-12 rounded-full bg-brand-gradient text-on-brand flex items-center justify-center mb-4">
          <BubbleLogo className="w-6 h-6" />
        </div>

        {mode === 'sending' && (
          <div className="text-center">
            <h1 className="text-lg font-semibold text-base">
              {t('verifyEmail.sentHeading', { defaultValue: 'Check your inbox' })}
            </h1>
            <p className="text-sm text-muted mt-2">
              {t('verifyEmail.sending', { defaultValue: 'Sending verification email...' })}
            </p>
          </div>
        )}

        {mode === 'sent' && user && (
          <div className="text-center">
            <h1 className="text-xl font-semibold text-base">
              {t('verifyEmail.sentHeading', { defaultValue: 'Check your inbox' })}
            </h1>
            <p className="text-sm text-muted mt-2">
              {t('verifyEmail.sentBody', {
                email: user.email,
                defaultValue: `We sent a verification link to ${user.email}.`,
              })}
            </p>
            <p className="text-sm text-muted mt-3">
              {t('verifyEmail.subEnter', {
                defaultValue: 'Open the Bubble.up email and click the link to finish your first sign-up.',
              })}
            </p>
            <button
              type="button"
              onClick={() => void sendVerificationEmail(false)}
              className="mt-6 w-full bg-brand-gradient text-on-brand font-medium text-sm rounded-2xl py-3.5 px-4 hover:opacity-95 transition shadow-bubble"
            >
              {t('verifyEmail.sendLink', { defaultValue: 'Resend verification link' })}
            </button>
          </div>
        )}

        {mode === 'redeeming' && (
          <div className="text-center">
            <h1 className="text-lg font-semibold text-base">{t('verifyEmail.verifyingHeading')}</h1>
            <p className="text-sm text-muted mt-2">{t('verifyEmail.verifyingSub')}</p>
          </div>
        )}

        {mode === 'redeemed' && (
          <div className="text-center">
            <h1 className="text-xl font-semibold text-base">{t('verifyEmail.successHeading')}</h1>
            <p className="text-sm text-muted mt-2">{t('verifyEmail.successSub')}</p>
          </div>
        )}

        {mode === 'error' && (
          <div className="text-center">
            <h1 className="text-lg font-semibold text-base">
              {t('verifyEmail.errorHeading')}
            </h1>
            <p className="text-sm text-muted mt-2">{error}</p>
            {user ? (
              <button
                type="button"
                onClick={() => void sendVerificationEmail(false)}
                className="mt-6 w-full bg-brand-gradient text-on-brand font-medium text-sm rounded-2xl py-3.5 px-4 hover:opacity-95 transition shadow-bubble"
              >
                {t('verifyEmail.sendLink', { defaultValue: 'Resend verification link' })}
              </button>
            ) : (
              <button
                onClick={() => navigate('/login', { replace: true })}
                className="mt-6 text-primary-600 font-medium hover:underline"
              >
                {t('oauth.backToSignIn')}
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function describeVerifyError(e: unknown, t: (k: string) => string): string {
  const err = errorBody(e)
  if (err?.code === 'VERIFICATION_TOKEN_EXPIRED') return t('verifyEmail.errorExpired')
  if (err?.code === 'VERIFICATION_TOKEN_INVALID') return t('verifyEmail.errorInvalidToken')
  if (err?.code === 'USER_NOT_FOUND') return t('verifyEmail.errorRestartSignUp')
  return err?.message || t('verifyEmail.errorGeneric')
}

function describeRequestError(e: unknown, t: (k: string) => string): string {
  const err = errorBody(e)
  if (err?.code === 'NOT_ACADEMIC_EMAIL') return t('verifyEmail.errorNotAcademic')
  if (err?.code === 'EMAIL_ALREADY_VERIFIED') return t('verifyEmail.errorAlreadyVerified')
  if (err?.code === 'VERIFICATION_TOO_MANY_REQUESTS') return t('verifyEmail.errorRateLimited')
  if (err?.code === 'MAIL_NOT_CONFIGURED') return t('verifyEmail.errorMailDown')
  if (err?.code === 'MAIL_SEND_FAILED') return t('verifyEmail.errorMailDown')
  if (err?.code === 'USER_NOT_FOUND') return t('verifyEmail.errorRestartSignUp')
  return err?.message || t('verifyEmail.errorGeneric')
}

function isRestartRequired(e: unknown): boolean {
  const code = errorCode(e)
  return code === 'USER_NOT_FOUND' || code === 'UNAUTHORIZED'
}

function verificationMarker(userId: string | undefined): string {
  return `bubbleup-verification-requested:${userId ?? 'anonymous'}`
}

function clearVerificationMarker(userId: string | undefined) {
  window.sessionStorage.removeItem(verificationMarker(userId))
}
