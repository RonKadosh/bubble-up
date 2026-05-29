import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ChatPanel } from '../groups/ChatPanel'
import { ChatRoom, getChatRoom } from '../../api/chat'

interface Props {
  chatRoomId: string
  meId: string | null
  onError: (msg: string) => void
}

/**
 * Loads the session's own chat room (EXPERT_SESSION-scoped — not returned by
 * the hub's {@link getRooms} listing) and renders it through the shared
 * {@link ChatPanel}. We pass {@code groupId=""} into ChatPanel because the
 * session has no group context; the LinkPicker (file / calendar share) probes
 * group-scoped endpoints and 404s in that mode — an accepted v1 limitation,
 * the chat itself works.
 */
export function ExpertRoomChatPanel({ chatRoomId, meId, onError }: Props) {
  const { t } = useTranslation()
  const [room, setRoom] = useState<ChatRoom | null>(null)

  useEffect(() => {
    let cancelled = false
    getChatRoom(chatRoomId)
      .then((r) => { if (!cancelled) setRoom(r) })
      .catch((e) => {
        console.warn('[ExpertRoomChatPanel] getChatRoom failed', e)
        if (!cancelled) onError(t('room.errorLoadChat'))
      })
    return () => { cancelled = true }
  }, [chatRoomId, onError, t])

  return (
    <ChatPanel
      groupId=""
      room={room}
      meId={meId}
      isMember={true}
      onError={onError}
      onUnreadChanged={() => { /* room context: badge isn't visible */ }}
      isExpertSession={true}
    />
  )
}
