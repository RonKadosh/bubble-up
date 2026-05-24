/**
 * The Bubble.up surface card. `bg-surface` + `border border-line` + a soft
 * themed shadow. Three roundness sizes — pick by how much real estate the
 * card holds.
 *
 *   - sm  → small chips, list rows               (rounded-xl)
 *   - md  → standard cards                       (rounded-2xl)
 *   - lg  → hero panels, modals, page wrappers   (rounded-3xl)
 *
 * Pass `interactive` to opt-in to the bubble-pop hover lift (use when the
 * card is itself a link/button).
 */
import type { HTMLAttributes } from 'react'

export type CardSize = 'sm' | 'md' | 'lg'

const SIZE_CLASSES: Record<CardSize, string> = {
  sm: 'rounded-xl',
  md: 'rounded-2xl',
  lg: 'rounded-3xl',
}

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  size?: CardSize
  interactive?: boolean
}

export function Card({
  size = 'md',
  interactive = false,
  className = '',
  children,
  ...rest
}: CardProps) {
  return (
    <div
      {...rest}
      className={`bg-surface border border-line shadow-themed ${SIZE_CLASSES[size]} ${
        interactive ? 'bubble-pop hover:border-line-strong hover:bg-surface-hover transition' : ''
      } ${className}`}
    >
      {children}
    </div>
  )
}
