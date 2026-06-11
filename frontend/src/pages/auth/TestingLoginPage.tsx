import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { login, register } from '../../api/auth'
import { errorBody } from '../../api/errors'
import { useAuthStore } from '../../store/authStore'
import { Button } from '../../components/Button'
import { AuthScene } from '../../components/AuthScene'

function describeError(e: unknown, isRegister: boolean, t: (k: string) => string): string {
  const err = errorBody(e)
  if (err?.fields?.length) {
    return err.fields.map((f) => `${f.field}: ${f.message}`).join(' | ')
  }
  if (err?.code === 'INVALID_CREDENTIALS') return t('login.errorInvalidCredentials')
  if (err?.code === 'EMAIL_ALREADY_EXISTS') return t('login.errorEmailExists')
  if (err?.message) return err.message
  return isRegister ? t('login.errorRegisterGeneric') : t('login.errorSignInGeneric')
}

/** The shared input look: soft 2xl corners + the iridescent focus ring. */
const inputClass =
  'w-full bg-surface text-base border border-line rounded-2xl px-4 py-3 text-sm transition focus-bubble'

export default function TestingLoginPage() {
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [isRegister, setIsRegister] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const navigate = useNavigate()
  const setAuth = useAuthStore((s) => s.setAuth)

  function switchMode(register: boolean) {
    setIsRegister(register)
    setError('')
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const res = isRegister
        ? await register(email, password, displayName.trim())
        : await login(email, password)
      setAuth(res.accessToken, res.refreshToken, {
        id: res.userId,
        email: res.email,
        role: res.role,
        displayName: res.displayName,
        avatarUrl: res.avatarUrl,
        // Hidden testing route: keep legacy password accounts usable in dev.
        emailVerified: true,
      })
      navigate('/dashboard')
    } catch (err) {
      setError(describeError(err, isRegister, t))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthScene>
      {/* Mode toggle — a segmented pill, the active half on the brand gradient. */}
      <div className="flex rounded-full bg-surface-muted border border-line p-1 mb-6" role="tablist">
        <ModeTab selected={!isRegister} onClick={() => switchMode(false)}>
          {t('login.tabSignIn')}
        </ModeTab>
        <ModeTab selected={isRegister} onClick={() => switchMode(true)}>
          {t('login.tabRegister')}
        </ModeTab>
      </div>

      <h1 className="text-3xl font-bold text-base">
        {isRegister ? t('login.headingRegister') : t('login.headingSignIn')}
      </h1>
      <p className="text-sm text-muted mt-2 mb-8">
        {isRegister ? t('login.subRegister') : t('login.subSignIn')}
      </p>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        {isRegister && (
          <div className="animate-pop-in">
            <label className="text-xs font-medium text-secondary mb-1 block">{t('login.displayName')}</label>
            <input
              type="text"
              placeholder={t('login.displayNamePlaceholder')}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className={inputClass}
              required
              maxLength={100}
              autoComplete="name"
            />
          </div>
        )}
        <div>
          <label className="text-xs font-medium text-secondary mb-1 block">{t('login.email')}</label>
          <input
            type="email"
            placeholder={t('login.emailPlaceholder')}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={inputClass}
            required
            autoComplete="email"
            dir="ltr"
          />
        </div>

        <div>
          <label className="text-xs font-medium text-secondary mb-1 block">{t('login.password')}</label>
          {/* Credential field is LTR (dir on the input) — keep the wrapper LTR
              too so the reveal button's logical `end-2` lands on the same side
              as the input's reserved `pe-14` padding (otherwise the password
              text overlaps the button under RTL). */}
          <div className="relative" dir="ltr">
            <input
              type={showPassword ? 'text' : 'password'}
              placeholder={isRegister ? t('login.passwordPlaceholderRegister') : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className={`${inputClass} pe-14`}
              required
              minLength={8}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
              dir="ltr"
            />
            <button
              type="button"
              onClick={() => setShowPassword((s) => !s)}
              className="absolute inset-y-0 end-2 px-2 text-xs text-muted hover:text-primary-600"
              tabIndex={-1}
            >
              {showPassword ? t('login.hide') : t('login.show')}
            </button>
          </div>
          {isRegister && (
            <p className="text-xs text-muted mt-1.5">{t('login.passwordHint')}</p>
          )}
        </div>

        {error && (
          <div className="bg-danger-soft text-danger text-sm px-4 py-2.5 rounded-2xl border border-line animate-pop-in">
            {error}
          </div>
        )}

        <Button
          type="submit"
          variant="deep"
          size="lg"
          disabled={submitting}
          className="w-full shadow-bubble"
        >
          {submitting
            ? (isRegister ? t('login.submittingRegister') : t('login.submittingSignIn'))
            : (isRegister ? t('login.submitRegister') : t('login.submitSignIn'))}
        </Button>
      </form>

      <div className="mt-6 text-center text-sm text-secondary">
        {isRegister ? t('login.switchToSignInPrompt') : t('login.switchToRegisterPrompt')}{' '}
        <button
          onClick={() => switchMode(!isRegister)}
          className="text-primary-600 font-medium hover:underline"
        >
          {isRegister ? t('login.switchToSignInAction') : t('login.switchToRegisterAction')}
        </button>
      </div>
    </AuthScene>
  )
}

function ModeTab({ selected, onClick, children }: {
  selected: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={selected}
      onClick={onClick}
      className={`flex-1 rounded-full px-4 py-2 text-sm font-semibold transition ${
        selected
          ? 'bg-brand-gradient-strong text-on-brand shadow-themed'
          : 'text-secondary hover:text-base'
      }`}
    >
      {children}
    </button>
  )
}
