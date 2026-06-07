import { useEffect } from 'react'
import { Navigate } from 'react-router-dom'
import { OnboardingWizard } from './dashboard/OnboardingWizard'
import { useOnboardingStore, isOnboarded } from '../store/onboardingStore'

/**
 * The onboarding-wizard host. Home and My Bubbles were merged into the `/groups`
 * hub (the activity feed is now the hub's no-Bubble-selected state), so this route
 * only does two things:
 *   - not yet onboarded → show the gating "Getting started" wizard.
 *   - onboarded         → redirect to the hub (the real home).
 *
 * Kept as a distinct route because the whole progressive-unlock system bounces
 * mid-onboarding users here (see RequireUnlocked in App.tsx), and the wizard's
 * L4 "Create a Bubble" step link-outs to /groups expect the wizard to live apart
 * from the hub. The sidebar logo ("Home") points here too: this route is the
 * dispatcher that decides wizard-vs-hub, so Home means the wizard during
 * onboarding and the hub feed afterwards.
 *
 * `state.home` is forwarded to the hub so it clears any selected Bubble and shows
 * the activity feed (the "no Bubble selected" state) — i.e. Home always lands on
 * the feed, not whatever Bubble was last open.
 */
export default function DashboardPage() {
  const onbStatus = useOnboardingStore((s) => s.status)
  const ensureOnboarding = useOnboardingStore((s) => s.ensureHydrated)
  useEffect(() => { ensureOnboarding() }, [ensureOnboarding])

  // `null` until status loads avoids a flash of the wizard before the redirect.
  if (!onbStatus) return null
  if (!isOnboarded(onbStatus)) return <OnboardingWizard />
  return <Navigate to="/groups" replace state={{ home: true }} />
}
