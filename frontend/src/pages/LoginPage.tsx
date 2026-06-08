import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { login, register } from '../api/auth'
import { errorBody } from '../api/errors'
import { useAuthStore } from '../store/authStore'
import { Button } from '../components/Button'
import { BubbleLogo } from '../components/Icons'

function describeError(e: unknown, isRegister: boolean, t: (k: string) => string): string {
  const err = errorBody(e)
  if (err?.fields?.length) {
    return err.fields.map((f) => `${f.field}: ${f.message}`).join(' • ')
  }
  if (err?.code === 'INVALID_CREDENTIALS') return t('login.errorInvalidCredentials')
  if (err?.code === 'EMAIL_ALREADY_EXISTS') return t('login.errorEmailExists')
  if (err?.message) return err.message
  return isRegister ? t('login.errorRegisterGeneric') : t('login.errorSignInGeneric')
}

export default function LoginPage() {
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
      })
      navigate('/dashboard')
    } catch (err) {
      setError(describeError(err, isRegister, t))
    } finally {
      setSubmitting(false)
    }
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
          <div className="bubble-surface relative overflow-hidden bg-surface rounded-[2.5rem] p-6 tablet:p-8 desktop:p-10">
          <h1 className="text-3xl font-bold text-base">
            {isRegister ? t('login.headingRegister') : t('login.headingSignIn')}
          </h1>
          <p className="text-sm text-muted mt-2 mb-8">
            {isRegister ? t('login.subRegister') : t('login.subSignIn')}
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            {isRegister && (
              <div>
                <label className="text-xs font-medium text-secondary mb-1 block">{t('login.displayName')}</label>
                <input
                  type="text"
                  placeholder={t('login.displayNamePlaceholder')}
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-3 text-sm focus:outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100 transition"
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
                className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-3 text-sm focus:outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100 transition"
                required
                autoComplete="email"
                dir="ltr"
              />
            </div>

            <div>
              <label className="text-xs font-medium text-secondary mb-1 block">{t('login.password')}</label>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  placeholder={isRegister ? t('login.passwordPlaceholderRegister') : '••••••••'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full bg-surface text-base border border-line rounded-2xl px-4 py-3 pe-14 text-sm focus:outline-none focus:border-primary-400 focus:ring-2 focus:ring-primary-100 transition"
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
              <div className="bg-danger-soft text-danger text-sm px-4 py-2.5 rounded-2xl border border-line">
                {error}
              </div>
            )}

            <Button
              type="submit"
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
              onClick={() => { setIsRegister(!isRegister); setError('') }}
              className="text-primary-600 font-medium hover:underline"
            >
              {isRegister ? t('login.switchToSignInAction') : t('login.switchToRegisterAction')}
            </button>
          </div>
        </div>
        </div>
      </main>
      </div>
    </div>
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
