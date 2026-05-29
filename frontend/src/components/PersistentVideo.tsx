import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useNavigate } from 'react-router-dom'
import { useActiveRoomStore } from '../store/activeRoomStore'
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
  const [room, setRoom] = useState<BubbleRoom | null>(null)
  const [rect, setRect] = useState<Rect>(() => pipRect())
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
          setRect({ top: r.top, left: r.left, width: r.width, height: r.height })
          return
        }
        // Anchor not mounted yet — keep last rect, retry shortly.
        return
      }
      // Off the room page → PiP.
      anchorEl = null
      observer?.disconnect()
      observer = null
      setRect(pipRect())
    }

    measure()
    // Anchor may mount slightly after route change. Poll briefly to catch it.
    pollHandle = window.setInterval(measure, 200)
    const stopPoll = window.setTimeout(() => {
      if (pollHandle != null) {
        window.clearInterval(pollHandle)
        pollHandle = null
      }
    }, 3000)
    window.addEventListener('resize', measure)

    return () => {
      stopped = true
      observer?.disconnect()
      window.removeEventListener('resize', measure)
      if (pollHandle != null) window.clearInterval(pollHandle)
      window.clearTimeout(stopPoll)
    }
  }, [roomId, onRoomPage, location.pathname])

  if (!roomId || !room) return null

  // When on the room page we sit inside the bento cell's rounded box — match
  // the bottom inner radius. In PiP mode it's a generic rounded card.
  const radiusStyle: React.CSSProperties = onRoomPage
    ? {
        borderTopLeftRadius: 0,
        borderTopRightRadius: 0,
        borderBottomLeftRadius: 'calc(2rem - 2px)',
        borderBottomRightRadius: 'calc(2.5rem - 2px)',
      }
    : { borderRadius: '1rem' }

  return (
    <div
      ref={containerRef}
      className={`fixed z-30 overflow-hidden bg-black transition-all duration-200 ease-out ${
        onRoomPage ? '' : 'shadow-bubble ring-1 ring-white/10'
      }`}
      style={{
        top: rect.top,
        left: rect.left,
        width: rect.width,
        height: rect.height,
        ...radiusStyle,
      }}
    >
      {/* Wrapped div so the absolutely-positioned children inside VideoPanel
          (Jitsi iframe via parentNode) get the right layout box.
          In PiP mode we disable pointer events on the iframe area so the
          overlay button below can receive the click — iframes capture all
          mouse input by default, which is why our outer onClick was being
          swallowed. */}
      <div
        className="w-full h-full"
        style={onRoomPage ? undefined : { pointerEvents: 'none' }}
      >
        <VideoPanel room={room} />
      </div>
      {!onRoomPage && (
        <button
          type="button"
          onClick={() => roomPagePath && navigate(roomPagePath)}
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
