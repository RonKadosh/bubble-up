import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { login, register } from '../api/auth'
import { useAuthStore } from '../store/authStore'
import { Button } from '../components/Button'
import {
  BookIcon,
  BubbleLogo,
  BulbIcon,
  CapIcon,
  HeartIcon,
  PeopleIcon,
  TrendIcon,
} from '../components/Icons'

interface BackendErrorBody {
  success: boolean
  error?: {
    code: string
    message: string
    category: string
    fields?: { field: string; message: string }[]
  }
}

function describeError(e: unknown, isRegister: boolean, t: (k: string) => string): string {
  const body = (e as { response?: { data?: BackendErrorBody } })?.response?.data
  const err = body?.error
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
        ? await register(email, password)
        : await login(email, password)
      setAuth(res.accessToken, res.refreshToken, { id: res.userId, email: res.email, role: res.role })
      navigate('/dashboard')
    } catch (err) {
      setError(describeError(err, isRegister, t))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="min-h-screen flex bg-base p-4 gap-4">
      <BubbleCollage />

      <div className="flex flex-1 items-center justify-center px-6 py-10 bg-surface rounded-[2.5rem] border border-line shadow-themed">
        <div className="w-full max-w-md">
          <div className="flex items-center gap-2.5 mb-8">
            <div className="w-11 h-11 rounded-full bg-brand-gradient text-on-brand flex items-center justify-center shadow-sm ring-on-brand">
              <BubbleLogo className="w-5 h-5" />
            </div>
            <span className="text-xl font-bold text-base">{t('brand.name')}</span>
          </div>

          <h1 className="text-3xl font-bold text-base">
            {isRegister ? t('login.headingRegister') : t('login.headingSignIn')}
          </h1>
          <p className="text-sm text-muted mt-2 mb-8">
            {isRegister ? t('login.subRegister') : t('login.subSignIn')}
          </p>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
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
    </div>
  )
}

// ---------------------------------------------------------------------------
// Bubble collage — empty glassy circles, floating icon chips, label pills,
// and dashed connector swooshes. Decorative; no interactivity.
// ---------------------------------------------------------------------------

function BubbleCollage() {
  const { t } = useTranslation()
  const focusWords = t('login.collage.focusLearnGrow', { returnObjects: true }) as string[]

  return (
    <div className="hidden lg:block lg:w-[55%] relative overflow-hidden bg-base rounded-[2.5rem] border border-line shadow-themed">
      <div className="absolute inset-0 bg-brand-gradient-soft opacity-50" />

      <DashedConnectors />

      <EmptyBubble className="top-[4%] start-[22%] w-[50%]" />
      <EmptyBubble className="top-[34%] start-[63%] w-[32%]" />
      <EmptyBubble className="top-[52%] start-[10%] w-[44%]" />
      <EmptyBubble className="top-[68%] start-[58%] w-[26%]" />

      <Dot className="top-[42%] start-[6%] w-3 h-3" />
      <Dot className="top-[18%] start-[10%] w-2 h-2" />
      <Dot className="top-[58%] start-[92%] w-2.5 h-2.5" />
      <Dot className="top-[92%] start-[40%] w-2 h-2" />

      <IconChip className="top-[16%] start-[8%]" size="md"><BookIcon className="w-5 h-5" /></IconChip>
      <IconChip className="top-[3%] start-[48%]" size="sm"><PeopleIcon className="w-4 h-4" /></IconChip>
      <IconChip className="top-[14%] start-[82%]" size="md"><CapIcon className="w-5 h-5" /></IconChip>
      <IconChip className="top-[38%] start-[90%]" size="md"><TrendIcon className="w-5 h-5" /></IconChip>
      <IconChip className="top-[88%] start-[44%]" size="sm"><BulbIcon className="w-4 h-4" /></IconChip>

      <LabelPill className="top-[22%] start-[52%]" dotted>
        {focusWords.map((w, i) => (
          <span key={w} className="inline-flex items-center gap-2">
            {i > 0 && <Dotsep />}
            <span className="font-semibold">{w}</span>
          </span>
        ))}
      </LabelPill>

      <CardPill className="top-[80%] start-[6%]" icon={<PeopleIcon className="w-4 h-4" />}>
        <p className="font-semibold text-sm leading-tight">{t('login.collage.shareIdeas')}</p>
        <p className="text-xs text-muted leading-tight">{t('login.collage.shareIdeasSub')}</p>
      </CardPill>

      <CardPill className="top-[83%] start-[64%]" icon={<HeartIcon className="w-4 h-4" />}>
        <p className="font-semibold text-sm leading-tight">{t('login.collage.studyTogether')}</p>
        <p className="text-xs text-muted leading-tight">{t('login.collage.studyTogetherSub')}</p>
      </CardPill>

      <div className="absolute top-6 start-6 z-20 flex items-center gap-2.5">
        <div className="w-11 h-11 rounded-full bg-brand-gradient text-on-brand flex items-center justify-center shadow-themed ring-on-brand">
          <BubbleLogo className="w-5 h-5" />
        </div>
        <span className="text-xl font-bold text-base">{t('brand.name')}</span>
      </div>
    </div>
  )
}

function EmptyBubble({ className = '' }: { className?: string }) {
  return (
    <div
      className={`absolute aspect-square rounded-full bg-gradient-to-br from-white/85 via-primary-50/70 to-primary-200/40 ring-1 ring-primary-200/60 shadow-themed backdrop-blur-sm ${className}`}
    >
      <div className="absolute top-[6%] start-[8%] w-[28%] h-[20%] rounded-full bg-white/60 blur-md" />
    </div>
  )
}

function Dot({ className = '' }: { className?: string }) {
  return <div className={`absolute rounded-full bg-primary-200/70 ${className}`} />
}

function IconChip({
  className = '',
  children,
  size = 'md',
}: {
  className?: string
  children: React.ReactNode
  size?: 'sm' | 'md'
}) {
  const dim = size === 'sm' ? 'w-9 h-9' : 'w-11 h-11'
  return (
    <div className={`absolute ${dim} rounded-full bg-surface text-primary-600 flex items-center justify-center shadow-themed ring-1 ring-line z-10 ${className}`}>
      {children}
    </div>
  )
}

function LabelPill({
  className = '',
  children,
  dotted,
}: {
  className?: string
  children: React.ReactNode
  dotted?: boolean
}) {
  return (
    <div className={`absolute z-10 inline-flex items-center gap-2 bg-surface rounded-full px-4 py-2 text-sm text-base shadow-themed ring-1 ring-line ${className}`}>
      {dotted && <span className="w-2 h-2 rounded-full bg-brand-gradient-strong shrink-0" />}
      {children}
    </div>
  )
}

function Dotsep() {
  return <span className="w-1 h-1 rounded-full bg-primary-300 shrink-0" />
}

function CardPill({
  className = '',
  icon,
  children,
}: {
  className?: string
  icon: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <div className={`absolute z-10 inline-flex items-center gap-3 bg-surface rounded-2xl px-3.5 py-2.5 shadow-themed ring-1 ring-line ${className}`}>
      <div className="w-9 h-9 rounded-full bg-primary-50 text-primary-600 flex items-center justify-center shrink-0">
        {icon}
      </div>
      <div className="min-w-0">{children}</div>
    </div>
  )
}

function DashedConnectors() {
  return (
    <svg
      className="absolute inset-0 w-full h-full pointer-events-none text-primary-300"
      viewBox="0 0 600 700"
      preserveAspectRatio="none"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeDasharray="3 7"
      strokeLinecap="round"
      opacity="0.55"
    >
      <path d="M 60 250 Q 100 100 280 90" />
      <path d="M 410 60 Q 540 130 560 280" />
      <path d="M 580 350 Q 600 480 510 580" />
      <path d="M 110 530 Q 100 660 280 670" />
      <path d="M 300 670 Q 430 700 540 620" />
      <path d="M 230 360 Q 320 340 360 410" opacity="0.7" />
    </svg>
  )
}
