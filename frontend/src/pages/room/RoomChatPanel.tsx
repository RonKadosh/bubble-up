import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChatPanel } from '../groups/ChatPanel'
import { ChatRoom, getRooms } from '../../api/chat'

interface Props {
  groupId: string
  chatRoomId: string
  meId: string | null
  onError: (msg: string) => void
}

/**
 * Thin wrapper that locates the bubble's general chat room and hands it to the
 * existing ChatPanel. Reusing the bubble chat is intentional — messages typed
 * during a session show up in the hub later, so the bubble has one persistent
 * thread. V3 Expert Sessions introduce ephemeral chat separately.
 */
export function RoomChatPanel({ groupId, chatRoomId, meId, onError }: Props) {
  const { t } = useTranslation()
  const [room, setRoom] = useState<ChatRoom | null>(null)

  useEffect(() => {
    let cancelled = false
    getRooms()
      .then((rooms) => {
        if (cancelled) return
        const match = rooms.find((r) => r.id === chatRoomId) ?? null
        setRoom(match)
      })
      .catch((e) => {
        console.warn('[RoomChatPanel] getRooms failed', e)
        if (!cancelled) onError(t('room.errorLoadChat'))
      })
    return () => { cancelled = true }
  }, [chatRoomId, onError, t])

  // BentoCell already provides the surface + radius around us — render the
  // chat directly into the cell's content area.
  return (
    <ChatPanel
      groupId={groupId}
      room={room}
      meId={meId}
      isMember={true}
      onError={onError}
      onUnreadChanged={() => { /* room context: badge isn't visible */ }}
    />
  )
}
