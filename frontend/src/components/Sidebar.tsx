import { type ComponentType, useEffect, useState } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
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
  CloseIcon,
  HelpIcon,
  LockIcon,
  LogoutIcon,
  MenuIcon,
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

/**
 * Row in the mobile account rolldown — a full-width icon + label on a surface
 * panel. Used for the utility actions (settings / help / report / logout) that
 * live in the rail's footer on desktop.
 */
function MobileMenuRow({ to, onClick, Icon, label, variant = 'default', locked = false, lockedLabel }: NavRowProps) {
  const base = 'flex items-center gap-3 w-full px-3 py-3 rounded-2xl text-sm font-medium transition-colors bubble-pop'

  if (locked) {
    return (
      <div aria-label={lockedLabel ?? label} aria-disabled="true" className={`${base} text-secondary/50 cursor-not-allowed`}>
        <Icon className="w-5 h-5 shrink-0" />
        <span className="flex-1 text-start">{label}</span>
        <LockIcon className="w-4 h-4 text-secondary/60" />
      </div>
    )
  }

  const cls = `${base} ${
    variant === 'danger' ? 'text-danger hover:bg-surface-muted' : 'text-base hover:bg-surface-hover'
  }`

  if (to) {
    return (
      <NavLink to={to} onClick={onClick} className={cls}>
        <Icon className="w-5 h-5 shrink-0" />
        <span className="flex-1 text-start">{label}</span>
      </NavLink>
    )
  }

  return (
    <button type="button" onClick={onClick} className={cls}>
      <Icon className="w-5 h-5 shrink-0" />
      <span className="flex-1 text-start">{label}</span>
    </button>
  )
}

export default function Layout() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const me = useAuthStore((s) => s.user)

  // Mobile-only account rolldown (settings / help / report / logout). On
  // tablet+ these live in the rail footer and this stays closed/unused.
  const [menuOpen, setMenuOpen] = useState(false)
  // Close the rolldown whenever the route changes or Escape is pressed.
  useEffect(() => { setMenuOpen(false) }, [location.pathname])
  useEffect(() => {
    if (!menuOpen) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setMenuOpen(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [menuOpen])

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
    navigate('/help')
  }

  return (
    <div className="container-app flex flex-col tablet:flex-row h-screen bg-base p-2 tablet:p-3 gap-2 tablet:gap-3">
      {/* Mobile top bar — brand + account rolldown trigger. Phone only. */}
      <header className="tablet:hidden relative z-20 flex items-center justify-between ps-3 pe-2 h-14 bg-brand-gradient-vertical text-on-brand shadow-bubble rounded-[1.5rem] shrink-0">
        <NavLink
          to="/dashboard"
          aria-label={t('nav.home')}
          className="flex items-center gap-2 px-1 py-1 rounded-full text-on-brand bubble-pop"
        >
          <BubbleLogo className="w-6 h-6" />
          <span className="font-semibold tracking-tight">Bubble.up</span>
        </NavLink>
        <button
          type="button"
          onClick={() => setMenuOpen((o) => !o)}
          aria-label={menuOpen ? t('nav.closeMenu') : t('nav.menu')}
          aria-expanded={menuOpen}
          className="grid place-items-center w-10 h-10 rounded-full text-on-brand hover:bg-white/15 transition-colors bubble-pop"
        >
          {menuOpen ? <CloseIcon className="w-5 h-5" /> : <MenuIcon className="w-5 h-5" />}
        </button>
      </header>

      {/* Mobile account rolldown — settings / help / report / logout. */}
      {menuOpen && (
        <div className="tablet:hidden fixed inset-0 z-40" role="dialog" aria-modal="true" aria-label={t('nav.menu')}>
          <button
            type="button"
            aria-label={t('nav.closeMenu')}
            onClick={() => setMenuOpen(false)}
            className="absolute inset-0 bg-black/30 backdrop-blur-sm"
          />
          <div className="absolute inset-x-2 top-2 origin-top max-h-[calc(100dvh-1rem)] overflow-y-auto rounded-[1.5rem] bg-surface border border-line shadow-themed p-2 animate-rolldown">
            <div className="flex items-center justify-between px-2 pb-1.5">
              <span className="text-xs font-semibold uppercase tracking-wide text-secondary">{t('nav.menu')}</span>
              <button
                type="button"
                onClick={() => setMenuOpen(false)}
                aria-label={t('nav.closeMenu')}
                className="grid place-items-center w-8 h-8 rounded-full text-secondary hover:bg-surface-hover transition-colors"
              >
                <CloseIcon className="w-4 h-4" />
              </button>
            </div>
            <div className="flex flex-col gap-1">
              {/* Primary destinations — moved here from the old bottom tab bar so
                  the phone has a single nav surface (this rolldown + the Home logo). */}
              <MobileMenuRow to="/dashboard" onClick={() => setMenuOpen(false)} Icon={BubbleLogo} label={t('nav.home')} />
              <MobileMenuRow to="/academy" onClick={() => setMenuOpen(false)} Icon={CapIcon} label={t('nav.academy')} {...lockProps('academy')} />
              <MobileMenuRow to="/experts" onClick={() => setMenuOpen(false)} Icon={BulbIcon} label={t('nav.experts')} {...lockProps('experts')} />
              {(me?.role === 'EXPERT' || me?.role === 'ADMIN') && (
                <MobileMenuRow to="/expert" onClick={() => setMenuOpen(false)} Icon={CapIcon} label={t('expert.hubNav')} />
              )}
              {me?.role === 'ADMIN' && (
                <MobileMenuRow to="/admin" onClick={() => setMenuOpen(false)} Icon={ShieldIcon} label="Admin" />
              )}
              <div className="my-1 mx-1 h-px bg-line" role="separator" />
              {/* Utility actions. */}
              <MobileMenuRow to="/settings" onClick={() => setMenuOpen(false)} Icon={SettingsIcon} label={t('nav.settings')} {...lockProps('settings')} />
              <MobileMenuRow onClick={() => { setMenuOpen(false); handleHelp() }} Icon={HelpIcon} label={t('nav.help')} />
              <MobileMenuRow to="/report" onClick={() => setMenuOpen(false)} Icon={ReportIcon} label={t('nav.report')} />
              <MobileMenuRow onClick={() => { setMenuOpen(false); handleLogout() }} Icon={LogoutIcon} label={t('nav.logout')} variant="danger" />
            </div>
          </div>
        </div>
      )}

      <aside className="hidden tablet:flex relative z-10 flex-col w-[4.5rem] bg-brand-gradient-vertical text-on-brand shadow-bubble rounded-[2rem] overflow-visible shrink-0">
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
