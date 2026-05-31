import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useParams } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { useActiveRoomStore } from '../store/activeRoomStore'
import { getRoomForEvent, type BubbleRoom } from '../api/room'
import { getExpertSession, type ExpertSession } from '../api/expert'
import { subscribeToExpertSessionWriters, subscribeToRoomPresence } from '../api/ws'
import { errorCode } from '../api/errors'
import { ExpertRoomHeader } from './room/ExpertRoomHeader'
import { RoomBentoShell } from './room/RoomBentoShell'
import { WhiteboardPanel } from './room/WhiteboardPanel'
import { ExpertRoomChatPanel } from './room/ExpertRoomChatPanel'
import { ExpertHostControls } from './room/ExpertHostControls'
import { VideoCountdownPlaceholder } from './room/VideoCountdownPlaceholder'

/**
 * Route: {@code /sessions/:sessionId} — the host or any user whose group is
 * enrolled in the session enters here. Renders the exact same bento layout as
 * {@link BubbleRoomPage} (via {@link RoomBentoShell}); the differences are
 * the header (session title), the chat loader (fetches the session-scoped
 * room by id), and the host controls in the footer slot.
 */
export default function ExpertRoomPage() {
  const { t } = useTranslation()
  const { sessionId } = useParams<{ sessionId: string }>()
  const meId = useAuthStore((s) => s.user?.id ?? null)

  const [session, setSession] = useState<ExpertSession | null>(null)
  const [room, setRoom] = useState<BubbleRoom | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [transientError, setTransientError] = useState<string | null>(null)
  /** Live-synced writer set so isWriter recomputes when the host grants/revokes. */
  const [writers, setWriters] = useState<Set<string>>(() => new Set())
  /** Live count of users in the video call: seeded from the room, updated over WS. */
  const [inCall, setInCall] = useState(0)
  /** Ticks every 30s so the videoOpen check + the header countdown stay current. */
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (!sessionId) return
    let cancelled = false
    ;(async () => {
      try {
        const s = await getExpertSession(sessionId)
        if (cancelled) return
        setSession(s)
        // Resolve the BubbleRoom via the calendar event — same path the calendar
        // "Enter Room" button uses, keeps room-resolution logic in one place.
        const r = await getRoomForEvent(s.calendarEventId)
        if (cancelled) return
        setRoom(r)
      } catch (e) {
        if (cancelled) return
        const code = errorCode(e)
        if (code === 'ROOM_NOT_FOUND' || code === 'EXPERT_SESSION_NOT_FOUND') setLoadError(t('groups.error.roomNotFound'))
        else if (code === 'FORBIDDEN') setLoadError(t('expert.room.forbidden'))
        else if (code === 'ROOM_NOT_YET_OPEN' || code === 'EXPERT_SESSION_NOT_OPEN_FOR_JOIN_YET')
          setLoadError(t('expert.room.notYetOpenNoTime'))
        else if (code === 'ROOM_ENDED') setLoadError(t('expert.room.ended'))
        else if (code === 'JITSI_NOT_CONFIGURED') setLoadError(t('groups.error.jitsiNotConfigured'))
        else setLoadError(t('room.errorLoadGeneric'))
      }
    })()
    return () => { cancelled = true }
  }, [sessionId, t])

  // Live whiteboard writer set — keeps in sync with host grant/revoke.
  useEffect(() => {
    if (!sessionId) return
    const off = subscribeToExpertSessionWriters(sessionId, (e) => {
      setWriters(new Set(e.writers))
    })
    return off
  }, [sessionId])

  // Heartbeat: drives the videoOpen flip + the time-relative header label.
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30000)
    return () => clearInterval(id)
  }, [])

  // Live "N in call" — seed from the room snapshot, then track the broadcast.
  useEffect(() => {
    if (!room) return
    setInCall(room.participantCount)
    return subscribeToRoomPresence(room.id, (e) => setInCall(e.count))
  }, [room?.id])

  const videoOpensAtMs = room?.videoOpensAt ? new Date(room.videoOpensAt).getTime() : 0
  const videoOpen = !room?.videoOpensAt || now >= videoOpensAtMs
  // When the video gate flips open, re-fetch the room so we pick up the fresh
  // Jitsi JWT (it's null in the pre-open response — see RoomQueryService).
  useEffect(() => {
    if (!videoOpen) return
    if (!session) return
    if (room && room.jitsiJwt) return
    getRoomForEvent(session.calendarEventId)
      .then(setRoom)
      .catch(() => { /* leave existing room state in place; page already rendered */ })
  }, [videoOpen, session?.id])

  // Register the active room so PersistentVideo mounts the Jitsi iframe and the
  // global PiP / "return to room" pill knows where to point. We pass groupId=null
  // since the session has no group context. Defer until videoOpen — before then
  // PersistentVideo would render the "Video not configured" fallback because the
  // backend hasn't issued a JWT yet.
  useEffect(() => {
    if (!room || !videoOpen) return
    useActiveRoomStore.getState().setActive(room.id, null)
  }, [room?.id, videoOpen])

  if (!sessionId) return <Navigate to="/experts" replace />

  if (loadError) {
    return (
      <div className="flex flex-col items-center justify-center h-full p-8 gap-3">
        <p className="text-base">{loadError}</p>
        <a href="/experts" className="text-sm text-link underline">{t('expertRoom.backToDirectory')}</a>
      </div>
    )
  }

  if (!session || !room) {
    return (
      <div className="flex items-center justify-center h-full text-muted text-sm">{t('expertRoom.loadingSession')}</div>
    )
  }

  // Video cell:
  //   - Before videoOpensAt → countdown placeholder (no anchor div → no Jitsi).
  //   - After videoOpensAt → the persistent-video anchor div; PersistentVideo
  //     overlays its iframe over this rect so Jitsi survives route changes.
  const videoSlot = !videoOpen && room.videoOpensAt
    ? <VideoCountdownPlaceholder videoOpensAt={room.videoOpensAt} sessionTitle={session.title} />
    : <div data-room-video-anchor className="w-full h-full bg-black" />

  const isHost = meId !== null && meId === session.expertUserId
  const isWriter = isHost || (meId !== null && writers.has(meId))

  return (
    <RoomBentoShell
      header={<ExpertRoomHeader session={session} room={room} inCall={inCall} />}
      errorBanner={transientError ? (
        <div className="px-4 py-2 text-xs bg-warning/15 text-warning border-b border-line">
          {transientError}
          <button onClick={() => setTransientError(null)} className="ml-3 underline">{t('room.dismiss')}</button>
        </div>
      ) : null}
      videoSlot={videoSlot}
      whiteboardSlot={<WhiteboardPanel roomId={room.id} isWriter={isWriter} />}
      chatSlot={
        <ExpertRoomChatPanel
          chatRoomId={room.chatRoomId}
          meId={meId}
          onError={setTransientError}
        />
      }
      footerSlot={isHost ? (
        <ExpertHostControls
          sessionId={session.id}
          writers={writers}
        />
      ) : null}
    />
  )
}
