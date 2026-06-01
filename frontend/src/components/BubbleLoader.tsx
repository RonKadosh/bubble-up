interface BubbleLoaderProps {
  /** Diameter in px. Default 64. */
  size?: number
  className?: string
}

/**
 * An iridescent soap-bubble that inflates, fills with the brand gradient, and
 * pops on a loop — the same visual language as the login-screen bubbles. Used
 * as a playful "working…" indicator (e.g. while a Live Bubble spins up).
 *
 * The fill/pop motion lives in the `bubble-loader` / `bubble-loader-fill`
 * keyframes in index.css.
 */
export function BubbleLoader({ size = 64, className = '' }: BubbleLoaderProps) {
  return (
    <div
      className={`bubble-loader ring-iridescent p-[2px] rounded-full shadow-themed ${className}`}
      style={{ width: size, height: size }}
      aria-hidden="true"
    >
      <div className="relative w-full h-full rounded-full overflow-hidden bg-gradient-to-br from-white/85 via-primary-50/70 to-primary-200/40 backdrop-blur-sm">
        {/* Liquid rising from the bottom — the "fill". */}
        <div className="bubble-loader-fill absolute inset-x-0 bottom-0 bg-brand-gradient-strong opacity-70" />
        {/* Specular highlight, matching the login EmptyBubble. */}
        <div className="absolute top-[10%] start-[14%] w-[30%] h-[22%] rounded-full bg-white/70 blur-md" />
      </div>
    </div>
  )
}
