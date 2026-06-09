import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { ChevronIcon } from './Icons'
import { useOnboardingStore } from '../store/onboardingStore'
import { useViewportStore } from '../store/viewportStore'
import type { OnboardingStatus } from '../api/onboarding'

/**
 * Registry-driven arrival guides. When an onboarding wizard CTA navigates with
 * `state: { guide: <key> }`, this globally-mounted banner shows the matching
 * "here's what to do" callout on the destination page, tinted as part of
 * onboarding. Once the step's task is done (server-derived), the dot turns green
 * and a "Back to setup" button appears to return to the wizard. No dismiss — the
 * banner goes away on its own when the user navigates back.
 *
 * Adding a guide = one entry in `GUIDES` + two i18n keys + a CTA that passes
 * `state: { guide }`. No new component, no per-page code, no backend change.
 */
export type GuideKey = 'profile' | 'enroll'

const GUIDES: Record<GuideKey, { titleKey: string; bodyKey: string; done: (s: OnboardingStatus) => boolean }> = {
  profile: {
    titleKey: 'onboarding.guide.profile.title',
    bodyKey: 'onboarding.guide.profile.body',
    done: (s) => s.studyBase.affiliationDone,
  },
  enroll: {
    titleKey: 'onboarding.guide.enroll.title',
    bodyKey: 'onboarding.guide.enroll.body',
    done: (s) => s.studyBase.coursesDone,
  },
}

function isGuideKey(v: unknown): v is GuideKey {
  return typeof v === 'string' && v in GUIDES
}

export function OnboardingGuide() {
  const { t } = useTranslation()
  const location = useLocation()
  const navigate = useNavigate()
  const status = useOnboardingStore((s) => s.status)
  const ensureHydrated = useOnboardingStore((s) => s.ensureHydrated)
  const isPhone = useViewportStore((s) => s.tier === 'phone')
  useEffect(() => { ensureHydrated() }, [ensureHydrated])
  // On phone the banner is collapsed by default so it doesn't crush the page;
  // the title stays visible and a chevron expands the detail on demand.
  const [expanded, setExpanded] = useState(false)

  const guide = (location.state as { guide?: unknown } | null)?.guide
  if (!isGuideKey(guide)) return null

  const def = GUIDES[guide]
  const done = status ? def.done(status) : false
  // Always open on tablet+; on phone open when expanded or once the step is done
  // (so the "Back to setup" CTA is reachable).
  const open = !isPhone || expanded || done

  return (
    <div className="shrink-0 p-3 tablet:p-4 pb-0">
      <div className="ring-iridescent p-[1.5px] rounded-3xl shadow-themed">
        <div className={`rounded-[calc(1.5rem-1.5px)] p-3 tablet:p-4 flex items-start gap-3 transition-colors ${done ? 'bg-bubble-green-soft' : 'bg-primary-50'}`}>
          <span
            className={`shrink-0 w-2.5 h-2.5 mt-1.5 rounded-full ${done ? 'bg-bubble-green' : 'bg-brand-gradient-strong'}`}
            aria-hidden
          />
          <div className="flex-1 min-w-0">
            {open && (
              <p className="text-[0.7rem] font-bold uppercase tracking-wide text-primary-600 mb-0.5">
                {t('onboarding.guide.badge')}
              </p>
            )}
            <p className="text-sm font-semibold text-base truncate">{t(def.titleKey)}</p>
            {open && <p className="text-sm text-muted mt-0.5">{t(def.bodyKey)}</p>}
            {open && done && (
              <Button size="sm" className="mt-3" onClick={() => navigate('/dashboard')}>
                {t('onboarding.guide.backToSetup')}
              </Button>
            )}
          </div>
          {isPhone && !done && (
            <button
              type="button"
              onClick={() => setExpanded((v) => !v)}
              aria-expanded={open}
              aria-label={t(open ? 'common.collapse' : 'common.expand')}
              className="shrink-0 -me-1 grid place-items-center w-8 h-8 rounded-full text-primary-600 hover:bg-primary-100 transition-colors"
            >
              <ChevronIcon className={`w-4 h-4 transition-transform ${open ? 'rotate-90' : '-rotate-90'}`} />
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
