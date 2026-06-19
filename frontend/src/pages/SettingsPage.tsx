import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Card } from '../components/Card'
import { PageShell } from '../components/PageHeader'
import { Tabs } from '../components/Tabs'
import { ReliabilityMeter } from '../components/ReliabilityMeter'
import { Button } from '../components/Button'
import {
  getReliability,
  getNextQuestion,
  submitAnswer,
  type Reliability,
  type NextQuestion,
} from '../api/matching'
import { DEMO_MODE } from '../api/demo'
import { SUPPORTED_LANGUAGES } from '../i18n'
import { useLanguageStore } from '../store/languageStore'
import { useThemeStore, type Theme } from '../store/themeStore'
import ProfileSection from './settings/ProfileSection'

type SettingsTab = 'profile' | 'matching' | 'preferences'

const TABS: SettingsTab[] = ['profile', 'matching', 'preferences']

// Light/dark appearance — mirrors LanguageSection's option-row style so both
// live together under the Preferences tab. App.tsx applies the class on change.
function ThemeSection() {
  const { t } = useTranslation()
  const theme = useThemeStore((s) => s.theme)
  const setTheme = useThemeStore((s) => s.setTheme)
  const options: { value: Theme; labelKey: string }[] = [
    { value: 'light', labelKey: 'settings.theme.light' },
    { value: 'dark', labelKey: 'settings.theme.dark' },
  ]

  return (
    <Card size="lg" className="p-5 tablet:p-6 shadow-bubble max-w-2xl">
      <h2 className="text-lg font-bold text-base">{t('settings.theme.title')}</h2>
      <p className="text-sm text-muted mt-1 mb-4">{t('settings.theme.description')}</p>
      <div className="flex flex-col gap-2">
        {options.map((o) => {
          const active = o.value === theme
          return (
            <button
              key={o.value}
              type="button"
              onClick={() => setTheme(o.value)}
              className={`flex items-center justify-between w-full rounded-2xl border px-4 py-3 text-sm transition bubble-pop ${
                active
                  ? 'border-primary-400 bg-surface-hover text-base font-semibold'
                  : 'border-line bg-surface text-secondary hover:bg-surface-hover'
              }`}
            >
              <span>{t(o.labelKey)}</span>
              {active && <span className="w-2.5 h-2.5 rounded-full bg-primary-500" />}
            </button>
          )
        })}
      </div>
    </Card>
  )
}

// Appearance + language, grouped under one Preferences tab.
function PreferencesSection() {
  return (
    <div className="flex flex-col gap-4 tablet:gap-6">
      <ThemeSection />
      <LanguageSection />
    </div>
  )
}

function LanguageSection() {
  const { t } = useTranslation()
  const lang = useLanguageStore((s) => s.lang)
  const setLang = useLanguageStore((s) => s.setLang)

  return (
    <Card size="lg" className="p-5 tablet:p-6 shadow-bubble max-w-2xl">
      <h2 className="text-lg font-bold text-base">{t('settings.language.title')}</h2>
      <p className="text-sm text-muted mt-1 mb-4">{t('settings.language.description')}</p>
      <div className="flex flex-col gap-2">
        {SUPPORTED_LANGUAGES.map((l) => {
          const active = l.code === lang
          return (
            <button
              key={l.code}
              type="button"
              dir={l.dir}
              onClick={() => setLang(l.code)}
              className={`flex items-center justify-between w-full rounded-2xl border px-4 py-3 text-sm transition bubble-pop ${
                active
                  ? 'border-primary-400 bg-surface-hover text-base font-semibold'
                  : 'border-line bg-surface text-secondary hover:bg-surface-hover'
              }`}
            >
              <span>{l.label}</span>
              {active && <span className="w-2.5 h-2.5 rounded-full bg-primary-500" />}
            </button>
          )
        })}
      </div>
    </Card>
  )
}

// The user's private "profile strength" in the matching system. Self-only — this
// never shows anyone else's confidence. Permanent home for the reliability meter
// once the onboarding widget retires.
function MatchingSection() {
  const { t } = useTranslation()
  const [reliability, setReliability] = useState<Reliability | null>(null)
  // The on-demand "build your profile" flow: fetch + answer questions back-to-back
  // (cooldown bypassed) until the pool is exhausted.
  const [question, setQuestion] = useState<NextQuestion | null>(null)
  const [finished, setFinished] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getReliability()
      .then((r) => { if (!cancelled) setReliability(r) })
      .catch(() => { /* best-effort; section just stays in its loading state */ })
    return () => { cancelled = true }
  }, [])

  async function loadNext() {
    setBusy(true)
    setError(null)
    try {
      const next = await getNextQuestion(true)   // ignore cooldown — deliberate flow
      if (next.hasQuestion) {
        setQuestion(next)
      } else {
        setQuestion(null)
        setFinished(true)
      }
    } catch {
      setError(t('matching.error'))
    } finally {
      setBusy(false)
    }
  }

  async function onAnswer(answerId: string) {
    if (!question?.questionId) return
    setBusy(true)
    setError(null)
    try {
      await submitAnswer(question.questionId, answerId)
      // Refresh the meter so it climbs as they answer, then advance to the next.
      getReliability().then((r) => {
        setReliability(r)
        // Let the demo tour advance once the profile crosses the matchable threshold.
        if (DEMO_MODE && r.matched) window.dispatchEvent(new Event('demo:matchable'))
      }).catch(() => { /* best-effort */ })
      await loadNext()
    } catch {
      setError(t('matching.error'))
      setBusy(false)
    }
  }

  const answered = reliability?.answeredQuestions ?? 0
  const cap = reliability?.questionCap ?? 0
  const allAnswered = finished || (cap > 0 && answered >= cap)

  return (
    <Card size="lg" data-tour="matching-quiz" className="p-5 tablet:p-6 shadow-bubble max-w-2xl">
      <h2 className="text-lg font-bold text-base">{t('settings.matching.title')}</h2>
      <p className="text-sm text-muted mt-1 mb-4">{t('settings.matching.description')}</p>
      {reliability ? (
        <ReliabilityMeter reliability={reliability} />
      ) : (
        <div className="h-2.5 rounded-full bg-surface-muted/60 animate-pulse" />
      )}
      <p className="text-xs text-muted mt-4">
        {t('settings.matching.answered', { answered, cap })}
      </p>

      <div className="mt-4 border-t border-line pt-4">
        {question?.hasQuestion ? (
          <div>
            <p className="text-sm text-base mb-3 leading-snug">{question.questionText}</p>
            <div className="flex flex-col gap-2">
              {(question.options ?? []).map((opt) => (
                <Button
                  key={opt.id}
                  variant="secondary"
                  size="sm"
                  wrap
                  disabled={busy}
                  onClick={() => onAnswer(opt.id)}
                >
                  {opt.text}
                </Button>
              ))}
            </div>
            {busy && <p className="mt-2 text-xs text-secondary">{t('matching.submitting')}</p>}
          </div>
        ) : allAnswered ? (
          <p className="text-sm text-secondary">{t('settings.matching.complete')}</p>
        ) : (
          <Button variant="primary" size="sm" disabled={busy} onClick={loadNext}>
            {t('settings.matching.answerCta')}
          </Button>
        )}
        {error && <p className="mt-2 text-xs text-danger">{error}</p>}
      </div>
    </Card>
  )
}

const SECTIONS: Record<SettingsTab, () => JSX.Element> = {
  profile: ProfileSection,
  matching: MatchingSection,
  preferences: PreferencesSection,
}

export default function SettingsPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<SettingsTab>('profile')

  return (
    <PageShell
      title={t('settings.title')}
      tabs={
        <Tabs
          items={TABS.map((key) => ({
            key,
            label: t(`settings.tabs.${key}`),
            tourId: key === 'matching' ? 'settings-tab-matching' : undefined,
          }))}
          active={tab}
          onChange={(key) => setTab(key as SettingsTab)}
        />
      }
    >
      {(() => { const Section = SECTIONS[tab]; return <Section /> })()}
    </PageShell>
  )
}
