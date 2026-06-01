import type { ReactNode } from 'react'

/**
 * The bento-grid cell that wraps every panel inside the GroupsPage hub.
 * Renders an animated iridescent-ring border + solid surface fill.
 *
 * Important: the inner panel uses `bg-surface` (fully opaque), NOT
 * `bg-surface-glass` + `backdrop-blur`. `backdrop-filter` samples whatever is
 * painted behind the element — which includes the parent's rotating conic
 * gradient — and would leak a rotating wash into the cell interior.
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
  return (
    <div className={`ring-iridescent p-[1.5px] bento-cell-radius flex flex-col min-h-0 overflow-hidden shadow-themed transition-shadow duration-300 hover:shadow-bubble ${className}`}>
      <div className="flex-1 min-h-0 bg-surface bento-cell-inner-radius flex flex-col overflow-hidden">
        <div className="px-4 py-2.5 border-b border-line flex items-center gap-2 shrink-0">
          {icon && <span className="text-base leading-none">{icon}</span>}
          <span className="font-semibold text-sm text-base">{label}</span>
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
      </div>
    </div>
  )
}
