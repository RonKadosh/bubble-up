import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../components/Button'
import { Card } from '../components/Card'
import { ReliabilityMeter } from '../components/ReliabilityMeter'
import { getReliability, type Reliability } from '../api/matching'
import { SUPPORTED_LANGUAGES } from '../i18n'
import { useLanguageStore } from '../store/languageStore'
import ProfileSection from './settings/ProfileSection'

type SettingsTab = 'profile' | 'matching' | 'language'

const TABS: SettingsTab[] = ['profile', 'matching', 'language']

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

  useEffect(() => {
    let cancelled = false
    getReliability()
      .then((r) => { if (!cancelled) setReliability(r) })
      .catch(() => { /* best-effort; section just stays in its loading state */ })
    return () => { cancelled = true }
  }, [])

  return (
    <Card size="lg" className="p-5 tablet:p-6 shadow-bubble max-w-2xl">
      <h2 className="text-lg font-bold text-base">{t('settings.matching.title')}</h2>
      <p className="text-sm text-muted mt-1 mb-4">{t('settings.matching.description')}</p>
      {reliability ? (
        <ReliabilityMeter reliability={reliability} />
      ) : (
        <div className="h-2.5 rounded-full bg-surface-muted/60 animate-pulse" />
      )}
      <p className="text-xs text-muted mt-4">
        {t('settings.matching.answered', {
          answered: reliability?.answeredQuestions ?? 0,
          cap: reliability?.questionCap ?? 0,
        })}
      </p>
    </Card>
  )
}

const SECTIONS: Record<SettingsTab, () => JSX.Element> = {
  profile: ProfileSection,
  matching: MatchingSection,
  language: LanguageSection,
}

export default function SettingsPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<SettingsTab>('profile')

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="container-app px-4 tablet:px-6 desktop:px-8 py-6 tablet:py-8">
        <header className="mb-6">
          <div className="flex items-center gap-3 mb-1">
            <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
            <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
            <h1 className="text-2xl font-bold text-base">{t('settings.title')}</h1>
          </div>
        </header>

        <nav className="flex gap-2 mb-6">
          {TABS.map((key) => (
            <Button
              key={key}
              size="sm"
              variant={tab === key ? 'primary' : 'secondary'}
              onClick={() => setTab(key)}
            >
              {t(`settings.tabs.${key}`)}
            </Button>
          ))}
        </nav>

        {(() => { const Section = SECTIONS[tab]; return <Section /> })()}
      </div>
    </div>
  )
}
