/**
 * The Bubble.up button system. Everything is a pill (`rounded-full`) and
 * everything springs on hover (`bubble-pop`). Variants set color & weight,
 * sizes set padding & text size.
 *
 * Pick a variant by intent, not appearance:
 *   - deep: the commit action — join/enroll/save/send/create (navy gradient,
 *     white text). At most one per surface; see design-doc §6.
 *   - primary: non-commit main CTAs — open, browse, start-here (silver gradient).
 *   - secondary: alternative actions (surface + border).
 *   - ghost: low-weight inline actions (no fill).
 *   - danger: destructive (pop, remove).
 */
import type { ButtonHTMLAttributes, AnchorHTMLAttributes, ReactNode } from 'react'

export type ButtonVariant = 'primary' | 'deep' | 'secondary' | 'ghost' | 'danger' | 'cell'
export type ButtonSize = 'xs' | 'sm' | 'md' | 'lg'

const SIZE_CLASSES: Record<ButtonSize, string> = {
  xs: 'px-3 py-1.5 text-xs',
  sm: 'px-4 py-2 text-sm',
  md: 'px-5 py-2.5 text-sm font-semibold',
  lg: 'px-6 py-3 text-base font-semibold',
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary:   'bg-brand-gradient-strong text-on-brand shadow-themed',
  /** The navy punch: the commit action of a surface (deep gradient, white text). */
  deep:      'bg-brand-gradient-deep text-white shadow-themed',
  secondary: 'bg-surface text-base border border-line hover:border-line-strong',
  ghost:     'text-secondary hover:bg-surface-hover',
  danger:    'bg-danger-soft text-danger border border-line hover:brightness-95',
  /** Inside dark bento cells — matches the 🔗 link-button look: muted fill + subtle border. */
  cell:      'bg-surface-muted text-base border border-line hover:bg-surface-hover hover:border-line-strong',
}

const BASE =
  'inline-flex items-center gap-2 bubble-pop disabled:opacity-60 disabled:cursor-not-allowed disabled:pointer-events-none'

// Default: a single-line pill (label buttons). `wrap`: text wraps and left-aligns
// inside a rounded-rect — for long, multi-line option labels (quiz answers) where a
// nowrap pill would overflow its curved ends. Set via the `wrap` prop.
const SHAPE_NOWRAP = 'justify-center whitespace-nowrap rounded-full'
const SHAPE_WRAP = 'justify-start text-start whitespace-normal rounded-2xl'

interface SharedProps {
  variant?: ButtonVariant
  size?: ButtonSize
  /** Let the label wrap to multiple lines (left-aligned, rounded-rect) instead of a nowrap pill. */
  wrap?: boolean
  leftIcon?: ReactNode
  rightIcon?: ReactNode
  className?: string
}

type ButtonProps = SharedProps & ButtonHTMLAttributes<HTMLButtonElement>

export function Button({
  variant = 'primary',
  size = 'md',
  wrap = false,
  leftIcon,
  rightIcon,
  className = '',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      {...rest}
      className={`${BASE} ${wrap ? SHAPE_WRAP : SHAPE_NOWRAP} ${SIZE_CLASSES[size]} ${VARIANT_CLASSES[variant]} ${className}`}
    >
      {leftIcon}
      {children}
      {rightIcon}
    </button>
  )
}

/** Same look as Button, renders as <a>. Use react-router's Link if you need client-side nav. */
type LinkButtonProps = SharedProps & AnchorHTMLAttributes<HTMLAnchorElement>

export function LinkButton({
  variant = 'primary',
  size = 'md',
  wrap = false,
  leftIcon,
  rightIcon,
  className = '',
  children,
  ...rest
}: LinkButtonProps) {
  return (
    <a
      {...rest}
      className={`${BASE} ${wrap ? SHAPE_WRAP : SHAPE_NOWRAP} ${SIZE_CLASSES[size]} ${VARIANT_CLASSES[variant]} ${className}`}
    >
      {leftIcon}
      {children}
      {rightIcon}
    </a>
  )
}

/**
 * Round icon-only button. The chat send button, sidebar collapse toggle,
 * month nav arrows in calendar — all the same shape.
 */
export type IconButtonSize = 'sm' | 'md' | 'lg'

const ICON_SIZE_CLASSES: Record<IconButtonSize, string> = {
  sm: 'w-9 h-9',
  md: 'w-10 h-10',
  lg: 'w-12 h-12',
}

interface IconButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: IconButtonSize
}

export function IconButton({
  variant = 'ghost',
  size = 'md',
  className = '',
  children,
  ...rest
}: IconButtonProps) {
  return (
    <button
      {...rest}
      className={`inline-flex items-center justify-center rounded-full bubble-pop disabled:opacity-60 disabled:cursor-not-allowed ${ICON_SIZE_CLASSES[size]} ${VARIANT_CLASSES[variant]} ${className}`}
    >
      {children}
    </button>
  )
}
