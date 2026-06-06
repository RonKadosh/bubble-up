import type { ReactNode } from 'react'
import { Card } from './Card'

/**
 * The bento-grid cell that wraps every panel inside the GroupsPage hub.
 *
 * Surface matches Academy's `Pane` (clean `Card` — `rounded-3xl border border-line
 * shadow-themed` + a `border-b border-line` header) so the hub reads as the same
 * product. The animated iridescent ring is reserved for the **focused** (maximized)
 * cell only — an "active/hero" accent, consistent with how Academy's selected
 * RowButton and the GroupSidebar selected item use it.
 *
 * Note: the focused cell's inner panel uses `bg-surface` (fully opaque), NOT a
 * glass/backdrop-blur fill. `backdrop-filter` would sample the parent's rotating
 * conic gradient and leak a rotating wash into the cell interior.
 *
 * Use className to pass grid-placement helpers (row-span-2, col-span-2, max-h-*).
 *
 * When `onFocus` is provided and `isFocused` is false, the header shows a
 * promote button that the user clicks to make this cell the focused (maximized)
 * one in the bento layout.
 */
interface BentoCellProps {
  /** Optional leading glyph. Omit for a clean text-only header. */
  icon?: string
  label: string
  children: ReactNode
  className?: string
  isFocused?: boolean
  onFocus?: () => void
  promoteLabel?: string
}

export function BentoCell({
  icon,
  label,
  children,
  className = '',
  isFocused = false,
  onFocus,
  promoteLabel,
}: BentoCellProps) {
  const showPromote = !isFocused && !!onFocus
  const body = (
    <>
      <div className="px-4 py-3 border-b border-line flex items-center gap-2 shrink-0">
        {icon && <span className="text-base leading-none">{icon}</span>}
        <span className="font-semibold text-sm text-secondary">{label}</span>
        {showPromote && (
          <button
            type="button"
            onClick={onFocus}
            aria-label={promoteLabel}
            title={promoteLabel}
            className="ml-auto text-muted hover:text-base text-sm leading-none px-1.5 py-0.5 rounded transition-colors"
          >
            ⤢
          </button>
        )}
      </div>
      <div className="flex-1 min-h-0 overflow-hidden flex flex-col">
        {children}
      </div>
    </>
  )

  // Focused cell: iridescent "active" ring. Symmetric rounded-3xl (1.75rem) corners
  // matching Academy's Card lg; inner radius backs off the 1.5px ring padding.
  if (isFocused) {
    return (
      <div className={`ring-iridescent p-[1.5px] rounded-3xl flex flex-col min-h-0 overflow-hidden shadow-themed ${className}`}>
        <div className="flex-1 min-h-0 bg-surface rounded-[calc(1.75rem-1.5px)] flex flex-col overflow-hidden">
          {body}
        </div>
      </div>
    )
  }

  // Default cell: the same clean surface as Academy's Pane.
  return (
    <Card size="lg" className={`flex flex-col min-h-0 overflow-hidden ${className}`}>
      {body}
    </Card>
  )
}
