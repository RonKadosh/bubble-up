import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useParams } from 'react-router-dom'
import { useAuthStore } from '../store/authStore'
import { useActiveRoomStore } from '../store/activeRoomStore'
import { BubbleRoom, getRoom } from '../api/room'
import { errorCode } from '../api/errors'
import { RoomHeader } from './room/RoomHeader'
import { RoomBentoShell } from './room/RoomBentoShell'
import { WhiteboardPanel } from './room/WhiteboardPanel'
import { RoomChatPanel } from './room/RoomChatPanel'

export default function BubbleRoomPage() {
  const { t } = useTranslation()
  const { roomId } = useParams<{ roomId: string }>()
  const meId = useAuthStore((s) => s.user?.id ?? null)

  const [room, setRoom] = useState<BubbleRoom | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [transientError, setTransientError] = useState<string | null>(null)

  useEffect(() => {
    if (!roomId) return
    let cancelled = false
    getRoom(roomId)
      .then((r) => { if (!cancelled) setRoom(r) })
      .catch((e) => {
        if (cancelled) return
        const code = errorCode(e)
        if (code === 'ROOM_NOT_FOUND') setLoadError(t('groups.error.roomNotFound'))
        else if (code === 'NOT_GROUP_MEMBER') setLoadError(t('groups.error.notMember'))
        else if (code === 'FORBIDDEN') setLoadError(t('expert.room.forbidden'))
        else if (code === 'ROOM_NOT_YET_OPEN' || code === 'EXPERT_SESSION_NOT_OPEN_FOR_JOIN_YET')
          setLoadError(t('expert.room.notYetOpenNoTime'))
        else if (code === 'ROOM_ENDED') setLoadError(t('expert.room.ended'))
        else if (code === 'JITSI_NOT_CONFIGURED') setLoadError(t('groups.error.jitsiNotConfigured'))
        else setLoadError(t('room.errorLoadGeneric'))
      })
    return () => { cancelled = true }
  }, [roomId])

  // Announce that we're in this call so the global "return" pill can offer
  // a way back when the user navigates elsewhere. We do NOT clear on unmount
  // — that would hide the pill the instant they tab away to the hub, which
  // defeats the point. The active state is cleared explicitly by the Leave
  // button (RoomHeader) or when the tab closes (the store isn't persisted).
  useEffect(() => {
    if (!room?.groupId) return
    useActiveRoomStore.getState().setActive(room.id, room.groupId)
  }, [room?.id, room?.groupId])

  if (!roomId) return <Navigate to="/groups" replace />

  if (loadError) {
    return (
      <div className="flex flex-col items-center justify-center h-full p-8 gap-3">
        <p className="text-base">{loadError}</p>
        <a href="/groups" className="text-sm text-link underline">{t('room.backToBubbles')}</a>
      </div>
    )
  }

  if (!room) {
    return (
      <div className="flex items-center justify-center h-full text-muted text-sm">{t('common.loading')}</div>
    )
  }

  // EXPERT_SESSION rooms have their own page (`/sessions/:sessionId`) — anyone
  // landing here via a bookmarked /rooms/:id URL gets redirected so the right
  // header / chat loader / host controls render.
  if (room.scope === 'EXPERT_SESSION' && room.expertSessionId) {
    return <Navigate to={`/sessions/${room.expertSessionId}`} replace />
  }

  // GROUP rooms must have a groupId — without one they're a corrupt row.
  if (!room.groupId) {
    return <Navigate to="/groups" replace />
  }

  // The Video cell is just an anchor — PersistentVideo (mounted at Layout
  // level) positions its iframe absolutely over this rect so Jitsi survives
  // route changes. The black bg keeps the cell looking right during the
  // first paint before PersistentVideo measures and overlays.
  const videoSlot = <div data-room-video-anchor className="w-full h-full bg-black" />

  return (
    <RoomBentoShell
      header={<RoomHeader room={room} />}
      errorBanner={transientError ? (
        <div className="px-4 py-2 text-xs bg-warning/15 text-warning border-b border-line">
          {transientError}
          <button onClick={() => setTransientError(null)} className="ml-3 underline">{t('room.dismiss')}</button>
        </div>
      ) : null}
      videoSlot={videoSlot}
      whiteboardSlot={<WhiteboardPanel roomId={room.id} isWriter={true} />}
      chatSlot={
        <RoomChatPanel
          groupId={room.groupId}
          chatRoomId={room.chatRoomId}
          meId={meId}
          onError={setTransientError}
        />
      }
    />
  )
}
