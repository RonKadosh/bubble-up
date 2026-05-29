import { useState, type ComponentType } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import { useThemeStore } from '../store/themeStore'
import { useLanguageStore } from '../store/languageStore'
import { SUPPORTED_LANGUAGES } from '../i18n'
import { logout as logoutApi } from '../api/auth'
import { Avatar } from './Avatar'
import { PersistentVideo } from './PersistentVideo'
import { QuizPrompt } from './QuizPrompt'
import {
  BulbIcon,
  CapIcon,
  DashboardIcon,
  GlobeIcon,
  HelpIcon,
  LogoutIcon,
  MoonIcon,
  PeopleIcon,
  ReportIcon,
  ShieldIcon,
  SunIcon,
} from './Icons'

type IconComp = ComponentType<{ className?: string }>

interface NavRowProps {
  to?: string
  onClick?: () => void
  Icon: IconComp
  label: string
  variant?: 'default' | 'danger'
  ariaLabel?: string
}

function NavRow({ to, onClick, Icon, label, variant = 'default', ariaLabel }: NavRowProps) {
  const base =
    'group relative flex items-center justify-center w-11 h-11 mx-auto rounded-full transition-all bubble-pop'
  const danger = variant === 'danger'

  const content = () => (
    <>
      <Icon className="w-5 h-5 shrink-0" />
      <span className="pointer-events-none absolute start-full ms-2 z-50 whitespace-nowrap rounded-full bg-surface text-base px-2.5 py-1 text-xs font-medium border border-line shadow-themed opacity-0 group-hover:opacity-100 transition-opacity">
        {label}
      </span>
    </>
  )

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

function LanguageSwitcher() {
  const lang = useLanguageStore((s) => s.lang)
  const setLang = useLanguageStore((s) => s.setLang)
  const [open, setOpen] = useState(false)
  const current = SUPPORTED_LANGUAGES.find((l) => l.code === lang) ?? SUPPORTED_LANGUAGES[0]

  return (
    <div className="relative">
      <NavRow
        onClick={() => setOpen((v) => !v)}
        Icon={GlobeIcon}
        label={current.label}
      />
      {open && (
        <div className="absolute bottom-full mb-2 start-12 z-50 min-w-[8rem] rounded-2xl bg-surface border border-line shadow-bubble overflow-hidden">
          {SUPPORTED_LANGUAGES.map((l) => (
            <button
              key={l.code}
              type="button"
              onClick={() => { setLang(l.code); setOpen(false) }}
              className={`w-full text-start px-4 py-2 text-sm hover:bg-surface-hover transition ${
                l.code === lang ? 'text-primary-600 font-semibold' : 'text-base'
              }`}
              dir={l.dir}
            >
              {l.label}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

export default function Layout() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const me = useAuthStore((s) => s.user)
  const theme = useThemeStore((s) => s.theme)
  const toggleTheme = useThemeStore((s) => s.toggle)

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

  function handleReport() {
    alert(`${t('nav.report')}: ${t('common.comingSoon')}`)
  }

  return (
    <div className="container-app flex h-screen bg-base p-2 tablet:p-3 gap-2 tablet:gap-3">
      <aside className="relative z-10 flex flex-col w-[4.5rem] bg-brand-gradient-vertical text-on-brand shadow-bubble rounded-[2rem] overflow-visible shrink-0">
        <div className="flex items-center justify-center px-3 py-4 border-b border-white/20">
          <NavLink
            to="/profile"
            aria-label={t('nav.profile')}
            className="group relative w-11 h-11 rounded-full flex items-center justify-center shrink-0 transition-all hover:bg-white/15 bubble-pop"
          >
            <Avatar
              id={me?.id ?? 'anon'}
              name={me?.displayName ?? '?'}
              imageUrl={me?.avatarUrl}
              size="md"
              ring
            />
            <span className="pointer-events-none absolute start-full ms-2 z-50 whitespace-nowrap rounded-full bg-surface text-base px-2.5 py-1 text-xs font-medium border border-line shadow-themed opacity-0 group-hover:opacity-100 transition-opacity">
              {me?.displayName ?? t('nav.profile')}
            </span>
          </NavLink>
        </div>

        <nav className="flex-1 flex flex-col gap-2 px-2 py-4">
          <NavRow to="/dashboard" Icon={DashboardIcon} label={t('nav.dashboard')} />
          <NavRow to="/groups" Icon={PeopleIcon} label={t('nav.myBubbles')} />
          <NavRow to="/academy" Icon={CapIcon} label={t('nav.academy')} />
          <NavRow to="/experts" Icon={BulbIcon} label={t('nav.experts')} />
          {(me?.role === 'EXPERT' || me?.role === 'ADMIN') && (
            <NavRow to="/expert" Icon={CapIcon} label={t('expert.hubNav')} />
          )}
          {me?.role === 'ADMIN' && (
            <NavRow to="/admin" Icon={ShieldIcon} label="Admin" />
          )}
        </nav>

        <div className="px-2 pb-3 pt-2 border-t border-white/20 flex flex-col gap-2">
          <NavRow
            onClick={toggleTheme}
            Icon={theme === 'dark' ? SunIcon : MoonIcon}
            label={theme === 'dark' ? t('nav.lightMode') : t('nav.darkMode')}
          />
          <LanguageSwitcher />
          <NavRow onClick={handleHelp} Icon={HelpIcon} label={t('nav.help')} />
          <NavRow onClick={handleReport} Icon={ReportIcon} label={t('nav.report')} />
          <NavRow onClick={handleLogout} Icon={LogoutIcon} label={t('nav.logout')} variant="danger" />
        </div>
      </aside>

      <main className="relative flex-1 min-h-0 flex flex-col overflow-hidden bg-surface rounded-[1.5rem] tablet:rounded-[2rem] shadow-themed border border-line">
        <Outlet />
      </main>
      <PersistentVideo />
      <QuizPrompt />
    </div>
  )
}
