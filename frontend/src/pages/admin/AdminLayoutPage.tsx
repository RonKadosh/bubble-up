import { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'
import AdminOverviewTab from './AdminOverviewTab'
import AdminUsersTab from './AdminUsersTab'
import AdminCatalogTab from './AdminCatalogTab'
import AdminGroupsTab from './AdminGroupsTab'
import AdminQuizTab from './AdminQuizTab'
import AdminMatchingTab from './AdminMatchingTab'
import AdminAuditTab from './AdminAuditTab'
import AdminExpertRequestsTab from './AdminExpertRequestsTab'
import AdminReportsTab from './AdminReportsTab'
import { getInboxCounts, InboxCounts } from '../../api/admin'
import { PageShell } from '../../components/PageHeader'
import { Tabs } from '../../components/Tabs'

const TABS = [
  { key: 'overview', labelKey: 'admin.tabs.overview' },
  { key: 'expert-requests', labelKey: 'admin.tabs.expertRequests' },
  { key: 'reports', labelKey: 'admin.tabs.reports' },
  { key: 'users', labelKey: 'admin.tabs.users' },
  { key: 'catalog', labelKey: 'admin.tabs.catalog' },
  { key: 'groups', labelKey: 'admin.tabs.bubbles' },
  { key: 'quiz', labelKey: 'admin.tabs.quiz' },
  { key: 'matching', labelKey: 'admin.tabs.matching' },
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

  const [counts, setCounts] = useState<InboxCounts | null>(null)
  const refreshCounts = useCallback(() => {
    getInboxCounts().then(setCounts).catch(() => { /* badge is best-effort */ })
  }, [])
  useEffect(() => { refreshCounts() }, [refreshCounts])

  const badgeFor = (key: TabKey): number | undefined => {
    if (!counts) return undefined
    if (key === 'expert-requests') return counts.pendingExpertRequests
    if (key === 'reports') return counts.pendingReports
    return undefined
  }

  return (
    <PageShell
      title={t('admin.title')}
      subtitle={t('admin.subtitle')}
      tabs={
        <Tabs
          items={TABS.map((tab) => ({ key: tab.key, label: t(tab.labelKey), badge: badgeFor(tab.key) }))}
          active={active}
          onChange={(key) => navigate(key === 'overview' ? '/admin' : `/admin/${key}`)}
        />
      }
    >
      {active === 'overview' && <AdminOverviewTab />}
      {active === 'expert-requests' && <AdminExpertRequestsTab onChanged={refreshCounts} />}
      {active === 'reports' && <AdminReportsTab onChanged={refreshCounts} />}
      {active === 'users' && <AdminUsersTab />}
      {active === 'catalog' && <AdminCatalogTab />}
      {active === 'groups' && <AdminGroupsTab />}
      {active === 'quiz' && <AdminQuizTab />}
      {active === 'matching' && <AdminMatchingTab />}
      {active === 'audit' && <AdminAuditTab />}
    </PageShell>
  )
}
