import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getDemoStats, DemoStats } from '../api/demo'
import { Card } from '../components/Card'
import { BubbleLogo } from '../components/Icons'

/**
 * Operator-only demo usage view (demo build, /demo/stats). The no-login demo has
 * no admin session, so access is by shared secret in the URL: /demo/stats?key=...
 * The key is forwarded to GET /api/demo/stats, which validates it server-side. No
 * key / wrong key → the endpoint 401s and we show a generic "unavailable" message.
 * Intentionally unlocalized — it's a developer-facing surface, not a user page.
 */
export default function DemoStatsPage() {
  const [params] = useSearchParams()
  const key = params.get('key') ?? ''
  const [stats, setStats] = useState<DemoStats | null>(null)
  const [error, setError] = useState(false)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let alive = true
    setLoading(true)
    setError(false)
    getDemoStats(key)
      .then((s) => { if (alive) setStats(s) })
      .catch(() => { if (alive) setError(true) })
      .finally(() => { if (alive) setLoading(false) })
    return () => { alive = false }
  }, [key])

  return (
    <div className="relative min-h-screen bg-base text-base">
      <div className="pointer-events-none absolute inset-0 z-0 bg-brand-gradient-soft opacity-20" />
      <div className="relative z-10 mx-auto flex min-h-screen max-w-2xl flex-col items-center justify-center gap-6 px-4 py-10">
        <div className="flex items-center gap-2.5">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-gradient text-on-brand">
            <BubbleLogo className="h-5 w-5" />
          </div>
          <span className="font-heading text-xl font-bold">Demo usage</span>
        </div>

        {loading && <p className="text-secondary">Loading…</p>}

        {!loading && error && (
          <Card size="lg" className="w-full p-8 text-center">
            <p className="text-danger">Stats unavailable — missing or invalid key.</p>
            <p className="mt-2 text-sm text-muted">Append <code>?key=&lt;DEMO_STATS_KEY&gt;</code> to the URL.</p>
          </Card>
        )}

        {!loading && stats && (
          <div className="grid w-full grid-cols-1 gap-4 tablet:grid-cols-3">
            <StatCell label="Total starts" value={stats.totalStarts} />
            <StatCell label="Active worlds" value={stats.activeSessions} />
            <StatCell label="Last 7 days" value={stats.last7Days} />
          </div>
        )}
      </div>
    </div>
  )
}

function StatCell({ label, value }: { label: string; value: number }) {
  return (
    <Card size="lg" className="flex flex-col items-center gap-2 p-8">
      <span className="font-heading text-4xl font-extrabold text-primary-600">{value}</span>
      <span className="text-sm text-secondary">{label}</span>
    </Card>
  )
}
