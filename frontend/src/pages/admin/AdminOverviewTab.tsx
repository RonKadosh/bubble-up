import { useEffect, useState } from 'react'
import { getOverview, Overview, UserRole } from '../../api/admin'

export default function AdminOverviewTab() {
  const [data, setData] = useState<Overview | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    getOverview().then(setData).catch((e) => setError(String(e)))
  }, [])

  if (error) return <p className="text-red-600">{error}</p>
  if (!data) return <p className="text-secondary">Loading…</p>

  return (
    <div className="flex flex-col gap-6">
      <section className="grid grid-cols-2 tablet:grid-cols-4 gap-3">
        <Kpi label="Total users" value={data.kpis.totalUsers} />
        <Kpi label="Total groups" value={data.kpis.totalGroups} />
        <Kpi label="Total courses" value={data.kpis.totalCourses} />
        <Kpi label="Verified experts" value={data.kpis.verifiedExperts} />
        <Kpi label="New users (this week)" value={data.kpis.newUsersThisWeek} />
        <Kpi label="New users (last week)" value={data.kpis.newUsersLastWeek} />
        <Kpi
          label="WoW delta"
          value={data.kpis.weekOverWeekDelta}
          accent={data.kpis.weekOverWeekDelta >= 0 ? 'positive' : 'negative'}
        />
        <Kpi label="Pending actions" value="—" muted />
      </section>

      <section className="grid grid-cols-1 tablet:grid-cols-[1fr_2fr] gap-3">
        <div className="rounded-2xl border border-line bg-surface p-4">
          <h3 className="text-sm font-semibold text-base mb-3">Role distribution</h3>
          <RoleBars distribution={data.roleDistribution} total={data.kpis.totalUsers} />
        </div>
        <div className="rounded-2xl border border-line bg-surface p-4">
          <h3 className="text-sm font-semibold text-base mb-3">Recent activity</h3>
          <ul className="flex flex-col gap-2">
            {data.recentActivity.map((a) => (
              <li key={`${a.kind}-${a.id}`} className="flex items-center justify-between text-sm">
                <div className="flex items-center gap-2">
                  <KindBadge kind={a.kind} />
                  <span className="text-base">{a.label}</span>
                </div>
                <span className="text-xs text-secondary">{new Date(a.at).toLocaleString()}</span>
              </li>
            ))}
            {data.recentActivity.length === 0 && (
              <li className="text-secondary text-sm">No recent activity yet.</li>
            )}
          </ul>
        </div>
      </section>
    </div>
  )
}

function Kpi({
  label,
  value,
  accent,
  muted,
}: {
  label: string
  value: number | string
  accent?: 'positive' | 'negative'
  muted?: boolean
}) {
  const color =
    accent === 'positive' ? 'text-green-600' : accent === 'negative' ? 'text-red-600' : 'text-base'
  return (
    <div className={`rounded-2xl border border-line bg-surface p-4 ${muted ? 'opacity-60' : ''}`}>
      <div className="text-xs uppercase tracking-wide text-secondary">{label}</div>
      <div className={`mt-1 text-2xl font-semibold ${color}`}>{value}</div>
    </div>
  )
}

function RoleBars({ distribution, total }: { distribution: Record<UserRole, number>; total: number }) {
  const roles: UserRole[] = ['STUDENT', 'EXPERT', 'ADMIN']
  return (
    <div className="flex flex-col gap-2">
      {roles.map((r) => {
        const v = distribution[r] ?? 0
        const pct = total > 0 ? Math.round((v / total) * 100) : 0
        return (
          <div key={r} className="flex flex-col gap-1">
            <div className="flex justify-between text-xs">
              <span className="text-secondary">{r}</span>
              <span className="text-base">{v} ({pct}%)</span>
            </div>
            <div className="h-2 rounded-full bg-base/40 overflow-hidden">
              <div className="h-full bg-indigo-600" style={{ width: `${pct}%` }} />
            </div>
          </div>
        )
      })}
    </div>
  )
}

function KindBadge({ kind }: { kind: Overview['recentActivity'][number]['kind'] }) {
  const map: Record<Overview['recentActivity'][number]['kind'], string> = {
    USER_REGISTERED: 'bg-blue-100 text-blue-700',
    GROUP_CREATED: 'bg-purple-100 text-purple-700',
    COURSE_CREATED: 'bg-emerald-100 text-emerald-700',
  }
  const label: Record<Overview['recentActivity'][number]['kind'], string> = {
    USER_REGISTERED: 'USER',
    GROUP_CREATED: 'GROUP',
    COURSE_CREATED: 'COURSE',
  }
  return (
    <span className={`text-[10px] font-medium uppercase tracking-wide px-2 py-0.5 rounded-full ${map[kind]}`}>
      {label[kind]}
    </span>
  )
}
