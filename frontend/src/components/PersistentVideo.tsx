import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'
import { useActiveRoomStore } from '../store/activeRoomStore'
import { useRoomLayoutStore } from '../store/roomLayoutStore'
import { useViewportStore } from '../store/viewportStore'
import { BubbleRoom, getRoom } from '../api/room'
import { subscribeToRoomLifecycle } from '../api/ws'
import { VideoPanel } from '../pages/room/VideoPanel'

/**
 * Lives at the Layout level and owns the Jitsi iframe for the duration of the
 * call. Two visual modes, driven by the current route:
 *
 *   - On `/rooms/:roomId` → matches the bounding box of the `data-room-video-anchor`
 *     element that BubbleRoomPage renders inside its Video bento cell. Looks like
 *     the iframe is "inside" the cell.
 *   - Anywhere else → fixed picture-in-picture at the bottom-right of the viewport,
 *     clickable to navigate back to the room.
 *
 * The iframe DOM node never moves — only the wrapper's `position: fixed` rect
 * animates between the two layouts. That preserves Jitsi's WebRTC connection
 * across navigations.
 */
const PIP_WIDTH = 280
const PIP_HEIGHT = 158 // 16:9
const PIP_MARGIN = 16

interface Rect {
  top: number
  left: number
  width: number
  height: number
}

// The floating picture-in-picture rect (desktop/tablet only — phone never floats
// a PiP; see `hiddenOffscreen` below). Bottom-right of the viewport.
function pipRect(): Rect {
  return {
    top: window.innerHeight - PIP_HEIGHT - PIP_MARGIN,
    left: window.innerWidth - PIP_WIDTH - PIP_MARGIN,
    width: PIP_WIDTH,
    height: PIP_HEIGHT,
  }
}

export function PersistentVideo() {
  const { t } = useTranslation()
  const roomId = useActiveRoomStore((s) => s.roomId)
  const location = useLocation()
  const navigate = useNavigate()
  const isPhone = useViewportStore((s) => s.tier === 'phone')
  const [room, setRoom] = useState<BubbleRoom | null>(null)
  const [rect, setRect] = useState<Rect>(() => pipRect())
  // True only while actually docked to the room's video cell. Goes false when the
  // anchor isn't mounted (e.g. phone, a non-video tab is focused) so we render as
  // a floating PiP rather than freezing at the cell's last size.
  const [anchored, setAnchored] = useState(false)
  const containerRef = useRef<HTMLDivElement | null>(null)

  // Fetch the room once when activeRoom is set. Jitsi needs the JWT + roomName
  // from the response. Cleared when activeRoom drops.
  useEffect(() => {
    if (!roomId) {
      setRoom(null)
      return
    }
    let cancelled = false
    getRoom(roomId)
      .then((r) => { if (!cancelled) setRoom(r) })
      .catch((e) => console.warn('[PersistentVideo] getRoom failed', e))
    return () => { cancelled = true }
  }, [roomId])

  // Lifecycle subscription: when the backend's scheduler hard-closes the room,
  // dispose the iframe by clearing activeRoom (this component unmounts → Jitsi
  // dispose runs inside VideoPanel's cleanup). The user is left on whichever
  // page they were on with the floating pill gone.
  useEffect(() => {
    if (!roomId) return
    const off = subscribeToRoomLifecycle(roomId, (evt) => {
      if (evt.event === 'ENDED') {
        useActiveRoomStore.getState().clearActive()
      }
      // EXTENDED handled implicitly — the bento page will re-fetch on next nav.
    })
    return off
  }, [roomId])

  // The "room page" is either /rooms/{roomId} (GROUP scope) or
  // /sessions/{sessionId} (EXPERT_SESSION scope). Derive the expected path from
  // the fetched room so the iframe sticks to the bento anchor on either route.
  const roomPagePath = room
    ? (room.scope === 'EXPERT_SESSION' && room.expertSessionId
        ? `/sessions/${room.expertSessionId}`
        : `/rooms/${room.id}`)
    : null
  const onRoomPage = !!roomPagePath && location.pathname.startsWith(roomPagePath)

  // Track the anchor element (when on the room page) or PiP position (otherwise).
  useEffect(() => {
    if (!roomId) return

    let stopped = false
    let observer: ResizeObserver | null = null
    let anchorEl: HTMLElement | null = null
    let pollHandle: number | null = null

    // Idempotent commit — the 250ms poll runs forever while a call is active, so
    // bail out of state updates when nothing actually moved to avoid re-rendering
    // (and re-laying-out the Jitsi iframe) 4×/sec for no reason.
    const apply = (next: Rect, anchoredVal: boolean) => {
      setAnchored(anchoredVal)
      setRect((prev) =>
        prev.top === next.top && prev.left === next.left && prev.width === next.width && prev.height === next.height
          ? prev
          : next,
      )
    }

    const goPip = () => {
      anchorEl = null
      observer?.disconnect()
      observer = null
      apply(pipRect(), false)
    }

    const measure = () => {
      if (stopped) return
      if (onRoomPage) {
        const el = document.querySelector('[data-room-video-anchor]') as HTMLElement | null
        if (el) {
          if (el !== anchorEl) {
            anchorEl = el
            observer?.disconnect()
            observer = new ResizeObserver(measure)
            observer.observe(el)
          }
          const r = el.getBoundingClientRect()
          apply({ top: r.top, left: r.left, width: r.width, height: r.height }, true)
          return
        }
        // On the room page but the video panel isn't mounted (phone: a non-video
        // tab is focused) → float as a small PiP instead of covering the panel.
        goPip()
        return
      }
      // Off the room page → PiP.
      goPip()
    }

    measure()
    // Poll continuously while mounted: the anchor mounts/unmounts on phone tab
    // switches, which aren't route changes, so a one-shot measure isn't enough.
    pollHandle = window.setInterval(measure, 250)
    window.addEventListener('resize', measure)

    return () => {
      stopped = true
      observer?.disconnect()
      window.removeEventListener('resize', measure)
      if (pollHandle != null) window.clearInterval(pollHandle)
    }
  }, [roomId, onRoomPage, location.pathname])

  if (!roomId || !room) return null

  // Phone: never float a PiP. The call stays alive (iframe stays mounted) but is
  // parked off-screen unless it's docked into the room's video cell, so a tiny
  // video window never sits over the chat/whiteboard/other tabs or other pages.
  // The user returns by re-entering the room's Video tab and ends with Leave.
  // Desktop/tablet keep the floating PiP.
  const hiddenOffscreen = isPhone && !anchored

  // When docked to the room page we sit inside the bento cell's rounded box —
  // match the bottom inner radius. As a floating PiP it's a generic rounded card.
  const radiusStyle: React.CSSProperties = anchored
    ? {
        borderTopLeftRadius: 0,
        borderTopRightRadius: 0,
        borderBottomLeftRadius: 'calc(2rem - 2px)',
        borderBottomRightRadius: 'calc(2.5rem - 2px)',
      }
    : { borderRadius: '1rem' }

  // Tapping the floating PiP returns to the video: if we're already on the room
  // page (phone, off the video tab) just refocus the video cell; otherwise go to
  // the room route.
  const returnToVideo = () => {
    if (onRoomPage) useRoomLayoutStore.getState().setFocused('video')
    else if (roomPagePath) navigate(roomPagePath)
  }

  // Parked off-screen (kept a sane size so Jitsi keeps the stream flowing — a
  // display:none / 0×0 box can make the browser suspend the call).
  const offscreenStyle: React.CSSProperties = {
    top: 0,
    left: -10000,
    width: PIP_WIDTH,
    height: PIP_HEIGHT,
    pointerEvents: 'none',
  }

  return (
    <div
      ref={containerRef}
      aria-hidden={hiddenOffscreen || undefined}
      className={`fixed z-30 overflow-hidden bg-black ${
        hiddenOffscreen ? '' : 'transition-all duration-200 ease-out'
      } ${anchored || hiddenOffscreen ? '' : 'shadow-bubble ring-1 ring-white/10'}`}
      style={
        hiddenOffscreen
          ? offscreenStyle
          : {
              top: rect.top,
              left: rect.left,
              width: rect.width,
              height: rect.height,
              ...radiusStyle,
            }
      }
    >
      {/* Wrapped div so the absolutely-positioned children inside VideoPanel
          (Jitsi iframe via parentNode) get the right layout box.
          In PiP mode we disable pointer events on the iframe area so the
          overlay button below can receive the click — iframes capture all
          mouse input by default, which is why our outer onClick was being
          swallowed. */}
      <div
        className="w-full h-full"
        style={anchored ? undefined : { pointerEvents: 'none' }}
      >
        <VideoPanel room={room} />
      </div>
      {!anchored && !hiddenOffscreen && (
        <button
          type="button"
          onClick={returnToVideo}
          aria-label={t('room.pip.returnAria')}
          className="group absolute inset-0 flex flex-col justify-between items-stretch p-2 text-white cursor-pointer hover:bg-black/30 transition-colors"
        >
          <span className="self-start text-[10px] font-semibold uppercase tracking-wide bg-black/50 px-1.5 py-0.5 rounded backdrop-blur-sm">
            {t('room.pip.label')}
          </span>
          <span className="self-center text-xs font-semibold bg-black/55 px-2.5 py-1 rounded-full opacity-0 group-hover:opacity-100 transition-opacity">
            {t('room.pip.return')}
          </span>
        </button>
      )}
    </div>
  )
}
