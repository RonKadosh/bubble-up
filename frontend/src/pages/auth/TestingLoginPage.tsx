import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login, register } from '../../api/auth'
import { errorBody } from '../../api/errors'
import { useAuthStore } from '../../store/authStore'
import { Button } from '../../components/Button'
import { BubbleField } from '../../components/BubbleField'

/**
 * DEV-ONLY password sign-in / register, reachable at `/login/testing`.
 *
 * Production uses Google OAuth exclusively. This page exists so teammates can
 * sign in with email + password (the legacy JWT path) without a Google
 * round-trip, and spin up arbitrary throwaway accounts during local testing.
 *
 * It is registered in App.tsx only when `import.meta.env.DEV` is true, and the
 * route + this module + the `login`/`register` API code are dead-code-eliminated
 * from `vite build` output — so active users can never reach it.
 *
 * Strings are intentionally plain English (no i18n): this is a developer tool,
 * which the repo's i18n rule explicitly exempts.
 */

/** Shared input look: soft 2xl corners + the iridescent focus ring. */
const inputClass =
  'w-full bg-surface text-base border border-line rounded-2xl px-4 py-3 text-sm transition focus-bubble'

export default function TestingLoginPage() {
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
        // Password/testing accounts are trusted as verified — there is no
        // separate email-verification step for them on the backend.
        emailVerified: true,
      })
      navigate('/dashboard')
    } catch (err) {
      const body = errorBody(err)
      if (body?.fields?.length) {
        setError(body.fields.map((f) => `${f.field}: ${f.message}`).join(' | '))
      } else {
        setError(body?.message || (isRegister ? 'Could not register.' : 'Could not sign in.'))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-base px-4 py-10">
      <div className="pointer-events-none absolute inset-0 z-0 bg-brand-gradient-soft opacity-30 dark:opacity-20" />
      <BubbleField />

      <div className="relative z-10 w-full max-w-md">
        <div className="ring-iridescent animate-rise-in rounded-[2.5rem] p-[2px] shadow-bubble">
          <div className="bubble-surface relative overflow-hidden rounded-[calc(2.5rem-2px)] p-6 tablet:p-8">
            {/* Unmistakable DEV banner — this is not the real sign-in. */}
            <div className="mb-6 rounded-2xl border border-warning-soft bg-warning-soft px-4 py-2.5 text-center text-xs font-semibold text-warning">
              DEV ONLY · password login · not available in production
            </div>

            {/* Mode toggle — a segmented pill, the active half on the brand gradient. */}
            <div className="mb-6 flex rounded-full border border-line bg-surface-muted p-1" role="tablist">
              <ModeTab selected={!isRegister} onClick={() => switchMode(false)}>
                Sign in
              </ModeTab>
              <ModeTab selected={isRegister} onClick={() => switchMode(true)}>
                Register
              </ModeTab>
            </div>

            <h1 className="text-2xl font-bold text-base">
              {isRegister ? 'Create a test account' : 'Sign in for testing'}
            </h1>
            <p className="mb-8 mt-2 text-sm text-muted">
              {isRegister
                ? 'Registers a password account directly against the backend.'
                : 'Signs in with email + password (legacy JWT path).'}
            </p>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              {isRegister && (
                <div className="animate-pop-in">
                  <label className="mb-1 block text-xs font-medium text-secondary">Display name</label>
                  <input
                    type="text"
                    placeholder="Ada Lovelace"
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
                <label className="mb-1 block text-xs font-medium text-secondary">Email</label>
                <input
                  type="email"
                  placeholder="you@post.bgu.ac.il"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className={inputClass}
                  required
                  autoComplete="email"
                  dir="ltr"
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-medium text-secondary">Password</label>
                <div className="relative" dir="ltr">
                  <input
                    type={showPassword ? 'text' : 'password'}
                    placeholder="password"
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
                    {showPassword ? 'Hide' : 'Show'}
                  </button>
                </div>
                {isRegister && (
                  <p className="mt-1.5 text-xs text-muted">At least 8 characters.</p>
                )}
              </div>

              {error && (
                <div className="animate-pop-in rounded-2xl border border-line bg-danger-soft px-4 py-2.5 text-sm text-danger">
                  {error}
                </div>
              )}

              <Button type="submit" variant="deep" size="lg" disabled={submitting} className="w-full shadow-bubble">
                {submitting
                  ? (isRegister ? 'Registering…' : 'Signing in…')
                  : (isRegister ? 'Register' : 'Sign in')}
              </Button>
            </form>
          </div>
        </div>
      </div>
    </div>
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
