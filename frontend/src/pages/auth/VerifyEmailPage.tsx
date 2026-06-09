import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  confirmVerificationEmail,
  requestVerificationEmail,
} from '../../api/auth'
import { errorBody } from '../../api/errors'
import { useAuthStore } from '../../store/authStore'
import { BubbleLogo } from '../../components/Icons'

type Mode = 'enter-email' | 'sent' | 'redeeming' | 'redeemed' | 'error'

/**
 * Dual-purpose page:
 *
 * <ul>
 *   <li><code>/auth/verify</code> (no token) — entered the academic email
 *       form. The user is signed in but emailVerified=false because they
 *       used a non-.ac.il Google account.</li>
 *   <li><code>/auth/verify?token=...</code> — landed here from the link in
 *       the verification email. We POST the token and update authStore.</li>
 * </ul>
 */
export default function VerifyEmailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const updateUser = useAuthStore((s) => s.updateUser)
  const accessToken = useAuthStore((s) => s.accessToken)

  const tokenFromUrl = params.get('token')
  const [mode, setMode] = useState<Mode>(tokenFromUrl ? 'redeeming' : 'enter-email')
  const [academicEmail, setAcademicEmail] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  // Token-redemption branch: fires once on mount when token is in the URL.
  useEffect(() => {
    if (!tokenFromUrl) return
    void (async () => {
      try {
        const res = await confirmVerificationEmail(tokenFromUrl)
        updateUser({ emailVerified: res.emailVerified, email: res.email })
        setMode('redeemed')
        // Slight delay so the user reads the "Verified!" message before the redirect.
        setTimeout(() => navigate('/dashboard', { replace: true }), 1500)
      } catch (e) {
        setError(describeVerifyError(e, t))
        setMode('error')
      }
    })()
  }, [tokenFromUrl, updateUser, navigate, t])

  // Form-submission branch: send the verification email.
  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      await requestVerificationEmail(academicEmail.trim())
      setMode('sent')
    } catch (err) {
      setError(describeRequestError(err, t))
    } finally {
      setSubmitting(false)
    }
  }

  // If the user lands here unauthenticated AND there's no token, send them home.
  useEffect(() => {
    if (!tokenFromUrl && !accessToken) navigate('/login', { replace: true })
  }, [tokenFromUrl, accessToken, navigate])

  return (
    <div className="min-h-screen bg-base flex items-center justify-center px-4">
      <div className="max-w-md w-full bg-surface rounded-3xl shadow-themed p-8">
        <div className="mx-auto w-12 h-12 rounded-full bg-brand-gradient text-on-brand flex items-center justify-center mb-4">
          <BubbleLogo className="w-6 h-6" />
        </div>

        {mode === 'enter-email' && (
          <>
            <h1 className="text-xl font-semibold text-base text-center">
              {t('verifyEmail.headingEnter')}
            </h1>
            <p className="text-sm text-muted text-center mt-2 mb-6">
              {t('verifyEmail.subEnter')}
            </p>

            <form onSubmit={handleSubmit} className="flex flex-col gap-3">
              <input
                type="email"
                placeholder={t('verifyEmail.emailPlaceholder')}
                value={academicEmail}
                onChange={(e) => setAcademicEmail(e.target.value)}
                className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-3 text-sm focus:outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100 transition"
                required
                dir="ltr"
                autoComplete="email"
              />
              {error && (
                <div className="bg-danger-soft text-danger text-sm px-4 py-2.5 rounded-2xl border border-line">
                  {error}
                </div>
              )}
              <button
                type="submit"
                disabled={submitting}
                className="w-full bg-brand-gradient text-on-brand font-medium text-sm rounded-2xl py-3.5 px-4 hover:opacity-95 disabled:opacity-60 transition shadow-bubble"
              >
                {submitting ? t('verifyEmail.sending') : t('verifyEmail.sendLink')}
              </button>
            </form>
          </>
        )}

        {mode === 'sent' && (
          <div className="text-center">
            <h1 className="text-xl font-semibold text-base">{t('verifyEmail.sentHeading')}</h1>
            <p className="text-sm text-muted mt-2">
              {t('verifyEmail.sentBody', { email: academicEmail })}
            </p>
            <button
              onClick={() => setMode('enter-email')}
              className="mt-6 text-sm text-primary-600 font-medium hover:underline"
            >
              {t('verifyEmail.changeEmail')}
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
            <button
              onClick={() => navigate('/auth/verify', { replace: true })}
              className="mt-6 text-primary-600 font-medium hover:underline"
            >
              {t('verifyEmail.tryAgain')}
            </button>
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
  return err?.message || t('verifyEmail.errorGeneric')
}

function describeRequestError(e: unknown, t: (k: string) => string): string {
  const err = errorBody(e)
  if (err?.code === 'NOT_ACADEMIC_EMAIL') return t('verifyEmail.errorNotAcademic')
  if (err?.code === 'EMAIL_ALREADY_VERIFIED') return t('verifyEmail.errorAlreadyVerified')
  if (err?.code === 'VERIFICATION_TOO_MANY_REQUESTS') return t('verifyEmail.errorRateLimited')
  if (err?.code === 'MAIL_NOT_CONFIGURED') return t('verifyEmail.errorMailDown')
  return err?.message || t('verifyEmail.errorGeneric')
}
