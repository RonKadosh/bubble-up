import { type ComponentType, useEffect } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import {
  useOnboardingStore,
  isNavUnlocked,
  NAV_UNLOCK_AT,
  type LockableFeature,
} from '../store/onboardingStore'
import { logout as logoutApi } from '../api/auth'
import { PersistentVideo } from './PersistentVideo'
import { QuizPrompt } from './QuizPrompt'
import { OnboardingGuide } from './OnboardingGuide'
import { UserProfileCard } from './UserProfileCard'
import {
  BubbleLogo,
  BulbIcon,
  CapIcon,
  HelpIcon,
  LockIcon,
  LogoutIcon,
  ReportIcon,
  SettingsIcon,
  ShieldIcon,
} from './Icons'

type IconComp = ComponentType<{ className?: string }>

interface NavRowProps {
  to?: string
  onClick?: () => void
  Icon: IconComp
  label: string
  variant?: 'default' | 'danger'
  ariaLabel?: string
  /** When set, the row is greyed + lock-badged and non-interactive (onboarding). */
  locked?: boolean
  lockedLabel?: string
}

function NavRow({ to, onClick, Icon, label, variant = 'default', ariaLabel, locked = false, lockedLabel }: NavRowProps) {
  const base =
    'group relative flex items-center justify-center w-11 h-11 mx-auto rounded-full transition-all bubble-pop'
  const danger = variant === 'danger'

  const hoverLabel = (text: string) => (
    <span className="pointer-events-none absolute start-full ms-2 z-50 whitespace-nowrap rounded-full bg-surface text-base px-2.5 py-1 text-xs font-medium border border-line shadow-themed opacity-0 group-hover:opacity-100 transition-opacity">
      {text}
    </span>
  )

  const content = () => (
    <>
      <Icon className="w-5 h-5 shrink-0" />
      {hoverLabel(label)}
    </>
  )

  // Locked (onboarding) — non-interactive, greyed, with a lock badge + "unlocks at" tooltip.
  if (locked) {
    return (
      <div
        aria-label={lockedLabel ?? label}
        aria-disabled="true"
        className={`${base} text-on-brand/40 cursor-not-allowed transition-opacity`}
      >
        <Icon className="w-5 h-5 shrink-0" />
        <span className="absolute -bottom-1 -end-1 grid place-items-center w-4 h-4 rounded-full bg-surface text-secondary shadow-sm">
          <LockIcon className="w-2.5 h-2.5" />
        </span>
        {hoverLabel(lockedLabel ?? label)}
      </div>
    )
  }

  if (to) {
    return (
      <NavLink
        to={to}
        aria-label={ariaLabel ?? label}
        className={({ isActive }) =>
          `${base} ${
            isActive
              ? 'bg-white/25 text-on-brand ring-on-brand backdrop-blur-sm'
              : 'text-on-brand/80 hover:bg-white/15 hover:text-on-brand'
          }`
        }
      >
        {content}
      </NavLink>
    )
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel ?? label}
      className={`${base} ${
        danger
          ? 'text-on-brand/80 hover:bg-[rgba(217,79,79,0.25)] hover:text-on-brand'
          : 'text-on-brand/80 hover:bg-white/15 hover:text-on-brand'
      }`}
    >
      {content()}
    </button>
  )
}

export default function Layout() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const me = useAuthStore((s) => s.user)

  // Progressive feature unlocking: lockable nav greys out until the onboarding
  // wizard reaches the level that unlocks it. Onboarded/loading → nothing locked.
  const onbStatus = useOnboardingStore((s) => s.status)
  const ensureOnboarding = useOnboardingStore((s) => s.ensureHydrated)
  useEffect(() => { ensureOnboarding() }, [ensureOnboarding])
  const lockProps = (feature: LockableFeature) =>
    isNavUnlocked(feature, onbStatus)
      ? {}
      : { locked: true, lockedLabel: t('nav.locked', { n: NAV_UNLOCK_AT[feature] - 1 }) }

  async function handleLogout() {
    const rt = useAuthStore.getState().refreshToken
    if (rt) {
      try {
        await logoutApi(rt)
      } catch {
        // best-effort
      }
    }
    clearAuth()
    navigate('/login')
  }

  function handleHelp() {
    alert(`${t('nav.help')}: ${t('common.comingSoon')}`)
  }

  return (
    <div className="container-app flex h-screen bg-base p-2 tablet:p-3 gap-2 tablet:gap-3">
      <aside className="relative z-10 flex flex-col w-[4.5rem] bg-brand-gradient-vertical text-on-brand shadow-bubble rounded-[2rem] overflow-visible shrink-0">
        <div className="flex items-center justify-center px-3 py-4 border-b border-white/20">
          <NavLink
            to="/dashboard"
            aria-label={t('nav.home')}
            className="group relative w-11 h-11 rounded-full flex items-center justify-center shrink-0 transition-all hover:bg-white/15 bubble-pop text-on-brand"
          >
            <BubbleLogo className="w-6 h-6" />
            <span className="pointer-events-none absolute start-full ms-2 z-50 whitespace-nowrap rounded-full bg-surface text-base px-2.5 py-1 text-xs font-medium border border-line shadow-themed opacity-0 group-hover:opacity-100 transition-opacity">
              {t('nav.home')}
            </span>
          </NavLink>
        </div>

        <nav className="flex-1 flex flex-col gap-2 px-2 py-4">
          {/* Home (the logo above) IS the Bubbles hub now — Home and My Bubbles
              were merged, so there's no separate "My Bubbles" row. */}
          <NavRow to="/academy" Icon={CapIcon} label={t('nav.academy')} {...lockProps('academy')} />
          <NavRow to="/experts" Icon={BulbIcon} label={t('nav.experts')} {...lockProps('experts')} />
          {(me?.role === 'EXPERT' || me?.role === 'ADMIN') && (
            <NavRow to="/expert" Icon={CapIcon} label={t('expert.hubNav')} />
          )}
          {me?.role === 'ADMIN' && (
            <NavRow to="/admin" Icon={ShieldIcon} label="Admin" />
          )}
        </nav>

        <div className="px-2 pb-3 pt-2 border-t border-white/20 flex flex-col gap-2">
          <NavRow to="/settings" Icon={SettingsIcon} label={t('nav.settings')} {...lockProps('settings')} />
          <NavRow onClick={handleHelp} Icon={HelpIcon} label={t('nav.help')} />
          <NavRow to="/report" Icon={ReportIcon} label={t('nav.report')} />
          <NavRow onClick={handleLogout} Icon={LogoutIcon} label={t('nav.logout')} variant="danger" />
        </div>
      </aside>

      <main className="relative flex-1 min-h-0 flex flex-col overflow-hidden bg-surface rounded-[1.5rem] tablet:rounded-[2rem] shadow-themed border border-line">
        <OnboardingGuide />
        <Outlet />
      </main>
      <PersistentVideo />
      <QuizPrompt />
      <UserProfileCard />
    </div>
  )
}
