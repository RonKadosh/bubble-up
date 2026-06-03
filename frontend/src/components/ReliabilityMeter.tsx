import { useTranslation } from 'react-i18next'
import type { Reliability } from '../api/matching'

/**
 * The user's private "profile strength" bar: fills to `confidence`, marks the
 * `threshold` where MATCHED picks unlock, and shows a badge once reached. Shared
 * by the onboarding widget and the Settings → Matching section. Self-only: this
 * never displays anyone else's confidence.
 */
export function ReliabilityMeter({
  reliability,
  className = '',
}: {
  reliability: Reliability
  className?: string
}) {
  const { t } = useTranslation()
  const pct = clampPct(reliability.confidence)
  const thresholdPct = clampPct(reliability.threshold)

  return (
    <div className={className}>
      <div className="flex items-center justify-between mb-1.5">
        <span className="text-xs font-semibold uppercase tracking-wide text-secondary">
          {t('matching.reliability.label')}
        </span>
        <span className="text-xs font-semibold text-base tabular-nums">{pct}%</span>
      </div>

      {/* Track + fill + threshold marker. The marker sits at the unlock point so
          progress reads as "distance to Matched", not just an abstract number. */}
      <div className="relative h-2.5 rounded-full bg-surface-muted overflow-hidden">
        <div
          className="absolute inset-y-0 start-0 rounded-full bg-brand-gradient-strong transition-[width] duration-500"
          style={{ width: `${pct}%` }}
        />
        <div
          className="absolute inset-y-0 w-0.5 bg-base/40"
          style={{ insetInlineStart: `${thresholdPct}%` }}
          aria-hidden
          title={t('matching.reliability.thresholdMarker')}
        />
      </div>

      <div className="mt-2 flex items-center justify-between gap-2">
        <p className="text-xs text-muted">
          {reliability.matched
            ? t('matching.reliability.hintMatched')
            : t('matching.reliability.hint')}
        </p>
        {reliability.matched && (
          <span className="shrink-0 rounded-full bg-surface-muted px-2 py-0.5 text-xs font-medium text-primary-600">
            {t('matching.reliability.matchedBadge')}
          </span>
        )}
      </div>
    </div>
  )
}

function clampPct(v: number): number {
  return Math.round(Math.min(1, Math.max(0, v)) * 100)
}
