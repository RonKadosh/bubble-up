/**
 * Decorative drifting soap-bubble field — photo bubbles (students inside) and
 * empty iridescent bubbles that slowly float, each on its own phase. Purely
 * decorative: the whole field is `pointer-events-none` / `aria-hidden`, and all
 * drift motion stops under `prefers-reduced-motion` (see index.css).
 *
 * Two consumers share these primitives:
 *   - LoginPage renders <BubbleField /> as a full-bleed background behind the
 *     landing hero.
 *   - AuthScene composes the primitives into its own card-centred arrangement.
 */
import type { CSSProperties } from 'react'

export function driftStyle(duration: number, delay: number, distance: number): CSSProperties {
  return {
    '--drift-duration': `${duration}s`,
    '--drift-delay': `${delay}s`,
    '--drift-distance': `${distance}px`,
  } as CSSProperties
}

export function PhotoBubble({ src, className = '', style }: { src: string; className?: string; style?: CSSProperties }) {
  return (
    <div className={`absolute aspect-square rounded-full ring-iridescent p-[2px] shadow-themed bubble-drift ${className}`} style={style}>
      <div className="h-full w-full overflow-hidden rounded-full bg-surface">
        <img src={src} alt="" className="h-full w-full object-cover" />
      </div>
    </div>
  )
}

export type BubbleHue = 'blue' | 'magenta' | 'green' | 'yellow'

// Literal class names (not `bubble-hue-${hue}`) so Tailwind's content scan finds
// them and doesn't purge the hand-written utilities.
const HUE_CLASS: Record<BubbleHue, string> = {
  blue: 'bubble-hue-blue',
  magenta: 'bubble-hue-magenta',
  green: 'bubble-hue-green',
  yellow: 'bubble-hue-yellow',
}

export function EmptyBubble({ hue = 'blue', className = '', style }: { hue?: BubbleHue; className?: string; style?: CSSProperties }) {
  return (
    <div className={`absolute aspect-square rounded-full ring-iridescent p-[2px] shadow-themed bubble-drift ${className}`} style={style}>
      <div className={`bubble-fill relative h-full w-full overflow-hidden rounded-full backdrop-blur-sm ${HUE_CLASS[hue]}`}>
        <div className="absolute start-[8%] top-[6%] h-[20%] w-[28%] rounded-full bg-white/60 blur-md dark:bg-white/25" />
      </div>
    </div>
  )
}

/**
 * Full-bleed decorative field tuned for the landing page. The hero content is
 * vertically centred and fills two columns — the left text column
 * (headline + subtitle + "demo" button, ~x:8–46% / y:30–71%) and the right
 * sign-in card (~x:54–84% / y:34–66%). The photo bubbles sit in a non-overlapping
 * row along the open lower band; the empties accent the top band, the narrow
 * centre gap, and the side margins. Nothing is placed over either content column.
 * On phones the stacked layout leaves no room, so a sparse set of small edge
 * bubbles shows instead.
 */
export function BubbleField() {
  return (
    <div aria-hidden className="pointer-events-none absolute inset-0 z-0 overflow-hidden">
      {/* Desktop: three photos in a staggered row across the lower band (clear of
          both content columns and of each other), empties as accents in the top
          band / centre gap / margins. */}
      <div className="absolute inset-0 hidden desktop:block">
        <PhotoBubble src="/images/photo-tablet.jpg" className="start-[3%]  top-[68%] w-[13%]" style={driftStyle(10, -6, -12)} />
        <PhotoBubble src="/images/photo-group.jpg"  className="start-[40%] top-[71%] w-[13%]" style={driftStyle(11, -1, -8)} />
        <PhotoBubble src="/images/photo-books.jpg"  className="start-[73%] top-[67%] w-[12%]" style={driftStyle(9, -3, -10)} />

        <EmptyBubble hue="yellow"  className="start-[6%]  top-[13%] w-[4%]" style={driftStyle(9, -1, -16)} />
        <EmptyBubble hue="blue"    className="start-[49%] top-[15%] w-[5%]" style={driftStyle(12, -5, -10)} />
        <EmptyBubble hue="magenta" className="end-[8%]    top-[17%] w-[4%]" style={driftStyle(6, -3, -14)} />
        <EmptyBubble hue="green"   className="start-[49%] top-[45%] w-[4%]" style={driftStyle(7, -4, -12)} />
        <EmptyBubble hue="blue"    className="start-[2%]  top-[42%] w-[3%]" style={driftStyle(8, -6, -10)} />
        <EmptyBubble hue="magenta" className="start-[51%] top-[60%] w-[3%]" style={driftStyle(9, -2, -12)} />
      </div>

      {/* Phone / tablet: a few small edge bubbles, clear of the centred content. */}
      <div className="absolute inset-0 desktop:hidden">
        <EmptyBubble hue="magenta" className="end-[6%] top-[4%] w-12"    style={driftStyle(8, -2, -10)} />
        <EmptyBubble hue="blue"    className="start-[5%] top-[42%] w-10" style={driftStyle(10, -5, -10)} />
        <EmptyBubble hue="green"   className="bottom-[18%] end-[7%] w-14" style={driftStyle(9, -1, -14)} />
        <EmptyBubble hue="yellow"  className="bottom-[6%] start-[10%] w-8" style={driftStyle(7, -3, -16)} />
      </div>
    </div>
  )
}
