import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  getMatchingFeedback, MatchFeedbackAnalytics,
  getMatchingStats, MatchingStats,
} from '../../api/admin'
import { Card } from '../../components/Card'
import { BubbleLoader } from '../../components/BubbleLoader'

/**
 * Read-only reflection on the matching engine. Two halves:
 *  - Profiles: aggregate, identity-free population health (role distribution, the
 *    population's average profile, group-confidence spread, most active groups).
 *  - Feedback: the shown→joined funnel + ratings.
 * All backed by GET /api/admin/matching-stats and /matching-feedback (no UI computation).
 */
export default function AdminMatchingTab() {
  const { t } = useTranslation()
  const [stats, setStats] = useState<MatchingStats | null>(null)
  const [feedback, setFeedback] = useState<MatchFeedbackAnalytics | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    Promise.allSettled([getMatchingStats(), getMatchingFeedback()]).then(([s, f]) => {
      if (cancelled) return
      if (s.status === 'fulfilled') setStats(s.value)
      if (f.status === 'fulfilled') setFeedback(f.value)
      if (s.status === 'rejected' && f.status === 'rejected') setError(true)
    })
    return () => { cancelled = true }
  }, [])

  if (error) {
    return (
      <Card size="lg" className="p-6 text-center">
        <p className="text-sm text-danger">{t('admin.matching.error')}</p>
      </Card>
    )
  }

  if (!stats && !feedback) {
    return (
      <div className="h-full min-h-[18rem] flex items-center justify-center">
        <div className="flex flex-col items-center gap-3 text-sm text-muted">
          <BubbleLoader size={56} />
          <span>{t('common.loading')}</span>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      {stats && <ProfilesSection stats={stats} />}
      {feedback && <FeedbackSection data={feedback} />}
    </div>
  )
}

// ─── Profiles (population health) ─────────────────────────────────────────────

function ProfilesSection({ stats }: { stats: MatchingStats }) {
  const { t } = useTranslation()
  const c = stats.coverage
  const topRoleCount = Math.max(1, ...stats.dominantRoles.map((r) => r.count))
  const topGroupConf = Math.max(1, ...stats.groupConfidence.map((g) => g.count))

  return (
    <div className="flex flex-col gap-6">
      <h2 className="text-base font-bold text-base">{t('admin.matching.profilesHeading')}</h2>

      <section className="grid grid-cols-2 tablet:grid-cols-3 desktop:grid-cols-5 gap-3">
        <Kpi label={t('admin.matching.kpi.profiledUsers')} value={c.profiledUsers} />
        <Kpi label={t('admin.matching.kpi.matchedReady')} value={c.matchedReadyUsers} />
        <Kpi label={t('admin.matching.kpi.avgConfidence')} value={`${Math.round(c.avgConfidence * 100)}%`} />
        <Kpi label={t('admin.matching.kpi.profiledGroups')} value={c.profiledGroups} />
        <Kpi label={t('admin.matching.kpi.quizAnswers')} value={c.quizAnswers} />
      </section>

      <section className="grid grid-cols-1 desktop:grid-cols-2 gap-4">
        <Card size="lg" className="p-5">
          <h3 className="text-lg font-bold text-base mb-1">{t('admin.matching.rolesTitle')}</h3>
          <p className="text-sm text-muted mb-4">{t('admin.matching.rolesSubtitle')}</p>
          <div className="flex flex-col gap-2.5">
            {stats.dominantRoles.map((r) => (
              <BarRow key={r.role} label={roleLabel(t, r.role)} value={r.count}
                      pct={Math.round((r.count / topRoleCount) * 100)} suffix={String(r.count)} />
            ))}
          </div>
        </Card>

        <Card size="lg" className="p-5">
          <h3 className="text-lg font-bold text-base mb-1">{t('admin.matching.profileTitle')}</h3>
          <p className="text-sm text-muted mb-4">{t('admin.matching.profileSubtitle')}</p>
          <div className="flex flex-col gap-2.5">
            {stats.populationProfile.map((r) => (
              <BarRow key={r.role} label={roleLabel(t, r.role)} value={r.avg}
                      pct={Math.round(r.avg * 100)} suffix={r.avg.toFixed(2)} />
            ))}
          </div>
        </Card>
      </section>

      <section className="grid grid-cols-1 desktop:grid-cols-[0.8fr_1.2fr] gap-4">
        <Card size="lg" className="p-5">
          <h3 className="text-lg font-bold text-base mb-1">{t('admin.matching.groupConfTitle')}</h3>
          <p className="text-sm text-muted mb-4">{t('admin.matching.groupConfSubtitle')}</p>
          <div className="flex flex-col gap-2.5">
            {stats.groupConfidence.map((g) => (
              <BarRow key={g.label} label={t(`admin.matching.conf.${g.label}`)} value={g.count}
                      pct={Math.round((g.count / topGroupConf) * 100)} suffix={String(g.count)}
                      tone={g.label === 'HIGH' ? 'good' : g.label === 'LOW' ? 'bad' : 'neutral'} />
            ))}
          </div>
        </Card>

        <Card size="lg" className="p-5">
          <h3 className="text-lg font-bold text-base mb-1">{t('admin.matching.topGroupsTitle')}</h3>
          <p className="text-sm text-muted mb-4">{t('admin.matching.topGroupsSubtitle')}</p>
          <div className="flex flex-col gap-2">
            {stats.topGroups.length === 0 && (
              <p className="text-sm text-muted">{t('admin.matching.empty')}</p>
            )}
            {stats.topGroups.map((g, i) => (
              <div key={`${g.name}-${i}`}
                   className="rounded-2xl border border-line bg-surface-muted px-3 py-2 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <p className="text-sm text-base truncate">{g.name}</p>
                  <p className="text-xs text-muted">
                    {t('admin.matching.topGroups.members', { count: g.memberCount })}
                  </p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <span className="text-[10px] font-semibold uppercase tracking-wide px-2 py-0.5 rounded-md bg-primary-100 text-primary-700">
                    {roleLabel(t, g.dominantRole)}
                  </span>
                  <span className="text-xs text-secondary tabular-nums w-9 text-end">
                    {Math.round(g.confidence * 100)}%
                  </span>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </section>
    </div>
  )
}

// ─── Feedback (conversion funnel) ─────────────────────────────────────────────

function FeedbackSection({ data }: { data: MatchFeedbackAnalytics }) {
  const { t } = useTranslation()
  const totalShown = data.funnelByMode.reduce((s, f) => s + f.shown, 0)
  const totalRatings = data.ratings.goodFit + data.ratings.badFit
  const empty = totalShown === 0 && totalRatings === 0

  return (
    <div className="flex flex-col gap-6">
      <h2 className="text-base font-bold text-base mt-2">{t('admin.matching.feedbackHeading')}</h2>

      {empty ? (
        <Card size="lg" className="p-6 text-center">
          <p className="text-sm text-muted">{t('admin.matching.empty')}</p>
        </Card>
      ) : (
        <>
          <Card size="lg" className="p-5">
            <h3 className="text-lg font-bold text-base mb-1">{t('admin.matching.funnelTitle')}</h3>
            <p className="text-sm text-muted mb-4">{t('admin.matching.funnelSubtitle')}</p>
            <div className="flex flex-col gap-4">
              {data.funnelByMode.map((f) => (
                <FunnelRow key={f.mode} mode={f.mode} shown={f.shown} joined={f.joined} joinRate={f.joinRate} />
              ))}
            </div>
          </Card>

          <Card size="lg" className="p-5">
            <h3 className="text-lg font-bold text-base mb-1">{t('admin.matching.bucketsTitle')}</h3>
            <p className="text-sm text-muted mb-4">{t('admin.matching.bucketsSubtitle')}</p>
            <div className="overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="text-xs uppercase tracking-wide text-muted">
                    <th className="text-start font-semibold py-2 pe-3">{t('admin.matching.col.bucket')}</th>
                    <th className="text-end font-semibold py-2 px-3">{t('admin.matching.col.shown')}</th>
                    <th className="text-end font-semibold py-2 px-3">{t('admin.matching.col.joined')}</th>
                    <th className="text-end font-semibold py-2 px-3">{t('admin.matching.col.joinRate')}</th>
                    <th className="text-end font-semibold py-2 px-3">{t('admin.matching.col.good')}</th>
                    <th className="text-end font-semibold py-2 ps-3">{t('admin.matching.col.bad')}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.matchPercentBuckets.map((b) => (
                    <tr key={b.label} className="border-t border-line">
                      <td className="py-2 pe-3 tabular-nums text-secondary">{b.label}</td>
                      <td className="py-2 px-3 text-end tabular-nums">{b.shown}</td>
                      <td className="py-2 px-3 text-end tabular-nums">{b.joined}</td>
                      <td className="py-2 px-3 text-end tabular-nums">{b.shown > 0 ? `${Math.round(b.joinRate * 100)}%` : '—'}</td>
                      <td className="py-2 px-3 text-end tabular-nums text-bubble-green">{b.goodFit || ''}</td>
                      <td className="py-2 ps-3 text-end tabular-nums text-danger">{b.badFit || ''}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          <Card size="lg" className="p-5">
            <h3 className="text-lg font-bold text-base mb-1">{t('admin.matching.ratingsTitle')}</h3>
            <p className="text-sm text-muted mb-4">{t('admin.matching.ratingsSubtitle')}</p>
            <div className="grid grid-cols-2 gap-3 max-w-md">
              <RatingStat label={t('admin.matching.goodFit')} value={data.ratings.goodFit} tone="good" />
              <RatingStat label={t('admin.matching.badFit')} value={data.ratings.badFit} tone="bad" />
            </div>
          </Card>
        </>
      )}
    </div>
  )
}

// ─── Shared bits ──────────────────────────────────────────────────────────────

function Kpi({ label, value }: { label: string; value: number | string }) {
  return (
    <Card size="lg" className="p-4">
      <p className="text-xs font-semibold uppercase tracking-wide text-muted">{label}</p>
      <p className="mt-1.5 text-2xl font-bold text-base tabular-nums">
        {typeof value === 'number' ? value.toLocaleString() : value}
      </p>
    </Card>
  )
}

function BarRow({ label, pct, suffix, tone = 'brand' }: {
  label: string; value: number; pct: number; suffix: string; tone?: 'brand' | 'good' | 'bad' | 'neutral'
}) {
  const fill = tone === 'good' ? 'bg-bubble-green'
    : tone === 'bad' ? 'bg-danger'
    : tone === 'neutral' ? 'bg-secondary/40'
    : 'bg-brand-gradient-strong'
  return (
    <div className="flex flex-col gap-1">
      <div className="flex justify-between text-xs">
        <span className="text-secondary">{label}</span>
        <span className="text-base tabular-nums">{suffix}</span>
      </div>
      <div className="h-2 rounded-full bg-surface-muted overflow-hidden">
        <div className={`h-full rounded-full ${fill}`} style={{ width: `${Math.max(0, Math.min(100, pct))}%` }} />
      </div>
    </div>
  )
}

function FunnelRow({ mode, shown, joined, joinRate }: { mode: string; shown: number; joined: number; joinRate: number }) {
  const { t } = useTranslation()
  const label = mode === 'MATCHED' ? t('admin.matching.modeMatched')
    : mode === 'TRENDING' ? t('admin.matching.modeTrending') : mode
  const widthPct = Math.round(joinRate * 100)
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex justify-between text-sm">
        <span className="font-semibold text-base">{label}</span>
        <span className="text-secondary tabular-nums">
          {t('admin.matching.shownJoined', { shown, joined })} · <span className="text-base font-semibold">{widthPct}%</span>
        </span>
      </div>
      <div className="h-2.5 rounded-full bg-surface-muted overflow-hidden">
        <div className="h-full bg-brand-gradient-strong rounded-full" style={{ width: `${widthPct}%` }} />
      </div>
    </div>
  )
}

function RatingStat({ label, value, tone }: { label: string; value: number; tone: 'good' | 'bad' }) {
  return (
    <div className={`rounded-2xl border border-line px-4 py-3 ${tone === 'good' ? 'bg-bubble-green-soft' : 'bg-danger-soft'}`}>
      <p className="text-xs text-muted">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${tone === 'good' ? 'text-bubble-green' : 'text-danger'}`}>
        {value.toLocaleString()}
      </p>
    </div>
  )
}

function roleLabel(t: (k: string) => string, role: string): string {
  return role === 'NONE' ? '—' : t(`admin.matching.role.${role}`)
}
