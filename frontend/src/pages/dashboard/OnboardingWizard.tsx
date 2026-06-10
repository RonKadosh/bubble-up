import { useEffect, useState } from 'react'
import { useNavigate, type NavigateFunction } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Card } from '../../components/Card'
import { Button } from '../../components/Button'
import { SparkleIcon } from '../../components/Icons'
import { ReliabilityMeter } from '../../components/ReliabilityMeter'
import { type GuideKey } from '../../components/OnboardingGuide'
import { useOnboardingStore, ONBOARDING_LEVEL_COUNT } from '../../store/onboardingStore'
import { useQuizPromptStore } from '../../store/quizPromptStore'
import type { OnboardingStatus } from '../../api/onboarding'
import { getDiscoverableBubbles, joinGroup, type Group } from '../../api/groups'
import { describeError } from '../../api/errors'

type LevelKind = 'explain' | 'action' | 'matching' | 'bubble'

interface LevelDef {
  kind: LevelKind
  titleKey: string
  bodyKey: string
  /** Whether the user may advance past this level (live, server-derived). */
  gate: (s: OnboardingStatus) => boolean
  /** Action levels link out to the page where the step is actually done. */
  cta?: { labelKey: string; go: (nav: NavigateFunction) => void }
}

// The five onboarding levels. Action levels (2–4) link out (carrying guide
// nav-state so the destination page shows its callout) and are hard-gated on the
// real condition; L5 is gated on having answered one quiz question.
const LEVELS: LevelDef[] = [
  {
    kind: 'explain',
    titleKey: 'onboarding.wizard.welcome.title',
    bodyKey: 'onboarding.wizard.welcome.body',
    gate: () => true,
  },
  {
    kind: 'action',
    titleKey: 'onboarding.wizard.profile.title',
    bodyKey: 'onboarding.wizard.profile.body',
    gate: (s) => s.studyBase.affiliationDone,
    cta: { labelKey: 'onboarding.wizard.profile.cta', go: (nav) => nav('/settings', { state: { guide: 'profile' satisfies GuideKey } }) },
  },
  {
    kind: 'action',
    titleKey: 'onboarding.wizard.enroll.title',
    bodyKey: 'onboarding.wizard.enroll.body',
    gate: (s) => s.studyBase.coursesDone,
    cta: { labelKey: 'onboarding.wizard.enroll.cta', go: (nav) => nav('/academy', { state: { guide: 'enroll' satisfies GuideKey } }) },
  },
  {
    kind: 'bubble',
    titleKey: 'onboarding.wizard.bubble.title',
    bodyKey: 'onboarding.wizard.bubble.body',
    gate: (s) => s.inBubble,
  },
  {
    kind: 'matching',
    titleKey: 'onboarding.wizard.matching.title',
    bodyKey: 'onboarding.wizard.matching.body',
    gate: (s) => s.reliability.answeredQuestions >= 1,
  },
]

/**
 * The gating "Getting started" wizard — the home page for a user who hasn't
 * finished onboarding. Shows `wizardLevel` (1–5); Next persists `level+1` but only
 * once the level's real gate passes. Action levels link out and auto-advance when
 * the user returns (the store `refresh()`es on mount). Finishing L5 → `setLevel(6)`.
 */
// What *completing* each level unlocks (the sidebar feature you just earned) —
// drives the reward line + celebration. L1 (intro) and L5 (finish) unlock nothing
// on their own; "one step forward" means finishing the profile unlocks Settings, etc.
const LEVEL_UNLOCKS: (string | null)[] = [null, 'settings', 'academy', 'bubbles', null]

export function OnboardingWizard() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const status = useOnboardingStore((s) => s.status)
  const wizardLevel = useOnboardingStore((s) => s.wizardLevel)
  const setWizardLevel = useOnboardingStore((s) => s.setWizardLevel)
  const refresh = useOnboardingStore((s) => s.refresh)
  const requestQuiz = useQuizPromptStore((s) => s.requestOpen)
  /** Transient celebration: a feature key ("academy"), 'allSet', or null. */
  const [celebration, setCelebration] = useState<string | null>(null)

  // Re-fetch on mount so progress is fresh after returning from a link-out page.
  useEffect(() => { refresh() }, [refresh])

  if (!status) return null

  const level = Math.min(Math.max(wizardLevel, 1), ONBOARDING_LEVEL_COUNT)
  const def = LEVELS[level - 1]
  const gateOk = def.gate(status)
  const isLast = level === ONBOARDING_LEVEL_COUNT
  // Endowed progress: account-created counts as a free segment, so the bar starts
  // at ~17% (level 1) and never reads 0%. Reaches 100% when the wizard completes.
  const pct = Math.round((level / (ONBOARDING_LEVEL_COUNT + 1)) * 100)
  const displayPct = celebration === 'allSet' ? 100 : pct
  const unlockKey = LEVEL_UNLOCKS[level - 1]

  function advance() {
    if (!gateOk) return
    if (isLast) {
      // Final step → fill the bar to 100% + "You're all set!", let it linger briefly,
      // then complete (which unmounts into the dashboard).
      setCelebration('allSet')
      window.setTimeout(() => setWizardLevel(level + 1), 1600)
    } else {
      // Celebrate the feature this level just unlocked (none for the L1 intro).
      if (unlockKey) {
        setCelebration(unlockKey)
        window.setTimeout(() => setCelebration(null), 1500)
      }
      setWizardLevel(level + 1)
    }
  }

  return (
    <div className="flex-1 overflow-y-auto p-4 tablet:p-8 flex items-start tablet:items-center justify-center">
      <Card size="lg" className="w-full max-w-xl p-6 tablet:p-8 shadow-bubble">
        {/* Workspace hook + endowed-progress bar */}
        <div className="flex items-start justify-between gap-3 mb-1.5">
          <div className="flex items-center gap-2 min-w-0">
            <span className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm shrink-0" aria-hidden />
            <span className="w-1.5 h-1.5 rounded-full bg-bubble-green shrink-0" aria-hidden />
            <h2 className="text-sm font-bold text-base leading-tight">{t('onboarding.wizard.workspaceTitle')}</h2>
          </div>
          <span className="shrink-0 text-xs font-semibold text-base tabular-nums whitespace-nowrap">
            {t('onboarding.wizard.percentComplete', { pct: displayPct })}
          </span>
        </div>
        {/* Grey → light-blue gradient, revealed left-to-right as the % climbs. */}
        <div
          className="relative h-2 rounded-full overflow-hidden"
          style={{ background: 'linear-gradient(to right, var(--progress-grey), var(--color-primary-400))' }}
        >
          <div
            className="absolute inset-y-0 end-0 bg-surface-muted transition-[inset-inline-start] duration-700 ease-out"
            style={{ insetInlineStart: `${displayPct}%` }}
          />
        </div>
        {/* Endowed-progress head start: the first slice is credited for signing up. */}
        {level === 1 && (
          <p className="mt-2 text-xs text-muted">{t('onboarding.wizard.headStart', { pct })}</p>
        )}
        {unlockKey && (
          <p className="mt-2 text-xs font-medium text-primary-600">
            {t('onboarding.wizard.unlockReward', { feature: t(`onboarding.feature.${unlockKey}`) })}
          </p>
        )}

        <h1 className="text-2xl font-bold text-base mt-5">{t(def.titleKey)}</h1>
        <p className="text-sm text-secondary mt-2 leading-relaxed whitespace-pre-line">{t(def.bodyKey)}</p>

        {/* Action level: link out to do it, then it unlocks on return. */}
        {def.kind === 'action' && def.cta && (
          <div className="mt-5 flex items-center gap-3">
            <Button onClick={() => def.cta!.go(navigate)}>{t(def.cta.labelKey)}</Button>
            {gateOk && <span className="text-sm font-medium text-primary-600">✓ {t('onboarding.wizard.stepDone')}</span>}
          </div>
        )}

        {/* Bubble level: find an existing bubble in your courses, or create one. */}
        {def.kind === 'bubble' && <BubbleStep done={gateOk} />}

        {/* Matching level: private meter + the first Daily Drop (the floating prompt pops it). */}
        {def.kind === 'matching' && (
          <div className="mt-5">
            <ReliabilityMeter reliability={status.reliability} />
            <Button className="mt-4" onClick={requestQuiz} disabled={gateOk}>
              {gateOk ? `✓ ${t('onboarding.wizard.stepDone')}` : t('onboarding.wizard.matching.cta')}
            </Button>
          </div>
        )}

        {/* Footer: Back / Next-or-Finish. Forward is hard-gated. */}
        <div className="mt-8 flex items-center justify-between gap-3">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setWizardLevel(level - 1)}
            disabled={level === 1}
          >
            {t('onboarding.wizard.back')}
          </Button>
          <div className="flex items-center gap-3">
            {!gateOk && def.kind !== 'explain' && (
              <span className="text-xs text-muted">{t('onboarding.wizard.lockedHint')}</span>
            )}
            <Button onClick={advance} disabled={!gateOk}>
              {isLast ? t('onboarding.wizard.finish') : t('onboarding.wizard.next')}
            </Button>
          </div>
        </div>
      </Card>

      {celebration && (
        <div className="fixed inset-0 z-50 grid place-items-center pointer-events-none p-4 overflow-hidden">
          {/* Bubbles floating up behind the badge. */}
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <span
              key={i}
              className={`absolute w-3 h-3 rounded-full animate-bubble-rise ${
                ['bg-bubble-magenta', 'bg-primary-400', 'bg-bubble-green'][i % 3]
              }`}
              style={{ left: `${40 + i * 4}%`, bottom: '40%', animationDelay: `${i * 0.1}s` }}
              aria-hidden
            />
          ))}
          <div className="relative flex items-center gap-3 rounded-3xl bg-surface border border-line shadow-bubble px-6 py-4 animate-celebrate-badge">
            <SparkleIcon className="w-6 h-6 text-bubble-magenta" />
            <p className="text-base font-bold text-base">
              {celebration === 'allSet'
                ? t('onboarding.wizard.allSet')
                : t('onboarding.wizard.unlocked', { feature: t(`onboarding.feature.${celebration}`) })}
            </p>
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * L4 body: two paths to "be in a Bubble". "Find a Bubble" loads the public bubbles
 * across the user's enrolled courses inline and joins one; "Create a Bubble" links
 * out to the hub's create form. Either flips `inBubble`, which unlocks the step.
 */
function BubbleStep({ done }: { done: boolean }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const refresh = useOnboardingStore((s) => s.refresh)
  const [finding, setFinding] = useState(false)
  const [bubbles, setBubbles] = useState<Group[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [joiningId, setJoiningId] = useState<string | null>(null)

  async function loadBubbles() {
    setFinding(true)
    setLoading(true)
    setError('')
    try {
      setBubbles(await getDiscoverableBubbles())
    } catch {
      setError(t('onboarding.wizard.bubble.loadError'))
    } finally {
      setLoading(false)
    }
  }

  async function handleJoin(id: string) {
    if (joiningId) return
    setJoiningId(id)
    setError('')
    try {
      await joinGroup(id)
      await refresh() // inBubble → true; the wizard's Next/Finish unlocks
    } catch (e) {
      setError(describeError(e, t, {
        GROUP_IS_FULL: 'onboarding.wizard.bubble.full',
        ALREADY_GROUP_MEMBER: 'onboarding.wizard.bubble.alreadyMember',
      }, 'onboarding.wizard.bubble.joinError'))
    } finally {
      setJoiningId(null)
    }
  }

  if (done) {
    return <p className="mt-5 text-sm font-medium text-primary-600">✓ {t('onboarding.wizard.bubble.joined')}</p>
  }

  return (
    <div className="mt-5">
      <div className="flex flex-wrap gap-2">
        <Button onClick={loadBubbles}>{t('onboarding.wizard.bubble.findCta')}</Button>
        <Button variant="secondary" onClick={() => navigate('/groups', { state: { openCreate: true } })}>
          {t('onboarding.wizard.bubble.createCta')}
        </Button>
      </div>

      {finding && (
        <div className="mt-4">
          {loading ? (
            <p className="text-sm text-muted">{t('onboarding.wizard.bubble.loading')}</p>
          ) : bubbles && bubbles.length > 0 ? (
            <div className="flex flex-col gap-2 max-h-72 overflow-y-auto pe-1">
              {bubbles.map((b) => {
                const full = b.memberCount >= b.maxMembers
                return (
                  <div key={b.id} className="flex items-center gap-3 rounded-2xl border border-line bg-surface/40 p-3">
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-semibold text-base truncate">🫧 {b.name}</p>
                      <p className="text-xs text-muted">
                        {t('onboarding.wizard.bubble.members', { count: b.memberCount, max: b.maxMembers })}
                      </p>
                    </div>
                    <Button
                      size="sm"
                      className="shrink-0"
                      disabled={full || joiningId === b.id}
                      onClick={() => handleJoin(b.id)}
                    >
                      {full
                        ? t('onboarding.wizard.bubble.fullLabel')
                        : joiningId === b.id
                          ? t('onboarding.wizard.bubble.joining')
                          : t('onboarding.wizard.bubble.join')}
                    </Button>
                  </div>
                )
              })}
            </div>
          ) : (
            <p className="rounded-2xl border border-dashed border-line bg-surface/40 px-4 py-5 text-center text-sm text-muted">
              {t('onboarding.wizard.bubble.empty')}
            </p>
          )}
        </div>
      )}

      {error && <p className="mt-3 text-sm text-danger">{error}</p>}
    </div>
  )
}
