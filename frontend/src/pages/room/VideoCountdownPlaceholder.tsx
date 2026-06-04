import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { fmtRelative } from './timeFormat'
import { formatClock } from '../../i18n/datetime'

interface Props {
  /** ISO timestamp when the Video cell becomes active. */
  videoOpensAt: string
  /** Session title — shown as the hero label so users know they're in the right room. */
  sessionTitle?: string
}

/**
 * Placeholder rendered in the Video bento cell of an expert session room
 * before {@code videoOpensAt} (i.e. before the session's {@code startsAt}).
 * Chat and whiteboard are live during this window; video opens at start.
 *
 * <p>Visual:
 * <ul>
 *   <li>Soft indigo→slate radial gradient instead of pure black so the cell
 *       reads as "waiting" rather than "broken".</li>
 *   <li>Animated pulsing ring around the camera icon as a passive
 *       "we're listening" signal.</li>
 *   <li>Session title + countdown stacked, larger than the previous tiny
 *       label so it reads at a glance.</li>
 * </ul>
 *
 * <p>The parent page (ExpertRoomPage) re-fetches the room shortly after
 * {@code videoOpensAt} passes and swaps this for the persistent-video anchor.
 */
export function VideoCountdownPlaceholder({ videoOpensAt, sessionTitle }: Props) {
  const { t } = useTranslation()
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30000)
    return () => clearInterval(id)
  }, [])

  // Absolute clock time for the tooltip / sub-label. Cheap to recompute each
  // render; reads the active language so it follows the live language toggle.
  const absoluteTime = formatClock(videoOpensAt)

  return (
    <div
      className="relative w-full h-full overflow-hidden flex items-center justify-center"
      style={{
        background:
          'radial-gradient(ellipse at center, rgba(59,130,246,0.18) 0%, rgba(15,23,42,0.95) 60%, #0b1220 100%)',
      }}
    >
      {/* Soft animated halo behind the icon */}
      <div
        aria-hidden="true"
        className="absolute h-48 w-48 rounded-full bg-primary-500/20 blur-3xl animate-pulse"
      />
      <div className="relative z-10 flex flex-col items-center gap-3 text-center px-6 text-white">
        <div className="relative flex h-16 w-16 items-center justify-center">
          <span className="absolute inset-0 rounded-full bg-primary-400/30 animate-ping" />
          <span className="relative flex h-12 w-12 items-center justify-center rounded-full bg-white/10 text-3xl">
            🎥
          </span>
        </div>
        {sessionTitle && (
          <p className="text-sm font-semibold text-white/90 max-w-xs truncate">{sessionTitle}</p>
        )}
        <p className="text-base font-medium">{t('expertRoom.videoCountdown.headline')}</p>
        <p className="text-sm text-white/70">
          {t('expertRoom.videoCountdown.opens', { when: fmtRelative(t, now, videoOpensAt) })}
        </p>
        <p className="text-xs text-white/50" title={videoOpensAt}>
          {t('expertRoom.videoCountdown.absoluteHint', { time: absoluteTime })}
        </p>
      </div>
    </div>
  )
}
