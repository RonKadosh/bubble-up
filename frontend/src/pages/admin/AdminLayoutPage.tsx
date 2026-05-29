import { useMemo } from 'react'
import { NavLink, useNavigate, useParams } from 'react-router-dom'
import AdminOverviewTab from './AdminOverviewTab'
import AdminUsersTab from './AdminUsersTab'
import AdminCatalogTab from './AdminCatalogTab'
import AdminGroupsTab from './AdminGroupsTab'
import AdminQuizTab from './AdminQuizTab'

const TABS = [
  { key: 'overview', label: 'Overview' },
  { key: 'users', label: 'Users' },
  { key: 'catalog', label: 'Catalog' },
  { key: 'groups', label: 'Groups' },
  { key: 'quiz', label: 'Quiz' },
] as const

type TabKey = (typeof TABS)[number]['key']

export default function AdminLayoutPage() {
  const params = useParams<{ tab?: string }>()
  const navigate = useNavigate()
  const active: TabKey = useMemo(() => {
    const raw = params.tab ?? 'overview'
    return TABS.some((t) => t.key === raw) ? (raw as TabKey) : 'overview'
  }, [params.tab])

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <header className="px-6 py-4 border-b border-line flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-base">Admin</h1>
          <p className="text-sm text-secondary">System administration for Bubble.up</p>
        </div>
        <NavLink
          to="/admin/experts"
          className="text-sm rounded-full px-4 py-2 border border-line bg-surface text-base hover:bg-surface-hover"
        >
          Expert verification →
        </NavLink>
      </header>
      <nav className="px-6 pt-3 border-b border-line flex gap-1 overflow-x-auto">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => navigate(tab.key === 'overview' ? '/admin' : `/admin/${tab.key}`)}
            className={`px-4 py-2 text-sm rounded-t-xl border-b-2 transition ${
              active === tab.key
                ? 'border-indigo-600 text-base font-medium'
                : 'border-transparent text-secondary hover:text-base'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </nav>
      <main className="flex-1 overflow-y-auto p-6">
        {active === 'overview' && <AdminOverviewTab />}
        {active === 'users' && <AdminUsersTab />}
        {active === 'catalog' && <AdminCatalogTab />}
        {active === 'groups' && <AdminGroupsTab />}
        {active === 'quiz' && <AdminQuizTab />}
      </main>
    </div>
  )
}
