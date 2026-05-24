import { useState, type ComponentType } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import { useThemeStore } from '../store/themeStore'
import { useLanguageStore } from '../store/languageStore'
import { SUPPORTED_LANGUAGES } from '../i18n'
import { logout as logoutApi } from '../api/auth'
import {
  BubbleLogo,
  ChevronIcon,
  DashboardIcon,
  GlobeIcon,
  HelpIcon,
  LogoutIcon,
  MoonIcon,
  PeopleIcon,
  ReportIcon,
  SunIcon,
} from './Icons'

type IconComp = ComponentType<{ className?: string }>

interface NavRowProps {
  to?: string
  onClick?: () => void
  Icon: IconComp
  label: string
  collapsed: boolean
  variant?: 'default' | 'danger'
  ariaLabel?: string
}

function NavRow({ to, onClick, Icon, label, collapsed, variant = 'default', ariaLabel }: NavRowProps) {
  const base =
    'group relative flex items-center gap-3 px-3.5 py-2.5 rounded-full transition-all text-sm font-medium bubble-pop text-start'
  const danger = variant === 'danger'

  const content = (active: boolean) => (
    <>
      <Icon className="w-5 h-5 shrink-0" />
      <span
        className={`whitespace-nowrap overflow-hidden transition-[max-width,opacity] duration-200 ${
          collapsed ? 'max-w-0 opacity-0' : 'max-w-[10rem] opacity-100'
        }`}
      >
        {label}
      </span>
      {collapsed && (
        <span className="pointer-events-none absolute start-full ms-2 z-50 whitespace-nowrap rounded-lg bg-surface text-base px-2.5 py-1 text-xs border border-line shadow-themed opacity-0 group-hover:opacity-100 transition-opacity">
          {label}
        </span>
      )}
      {active && !collapsed && (
        <span className="absolute end-3 w-1.5 h-1.5 rounded-full bg-white/90" />
      )}
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
        {({ isActive }) => content(isActive)}
      </NavLink>
    )
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={ariaLabel ?? label}
      className={`${base} w-full ${
        danger
          ? 'text-on-brand/80 hover:bg-[rgba(217,79,79,0.25)] hover:text-on-brand'
          : 'text-on-brand/80 hover:bg-white/15 hover:text-on-brand'
      }`}
    >
      {content(false)}
    </button>
  )
}

function LanguageSwitcher({ collapsed }: { collapsed: boolean }) {
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
        collapsed={collapsed}
      />
      {open && (
        <div className="absolute bottom-full mb-2 start-2 end-2 z-50 rounded-2xl bg-surface border border-line shadow-bubble overflow-hidden">
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
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const clearAuth = useAuthStore((s) => s.clearAuth)
  const user = useAuthStore((s) => s.user)
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
    <div className="flex h-screen bg-base p-3 gap-3">
      <aside
        className={`flex flex-col bg-brand-gradient-vertical text-on-brand shadow-bubble rounded-[2rem] transition-[width] duration-200 ease-out overflow-hidden ${
          collapsed ? 'w-[4.5rem]' : 'w-60'
        }`}
      >
        <div className="flex items-center justify-between px-3 py-4 border-b border-white/20">
          <div className={`flex items-center gap-2.5 overflow-hidden ${collapsed ? 'justify-center w-full' : ''}`}>
            <div className="w-11 h-11 rounded-full bg-white/35 ring-on-brand flex items-center justify-center shrink-0 backdrop-blur-sm shadow-sm">
              <BubbleLogo className="w-5 h-5" />
            </div>
            {!collapsed && (
              <span className="font-bold text-lg tracking-tight whitespace-nowrap">{t('brand.name')}</span>
            )}
          </div>
          {!collapsed && (
            <button
              onClick={() => setCollapsed(true)}
              className="p-1.5 rounded-lg hover:bg-white/15 text-on-brand/80"
              aria-label={t('nav.collapse')}
              title={t('nav.collapse')}
            >
              {/* Chevron points to the inline-start edge regardless of direction. */}
              <ChevronIcon className="w-4 h-4 rtl:rotate-180" />
            </button>
          )}
        </div>

        {collapsed && (
          <button
            onClick={() => setCollapsed(false)}
            className="mx-auto mt-2 p-1.5 rounded-lg hover:bg-white/15 text-on-brand/80"
            aria-label={t('nav.expand')}
            title={t('nav.expand')}
          >
            <ChevronIcon className="w-4 h-4 rotate-180 rtl:rotate-0" />
          </button>
        )}

        <nav className="flex-1 flex flex-col gap-1 px-2 py-4">
          <NavRow to="/dashboard" Icon={DashboardIcon} label={t('nav.home')} collapsed={collapsed} />
          <NavRow to="/groups" Icon={PeopleIcon} label={t('nav.myBubbles')} collapsed={collapsed} />
        </nav>

        <div className="px-2 pb-3 pt-2 border-t border-white/20 flex flex-col gap-1">
          <NavRow
            onClick={toggleTheme}
            Icon={theme === 'dark' ? SunIcon : MoonIcon}
            label={theme === 'dark' ? t('nav.lightMode') : t('nav.darkMode')}
            collapsed={collapsed}
          />
          <LanguageSwitcher collapsed={collapsed} />
          <NavRow onClick={handleHelp} Icon={HelpIcon} label={t('nav.help')} collapsed={collapsed} />
          <NavRow onClick={handleReport} Icon={ReportIcon} label={t('nav.report')} collapsed={collapsed} />
          <NavRow onClick={handleLogout} Icon={LogoutIcon} label={t('nav.logout')} collapsed={collapsed} variant="danger" />
        </div>

        {!collapsed && user && (
          <div className="mx-3 mb-3 px-3 py-2.5 rounded-2xl bg-white/25 backdrop-blur-sm text-xs text-on-brand/85 truncate ring-on-brand">
            <p className="text-[10px] uppercase tracking-wider text-on-brand/65">{t('nav.signedIn')}</p>
            <p className="truncate" dir="ltr">{user.email}</p>
          </div>
        )}
      </aside>

      <main className="flex-1 flex flex-col overflow-hidden bg-surface rounded-[2rem] shadow-themed border border-line">
        <Outlet />
      </main>
    </div>
  )
}
