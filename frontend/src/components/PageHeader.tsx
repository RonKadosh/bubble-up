/**
 * Shared page-layout primitives. One header voice + one content gutter across
 * the stacked pages (Dashboard / Academy / Experts) so they read as one product.
 *
 *   - PageWidth    → the centered content column. Owns the single source of
 *                    truth for page max-width + horizontal padding, so every
 *                    page's header and body start at the exact same left edge.
 *                    Wrap both the header block and the scrollable body in it.
 *   - PageHeader   → the bubble-dots + title cluster, optional subtitle, an
 *                    inline `titleAfter` slot (e.g. a badge next to the title),
 *                    and a right-aligned `actions` slot (term select, a link…).
 *                    Renders the inner flex row only — each page keeps its own
 *                    outer shell (vertical padding / scroll).
 *   - SectionLabel → the uppercase-muted eyebrow used above stacked sections.
 *
 * Related primitives in one file, following the Button.tsx precedent.
 */
import type { ReactNode } from 'react'

export function PageWidth({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <div className={`mx-auto w-full max-w-5xl px-4 tablet:px-6 desktop:px-8 ${className}`}>
      {children}
    </div>
  )
}

interface PageHeaderProps {
  title: ReactNode
  subtitle?: ReactNode
  /** Small inline element rendered next to the title (e.g. Academy's uni badge). */
  titleAfter?: ReactNode
  /** Right-aligned controls (term select, "Become an expert" link…). */
  actions?: ReactNode
}

export function PageHeader({ title, subtitle, titleAfter, actions }: PageHeaderProps) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <div className="flex items-center gap-2.5 mb-1">
          <span className="flex items-center gap-1" aria-hidden="true">
            <span className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
            <span className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
          </span>
          <h1 className="text-2xl tablet:text-3xl font-bold tracking-tight text-base">{title}</h1>
          {titleAfter}
        </div>
        {subtitle && <p className="text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex items-center gap-2 shrink-0">{actions}</div>}
    </div>
  )
}

export function SectionLabel({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return (
    <h2 className={`text-sm font-semibold uppercase tracking-wide text-muted ${className}`}>
      {children}
    </h2>
  )
}
