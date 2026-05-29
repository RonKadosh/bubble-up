import { create } from 'zustand'

/**
 * Viewport tier — kept in lockstep with the Tailwind breakpoints defined in
 * `tailwind.config.js` and documented in §10 of `design-doc.md`. JS-side
 * consumers (e.g. components that conditionally mount one subtree vs. another)
 * read this; pure styling decisions stay in Tailwind utilities.
 */
export type ViewportTier = 'phone' | 'tablet' | 'desktop'

interface ViewportState {
  tier: ViewportTier
}

const TABLET_PX = 600
const DESKTOP_PX = 1200

function computeTier(): ViewportTier {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return 'desktop'
  if (window.matchMedia(`(min-width: ${DESKTOP_PX}px)`).matches) return 'desktop'
  if (window.matchMedia(`(min-width: ${TABLET_PX}px)`).matches) return 'tablet'
  return 'phone'
}

export const useViewportStore = create<ViewportState>(() => ({
  tier: computeTier(),
}))

// Attach the listeners once at module load. Mirrors how `applyThemeClass` /
// `languageStore` self-bootstrap — no component needs to know about media
// queries.
if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
  const tabletMq = window.matchMedia(`(min-width: ${TABLET_PX}px)`)
  const desktopMq = window.matchMedia(`(min-width: ${DESKTOP_PX}px)`)
  const update = () => useViewportStore.setState({ tier: computeTier() })
  tabletMq.addEventListener?.('change', update)
  desktopMq.addEventListener?.('change', update)
}
