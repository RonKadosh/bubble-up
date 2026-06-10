/**
 * The shared auth-screen scene: brand wash, logo header, a field of drifting
 * soap bubbles, and the iridescent-ring card that hosts the form. Used by
 * LoginPage (Google OAuth) and TestingLoginPage (hidden dev credentials form)
 * so the two stay one design.
 *
 * Motion language: every bubble drifts on its own slow phase (`bubble-drift`
 * with per-bubble duration/delay), the card rises in once on mount, and the
 * card surface itself is a `bubble-surface` — the same soapy treatment as the
 * admin Bubbles KPI.
 */
import type { CSSProperties, ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { BubbleLogo } from './Icons'

function driftStyle(duration: number, delay: number, distance: number): CSSProperties {
  return {
    '--drift-duration': `${duration}s`,
    '--drift-delay': `${delay}s`,
    '--drift-distance': `${distance}px`,
  } as CSSProperties
}

export function AuthScene({ children }: { children: ReactNode }) {
  const { t } = useTranslation()

  return (
    <div className="min-h-screen relative overflow-hidden bg-base">
      <div className="absolute inset-0 bg-brand-gradient-soft opacity-40 dark:opacity-20 pointer-events-none" />

      <div className="relative min-h-screen mx-auto max-w-[1200px]">
        <header className="absolute top-4 start-4 tablet:top-6 tablet:start-6 desktop:top-8 desktop:start-8 z-30 flex items-center gap-2.5">
          <div className="ring-iridescent p-[1.5px] rounded-full shadow-themed">
            <div className="w-11 h-11 rounded-full bg-brand-gradient text-on-brand flex items-center justify-center">
              <BubbleLogo className="w-5 h-5" />
            </div>
          </div>
          <span className="font-heading text-xl font-bold text-base">{t('brand.name')}</span>
        </header>

        {/* Desktop bubble field — photos up top, empties trailing down. */}
        <div className="hidden desktop:block absolute inset-0 pointer-events-none">
          <PhotoBubble src="/images/photo-books.jpg" className="top-[15%] start-[38%] w-[18%]" style={driftStyle(9, -3, -10)} />
          <PhotoBubble src="/images/photo-group.jpg" className="top-[10%] start-[20%] w-[24%]" style={driftStyle(11, -1, -8)} />
          <PhotoBubble src="/images/photo-tablet.jpg" className="top-[40%] start-[14%] w-[18%]" style={driftStyle(10, -6, -12)} />

          <EmptyBubble hue="magenta" className="top-[44%] start-[36%] w-[10%]" style={driftStyle(8, -2, -14)} />
          <EmptyBubble hue="blue" className="top-[41%] start-[50%] w-[15%]" style={driftStyle(12, -5, -10)} />

          <EmptyBubble hue="green" className="top-[60%] start-[44%] w-[8%]" style={driftStyle(7, -4, -12)} />
          <EmptyBubble hue="yellow" className="top-[62%] start-[66%] w-[7%]" style={driftStyle(9, -1, -16)} />
          <EmptyBubble hue="blue" className="top-[64%] start-[35%] w-[7%]" style={driftStyle(8, -6, -10)} />

          <EmptyBubble hue="magenta" className="top-[78%] start-[48%] w-[5%]" style={driftStyle(6, -3, -14)} />
          <EmptyBubble hue="green" className="top-[76%] start-[60%] w-[5%]" style={driftStyle(7, -5, -12)} />

          <EmptyBubble hue="blue" className="top-[90%] start-[80%] w-[3%]" style={driftStyle(5, -2, -16)} />
          <EmptyBubble hue="magenta" className="top-[86%] start-[68%] w-[4%]" style={driftStyle(6, -4, -12)} />
        </div>

        {/* Phone / tablet: a few small bubbles at the edges so small screens
            aren't bare — kept clear of the centered card. */}
        <div className="desktop:hidden absolute inset-0 pointer-events-none">
          <EmptyBubble hue="magenta" className="top-[3%] end-[6%] w-14" style={driftStyle(8, -2, -10)} />
          <EmptyBubble hue="green" className="top-[9%] end-[20%] w-8" style={driftStyle(6, -4, -12)} />
          <EmptyBubble hue="yellow" className="top-[58%] start-[4%] w-10" style={driftStyle(9, -1, -14)} />
          <EmptyBubble hue="blue" className="bottom-[8%] end-[10%] w-20" style={driftStyle(10, -5, -10)} />
          <EmptyBubble hue="magenta" className="bottom-[16%] start-[12%] w-7" style={driftStyle(7, -3, -16)} />
        </div>

        <main className="relative z-20 min-h-screen flex items-start justify-center desktop:justify-end px-4 tablet:px-6 desktop:pe-4 pt-[14vh] tablet:pt-[12vh] desktop:pt-[14vh] pb-12 tablet:pb-24">
          <div className="w-full max-w-md ring-iridescent p-[2px] rounded-[2.5rem] shadow-bubble animate-rise-in">
            {/* Inner radius backs off the 2px ring so the corners stay concentric. */}
            <div className="relative overflow-hidden bubble-surface rounded-[calc(2.5rem-2px)] p-6 tablet:p-8 desktop:p-10">
              {children}
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}

function PhotoBubble({ src, className = '', style }: { src: string; className?: string; style?: CSSProperties }) {
  return (
    <div className={`absolute aspect-square rounded-full ring-iridescent p-[2px] shadow-themed bubble-drift ${className}`} style={style}>
      <div className="w-full h-full rounded-full overflow-hidden bg-surface">
        <img src={src} alt="" className="w-full h-full object-cover" />
      </div>
    </div>
  )
}

type BubbleHue = 'blue' | 'magenta' | 'green' | 'yellow'

// Literal class names (not `bubble-hue-${hue}`) so Tailwind's content scan
// finds them and doesn't purge the hand-written utilities.
const HUE_CLASS: Record<BubbleHue, string> = {
  blue: 'bubble-hue-blue',
  magenta: 'bubble-hue-magenta',
  green: 'bubble-hue-green',
  yellow: 'bubble-hue-yellow',
}

function EmptyBubble({ hue = 'blue', className = '', style }: { hue?: BubbleHue; className?: string; style?: CSSProperties }) {
  return (
    <div className={`absolute aspect-square rounded-full ring-iridescent p-[2px] shadow-themed bubble-drift ${className}`} style={style}>
      <div className={`w-full h-full rounded-full bubble-fill ${HUE_CLASS[hue]} backdrop-blur-sm relative overflow-hidden`}>
        <div className="absolute top-[6%] start-[8%] w-[28%] h-[20%] rounded-full bg-white/60 dark:bg-white/25 blur-md" />
      </div>
    </div>
  )
}
