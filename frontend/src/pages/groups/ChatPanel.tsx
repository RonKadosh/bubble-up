import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Avatar } from '../../components/Avatar'
import { useUserCardStore } from '../../store/userCardStore'
import {
  ChatLinkTargetType,
  ChatMessage,
  ChatRoom,
  PinUpdate,
  getMessages,
  getMessagesFocused,
  getPinnedMessages,
  markRead,
  pinMessage,
  sendLinkMessage,
  sendTextMessage,
  unpinMessage,
} from '../../api/chat'
import { CalendarEvent, getEvent, listEvents } from '../../api/calendar'
import { DEMO_MODE } from '../../api/demo'
import {
  GroupFile,
  GroupFolder,
  fileIcon,
  formatBytes,
  getFile,
  getFiles,
  listFolders,
} from '../../api/files'
import { getRoomForEvent } from '../../api/room'
import { getExpertSession, type ExpertSession } from '../../api/expert'
import { describeError } from '../../api/errors'
import {
  Poll,
  PollUpdate,
  closePoll,
  createPoll,
  getPoll,
  votePoll,
} from '../../api/polls'
import { subscribeToRoom, subscribeToRoomPins, subscribeToRoomPolls } from '../../api/ws'
import { Button, IconButton } from '../../components/Button'
import {
  CapIcon, UserPlusIcon, UserMinusIcon, ClockIcon, CalendarIcon, VideoIcon,
  PollIcon, FileIcon, FolderIcon, LinkIcon, PinIcon, ReplyIcon, PlusIcon, SendIcon, CrownIcon,
} from '../../components/Icons'
import {
  BUBBLE_EMOJIS,
  BUBBLE_EMOJI_BY_SHORTCODE,
  BUBBLE_EMOJI_REGEX,
  BubbleEmoji,
  BubbleEmojiDef,
  bubbleEmojiSpanHTML,
  renderBubbleContent,
} from '../../components/BubbleEmojis'
import { TYPE_COLORS, fmtRange } from './calendarFormat'
import { formatClock, formatDateTime } from '../../i18n/datetime'

const PAGE_SIZE = 50

interface ChatPanelProps {
  groupId: string
  room: ChatRoom | null
  meId: string | null
  isMember: boolean
  onError: (msg: string) => void
  /** Called after the user marks-read or after a new message arrives — parent re-fetches rooms to refresh badges. */
  onUnreadChanged: () => void
  /**
   * Called when the user clicks a FileLinkCard. Parent (GroupsPage) should switch
   * the Files tile to focused and pass the file id down so the FilesPanel auto-opens it.
   */
  onOpenFile?: (fileId: string) => void
  /** True when this panel is *not* the bento focus (i.e. minimized). Per-box compact rendering lands in a follow-up. */
  compact?: boolean
  /**
   * True when the chat lives inside an expert-session room (no group context).
   * Adjusts the wording on SYSTEM_JOIN / SYSTEM_LEAVE rows ("joined the session"
   * vs the default "joined the Bubble").
   */
  isExpertSession?: boolean
}

export function ChatPanel({ groupId, room, meId, isMember, onError, onUnreadChanged, onOpenFile, isExpertSession }: ChatPanelProps) {
  const { t } = useTranslation()
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [content, setContent] = useState('')
  const [hasMoreOlder, setHasMoreOlder] = useState(true)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [showLinkPicker, setShowLinkPicker] = useState(false)
  const [showEmojiPicker, setShowEmojiPicker] = useState(false)
  /** The consolidated "share & more" popover (calendar/file share + create poll). */
  const [showComposerMenu, setShowComposerMenu] = useState(false)
  const editorRef = useRef<HTMLDivElement | null>(null)
  /** Currently composing a reply to this message — drives the reply preview bar above the input. */
  const [replyTo, setReplyTo] = useState<ChatMessage | null>(null)
  /** Message id flashed with a highlight ring after click-to-jump. Cleared by a timer. */
  const [highlightedId, setHighlightedId] = useState<string | null>(null)
  /** Snapshot taken when the room is first opened, used to render the "New messages" divider. Cleared on mark-read. */
  const [initialUnread, setInitialUnread] = useState(0)
  /** Pinned messages for this room, newest pin first. Seeded by GET /pins, updated by WS. */
  const [pinned, setPinned] = useState<ChatMessage[]>([])
  /** Toggles the "view all pinned" modal. */
  const [showPinnedList, setShowPinnedList] = useState(false)
  /** Resolved calendar-event lookup shared by every LINK card so duplicate ids fetch once. */
  const [eventCache, setEventCache] = useState<Map<string, CalendarEvent | 'unavailable'>>(new Map())
  const inflightEvents = useRef<Set<string>>(new Set())
  /** Resolved poll lookup. Same pattern as eventCache — one fetch per poll id. */
  const [pollCache, setPollCache] = useState<Map<string, Poll | 'unavailable'>>(new Map())
  const inflightPolls = useRef<Set<string>>(new Set())
  /** Resolved file lookup for FILE link cards. Same pattern as eventCache. */
  const [fileCache, setFileCache] = useState<Map<string, GroupFile | 'unavailable'>>(new Map())
  const inflightFiles = useRef<Set<string>>(new Set())
  /** Toggles the "create poll" composer modal. */
  const [showPollComposer, setShowPollComposer] = useState(false)
  const scrollerRef = useRef<HTMLDivElement | null>(null)
  const bottomRef = useRef<HTMLDivElement | null>(null)
  /** Track whether user is parked near the bottom — controls auto-scroll on new messages. */
  const stickToBottomRef = useRef(true)
  /** Last message id we marked-read for; skip POST /read if unchanged. */
  const lastMarkedReadIdRef = useRef<string | null>(null)
  const roomId = room?.id ?? null

  function resolveEvent(id: string) {
    if (eventCache.has(id) || inflightEvents.current.has(id)) return
    inflightEvents.current.add(id)
    getEvent(id)
      .then((ev) => setEventCache((prev) => new Map(prev).set(id, ev)))
      .catch(() => setEventCache((prev) => new Map(prev).set(id, 'unavailable')))
      .finally(() => inflightEvents.current.delete(id))
  }

  function resolvePoll(id: string) {
    if (pollCache.has(id) || inflightPolls.current.has(id)) return
    inflightPolls.current.add(id)
    getPoll(id)
      .then((p) => setPollCache((prev) => new Map(prev).set(id, p)))
      .catch(() => setPollCache((prev) => new Map(prev).set(id, 'unavailable')))
      .finally(() => inflightPolls.current.delete(id))
  }

  function resolveFile(id: string) {
    if (fileCache.has(id) || inflightFiles.current.has(id)) return
    inflightFiles.current.add(id)
    getFile(groupId, id)
      .then((f) => setFileCache((prev) => new Map(prev).set(id, f)))
      .catch(() => setFileCache((prev) => new Map(prev).set(id, 'unavailable')))
      .finally(() => inflightFiles.current.delete(id))
  }

  /** Merge a live PollUpdate event into a cached Poll (counts + voters + closedAt). */
  function applyPollUpdate(e: PollUpdate) {
    setPollCache((prev) => {
      const cached = prev.get(e.pollId)
      if (!cached || cached === 'unavailable') return prev   // not yet loaded — let lazy load fetch fresh
      const optionVoters = e.optionVoters || {}
      const nextOptions = cached.options.map((o) => {
        const voters = optionVoters[o.id] ?? []
        return {
          ...o,
          voteCount: e.optionCounts[o.id] ?? 0,
          voters,
        }
      })
      const myId = meId
      const myVote = myId
        ? nextOptions.filter((o) => o.voters.includes(myId)).map((o) => o.id)
        : []
      const updated: Poll = {
        ...cached,
        options: nextOptions,
        totalVotes: e.totalVotes,
        closedAt: e.closedAt,
        myVote,
      }
      return new Map(prev).set(e.pollId, updated)
    })
  }

  async function handleVote(pollId: string, optionIds: string[]) {
    try {
      const poll = await votePoll(pollId, optionIds)
      setPollCache((prev) => new Map(prev).set(pollId, poll))
    } catch (err) {
      onError(describeError(err, t, { TOO_MANY_REQUESTS: 'common.rateLimited' }, 'groups.error.pollVote'))
    }
  }

  async function handleClosePoll(pollId: string) {
    if (!confirm(t('groups.chat.poll.confirmClose'))) return
    try {
      const poll = await closePoll(pollId)
      setPollCache((prev) => new Map(prev).set(pollId, poll))
    } catch {
      onError(t('groups.error.pollClose'))
    }
  }

  async function handleCreatePoll(question: string, options: string[], allowMultiple: boolean) {
    setShowPollComposer(false)
    if (!roomId) return
    try {
      const poll = await createPoll(roomId, { question, options, allowMultiple })
      setPollCache((prev) => new Map(prev).set(poll.id, poll))
      // The LINK chat message that surfaces this poll arrives via the chatRoom subscription;
      // no need to append it manually here.
      stickToBottomRef.current = true
    } catch (err) {
      onError(describeError(err, t, { TOO_MANY_REQUESTS: 'common.rateLimited' }, 'groups.error.pollCreate'))
    }
  }

  function dedupeAppend(msg: ChatMessage) {
    setMessages((prev) => (prev.some((m) => m.id === msg.id) ? prev : [...prev, msg]))
  }

  function dedupePrepend(older: ChatMessage[]) {
    setMessages((prev) => {
      const seen = new Set(prev.map((m) => m.id))
      const fresh = older.filter((m) => !seen.has(m.id))
      return [...fresh, ...prev]
    })
  }

  /** Dedupe + sort by (sentAt, id). Used by focus-load when target lies outside the loaded window. */
  function dedupeMerge(extra: ChatMessage[]) {
    setMessages((prev) => {
      const byId = new Map(prev.map((m) => [m.id, m]))
      for (const m of extra) if (!byId.has(m.id)) byId.set(m.id, m)
      return [...byId.values()].sort((a, b) => {
        const at = a.sentAt.localeCompare(b.sentAt)
        return at !== 0 ? at : a.id.localeCompare(b.id)
      })
    })
  }

  /** Fast lookup of any loaded message — used by the quoted-snippet preview. */
  const messagesById = useMemo(() => {
    const m = new Map<string, ChatMessage>()
    for (const x of messages) m.set(x.id, x)
    return m
  }, [messages])

  /**
   * Same lookup, but as a ref. WS subscription handlers capture the closure at subscribe
   * time, so they see stale memos. The ref keeps the latest map reachable from inside
   * those handlers without re-subscribing on every render.
   */
  const messagesByIdRef = useRef<Map<string, ChatMessage>>(messagesById)
  useEffect(() => { messagesByIdRef.current = messagesById }, [messagesById])

  /** Click-to-jump for replies (and later, pins). Scrolls the row into view + flashes it. */
  async function jumpToMessage(id: string) {
    const flash = () => {
      const el = document.querySelector(`[data-msgid="${id}"]`)
      if (!el) return false
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      setHighlightedId(id)
      setTimeout(() => setHighlightedId((prev) => (prev === id ? null : prev)), 1500)
      return true
    }
    if (flash()) return
    if (!roomId) return
    try {
      const page = await getMessagesFocused(roomId, id, PAGE_SIZE)
      // server returns DESC; reverse to ASC for merge ordering
      dedupeMerge(page.slice().reverse())
      requestAnimationFrame(() => { flash() })
    } catch {/* parent gone — silent; user just sees a no-op */}
  }

  // Initial load + subscribe when room changes.
  useEffect(() => {
    if (!isMember || !roomId) return
    let cancelled = false
    let unsub: (() => void) | null = null
    setMessages([])
    setHasMoreOlder(true)
    setLoadingOlder(false)
    setInitialUnread(room?.unreadCount ?? 0)
    setReplyTo(null)
    setHighlightedId(null)
    setPinned([])
    setShowPinnedList(false)
    setPollCache(new Map())
    setShowPollComposer(false)
    stickToBottomRef.current = true
    lastMarkedReadIdRef.current = null

    let unsubPins: (() => void) | null = null
    let unsubPolls: (() => void) | null = null

    ;(async () => {
      try {
        const page = await getMessages(roomId, { size: PAGE_SIZE })
        if (cancelled) return
        setMessages(page.slice().reverse())   // server returns DESC; flip to ASC for display
        if (page.length < PAGE_SIZE) setHasMoreOlder(false)
        unsub = subscribeToRoom(roomId, (msg) => {
          dedupeAppend(msg)
          if (stickToBottomRef.current) {
            // arriving while user is at the bottom — chase it and ack.
            queueMicrotask(() => {
              bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
              markReadAndRefresh(msg.id)
            })
          } else {
            // user is scrolled up — they'll mark-read when they come back down. Just bump the badge.
            onUnreadChanged()
          }
        })
      } catch {
        onError(t('groups.error.loadChat'))
      }

      try {
        const pins = await getPinnedMessages(roomId)
        if (cancelled) return
        setPinned(pins)
        unsubPins = subscribeToRoomPins(roomId, (e) => {
          applyPinUpdate(e)
        })
      } catch {/* pins are decorative — silent on failure */}

      unsubPolls = subscribeToRoomPolls(roomId, (e) => {
        applyPollUpdate(e)
      })
    })()

    return () => {
      cancelled = true
      unsub?.()
      unsubPins?.()
      unsubPolls?.()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, isMember])

  /**
   * Apply a pin/unpin event. Mutates both the message list (so the bubble's gold border
   * appears/disappears) and the pinned strip (so the top bar count refreshes live).
   */
  function applyPinUpdate(e: PinUpdate) {
    setMessages((prev) => prev.map((m) =>
      m.id === e.messageId
        ? { ...m, pinned: e.pinned, pinnedAt: e.pinnedAt, pinnedByUserId: e.pinnedByUserId }
        : m
    ))
    setPinned((prev) => {
      // remove the message from current pinned list (we'll insert it again if pinned=true)
      const without = prev.filter((m) => m.id !== e.messageId)
      if (!e.pinned) return without
      // try to locate the message in the loaded set so we can place the full snapshot
      const updatedFromLoaded = messagesByIdRef.current.get(e.messageId)
      if (updatedFromLoaded) {
        const updated: ChatMessage = {
          ...updatedFromLoaded,
          pinned: true,
          pinnedAt: e.pinnedAt,
          pinnedByUserId: e.pinnedByUserId,
        }
        return [updated, ...without]
      }
      // Not in the loaded window — refresh from server to get the full message body.
      if (roomId) getPinnedMessages(roomId).then(setPinned).catch(() => {/* keep stale */})
      return without
    })
  }

  /** Pin/unpin trigger fired from the per-message hover action. */
  async function handleTogglePin(message: ChatMessage) {
    if (!roomId) return
    try {
      if (message.pinned) {
        const e = await unpinMessage(roomId, message.id)
        applyPinUpdate(e)
      } else {
        const e = await pinMessage(roomId, message.id)
        applyPinUpdate(e)
      }
    } catch {
      onError(t('groups.error.pin'))
    }
  }

  // Auto-scroll to bottom + mark-read on first render of messages.
  useEffect(() => {
    if (messages.length === 0 || !roomId) return
    if (stickToBottomRef.current) {
      bottomRef.current?.scrollIntoView({ behavior: 'auto' })
      const latest = messages[messages.length - 1]
      markReadAndRefresh(latest.id)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, messages.length > 0])

  async function markReadAndRefresh(messageId: string) {
    if (!roomId) return
    if (lastMarkedReadIdRef.current === messageId) return   // dedupe — was the cascade culprit
    lastMarkedReadIdRef.current = messageId
    try {
      await markRead(roomId, messageId)
      setInitialUnread(0)
      onUnreadChanged()
    } catch {/* badge will catch up on next refresh */}
  }

  async function handleScroll() {
    const el = scrollerRef.current
    if (!el) return
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
    stickToBottomRef.current = distanceFromBottom < 40

    if (el.scrollTop > 0 || !hasMoreOlder || loadingOlder || messages.length === 0 || !roomId) return
    const oldest = messages[0]
    setLoadingOlder(true)
    const prevHeight = el.scrollHeight
    try {
      const older = await getMessages(roomId, { before: oldest.id, size: PAGE_SIZE })
      if (older.length < PAGE_SIZE) setHasMoreOlder(false)
      dedupePrepend(older.slice().reverse())
      // Preserve scroll position so the user stays anchored on the same row.
      requestAnimationFrame(() => {
        if (!scrollerRef.current) return
        scrollerRef.current.scrollTop = scrollerRef.current.scrollHeight - prevHeight
      })
    } catch {
      onError(t('groups.error.loadOlder'))
    } finally {
      setLoadingOlder(false)
    }
  }

  async function handleSend(e: React.FormEvent) {
    e.preventDefault()
    if (!roomId || !content.trim()) return
    const text = content
    const replyId = replyTo?.id
    setContent('')
    setReplyTo(null)
    if (editorRef.current) editorRef.current.innerHTML = ''
    try {
      const msg = await sendTextMessage(roomId, text, replyId)
      dedupeAppend(msg)
      // Lets the demo guided tour advance off the visitor's first real message.
      if (DEMO_MODE) window.dispatchEvent(new Event('demo:chat-sent'))
      stickToBottomRef.current = true
      queueMicrotask(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
        markReadAndRefresh(msg.id)
      })
    } catch (err) {
      onError(describeError(err, t, { TOO_MANY_REQUESTS: 'common.rateLimited' }, 'groups.error.send'))
      setContent(text)
      if (editorRef.current) deserializeIntoEditor(editorRef.current, text)
      if (replyId) setReplyTo((prev) => prev ?? messagesById.get(replyId) ?? null)
    }
  }

  function handlePickEmoji(shortcode: string) {
    const def = BUBBLE_EMOJI_BY_SHORTCODE.get(shortcode)
    const editor = editorRef.current
    if (!def || !editor) {
      setShowEmojiPicker(false)
      return
    }
    insertEmojiAtSelection(editor, def)
    setContent(serializeEditor(editor))
    setShowEmojiPicker(false)
  }

  async function handlePickLink(linkTargetType: ChatLinkTargetType, linkTargetId: string, caption?: string) {
    setShowLinkPicker(false)
    if (!roomId) return
    const replyId = replyTo?.id
    setReplyTo(null)
    try {
      const msg = await sendLinkMessage(roomId, linkTargetType, linkTargetId, caption, replyId)
      dedupeAppend(msg)
      stickToBottomRef.current = true
      queueMicrotask(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
        markReadAndRefresh(msg.id)
      })
    } catch (err) {
      onError(describeError(err, t, { TOO_MANY_REQUESTS: 'common.rateLimited' }, 'groups.error.shareLink'))
    }
  }

  if (!isMember) {
    return <div className="p-6 text-sm text-muted">{t('groups.chat.joinPrompt')}</div>
  }
  if (!room) {
    return <div className="p-6 text-sm text-muted">{t('groups.chat.loading')}</div>
  }

  const dividerAt = initialUnread > 0 ? Math.max(0, messages.length - initialUnread) : -1

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      {pinned.length > 0 && (
        <PinnedStrip
          pinned={pinned}
          onJumpTo={jumpToMessage}
          onShowAll={() => setShowPinnedList(true)}
        />
      )}
      <div
        ref={scrollerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto overflow-x-hidden px-3 py-4 tablet:p-5 flex flex-col gap-3"
      >
        {loadingOlder && (
          <p className="text-center text-xs text-muted">{t('groups.chat.loadingOlder')}</p>
        )}
        {!hasMoreOlder && messages.length > 0 && (
          <p className="text-center text-xs text-muted">{t('groups.chat.startOfChat')}</p>
        )}
        {messages.length === 0 && (
          <p className="text-muted text-sm text-center mt-8">{t('groups.chat.noMessages')}</p>
        )}
        {messages.map((m, idx) => (
          <div key={m.id}>
            {idx === dividerAt && (
              <div className="flex items-center gap-2 my-3 text-xs text-primary-600 font-semibold">
                <div className="flex-1 h-px bg-primary-200" />
                {t('groups.chat.newMessages')}
                <div className="flex-1 h-px bg-primary-200" />
              </div>
            )}
            <ChatMessageRow
              message={m}
              meId={meId}
              groupId={groupId}
              eventCache={eventCache}
              onNeedEvent={resolveEvent}
              pollCache={pollCache}
              onNeedPoll={resolvePoll}
              onVotePoll={handleVote}
              onClosePoll={handleClosePoll}
              fileCache={fileCache}
              onNeedFile={resolveFile}
              onOpenFile={onOpenFile}
              quotedParent={m.replyToMessageId ? messagesById.get(m.replyToMessageId) ?? null : null}
              highlighted={highlightedId === m.id}
              onReply={() => setReplyTo(m)}
              onJumpTo={jumpToMessage}
              onTogglePin={() => handleTogglePin(m)}
              isExpertSession={isExpertSession ?? false}
            />
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      {replyTo && (
        <ReplyPreviewBar
          parent={replyTo}
          meId={meId}
          onCancel={() => setReplyTo(null)}
        />
      )}
      <form
        onSubmit={handleSend}
        data-tour="chat-composer"
        className="bg-surface border-t border-line px-5 py-3 flex gap-2 items-center min-w-0"
      >
        <div className="relative shrink-0" data-tour="composer-share">
          <IconButton
            variant="cell"
            size="md"
            type="button"
            onClick={() => setShowComposerMenu((v) => !v)}
            aria-label={t('groups.chat.menu.openAria')}
            title={t('groups.chat.menu.openTitle')}
          >
            <PlusIcon className="w-5 h-5" />
          </IconButton>
          {showComposerMenu && (
            <ComposerMenuPopover
              onShareLink={() => { setShowComposerMenu(false); setShowLinkPicker(true) }}
              onCreatePoll={() => { setShowComposerMenu(false); setShowPollComposer(true) }}
              onClose={() => setShowComposerMenu(false)}
            />
          )}
        </div>
        <div className="relative shrink-0">
          <IconButton
            variant="cell"
            size="md"
            type="button"
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => setShowEmojiPicker((v) => !v)}
            aria-label={t('groups.chat.emoji.pickerAria')}
            title={t('groups.chat.emoji.pickerTitle')}
          >
            <BubbleEmoji def={BUBBLE_EMOJIS[0]} className="w-5 h-5" />
          </IconButton>
          {showEmojiPicker && (
            <BubbleEmojiPopover
              onPick={handlePickEmoji}
              onClose={() => setShowEmojiPicker(false)}
            />
          )}
        </div>
        <div className="relative flex-1 min-w-0">
          <div
            ref={editorRef}
            contentEditable
            suppressContentEditableWarning
            role="textbox"
            data-tour="chat-input"
            aria-label={t('groups.chat.messagePlaceholder')}
            onInput={() => {
              const editor = editorRef.current
              if (editor) setContent(serializeEditor(editor))
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                e.currentTarget.closest('form')?.requestSubmit()
              }
            }}
            onPaste={(e) => {
              e.preventDefault()
              const text = e.clipboardData.getData('text/plain')
              document.execCommand('insertText', false, text)
            }}
            className="w-full border border-line bg-surface-muted text-base rounded-3xl px-5 py-2.5 text-sm focus-bubble transition-shadow h-[2.75rem] overflow-y-auto whitespace-pre-wrap break-words leading-snug outline-none"
          />
          {!content && (
            <span
              className="pointer-events-none absolute inset-y-0 start-5 flex items-center text-muted text-sm"
              aria-hidden="true"
            >
              {t('groups.chat.messagePlaceholder')}
            </span>
          )}
        </div>
        <button
          type="submit"
          className="bg-brand-gradient-deep text-white rounded-full w-12 h-12 shrink-0 flex items-center justify-center shadow-themed bubble-pop"
          aria-label={t('groups.chat.sendAria')}
        >
          <SendIcon className="w-5 h-5 rtl:-scale-x-100" />
        </button>
      </form>
      {showLinkPicker && (
        <LinkPickerModal
          groupId={groupId}
          onPick={handlePickLink}
          onCancel={() => setShowLinkPicker(false)}
        />
      )}
      {showPinnedList && (
        <PinnedListModal
          pinned={pinned}
          onJumpTo={(id) => { setShowPinnedList(false); jumpToMessage(id) }}
          onUnpin={(m) => handleTogglePin(m)}
          onCancel={() => setShowPinnedList(false)}
        />
      )}
      {showPollComposer && (
        <PollComposerModal
          onCreate={handleCreatePoll}
          onCancel={() => setShowPollComposer(false)}
        />
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: one chat row. Renders system join/leave, text, or LINK (calendar event).
// ---------------------------------------------------------------------------

interface ChatMessageRowProps {
  message: ChatMessage
  meId: string | null
  /** The Bubble this chat belongs to ("" in an expert-session chat). Threaded into
   *  session/room link cards so entering a session remembers the originating Bubble. */
  groupId: string
  eventCache: Map<string, CalendarEvent | 'unavailable'>
  onNeedEvent: (id: string) => void
  pollCache: Map<string, Poll | 'unavailable'>
  onNeedPoll: (id: string) => void
  onVotePoll: (pollId: string, optionIds: string[]) => void
  onClosePoll: (pollId: string) => void
  fileCache: Map<string, GroupFile | 'unavailable'>
  onNeedFile: (id: string) => void
  /** Called when the user clicks a FileLinkCard. Parent switches Files tile + opens viewer. */
  onOpenFile?: (fileId: string) => void
  /** The message this one is replying to (if loaded). Null = either no reply, or parent not in cache. */
  quotedParent: ChatMessage | null
  /** True when the row should briefly flash after click-to-jump. */
  highlighted: boolean
  /** Triggered by the hover "↩ Reply" action — parent panel uses this to prime the reply preview bar. */
  onReply: () => void
  /** Click on quoted snippet → scroll-and-flash the parent. Parent panel handles focus-load. */
  onJumpTo: (id: string) => void
  /** Toggle pinned state for this message. */
  onTogglePin: () => void
  /** True when this row is rendered inside an expert-session chat. Drives the
   *  wording on SYSTEM_JOIN / SYSTEM_LEAVE rows. */
  isExpertSession: boolean
}

function ChatMessageRow({
  message: m, meId, groupId, eventCache, onNeedEvent, pollCache, onNeedPoll, onVotePoll, onClosePoll,
  fileCache, onNeedFile, onOpenFile,
  quotedParent, highlighted, onReply, onJumpTo, onTogglePin, isExpertSession,
}: ChatMessageRowProps) {
  const { t } = useTranslation()
  const openUserCard = useUserCardStore((s) => s.open)
  // Tap-to-reveal the reply/pin actions + the full timestamp. On desktop hover
  // also reveals them; on touch (no hover) tapping the message body is the gesture.
  const [showActions, setShowActions] = useState(false)
  if (m.messageType === 'SYSTEM_JOIN' || m.messageType === 'SYSTEM_LEAVE') {
    const join = m.messageType === 'SYSTEM_JOIN'
    const phrase = isExpertSession
      ? (join ? t('expertRoom.chat.joined') : t('expertRoom.chat.left'))
      : (join ? t('dashboard.kind.joinedBubble') : t('dashboard.kind.leftBubble'))
    const RowIcon = isExpertSession && join ? CapIcon : join ? UserPlusIcon : UserMinusIcon
    return (
      <p data-msgid={m.id} className="flex items-center justify-center gap-1.5 text-center text-xs text-muted italic">
        <RowIcon className="w-3.5 h-3.5 shrink-0" />
        <span className="font-mono" dir="ltr">{m.content || m.subjectUserId?.slice(0, 8) || '?'}</span>{' '}{phrase}
      </p>
    )
  }

  if (m.messageType === 'SYSTEM_ROOM_EXTENDED') {
    return (
      <p data-msgid={m.id} className="flex items-center justify-center gap-1.5 text-center text-xs text-muted italic">
        <ClockIcon className="w-3.5 h-3.5 shrink-0" /> {m.content || 'Session extended.'}
      </p>
    )
  }

  if (m.messageType === 'SYSTEM_OWNERSHIP_TRANSFER') {
    return (
      <p data-msgid={m.id} className="flex items-center justify-center gap-1.5 text-center text-xs text-muted italic">
        <CrownIcon className="w-3.5 h-3.5 shrink-0 text-amber-500" /> {m.content}
      </p>
    )
  }

  if (m.messageType === 'SYSTEM_GROUP_ROOM_OPEN' && m.linkTargetType === 'ROOM' && m.linkTargetId) {
    return (
      <GroupRoomLiveCard
        messageId={m.id}
        roomId={m.linkTargetId}
        content={m.content || t('groups.chat.roomLiveFallback')}
      />
    )
  }

  if (m.messageType === 'SYSTEM_EXPERT_SESSION_OPEN'
      && m.linkTargetType === 'EXPERT_SESSION' && m.linkTargetId) {
    return (
      <div data-msgid={m.id} className="flex flex-col items-center gap-1 my-1">
        <p className="flex items-center justify-center gap-1.5 text-center text-xs text-muted italic">
          <CapIcon className="w-3.5 h-3.5 shrink-0" /> {m.content || t('expertRoom.systemOpenFallback')}
        </p>
        <div className="w-full max-w-[20rem]">
          <ExpertSessionLinkCard
            sessionId={m.linkTargetId}
            caption=""
            mine={false}
            fromGroupId={groupId}
          />
        </div>
      </div>
    )
  }

  const mine = m.senderId === meId
  const highlightRing = highlighted ? 'ring-2 ring-primary-400 ring-offset-1' : ''
  const pinBorder = m.pinned ? 'border-s-4 border-amber-400' : ''
  const actions = (
    <div className={`${showActions ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'} transition-opacity flex flex-col gap-0.5 text-xs self-center`}>
      <button
        type="button"
        onClick={onReply}
        aria-label={t('groups.chat.reply.button')}
        title={t('groups.chat.reply.button')}
        className="text-muted hover:text-primary-600 px-1.5 py-0.5 rounded-md inline-flex items-center justify-center"
      >
        <ReplyIcon className="w-4 h-4 rtl:-scale-x-100" />
      </button>
      <button
        type="button"
        onClick={onTogglePin}
        aria-label={m.pinned ? t('groups.chat.pin.unpin') : t('groups.chat.pin.button')}
        title={m.pinned ? t('groups.chat.pin.unpin') : t('groups.chat.pin.button')}
        className={`px-1.5 py-0.5 rounded-md inline-flex items-center justify-center ${m.pinned ? 'text-amber-500' : 'text-muted hover:text-amber-500'}`}
      >
        <PinIcon className="w-4 h-4" />
      </button>
    </div>
  )
  return (
    <div
      data-msgid={m.id}
      className={`group flex items-end gap-2 min-w-0 ${mine ? 'justify-end' : 'justify-start'}`}
    >
      {mine && actions}
      {!mine && m.senderId && (
        <button
          type="button"
          onClick={() => openUserCard(m.senderId!)}
          aria-label={m.senderDisplayName ?? m.senderId}
          className="shrink-0 self-end"
        >
          <Avatar
            id={m.senderId}
            name={m.senderDisplayName ?? '?'}
            imageUrl={m.senderAvatarUrl}
            size="sm"
          />
        </button>
      )}
      <div
        onClick={(e) => {
          // Tapping the bubble toggles the action menu — but let inner buttons/links
          // (link cards, poll votes, the quoted-jump, the timestamp) do their own thing.
          if ((e.target as HTMLElement).closest('button, a')) return
          setShowActions((v) => !v)
        }}
        className={`min-w-0 max-w-[75%] tablet:max-w-[60%] px-4 py-2.5 shadow-themed text-sm transition-shadow cursor-pointer ${highlightRing} ${pinBorder} ${
        mine
          ? 'bg-brand-gradient-strong text-on-brand rounded-[1.5rem] rounded-br-[3px]'
          : 'bg-surface border border-line text-base rounded-[1.5rem] rounded-bl-[3px]'
      }`}>
        {!mine && m.senderId && (
          // Sender name is a plain label — only the avatar opens the profile card.
          <span className="text-xs font-semibold text-primary-600 mb-1 block truncate text-start">
            {m.senderDisplayName ?? `${m.senderId.slice(0, 8)}…`}
          </span>
        )}
        {m.replyToMessageId && (
          <QuotedPreview
            parent={quotedParent}
            parentId={m.replyToMessageId}
            mine={mine}
            onClick={() => onJumpTo(m.replyToMessageId!)}
          />
        )}
        {m.messageType === 'LINK' && m.linkTargetType === 'CALENDAR_EVENT' && m.linkTargetId ? (
          <CalendarLinkCard
            eventId={m.linkTargetId}
            caption={m.content}
            mine={mine}
            cache={eventCache}
            onNeedEvent={onNeedEvent}
            fromGroupId={groupId}
          />
        ) : m.messageType === 'LINK' && m.linkTargetType === 'POLL' && m.linkTargetId ? (
          <PollCard
            pollId={m.linkTargetId}
            mine={mine}
            meId={meId}
            cache={pollCache}
            onNeed={onNeedPoll}
            onVote={onVotePoll}
            onClose={onClosePoll}
          />
        ) : m.messageType === 'LINK' && m.linkTargetType === 'FILE' && m.linkTargetId ? (
          <FileLinkCard
            fileId={m.linkTargetId}
            caption={m.content}
            mine={mine}
            cache={fileCache}
            onNeedFile={onNeedFile}
            onOpen={onOpenFile}
          />
        ) : m.messageType === 'LINK' && m.linkTargetType === 'EXPERT_SESSION' && m.linkTargetId ? (
          <ExpertSessionLinkCard
            sessionId={m.linkTargetId}
            caption={m.content}
            mine={mine}
            fromGroupId={groupId}
          />
        ) : (
          <p className="leading-snug whitespace-pre-wrap break-words">{renderBubbleContent(m.content)}</p>
        )}
        {/* Clock time inline; full date reveals on hover (desktop) and on focus —
            tapping the time focuses it, so mobile/tablet get the same popup. No state. */}
        <button
          type="button"
          aria-label={formatDateTime(m.sentAt)}
          className={`group/ts relative block ms-auto w-fit text-end text-[10px] mt-1 leading-none cursor-default ${mine ? 'text-on-brand/70' : 'text-muted'}`}
        >
          <time dateTime={m.sentAt} dir="ltr">{formatClock(m.sentAt)}</time>
          <span
            aria-hidden="true"
            dir="ltr"
            className={`pointer-events-none absolute top-full end-0 mt-1 z-50 whitespace-nowrap rounded-md bg-gray-900 text-white px-2 py-1 text-[11px] font-normal shadow-themed group-hover/ts:opacity-100 group-focus/ts:opacity-100 transition-opacity ${showActions ? 'opacity-100' : 'opacity-0'}`}
          >
            {formatDateTime(m.sentAt)}
          </span>
        </button>
      </div>
      {!mine && actions}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: snippet rendered above a reply's main body, click-to-jump to parent.
// ---------------------------------------------------------------------------

interface QuotedPreviewProps {
  parent: ChatMessage | null
  parentId: string
  mine: boolean
  onClick: () => void
}

function QuotedPreview({ parent, parentId, mine, onClick }: QuotedPreviewProps) {
  const { t } = useTranslation()
  const border = mine ? 'border-white/40 bg-white/15' : 'border-primary-300 bg-surface-muted'
  const senderLabel = parent
    ? (parent.senderDisplayName
        ?? (parent.senderId ? `${parent.senderId.slice(0, 8)}…` : t('groups.chat.reply.unknownAuthor')))
    : t('groups.chat.reply.unknownAuthor')
  const snippet = parent
    ? snippetOf(parent)
    : t('groups.chat.reply.parentUnavailable', { id: parentId.slice(0, 8) })
  return (
    <button
      type="button"
      onClick={onClick}
      className={`w-full text-start border-s-4 rounded-md px-2 py-1 mb-1.5 text-xs transition-colors hover:opacity-90 ${border}`}
      title={t('groups.chat.reply.jumpTitle')}
    >
      <div className={`font-semibold text-[10px] truncate ${mine ? 'text-on-brand/80' : 'text-primary-700'}`}>{senderLabel}</div>
      <div className={`truncate ${mine ? 'text-on-brand/85' : 'text-secondary'}`}>{snippet}</div>
    </button>
  )
}

/** Small inline glyph + text, used by the reply/pin snippet previews. */
function snippetWith(Icon: typeof LinkIcon, label: ReactNode): ReactNode {
  return (
    <span className="inline-flex items-center gap-1 align-middle">
      <Icon className="w-3 h-3 shrink-0" />
      {label}
    </span>
  )
}

function snippetOf(m: ChatMessage): ReactNode {
  if (m.messageType === 'LINK') {
    const caption = (m.content || '').replace(/\s+/g, ' ').trim()
    const captionNodes = caption ? renderBubbleContent(caption) : null
    if (m.linkTargetType === 'CALENDAR_EVENT') return snippetWith(CalendarIcon, captionNodes ?? 'calendar event')
    if (m.linkTargetType === 'POLL') return snippetWith(PollIcon, captionNodes ?? 'poll')
    if (m.linkTargetType === 'FILE') return snippetWith(FileIcon, captionNodes ?? 'file')
    if (m.linkTargetType === 'EXPERT_SESSION') return snippetWith(CapIcon, captionNodes ?? 'expert session')
    return snippetWith(LinkIcon, captionNodes ?? 'link')
  }
  if (m.messageType === 'SYSTEM_JOIN') return snippetWith(UserPlusIcon, 'joined the Bubble')
  if (m.messageType === 'SYSTEM_LEAVE') return snippetWith(UserMinusIcon, 'left the Bubble')
  const text = (m.content || '').replace(/\s+/g, ' ').trim()
  return renderBubbleContent(text)
}

// ---------------------------------------------------------------------------
// Private: bar above the input that shows what you're replying to.
// ---------------------------------------------------------------------------

interface ReplyPreviewBarProps {
  parent: ChatMessage
  meId: string | null
  onCancel: () => void
}

// ---------------------------------------------------------------------------
// Private: collapsible strip at the top of the chat showing the most recent pin.
// ---------------------------------------------------------------------------

interface PinnedStripProps {
  pinned: ChatMessage[]
  onJumpTo: (id: string) => void
  onShowAll: () => void
}

function PinnedStrip({ pinned, onJumpTo, onShowAll }: PinnedStripProps) {
  const { t } = useTranslation()
  const top = pinned[0]
  if (!top) return null
  return (
    <div className="bg-amber-50 dark:bg-amber-900/20 border-b border-amber-200 dark:border-amber-700 px-4 py-2 flex items-center gap-3 text-xs">
      <PinIcon className="w-3.5 h-3.5 shrink-0 text-amber-500" aria-hidden />
      <button
        type="button"
        onClick={() => onJumpTo(top.id)}
        className="flex-1 text-start truncate hover:underline"
        title={t('groups.chat.pinned.jumpTitle')}
      >
        <span className="font-semibold me-2">{t('groups.chat.pinned.strip', { count: pinned.length })}</span>
        <span className="text-secondary">{snippetOf(top)}</span>
      </button>
      {pinned.length > 1 && (
        <button
          type="button"
          onClick={onShowAll}
          className="text-primary-700 hover:underline whitespace-nowrap"
        >
          {t('groups.chat.pinned.viewAll')}
        </button>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: modal listing every pinned message; click → jump, optional unpin.
// ---------------------------------------------------------------------------

interface PinnedListModalProps {
  pinned: ChatMessage[]
  onJumpTo: (id: string) => void
  onUnpin: (m: ChatMessage) => void
  onCancel: () => void
}

function PinnedListModal({ pinned, onJumpTo, onUnpin, onCancel }: PinnedListModalProps) {
  const { t } = useTranslation()
  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-3 tablet:p-4 animate-fade-in" onClick={onCancel}>
      <div
        className="bg-surface rounded-3xl shadow-bubble animate-pop-in w-full max-w-[32rem] max-h-[80vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center justify-between">
          <h3 className="font-semibold">{t('groups.chat.pinned.modalTitle')}</h3>
          <button onClick={onCancel} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>
        <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-2">
          {pinned.length === 0 && (
            <p className="text-sm text-muted">{t('groups.chat.pinned.empty')}</p>
          )}
          {pinned.map((m) => (
            <div key={m.id} className="border border-line rounded-2xl p-3 flex flex-col gap-1 bubble-pop">
              <button
                type="button"
                onClick={() => onJumpTo(m.id)}
                className="text-start"
              >
                <div className="font-mono text-[10px] text-muted mb-1" dir="ltr">
                  {m.senderId ? m.senderId.slice(0, 8) + '…' : '—'}
                </div>
                <div className="text-sm text-base whitespace-pre-wrap line-clamp-3">{snippetOf(m)}</div>
              </button>
              <div className="flex justify-end">
                <button
                  type="button"
                  onClick={() => onUnpin(m)}
                  className="text-xs text-muted hover:text-amber-600"
                >
                  {t('groups.chat.pin.unpin')}
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}

function ReplyPreviewBar({ parent, meId, onCancel }: ReplyPreviewBarProps) {
  const { t } = useTranslation()
  const authorLabel = parent.senderId === meId
    ? t('common.you')
    : parent.senderDisplayName
      ?? (parent.senderId ? `${parent.senderId.slice(0, 8)}…` : t('groups.chat.reply.unknownAuthor'))
  return (
    <div className="bg-surface-muted border-t border-line px-5 py-2 flex items-center gap-3 text-xs">
      <ReplyIcon className="w-4 h-4 shrink-0 text-muted rtl:-scale-x-100" />
      <div className="flex-1 min-w-0">
        <div className="font-semibold text-primary-700 truncate">
          {t('groups.chat.reply.replyingTo', { name: authorLabel })}
        </div>
        <div className="truncate text-secondary">{snippetOf(parent)}</div>
      </div>
      <button
        type="button"
        onClick={onCancel}
        aria-label={t('groups.chat.reply.cancel')}
        title={t('groups.chat.reply.cancel')}
        className="text-muted hover:text-secondary text-lg leading-none px-2"
      >
        ×
      </button>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: chat link card that lazy-loads a calendar event (or falls back).
// ---------------------------------------------------------------------------

interface CalendarLinkCardProps {
  eventId: string
  caption: string
  mine: boolean
  /** Shared lookup owned by the ChatPanel; identical event ids only fetch once across the room. */
  cache: Map<string, CalendarEvent | 'unavailable'>
  /** Asks the panel to fetch this event id if it isn't already cached or in-flight. */
  onNeedEvent: (id: string) => void
  /** The Bubble this card lives in — remembered when entering an expert session so
   *  the session room can juggle back to it. "" in an expert-session chat. */
  fromGroupId: string
}

function CalendarLinkCard({ eventId, caption, mine, cache, onNeedEvent, fromGroupId }: CalendarLinkCardProps) {
  const navigate = useNavigate()
  useEffect(() => { onNeedEvent(eventId) }, [eventId, onNeedEvent])

  const cached = cache.get(eventId)
  const unavailable = cached === 'unavailable'
  const event: CalendarEvent | null = cached && cached !== 'unavailable' ? cached : null

  const containerCls = mine
    ? 'border border-white/40 bg-white/25 backdrop-blur-sm'
    : 'border border-line bg-surface-muted'

  if (unavailable) {
    return (
      <div className={`rounded-lg p-2 text-xs inline-flex items-center gap-1.5 ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        <LinkIcon className="w-3.5 h-3.5 shrink-0" /> Link unavailable
      </div>
    )
  }
  if (!event) {
    return (
      <div className={`rounded-lg p-2 text-xs ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        Loading link…
      </div>
    )
  }

  async function handleEnterRoom(e: React.MouseEvent) {
    e.stopPropagation()
    try {
      const room = await getRoomForEvent(eventId)
      if (room.scope === 'EXPERT_SESSION' && room.expertSessionId) {
        navigate(`/sessions/${room.expertSessionId}`, { state: fromGroupId ? { fromGroupId } : undefined })
      } else {
        navigate(`/rooms/${room.id}`)
      }
    } catch (err) {
      console.warn('[ChatPanel] enter room failed', err)
    }
  }

  return (
    <div className={`rounded-lg p-2 flex flex-col gap-1 ${containerCls}`}>
      <div className="flex items-center gap-2 text-xs">
        <CalendarIcon className={`w-3.5 h-3.5 shrink-0 ${mine ? 'text-on-brand/85' : 'text-muted'}`} />
        <span className={`px-1.5 py-0.5 rounded-md text-[10px] ${TYPE_COLORS[event.eventType]}`}>
          {event.eventType.replace('_', ' ')}
        </span>
      </div>
      <p className={`text-xs ${mine ? 'text-on-brand/85' : 'text-secondary'}`}>
        {fmtRange(event.startsAt, event.endsAt)}
      </p>
      {event.description && (
        <p className={`text-xs ${mine ? 'text-on-brand/80' : 'text-secondary'}`}>{event.description}</p>
      )}
      {caption && (
        <p className={`text-xs italic ${mine ? 'text-on-brand/80' : 'text-muted'}`}>{caption}</p>
      )}
      {event.eventType === 'STUDY_SESSION' && (() => {
        const now = Date.now()
        const opensAt = new Date(event.startsAt).getTime() - 15 * 60_000
        const endsAtMs = new Date(event.endsAt).getTime()
        const baseCls = `mt-1 self-start text-[10px] px-2 py-1 rounded-md font-semibold`
        const enabledCls = mine
          ? 'bg-white/40 text-on-brand hover:bg-white/55'
          : 'bg-primary-500 text-white hover:bg-primary-600'
        const disabledCls = mine
          ? 'bg-white/15 text-on-brand/60 cursor-not-allowed'
          : 'bg-line/60 text-muted cursor-not-allowed'

        const videoBtn = `${baseCls} inline-flex items-center gap-1`
        if (now > endsAtMs) {
          return <button type="button" disabled className={`${videoBtn} ${disabledCls}`}><VideoIcon className="w-3 h-3" /> Ended</button>
        }
        if (now < opensAt) {
          const mins = Math.max(1, Math.ceil((opensAt - now) / 60_000))
          return <button type="button" disabled className={`${videoBtn} ${disabledCls}`}><VideoIcon className="w-3 h-3" /> Opens in {mins} min</button>
        }
        return (
          <button type="button" onClick={handleEnterRoom} className={`${videoBtn} ${enabledCls}`}>
            <VideoIcon className="w-3 h-3" /> Enter Room
          </button>
        )
      })()}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: chat link card that lazy-loads an expert session (or falls back).
// Mirrors CalendarLinkCard but resolves an ExpertSession (not a CalendarEvent)
// and routes the user to `/sessions/{id}` — the dedicated expert room page.
// ---------------------------------------------------------------------------

interface ExpertSessionLinkCardProps {
  sessionId: string
  caption: string
  mine: boolean
  /** The Bubble this card lives in — passed to the session room so it can juggle
   *  back to this Bubble. "" in an expert-session chat (no originating Bubble). */
  fromGroupId: string
}

function ExpertSessionLinkCard({ sessionId, caption, mine, fromGroupId }: ExpertSessionLinkCardProps) {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [session, setSession] = useState<ExpertSession | null>(null)
  const [unavailable, setUnavailable] = useState(false)

  useEffect(() => {
    let cancelled = false
    getExpertSession(sessionId)
      .then((s) => { if (!cancelled) setSession(s) })
      .catch(() => { if (!cancelled) setUnavailable(true) })
    return () => { cancelled = true }
  }, [sessionId])

  const containerCls = mine
    ? 'border border-white/40 bg-white/25 backdrop-blur-sm'
    : 'border border-line bg-surface-muted'

  if (unavailable) {
    return (
      <div className={`rounded-lg p-2 text-xs inline-flex items-center gap-1.5 ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        <LinkIcon className="w-3.5 h-3.5 shrink-0" /> {t('expertRoom.linkCard.unavailable')}
      </div>
    )
  }
  if (!session) {
    return (
      <div className={`rounded-lg p-2 text-xs ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        {t('expertRoom.linkCard.loading')}
      </div>
    )
  }

  const baseCls = `mt-1 self-start text-[10px] px-2 py-1 rounded-md font-semibold`
  const enabledCls = mine
    ? 'bg-white/40 text-on-brand hover:bg-white/55'
    : 'bg-primary-500 text-white hover:bg-primary-600'

  return (
    <div className={`rounded-lg p-2 flex flex-col gap-1 ${containerCls}`}>
      <div className="flex items-center gap-2 text-xs">
        <CapIcon className={`w-3.5 h-3.5 shrink-0 ${mine ? 'text-on-brand/85' : 'text-muted'}`} />
        <span className={`px-1.5 py-0.5 rounded-md text-[10px] ${TYPE_COLORS.EXPERT_SESSION}`}>
          EXPERT SESSION
        </span>
      </div>
      <p className={`text-xs font-medium ${mine ? 'text-on-brand' : 'text-base'}`}>{session.title}</p>
      {session.startsAt && session.endsAt && (
        <p className={`text-xs ${mine ? 'text-on-brand/85' : 'text-secondary'}`}>
          {fmtRange(session.startsAt, session.endsAt)}
        </p>
      )}
      {caption && (
        <p className={`text-xs italic ${mine ? 'text-on-brand/80' : 'text-muted'}`}>{caption}</p>
      )}
      <button
        type="button"
        onClick={(e) => { e.stopPropagation(); navigate(`/sessions/${session.id}`, { state: fromGroupId ? { fromGroupId } : undefined }) }}
        className={`${baseCls} inline-flex items-center gap-1 ${enabledCls}`}
      >
        <CapIcon className="w-3 h-3" /> {t('expertRoom.linkCard.enterButton')}
      </button>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: chat link card that lazy-loads a group file (or falls back).
// ---------------------------------------------------------------------------

interface FileLinkCardProps {
  fileId: string
  caption: string
  mine: boolean
  cache: Map<string, GroupFile | 'unavailable'>
  onNeedFile: (id: string) => void
  /**
   * Called when the user clicks the card. ChatPanel forwards it up to GroupsPage,
   * which focuses the Files tile and routes the FilesPanel to the file.
   */
  onOpen?: (fileId: string) => void
}

function FileLinkCard({ fileId, caption, mine, cache, onNeedFile, onOpen }: FileLinkCardProps) {
  useEffect(() => { onNeedFile(fileId) }, [fileId, onNeedFile])

  const cached = cache.get(fileId)
  const unavailable = cached === 'unavailable'
  const file: GroupFile | null = cached && cached !== 'unavailable' ? cached : null

  const containerCls = mine
    ? 'border border-white/40 bg-white/25 backdrop-blur-sm'
    : 'border border-line bg-surface-muted'

  if (unavailable) {
    return (
      <div className={`rounded-lg p-2 text-xs inline-flex items-center gap-1.5 ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        <LinkIcon className="w-3.5 h-3.5 shrink-0" /> File unavailable
      </div>
    )
  }
  if (!file) {
    return (
      <div className={`rounded-lg p-2 text-xs ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        Loading file…
      </div>
    )
  }

  const interactive = !!onOpen
  return (
    <button
      type="button"
      onClick={interactive ? () => onOpen!(fileId) : undefined}
      disabled={!interactive}
      className={`text-start rounded-lg p-2 flex items-center gap-2 ${containerCls} ${
        interactive ? 'hover:brightness-105 cursor-pointer' : 'cursor-default'
      }`}
    >
      <span className="text-xl shrink-0" aria-hidden>{fileIcon(file.contentType)}</span>
      <div className="flex-1 min-w-0">
        <p className={`text-sm font-semibold truncate ${mine ? 'text-on-brand' : 'text-base'}`} title={file.originalName}>
          {file.originalName}
        </p>
        <p className={`text-[11px] ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
          {formatBytes(file.sizeBytes)}
        </p>
        {caption && (
          <p className={`text-xs italic mt-0.5 ${mine ? 'text-on-brand/80' : 'text-muted'}`}>{caption}</p>
        )}
      </div>
    </button>
  )
}

// ---------------------------------------------------------------------------
// Private: SYSTEM_GROUP_ROOM_OPEN renderer — the "your Bubble is live" card.
// Posted by the lifecycle scheduler when a GROUP room opens for joining. Mirrors
// the expert-session-open card but routes into the group's Bubble Room.
// ---------------------------------------------------------------------------

interface GroupRoomLiveCardProps {
  messageId: string
  roomId: string
  content: string
}

function GroupRoomLiveCard({ messageId, roomId, content }: GroupRoomLiveCardProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return (
    <div data-msgid={messageId} className="flex flex-col items-center gap-1 my-1">
      <p className="flex items-center justify-center gap-1.5 text-center text-xs text-muted italic">
        <VideoIcon className="w-3.5 h-3.5 shrink-0 text-bubble-green" /> {content}
      </p>
      <div className="w-full max-w-[20rem] rounded-lg p-2 flex flex-col gap-1 border border-line bg-surface-muted">
        <div className="flex items-center gap-2 text-xs">
          <VideoIcon className="w-3.5 h-3.5 shrink-0 text-bubble-green" />
          <span className="px-1.5 py-0.5 rounded-md text-[10px] bg-bubble-green-soft text-bubble-green font-semibold">
            {t('groups.chat.roomLiveBadge')}
          </span>
        </div>
        <button
          type="button"
          onClick={() => navigate(`/rooms/${roomId}`)}
          className="mt-1 self-start text-[10px] px-2 py-1 rounded-md font-semibold inline-flex items-center gap-1 bg-primary-500 text-white hover:bg-primary-600"
        >
          <VideoIcon className="w-3 h-3" /> {t('groups.chat.roomLiveEnter')}
        </button>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: modal picker — share a calendar event OR a group file to chat.
// Two tabs share the same shell (header / preview / caption / Cancel + Share);
// each tab owns its own state shape so the selection is well-typed.
// ---------------------------------------------------------------------------

interface LinkPickerModalProps {
  groupId: string
  onPick: (type: ChatLinkTargetType, id: string, caption?: string) => void
  onCancel: () => void
}

type PickerTab = 'calendar' | 'file'

function LinkPickerModal({ groupId, onPick, onCancel }: LinkPickerModalProps) {
  const { t } = useTranslation()
  const [tab, setTab] = useState<PickerTab>('calendar')
  const [caption, setCaption] = useState('')
  const [selectedEventId, setSelectedEventId] = useState<string | null>(null)
  const [selectedFileId, setSelectedFileId] = useState<string | null>(null)

  function handleShare() {
    if (tab === 'calendar' && selectedEventId) {
      onPick('CALENDAR_EVENT', selectedEventId, caption.trim() || undefined)
    } else if (tab === 'file' && selectedFileId) {
      onPick('FILE', selectedFileId, caption.trim() || undefined)
    }
  }

  const canShare =
    (tab === 'calendar' && !!selectedEventId) || (tab === 'file' && !!selectedFileId)

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-3 tablet:p-4 animate-fade-in" onClick={onCancel}>
      <div
        className="bg-surface rounded-3xl shadow-bubble animate-pop-in w-full max-w-[28rem] max-h-[80vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center justify-between">
          <h3 className="font-semibold">{t('groups.chat.modalTitle')}</h3>
          <button onClick={onCancel} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>
        <div className="px-3 pt-2 border-b border-line flex gap-1 shrink-0">
          {(['calendar', 'file'] as PickerTab[]).map((k) => {
            const active = tab === k
            return (
              <button
                key={k}
                type="button"
                onClick={() => setTab(k)}
                className={`px-3 py-1.5 text-xs font-semibold rounded-t-lg border-b-2 transition-colors ${
                  active
                    ? 'text-primary-600 border-primary-500'
                    : 'text-muted hover:text-base border-transparent'
                }`}
              >
                {k === 'calendar' ? t('groups.chat.linkPicker.calendarTab') : t('groups.chat.linkPicker.filesTab')}
              </button>
            )
          })}
        </div>
        {tab === 'calendar' ? (
          <CalendarEventList
            groupId={groupId}
            selectedId={selectedEventId}
            onSelect={setSelectedEventId}
          />
        ) : (
          <FilePickerList
            groupId={groupId}
            selectedId={selectedFileId}
            onSelect={setSelectedFileId}
          />
        )}
        <div className="px-4 py-3 border-t border-line flex flex-col gap-2">
          <input
            placeholder={t('groups.chat.captionPlaceholder')}
            value={caption}
            onChange={(e) => setCaption(e.target.value)}
            className="border border-line bg-surface rounded-full px-4 py-2 text-sm focus:outline-none focus:border-primary-400"
          />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={onCancel}>
              {t('common.cancel')}
            </Button>
            <Button variant="deep" size="sm" disabled={!canShare} onClick={handleShare}>
              {t('common.share')}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ----- Calendar tab body -----

interface CalendarEventListProps {
  groupId: string
  selectedId: string | null
  onSelect: (id: string) => void
}

function CalendarEventList({ groupId, selectedId, onSelect }: CalendarEventListProps) {
  const { t } = useTranslation()
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const from = new Date().toISOString()
    const to = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString()
    listEvents('GROUP', groupId, from, to)
      .then((evs) => setEvents(evs.slice().sort((a, b) => a.startsAt.localeCompare(b.startsAt))))
      .catch(() => setEvents([]))
      .finally(() => setLoading(false))
  }, [groupId])

  return (
    <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-2">
      {loading && <p className="text-sm text-muted">{t('groups.chat.modalLoading')}</p>}
      {!loading && events.length === 0 && (
        <p className="text-sm text-muted">{t('groups.chat.modalEmpty')}</p>
      )}
      {events.map((ev) => {
        const active = ev.id === selectedId
        return (
          <button
            key={ev.id}
            onClick={() => onSelect(ev.id)}
            className={`text-start border rounded-2xl p-3 transition-all bubble-pop ${
              active ? 'border-primary-400 bg-primary-50' : 'border-line hover:bg-surface-muted'
            }`}
          >
            <div className="flex items-center gap-2">
              <span className={`text-xs px-1.5 py-0.5 rounded-md ${TYPE_COLORS[ev.eventType]}`}>
                {ev.eventType.replace('_', ' ')}
              </span>
              <span className="text-xs text-secondary">{fmtRange(ev.startsAt, ev.endsAt)}</span>
            </div>
            {ev.description && (
              <p className="text-xs text-secondary mt-1 truncate">{ev.description}</p>
            )}
          </button>
        )
      })}
    </div>
  )
}

// ----- File tab body — folder-aware picker -----

interface FilePickerListProps {
  groupId: string
  selectedId: string | null
  onSelect: (id: string) => void
}

function FilePickerList({ groupId, selectedId, onSelect }: FilePickerListProps) {
  const { t } = useTranslation()
  const [folders, setFolders] = useState<GroupFolder[]>([])
  const [files, setFiles] = useState<GroupFile[]>([])
  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const folderById = useMemo(() => {
    const m = new Map<string, GroupFolder>()
    for (const f of folders) m.set(f.id, f)
    return m
  }, [folders])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    Promise.all([
      listFolders(groupId),
      getFiles(groupId, currentFolderId ? { folderId: currentFolderId } : { scope: 'root' }),
    ])
      .then(([fol, fil]) => {
        if (cancelled) return
        setFolders(fol)
        setFiles(fil)
      })
      .catch(() => {
        if (!cancelled) { setFolders([]); setFiles([]) }
      })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [groupId, currentFolderId])

  const breadcrumb: { id: string | null; name: string }[] = useMemo(() => {
    const chain: { id: string | null; name: string }[] = [{ id: null, name: t('groups.files.root') }]
    let cursor = currentFolderId
    const visited = new Set<string>()
    const stack: { id: string | null; name: string }[] = []
    while (cursor && !visited.has(cursor)) {
      visited.add(cursor)
      const f = folderById.get(cursor)
      if (!f) break
      stack.unshift({ id: f.id, name: f.name })
      cursor = f.parentId
    }
    return [...chain, ...stack]
  }, [currentFolderId, folderById, t])

  const subfolders = useMemo(
    () => folders.filter((f) => f.parentId === currentFolderId)
                 .sort((a, b) => a.name.localeCompare(b.name)),
    [folders, currentFolderId]
  )

  return (
    <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
      <nav className="px-3 pt-2 pb-1 flex flex-wrap items-center gap-1 text-xs text-secondary shrink-0">
        {breadcrumb.map((b, idx) => {
          const last = idx === breadcrumb.length - 1
          return (
            <span key={b.id ?? 'root'} className="flex items-center gap-1 min-w-0">
              {idx > 0 && <span className="text-muted">/</span>}
              {last ? (
                <span className="font-semibold text-base truncate" title={b.name}>{b.name}</span>
              ) : (
                <button
                  type="button"
                  onClick={() => setCurrentFolderId(b.id)}
                  className="hover:text-base hover:underline truncate"
                  title={b.name}
                >
                  {b.name}
                </button>
              )}
            </span>
          )
        })}
      </nav>
      <div className="flex-1 overflow-y-auto p-3 flex flex-col gap-2">
        {loading && <p className="text-sm text-muted">{t('groups.chat.modalLoading')}</p>}
        {!loading && subfolders.length === 0 && files.length === 0 && (
          <p className="text-sm text-muted">{t('groups.files.emptyFolder')}</p>
        )}
        {subfolders.map((folder) => (
          <button
            key={folder.id}
            type="button"
            onClick={() => { setCurrentFolderId(folder.id); onSelect('') /* clear selection while navigating */ }}
            className="text-start border border-line rounded-2xl p-2.5 flex items-center gap-2 hover:bg-surface-muted bubble-pop"
          >
            <FolderIcon className="w-5 h-5 shrink-0 text-muted" aria-hidden />
            <span className="text-sm truncate">{folder.name}</span>
          </button>
        ))}
        {files.map((f) => {
          const active = f.id === selectedId
          return (
            <button
              key={f.id}
              type="button"
              onClick={() => onSelect(f.id)}
              className={`text-start border rounded-2xl p-2.5 flex items-center gap-2 transition-all bubble-pop ${
                active ? 'border-primary-400 bg-primary-50' : 'border-line hover:bg-surface-muted'
              }`}
            >
              <span className="text-lg" aria-hidden>{fileIcon(f.contentType)}</span>
              <div className="flex-1 min-w-0">
                <p className="text-sm truncate" title={f.originalName}>{f.originalName}</p>
                <p className="text-xs text-muted">{formatBytes(f.sizeBytes)}</p>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Composer DOM helpers — the message input is a contentEditable div so emojis
// can render inline as you type. We serialize the DOM tree back to a plain
// string (with `:shortcode:` tokens) at send time; we never set children via
// React. React doesn't reconcile inside the editor.
// ---------------------------------------------------------------------------

function serializeEditor(root: HTMLElement): string {
  return serializeChildren(root)
}

function serializeChildren(el: HTMLElement): string {
  let out = ''
  el.childNodes.forEach((child) => {
    if (child.nodeType === Node.TEXT_NODE) {
      out += child.textContent ?? ''
      return
    }
    if (child.nodeType !== Node.ELEMENT_NODE) return
    const ce = child as HTMLElement
    const sc = ce.dataset.shortcode
    if (sc) {
      out += `:${sc}:`
      return
    }
    if (ce.tagName === 'BR') {
      out += '\n'
      return
    }
    if (ce.tagName === 'DIV') {
      if (out.length > 0) out += '\n'
      out += serializeChildren(ce)
      return
    }
    out += ce.textContent ?? ''
  })
  return out
}

function deserializeIntoEditor(root: HTMLElement, text: string) {
  root.innerHTML = ''
  BUBBLE_EMOJI_REGEX.lastIndex = 0
  let lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = BUBBLE_EMOJI_REGEX.exec(text)) !== null) {
    const [full, name] = match
    const def = BUBBLE_EMOJI_BY_SHORTCODE.get(name)
    if (!def) continue
    if (match.index > lastIndex) {
      appendTextWithBreaks(root, text.slice(lastIndex, match.index))
    }
    appendEmoji(root, def)
    lastIndex = match.index + full.length
  }
  if (lastIndex < text.length) {
    appendTextWithBreaks(root, text.slice(lastIndex))
  }
}

function appendTextWithBreaks(parent: HTMLElement, text: string) {
  const lines = text.split('\n')
  lines.forEach((line, i) => {
    if (i > 0) parent.appendChild(document.createElement('br'))
    if (line) parent.appendChild(document.createTextNode(line))
  })
}

function appendEmoji(parent: HTMLElement, def: BubbleEmojiDef) {
  const tmpl = document.createElement('template')
  tmpl.innerHTML = bubbleEmojiSpanHTML(def)
  const node = tmpl.content.firstChild
  if (node) parent.appendChild(node)
}

function insertEmojiAtSelection(editor: HTMLDivElement, def: BubbleEmojiDef) {
  editor.focus()
  const tmpl = document.createElement('template')
  tmpl.innerHTML = bubbleEmojiSpanHTML(def)
  const node = tmpl.content.firstChild
  if (!node) return
  const sel = window.getSelection()
  const inEditor =
    sel && sel.rangeCount > 0 && sel.anchorNode && editor.contains(sel.anchorNode)
  if (!inEditor) {
    editor.appendChild(node)
    const range = document.createRange()
    range.setStartAfter(node)
    range.collapse(true)
    sel?.removeAllRanges()
    sel?.addRange(range)
    return
  }
  const range = sel!.getRangeAt(0)
  range.deleteContents()
  range.insertNode(node)
  range.setStartAfter(node)
  range.collapse(true)
  sel!.removeAllRanges()
  sel!.addRange(range)
}

// ---------------------------------------------------------------------------
// Private: lightweight popover above the input — one button per bubble emoji.
// ---------------------------------------------------------------------------

interface BubbleEmojiPopoverProps {
  onPick: (shortcode: string) => void
  onClose: () => void
}

function BubbleEmojiPopover({ onPick, onClose }: BubbleEmojiPopoverProps) {
  const { t } = useTranslation()
  const ref = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    function onDocMouseDown(e: MouseEvent) {
      const node = ref.current
      if (!node) return
      // The trigger button lives in the same wrapping `.relative` div as the popover,
      // so a click on it bubbles up through that wrapper — walk up from the popover.
      const wrapper = node.parentElement
      if (wrapper && wrapper.contains(e.target as Node)) return
      onClose()
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('mousedown', onDocMouseDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDocMouseDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  return (
    <div
      ref={ref}
      role="dialog"
      aria-label={t('groups.chat.emoji.pickerTitle')}
      className="absolute bottom-full mb-2 start-0 z-30 bg-surface border border-line rounded-2xl shadow-bubble p-2 flex flex-wrap gap-1 max-w-[14rem] tablet:max-w-none tablet:flex-nowrap"
    >
      {BUBBLE_EMOJIS.map((def) => (
        <button
          key={def.shortcode}
          type="button"
          onMouseDown={(e) => e.preventDefault()}
          onClick={() => onPick(def.shortcode)}
          aria-label={t(def.nameKey)}
          title={t(def.nameKey)}
          className="p-1 rounded-xl hover:bg-surface-muted bubble-pop"
        >
          <BubbleEmoji def={def} className="w-7 h-7" />
        </button>
      ))}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: "share & more" popover above the input. Consolidates the share-a-link
// and create-a-poll actions that used to be separate icon buttons in the
// composer row (emoji stays standalone). Click-outside / Esc dismiss mirrors
// BubbleEmojiPopover.
// ---------------------------------------------------------------------------

interface ComposerMenuPopoverProps {
  onShareLink: () => void
  onCreatePoll: () => void
  onClose: () => void
}

function ComposerMenuPopover({ onShareLink, onCreatePoll, onClose }: ComposerMenuPopoverProps) {
  const { t } = useTranslation()
  const ref = useRef<HTMLDivElement | null>(null)

  useEffect(() => {
    function onDocMouseDown(e: MouseEvent) {
      const node = ref.current
      if (!node) return
      // The trigger button shares the wrapping `.relative` div with the popover,
      // so a click on it bubbles through that wrapper — walk up from the popover.
      const wrapper = node.parentElement
      if (wrapper && wrapper.contains(e.target as Node)) return
      onClose()
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('mousedown', onDocMouseDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDocMouseDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [onClose])

  return (
    <div
      ref={ref}
      role="menu"
      aria-label={t('groups.chat.menu.menuAria')}
      className="absolute bottom-full mb-2 start-0 z-30 bg-surface border border-line rounded-2xl shadow-bubble p-1.5 flex flex-col gap-1 min-w-[12rem]"
    >
      <button
        type="button"
        role="menuitem"
        onClick={onShareLink}
        className="flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-surface-muted text-sm text-start bubble-pop"
      >
        <LinkIcon className="w-4 h-4 shrink-0 text-muted" aria-hidden />
        <span>{t('groups.chat.menu.share')}</span>
      </button>
      <button
        type="button"
        role="menuitem"
        onClick={onCreatePoll}
        className="flex items-center gap-2.5 px-3 py-2 rounded-xl hover:bg-surface-muted text-sm text-start bubble-pop"
      >
        <PollIcon className="w-4 h-4 shrink-0 text-muted" aria-hidden />
        <span>{t('groups.chat.menu.poll')}</span>
      </button>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: poll card rendered inline in chat for LINK messages with linkTargetType=POLL.
// ---------------------------------------------------------------------------

interface PollCardProps {
  pollId: string
  mine: boolean
  meId: string | null
  cache: Map<string, Poll | 'unavailable'>
  onNeed: (id: string) => void
  onVote: (pollId: string, optionIds: string[]) => void
  onClose: (pollId: string) => void
}

function PollCard({ pollId, mine, meId, cache, onNeed, onVote, onClose }: PollCardProps) {
  const { t } = useTranslation()
  useEffect(() => { onNeed(pollId) }, [pollId, onNeed])

  /** Local draft of selected options; flushed to server on submit (multi) or click (single). */
  const [draft, setDraft] = useState<Set<string>>(new Set())

  const cached = cache.get(pollId)
  // Keep draft in sync with server when poll arrives or another tab votes.
  useEffect(() => {
    if (cached && cached !== 'unavailable') setDraft(new Set(cached.myVote))
  }, [cached])

  if (cached === 'unavailable') {
    return (
      <div className={`rounded-lg p-2 text-xs ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        {t('groups.chat.poll.pollUnavailable')}
      </div>
    )
  }
  if (!cached) {
    return (
      <div className={`rounded-lg p-2 text-xs ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        {t('groups.chat.poll.loadingPoll')}
      </div>
    )
  }

  const poll = cached
  const closed = !!poll.closedAt
  const total = poll.totalVotes
  const isCreator = meId && meId === poll.createdByUserId
  const containerCls = mine
    ? 'border border-white/40 bg-white/20'
    : 'border border-line bg-surface-muted'

  function toggle(optionId: string) {
    if (closed) return
    if (poll.allowMultiple) {
      const next = new Set(draft)
      if (next.has(optionId)) next.delete(optionId)
      else next.add(optionId)
      setDraft(next)
    } else {
      // single-choice: clicking commits immediately
      onVote(poll.id, [optionId])
    }
  }

  function submitMulti() {
    onVote(poll.id, [...draft])
  }

  const draftDirty = poll.allowMultiple && (
    draft.size !== poll.myVote.length ||
    [...draft].some((id) => !poll.myVote.includes(id))
  )

  return (
    <div className={`rounded-lg p-3 flex flex-col gap-2 ${containerCls}`}>
      <div className="flex items-center gap-2">
        <PollIcon className={`w-4 h-4 shrink-0 ${mine ? 'text-on-brand/85' : 'text-muted'}`} aria-hidden />
        <div className={`text-sm font-semibold ${mine ? 'text-on-brand' : 'text-base'}`}>{poll.question}</div>
      </div>
      {poll.allowMultiple && (
        <p className={`text-[10px] uppercase tracking-wide ${mine ? 'text-on-brand/70' : 'text-muted'}`}>
          {t('groups.chat.poll.multiSelect')}
        </p>
      )}
      <ul className="flex flex-col gap-1.5">
        {poll.options.map((opt) => {
          const selected = draft.has(opt.id)
          const ratio = total > 0 ? opt.voteCount / total : 0
          const youVoted = !!meId && opt.voters.includes(meId)
          return (
            <li key={opt.id}>
              <button
                type="button"
                onClick={() => toggle(opt.id)}
                disabled={closed}
                className={`relative w-full text-start border rounded-lg overflow-hidden transition-colors ${
                  selected ? 'border-primary-500' : 'border-line'
                } ${closed ? 'cursor-default' : 'hover:bg-surface'}`}
              >
                {/* Vote bar */}
                <div
                  aria-hidden
                  className={`absolute inset-y-0 start-0 ${mine ? 'bg-white/30' : 'bg-primary-100'}`}
                  style={{ width: `${ratio * 100}%` }}
                />
                <div className="relative px-2.5 py-1.5 flex items-center gap-2">
                  <span className={`text-xs ${selected ? 'text-primary-700 font-semibold' : ''}`}>
                    {selected ? '☑' : '☐'}
                  </span>
                  <span className={`flex-1 text-sm ${mine ? 'text-on-brand' : 'text-base'}`}>{opt.text}</span>
                  <span className={`text-xs ${mine ? 'text-on-brand/80' : 'text-muted'} whitespace-nowrap`}>
                    {opt.voteCount}{youVoted ? ' ✓' : ''}
                  </span>
                </div>
              </button>
              {opt.voters.length > 0 && (
                <div className={`flex flex-wrap gap-1 mt-1 ps-2 text-[10px] font-mono ${mine ? 'text-on-brand/70' : 'text-muted'}`} dir="ltr">
                  {opt.voters.slice(0, 6).map((u) => <span key={u}>{u.slice(0, 6)}</span>)}
                  {opt.voters.length > 6 && <span>+{opt.voters.length - 6}</span>}
                </div>
              )}
            </li>
          )
        })}
      </ul>
      <div className="flex items-center gap-3 text-xs">
        <span className={mine ? 'text-on-brand/80' : 'text-muted'}>
          {t('groups.chat.poll.totalVotes', { count: total })}
        </span>
        {closed && (
          <span className={`px-1.5 py-0.5 rounded-md ${mine ? 'bg-white/20 text-on-brand' : 'bg-surface text-secondary'}`}>
            {t('groups.chat.poll.closed')}
          </span>
        )}
        <div className="flex-1" />
        {poll.allowMultiple && !closed && draftDirty && (
          <button
            type="button"
            onClick={submitMulti}
            className={`px-2 py-0.5 rounded-md text-xs ${mine ? 'bg-white/30 text-on-brand' : 'bg-primary-500 text-white'}`}
          >
            {t('groups.chat.poll.submitVote')}
          </button>
        )}
        {isCreator && !closed && (
          <button
            type="button"
            onClick={() => onClose(poll.id)}
            className={`text-xs ${mine ? 'text-on-brand/80 hover:text-on-brand' : 'text-muted hover:text-secondary'}`}
          >
            {t('groups.chat.poll.closePoll')}
          </button>
        )}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Private: modal for creating a new poll. Question + 2..10 options + multi toggle.
// ---------------------------------------------------------------------------

interface PollComposerModalProps {
  onCreate: (question: string, options: string[], allowMultiple: boolean) => void
  onCancel: () => void
}

function PollComposerModal({ onCreate, onCancel }: PollComposerModalProps) {
  const { t } = useTranslation()
  const [question, setQuestion] = useState('')
  const [options, setOptions] = useState<string[]>(['', ''])
  const [allowMultiple, setAllowMultiple] = useState(true)

  function setOption(i: number, val: string) {
    setOptions((prev) => prev.map((o, idx) => (idx === i ? val : o)))
  }
  function addOption() {
    setOptions((prev) => (prev.length < 10 ? [...prev, ''] : prev))
  }
  function removeOption(i: number) {
    setOptions((prev) => (prev.length > 2 ? prev.filter((_, idx) => idx !== i) : prev))
  }

  const cleanedOptions = options.map((o) => o.trim()).filter((o) => o.length > 0)
  const valid = question.trim().length > 0 && cleanedOptions.length >= 2
    && new Set(cleanedOptions).size === cleanedOptions.length

  function submit() {
    if (!valid) return
    onCreate(question.trim(), cleanedOptions, allowMultiple)
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-3 tablet:p-4 animate-fade-in" onClick={onCancel}>
      <div
        className="bg-surface rounded-3xl shadow-bubble animate-pop-in w-full max-w-[32rem] max-h-[80vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center justify-between">
          <h3 className="font-semibold">{t('groups.chat.poll.composerTitle')}</h3>
          <button onClick={onCancel} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>
        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-3">
          <input
            placeholder={t('groups.chat.poll.questionPlaceholder')}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            maxLength={500}
            className="border border-line bg-surface rounded-full px-4 py-2 text-sm focus:outline-none focus:border-primary-400"
          />
          <div className="flex flex-col gap-2">
            {options.map((opt, i) => (
              <div key={i} className="flex gap-2 items-center">
                <input
                  placeholder={t('groups.chat.poll.optionPlaceholder', { n: i + 1 })}
                  value={opt}
                  onChange={(e) => setOption(i, e.target.value)}
                  maxLength={200}
                  className="flex-1 border border-line bg-surface rounded-full px-4 py-2 text-sm focus:outline-none focus:border-primary-400"
                />
                {options.length > 2 && (
                  <button
                    type="button"
                    onClick={() => removeOption(i)}
                    aria-label={t('groups.chat.poll.removeOption')}
                    className="text-muted hover:text-danger px-2"
                  >
                    ×
                  </button>
                )}
              </div>
            ))}
            {options.length < 10 && (
              <button
                type="button"
                onClick={addOption}
                className="self-start text-xs text-primary-700 hover:underline"
              >
                {t('groups.chat.poll.addOption')}
              </button>
            )}
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={allowMultiple}
              onChange={(e) => setAllowMultiple(e.target.checked)}
            />
            <span>{t('groups.chat.poll.allowMultiple')}</span>
          </label>
        </div>
        <div className="px-4 py-3 border-t border-line flex justify-end gap-2">
          <Button variant="ghost" size="sm" onClick={onCancel}>
            {t('common.cancel')}
          </Button>
          <Button variant="deep" size="sm" disabled={!valid} onClick={submit}>
            {t('groups.chat.poll.create')}
          </Button>
        </div>
      </div>
    </div>
  )
}
