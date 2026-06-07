/**
 * The pill tab bar — Bubble.up's rounded, brand-gradient tab navigation.
 * Returns the button list (a fragment), meant to drop into `PageShell`'s
 * `tabs` slot, which supplies the bordered `<nav>` wrapper + horizontal scroll.
 *
 * Drive it from route state (Admin: navigate on change) or local state
 * (Settings: setTab on change) — it only cares about `active` + `onChange`.
 */
import type { ReactNode } from 'react'

export interface TabItem {
  key: string
  label: ReactNode
  /** Optional pending-count pill rendered after the label when > 0. */
  badge?: number
}

interface TabsProps {
  items: TabItem[]
  active: string
  onChange: (key: string) => void
}

export function Tabs({ items, active, onChange }: TabsProps) {
  return (
    <>
      {items.map((item) => {
        const selected = item.key === active
        return (
          <button
            key={item.key}
            type="button"
            onClick={() => onChange(item.key)}
            aria-pressed={selected}
            // No transform on hover (no bubble-pop): the tabs live in an
            // overflow-x scroll row, and a hover scale/translate nudges the
            // layout and pushes the page. Emphasis is color + border + a soft
            // shadow instead, so hovering never reflows anything.
            className={`shrink-0 px-4 py-2 text-sm rounded-full border transition ${
              selected
                ? 'bg-brand-gradient-strong border-transparent text-on-brand shadow-themed font-semibold'
                : 'bg-surface border-line text-secondary hover:text-base hover:border-line-strong hover:shadow-sm'
            }`}
          >
            <span className="inline-flex items-center gap-1.5">
              {item.label}
              {item.badge != null && item.badge > 0 && (
                <span
                  className={`inline-flex items-center justify-center min-w-[1.25rem] h-5 px-1.5 text-xs font-semibold rounded-full ${
                    selected ? 'bg-on-brand/20 text-on-brand' : 'bg-bubble-magenta text-white'
                  }`}
                >
                  {item.badge > 99 ? '99+' : item.badge}
                </span>
              )}
            </span>
          </button>
        )
      })}
    </>
  )
}
