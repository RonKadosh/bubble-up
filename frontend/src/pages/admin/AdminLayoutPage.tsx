import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import AdminOverviewTab from './AdminOverviewTab'
import AdminUsersTab from './AdminUsersTab'
import AdminCatalogTab from './AdminCatalogTab'
import AdminGroupsTab from './AdminGroupsTab'
import AdminQuizTab from './AdminQuizTab'
import AdminAuditTab from './AdminAuditTab'
import { Button } from '../../components/Button'

const TABS = [
  { key: 'overview', labelKey: 'admin.tabs.overview' },
  { key: 'users', labelKey: 'admin.tabs.users' },
  { key: 'catalog', labelKey: 'admin.tabs.catalog' },
  { key: 'groups', labelKey: 'admin.tabs.bubbles' },
  { key: 'quiz', labelKey: 'admin.tabs.quiz' },
  { key: 'audit', labelKey: 'admin.tabs.audit' },
] as const

type TabKey = (typeof TABS)[number]['key']

export default function AdminLayoutPage() {
  const { t } = useTranslation()
  const params = useParams<{ tab?: string }>()
  const navigate = useNavigate()
  const active: TabKey = useMemo(() => {
    const raw = params.tab ?? 'overview'
    return TABS.some((tab) => tab.key === raw) ? (raw as TabKey) : 'overview'
  }, [params.tab])

  return (
    <div className="flex flex-col h-full overflow-hidden bg-base">
      <header className="px-4 tablet:px-6 py-4 border-b border-line flex flex-col tablet:flex-row tablet:items-center tablet:justify-between gap-3">
        <div>
          <div className="flex items-center gap-2 mb-1">
            <span className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
            <span className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
            <h1 className="text-2xl font-bold text-base">{t('admin.title')}</h1>
          </div>
          <p className="text-sm text-muted ms-[1.6rem]">{t('admin.subtitle')}</p>
        </div>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          className="self-start tablet:self-auto"
          onClick={() => navigate('/admin/experts')}
        >
          {t('admin.expertReview')}
        </Button>
      </header>

      <nav className="px-4 tablet:px-6 py-3 border-b border-line flex gap-2 overflow-x-auto">
        {TABS.map((tab) => {
          const selected = active === tab.key
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => navigate(tab.key === 'overview' ? '/admin' : `/admin/${tab.key}`)}
              aria-pressed={selected}
              className={`shrink-0 px-4 py-2 text-sm rounded-full bubble-pop border transition ${
                selected
                  ? 'bg-brand-gradient-strong border-transparent text-on-brand shadow-themed font-semibold'
                  : 'bg-surface border-line text-secondary hover:text-base hover:border-line-strong'
              }`}
            >
              {t(tab.labelKey)}
            </button>
          )
        })}
      </nav>

      <main className="flex-1 overflow-y-auto p-4 tablet:p-6">
        {active === 'overview' && <AdminOverviewTab />}
        {active === 'users' && <AdminUsersTab />}
        {active === 'catalog' && <AdminCatalogTab />}
        {active === 'groups' && <AdminGroupsTab />}
        {active === 'quiz' && <AdminQuizTab />}
        {active === 'audit' && <AdminAuditTab />}
      </main>
    </div>
  )
}
