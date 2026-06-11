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
 *   - PageShell    → the full-bleed console shell (Admin's look): a bordered
 *                    header band wrapping PageHeader, an optional tab band, and a
 *                    single scrolling body. The one outer structure shared by the
 *                    stacked pages so they read as one product.
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
            <span className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm animate-dot-bob" />
            <span className="w-1.5 h-1.5 rounded-full bg-bubble-green animate-dot-bob-late" />
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

interface PageShellProps {
  title: ReactNode
  subtitle?: ReactNode
  /** Small inline element rendered next to the title (e.g. Academy's uni badge). */
  titleAfter?: ReactNode
  /** Right-aligned header controls (term select, an action button…). */
  actions?: ReactNode
  /** Optional tab band, rendered with its own border-b below the header. */
  tabs?: ReactNode
  /** Scrollable body content. */
  children: ReactNode
  /** Override the default body padding (`p-4 tablet:p-6`). */
  bodyClassName?: string
}

/**
 * The full-bleed page shell: a bordered header band (PageHeader inside), an
 * optional tab band, and a single scrolling body. Body is edge-to-edge by
 * default — wrap children in <PageWidth> if a page reads better constrained.
 */
export function PageShell({
  title,
  subtitle,
  titleAfter,
  actions,
  tabs,
  children,
  bodyClassName = 'p-4 tablet:p-6',
}: PageShellProps) {
  return (
    <div className="flex flex-col h-full overflow-hidden bg-base">
      <header className="relative px-4 tablet:px-6 py-4 border-b border-line shrink-0 bg-surface">
        <PageHeader title={title} subtitle={subtitle} titleAfter={titleAfter} actions={actions} />
        {/* The signature iridescent hairline, laid over the plain border. */}
        <div className="divider-iridescent absolute inset-x-0 -bottom-px opacity-70" aria-hidden="true" />
      </header>
      {tabs && (
        <nav className="px-4 tablet:px-6 py-3 border-b border-line flex items-center gap-2 overflow-x-auto no-scrollbar shrink-0">
          {tabs}
        </nav>
      )}
      <main className={`flex-1 min-h-0 overflow-y-auto ${bodyClassName}`}>{children}</main>
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
