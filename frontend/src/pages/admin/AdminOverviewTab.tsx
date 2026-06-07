import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getOverview, Overview, UserRole } from '../../api/admin'
import { Card } from '../../components/Card'
import { BubbleLoader } from '../../components/BubbleLoader'

const ROLES: UserRole[] = ['STUDENT', 'EXPERT', 'ADMIN']

export default function AdminOverviewTab() {
  const { t, i18n } = useTranslation()
  const [data, setData] = useState<Overview | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    getOverview()
      .then((next) => { if (!cancelled) setData(next) })
      .catch(() => { if (!cancelled) setError(true) })
    return () => { cancelled = true }
  }, [])

  const attention = useMemo(() => data ? buildAttention(data) : [], [data])

  if (error) {
    return (
      <Card size="lg" className="p-6 text-center">
        <p className="text-sm text-danger">{t('admin.overview.error')}</p>
      </Card>
    )
  }

  if (!data) {
    return (
      <div className="h-full min-h-[18rem] flex items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-sm text-muted">
          <BubbleLoader size={56} />
          <span>{t('common.loading')}</span>
        </div>
      </div>
    )
  }

  const activityTime = new Intl.DateTimeFormat(i18n.language, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  return (
    <div className="flex flex-col gap-6">
      <section className="grid grid-cols-1 tablet:grid-cols-2 desktop:grid-cols-4 gap-3">
        <Kpi label={t('admin.overview.kpi.users')} value={data.kpis.totalUsers} />
        <Kpi label={t('admin.overview.kpi.bubbles')} value={data.kpis.totalGroups} variant="bubbles" />
        <Kpi label={t('admin.overview.kpi.courses')} value={data.kpis.totalCourses} />
        <Kpi label={t('admin.overview.kpi.experts')} value={data.kpis.verifiedExperts} />
      </section>

      <section className="grid grid-cols-1 desktop:grid-cols-[1.05fr_0.95fr] gap-4">
        <Card size="lg" className="p-5">
          <div className="flex items-start justify-between gap-4 mb-4">
            <div>
              <h2 className="text-lg font-bold text-base">{t('admin.overview.growthTitle')}</h2>
              <p className="text-sm text-muted">{t('admin.overview.growthSubtitle')}</p>
            </div>
            <DeltaBadge value={data.kpis.weekOverWeekDelta} />
          </div>
          <div className="grid grid-cols-1 tablet:grid-cols-3 gap-3">
            <MiniMetric label={t('admin.overview.kpi.newThisWeek')} value={data.kpis.newUsersThisWeek} />
            <MiniMetric label={t('admin.overview.kpi.newLastWeek')} value={data.kpis.newUsersLastWeek} />
            <MiniMetric label={t('admin.overview.kpi.weekDelta')} value={data.kpis.weekOverWeekDelta} />
          </div>
        </Card>

        <Card size="lg" className="p-5">
          <h2 className="text-lg font-bold text-base mb-1">{t('admin.overview.rolesTitle')}</h2>
          <p className="text-sm text-muted mb-4">{t('admin.overview.rolesSubtitle')}</p>
          <RoleBars distribution={data.roleDistribution} total={data.kpis.totalUsers} />
        </Card>
      </section>

      <section className="grid grid-cols-1 desktop:grid-cols-[0.9fr_1.1fr] gap-4">
        <Card size="lg" className="p-5">
          <h2 className="text-lg font-bold text-base mb-1">{t('admin.overview.attentionTitle')}</h2>
          <p className="text-sm text-muted mb-4">{t('admin.overview.attentionSubtitle')}</p>
          <div className="flex flex-col gap-2">
            {attention.map((item) => (
              <AttentionRow key={item.key} item={item} />
            ))}
          </div>
        </Card>

        <Card size="lg" className="p-5">
          <h2 className="text-lg font-bold text-base mb-1">{t('admin.overview.activityTitle')}</h2>
          <p className="text-sm text-muted mb-4">{t('admin.overview.activitySubtitle')}</p>
          <ul className="flex flex-col gap-2">
            {data.recentActivity.map((activity) => (
              <li
                key={`${activity.kind}-${activity.id}`}
                className="rounded-2xl border border-line bg-surface-muted px-3 py-2 flex flex-col tablet:flex-row tablet:items-center tablet:justify-between gap-2"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <KindBadge kind={activity.kind} />
                  <span className="text-sm text-base truncate">{activity.label}</span>
                </div>
                <span className="text-xs text-muted shrink-0">
                  {activityTime.format(new Date(activity.at))}
                </span>
              </li>
            ))}
            {data.recentActivity.length === 0 && (
              <li className="rounded-2xl border border-dashed border-line px-4 py-6 text-center text-sm text-muted">
                {t('admin.overview.noActivity')}
              </li>
            )}
          </ul>
        </Card>
      </section>
    </div>
  )
}

function Kpi({ label, value, variant }: { label: string; value: number; variant?: 'bubbles' }) {
  return (
    <Card size="lg" className={`p-5 relative overflow-hidden ${variant === 'bubbles' ? 'admin-bubble-kpi' : ''}`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</p>
          <p className="mt-2 text-3xl font-bold text-base">{value.toLocaleString()}</p>
        </div>
        {variant === 'bubbles' ? <BubbleKpiIcon /> : (
          <div className="ring-iridescent p-[1.5px] rounded-full shrink-0">
            <span className="block w-9 h-9 rounded-full bg-surface" />
          </div>
        )}
      </div>
    </Card>
  )
}

function BubbleKpiIcon() {
  return (
    <div className="relative shrink-0 w-14 h-12" aria-hidden="true">
      <span className="absolute right-1 top-1 w-9 h-9 rounded-full bg-bubble-magenta-soft border border-bubble-magenta/30 shadow-themed" />
      <span className="absolute right-7 top-5 w-5 h-5 rounded-full bg-bubble-green-soft border border-bubble-green/30" />
      <span className="absolute right-4 top-3 w-2 h-2 rounded-full bg-surface/80" />
    </div>
  )
}

function MiniMetric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-2xl border border-line bg-surface-muted px-4 py-3">
      <p className="text-xs text-muted">{label}</p>
      <p className="mt-1 text-xl font-semibold text-base">{value.toLocaleString()}</p>
    </div>
  )
}

function DeltaBadge({ value }: { value: number }) {
  const positive = value >= 0
  return (
    <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
      positive ? 'bg-bubble-green-soft text-bubble-green' : 'bg-danger-soft text-danger'
    }`}>
      {positive ? '+' : ''}{value.toLocaleString()}
    </span>
  )
}

function RoleBars({ distribution, total }: { distribution: Record<UserRole, number>; total: number }) {
  const { t } = useTranslation()
  return (
    <div className="flex flex-col gap-3">
      {ROLES.map((role) => {
        const value = distribution[role] ?? 0
        const pct = total > 0 ? Math.round((value / total) * 100) : 0
        return (
          <div key={role} className="flex flex-col gap-1.5">
            <div className="flex justify-between text-xs">
              <span className="text-secondary">{t(`admin.roles.${role}`)}</span>
              <span className="text-base">{value.toLocaleString()} ({pct}%)</span>
            </div>
            <div className="h-2 rounded-full bg-surface-muted overflow-hidden">
              <div className="h-full bg-brand-gradient-strong rounded-full" style={{ width: `${pct}%` }} />
            </div>
          </div>
        )
      })}
    </div>
  )
}

type AttentionItem = {
  key: string
  tone: 'warning' | 'ok' | 'info'
  titleKey: string
  bodyKey: string
}

function buildAttention(data: Overview): AttentionItem[] {
  const adminCount = data.roleDistribution.ADMIN ?? 0
  return [
    {
      key: 'adminCoverage',
      tone: adminCount <= 1 ? 'warning' : 'ok',
      titleKey: adminCount <= 1 ? 'admin.overview.attention.oneAdminTitle' : 'admin.overview.attention.adminCoverageTitle',
      bodyKey: adminCount <= 1 ? 'admin.overview.attention.oneAdminBody' : 'admin.overview.attention.adminCoverageBody',
    },
    {
      key: 'expertReview',
      tone: data.kpis.verifiedExperts === 0 ? 'info' : 'ok',
      titleKey: data.kpis.verifiedExperts === 0 ? 'admin.overview.attention.noExpertsTitle' : 'admin.overview.attention.expertsTitle',
      bodyKey: data.kpis.verifiedExperts === 0 ? 'admin.overview.attention.noExpertsBody' : 'admin.overview.attention.expertsBody',
    },
    {
      key: 'catalog',
      tone: data.kpis.totalCourses > 0 ? 'ok' : 'warning',
      titleKey: data.kpis.totalCourses > 0 ? 'admin.overview.attention.catalogTitle' : 'admin.overview.attention.catalogEmptyTitle',
      bodyKey: data.kpis.totalCourses > 0 ? 'admin.overview.attention.catalogBody' : 'admin.overview.attention.catalogEmptyBody',
    },
  ]
}

function AttentionRow({ item }: { item: AttentionItem }) {
  const { t } = useTranslation()
  const toneClass = {
    warning: 'bg-warning/15 text-warning',
    ok: 'bg-bubble-green-soft text-bubble-green',
    info: 'bg-primary-100 text-primary-700',
  }[item.tone]
  return (
    <div className="rounded-2xl border border-line bg-surface-muted px-3 py-3 flex gap-3">
      <span className={`mt-0.5 w-2.5 h-2.5 rounded-full shrink-0 ${toneClass}`} />
      <div>
        <p className="text-sm font-semibold text-base">{t(item.titleKey)}</p>
        <p className="text-xs text-muted mt-0.5">{t(item.bodyKey)}</p>
      </div>
    </div>
  )
}

function KindBadge({ kind }: { kind: Overview['recentActivity'][number]['kind'] }) {
  const { t } = useTranslation()
  const tone: Record<Overview['recentActivity'][number]['kind'], string> = {
    USER_REGISTERED: 'bg-primary-100 text-primary-700',
    GROUP_CREATED: 'bg-bubble-magenta-soft text-bubble-magenta',
    COURSE_CREATED: 'bg-bubble-green-soft text-bubble-green',
  }
  return (
    <span className={`text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-md ${tone[kind]}`}>
      {t(`admin.activity.${kind}`)}
    </span>
  )
}
