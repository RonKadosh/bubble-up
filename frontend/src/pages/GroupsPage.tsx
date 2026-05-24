import { useEffect, useMemo, useRef, useState } from 'react'
import {
  Group,
  GroupMember,
  Visibility,
  addMember,
  createGroup,
  deleteGroup,
  getGroups,
  getMembers,
  joinGroup,
  leaveGroup,
  removeMember,
  transferOwnership,
} from '../api/groups'
import {
  GroupFile,
  deleteFile,
  downloadFile,
  getFiles,
  uploadFile,
} from '../api/files'
import {
  ChatLinkTargetType,
  ChatMessage,
  ChatRoom,
  getMessages,
  getRooms,
  markRead,
  sendLinkMessage,
  sendTextMessage,
} from '../api/chat'
import { subscribeToRoom } from '../api/ws'
import {
  CalendarEvent,
  CalendarEventType,
  EVENT_TYPES,
  createEvent,
  deleteEvent,
  getEvent,
  listEvents,
  updateEvent,
} from '../api/calendar'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import { Avatar } from '../components/Avatar'
import { Button } from '../components/Button'

type HubTab = 'chat' | 'calendar' | 'files' | 'members'

export default function GroupsPage() {
  const { t } = useTranslation()
  const me = useAuthStore((s) => s.user)
  const [groups, setGroups] = useState<Group[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<HubTab>('chat')
  const [membersById, setMembersById] = useState<Record<string, GroupMember[]>>({})
  const [rooms, setRooms] = useState<ChatRoom[]>([])
  const [error, setError] = useState('')

  // Per-group unread count (Σ unreadCount over that group's rooms). Sidebar badges read this.
  const unreadByGroup = useMemo(() => {
    const acc: Record<string, number> = {}
    for (const r of rooms) acc[r.groupId] = (acc[r.groupId] ?? 0) + r.unreadCount
    return acc
  }, [rooms])

  async function refreshRooms() {
    try { setRooms(await getRooms()) } catch {/* sidebar badges will catch up on next refresh */}
  }

  // Create-group form (collapsible sidebar panel)
  const [showCreate, setShowCreate] = useState(false)
  const [newName, setNewName] = useState('')
  const [newDescription, setNewDescription] = useState('')
  const [newVisibility, setNewVisibility] = useState<Visibility>('PUBLIC')

  async function loadGroups() {
    try {
      setGroups(await getGroups())
    } catch {
      setError(t('groups.error.loadList'))
    }
  }

  useEffect(() => {
    loadGroups()
    refreshRooms()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Keep members cached for the selected group so role checks render correctly
  useEffect(() => {
    if (!selectedId || membersById[selectedId]) return
    getMembers(selectedId)
      .then((m) => setMembersById((prev) => ({ ...prev, [selectedId]: m })))
      .catch(() => {/* ignore — user may not be a member yet */})
  }, [selectedId, membersById])

  async function refreshMembers(groupId: string) {
    try {
      const m = await getMembers(groupId)
      setMembersById((prev) => ({ ...prev, [groupId]: m }))
    } catch {/* ignore */}
  }

  function selectGroup(id: string) {
    setSelectedId(id)
    setActiveTab('chat')
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    try {
      const created = await createGroup({
        name: newName,
        description: newDescription || undefined,
        visibility: newVisibility,
      })
      setNewName(''); setNewDescription(''); setNewVisibility('PUBLIC')
      setShowCreate(false)
      await loadGroups()
      refreshRooms()
      selectGroup(created.id)
    } catch {
      setError(t('groups.error.create'))
    }
  }

  async function handleJoin(groupId: string) {
    try {
      await joinGroup(groupId)
      await loadGroups()
      refreshMembers(groupId)
      refreshRooms()
    } catch {
      setError(t('groups.error.join'))
    }
  }

  async function handleLeave(groupId: string) {
    try {
      await leaveGroup(groupId)
      await loadGroups()
      refreshMembers(groupId)
      refreshRooms()
    } catch (e: any) {
      const code = e?.response?.data?.error?.code
      setError(code === 'OWNER_MUST_TRANSFER_OR_EMPTY'
        ? t('groups.error.leaveOwner')
        : t('groups.error.leave'))
    }
  }

  async function handleDelete(groupId: string) {
    if (!confirm(t('groups.confirm.popBubble'))) return
    try {
      await deleteGroup(groupId)
      setSelectedId(null)
      await loadGroups()
      refreshRooms()
    } catch (e: any) {
      const code = e?.response?.data?.error?.code
      setError(code === 'GROUP_NOT_EMPTY'
        ? t('groups.error.popNotEmpty')
        : t('groups.error.pop'))
    }
  }

  async function handleAddMember(groupId: string, userId: string) {
    try {
      await addMember(groupId, userId)
      await loadGroups()
      refreshMembers(groupId)
      refreshRooms()
    } catch (e: any) {
      const code = e?.response?.data?.error?.code
      setError(
        code === 'USER_NOT_FOUND' ? t('groups.error.addMissingUser')
        : code === 'ALREADY_GROUP_MEMBER' ? t('groups.error.addAlreadyMember')
        : t('groups.error.addGeneric'))
    }
  }

  async function handleRemoveMember(groupId: string, userId: string) {
    try {
      await removeMember(groupId, userId)
      await loadGroups()
      refreshMembers(groupId)
      refreshRooms()
    } catch {
      setError(t('groups.error.removeGeneric'))
    }
  }

  async function handleTransfer(groupId: string, newOwnerId: string) {
    if (!confirm(t('groups.confirm.transferOwnership'))) return
    try {
      await transferOwnership(groupId, newOwnerId)
      await loadGroups()
      refreshMembers(groupId)
    } catch {
      setError(t('groups.error.transferGeneric'))
    }
  }

  const selected = groups.find((g) => g.id === selectedId) ?? null
  const selectedMembers = selectedId ? membersById[selectedId] ?? [] : []
  const isOwner = !!selected && me?.id === selected.ownerId
  const isMember = isOwner || selectedMembers.some((m) => m.userId === me?.id)
  // Memoized so refreshRooms() doesn't churn a new object reference on every parent
  // re-render — ChatPanel/CalendarPanel see a stable `room` prop until id actually changes.
  const selectedRoom = useMemo(() => {
    if (!selectedId) return null
    return rooms
      .filter((r) => r.groupId === selectedId)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))[0] ?? null
  }, [rooms, selectedId])

  return (
    <div className="flex flex-1 overflow-hidden">
      {/* ---------------- Sidebar ---------------- */}
      <aside className="w-80 bg-surface border-e border-line flex flex-col">
          <div className="p-4 border-b border-line flex items-center justify-between">
            <h2 className="font-semibold text-base">{t('groups.sidebarTitle')}</h2>
            <Button
              variant={showCreate ? 'ghost' : 'primary'}
              size={showCreate ? 'xs' : 'sm'}
              onClick={() => setShowCreate((v) => !v)}
            >
              {showCreate ? t('common.cancel') : t('groups.newBubble')}
            </Button>
          </div>

          {showCreate && (
            <form onSubmit={handleCreate} className="p-3 border-b border-line flex flex-col gap-2 bg-surface-muted">
              <input
                placeholder={t('groups.createForm.bubbleName')}
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
                required
              />
              <input
                placeholder={t('groups.createForm.descriptionOptional')}
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
              />
              <div className="flex gap-3 text-xs">
                <label className="flex items-center gap-1">
                  <input
                    type="radio"
                    checked={newVisibility === 'PUBLIC'}
                    onChange={() => setNewVisibility('PUBLIC')}
                  />
                  {t('groups.createForm.public')}
                </label>
                <label className="flex items-center gap-1">
                  <input
                    type="radio"
                    checked={newVisibility === 'PRIVATE'}
                    onChange={() => setNewVisibility('PRIVATE')}
                  />
                  {t('groups.createForm.private')}
                </label>
              </div>
              <Button type="submit" size="sm" className="mt-1 w-full">
                {t('groups.createForm.submit')}
              </Button>
            </form>
          )}

          <div className="flex-1 overflow-y-auto">
            {groups.length === 0 && (
              <p className="p-4 text-sm text-muted">
                {t('groups.emptyList')} <span className="font-semibold">{t('groups.newBubble')}</span>.
              </p>
            )}
            {groups.map((g) => {
              const active = g.id === selectedId
              const youAreOwner = me?.id === g.ownerId
              const unread = unreadByGroup[g.id] ?? 0
              return (
                <button
                  key={g.id}
                  onClick={() => selectGroup(g.id)}
                  className={`group w-full flex items-center gap-3 px-3 py-2.5 my-1 mx-2 text-start rounded-2xl transition-all ${
                    active
                      ? 'bg-brand-gradient-strong text-on-brand shadow-themed'
                      : 'hover:bg-surface-muted text-base'
                  }`}
                >
                  <Avatar id={g.id} name={g.name} size="md" ring />
                  <div className="flex-1 min-w-0">
                    <p className="font-semibold truncate">{g.name}</p>
                    <p className={`text-xs truncate ${active ? 'text-on-brand/80' : 'text-muted'}`}>
                      {t('groups.memberLabel', { count: g.memberCount })}
                      {' · '}
                      {youAreOwner
                        ? t('groups.ownerBadge')
                        : g.visibility === 'PRIVATE'
                          ? t('groups.privateBadge')
                          : t('groups.publicBadge')}
                    </p>
                  </div>
                  {unread > 0 && !active && (
                    <span className="bg-brand-gradient-strong text-on-brand text-xs font-bold rounded-full min-w-[1.5rem] h-6 px-2 flex items-center justify-center shadow-sm">
                      {unread > 99 ? '99+' : unread}
                    </span>
                  )}
                </button>
              )
            })}
          </div>
        </aside>

        {/* ---------------- Main ---------------- */}
        <main className="flex-1 flex flex-col bg-base overflow-hidden">
          {error && (
            <div className="bg-danger-soft border-b border-line text-danger text-sm px-4 py-2 flex justify-between">
              <span>{error}</span>
              <button onClick={() => setError('')} className="text-danger">×</button>
            </div>
          )}

          {!selected ? (
            <div className="flex-1 flex items-center justify-center text-muted text-sm">
              {t('groups.pickFromSidebar')}
            </div>
          ) : (
            <>
              <header className="bg-surface border-b border-line px-6 pt-4">
                <div className="flex items-center justify-between mb-3 gap-3">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <h1 className="text-lg font-semibold truncate">{selected.name}</h1>
                      <span className={`text-xs px-2 py-0.5 rounded-md ${
                        selected.visibility === 'PUBLIC'
                          ? 'bg-primary-100 text-primary-700'
                          : 'bg-amber-100 text-amber-800'
                      }`}>
                        {selected.visibility === 'PUBLIC' ? t('groups.publicBadge') : t('groups.privateBadge')}
                      </span>
                    </div>
                    {selected.description && (
                      <p className="text-sm text-secondary mt-0.5 truncate">{selected.description}</p>
                    )}
                  </div>
                  <div className="flex gap-2 text-sm shrink-0 items-center">
                    {!isMember && selected.visibility === 'PUBLIC' && (
                      <Button onClick={() => handleJoin(selected.id)}>
                        {t('groups.header.hopIn')}
                      </Button>
                    )}
                    {isMember && !isOwner && (
                      <Button variant="secondary" size="xs" onClick={() => handleLeave(selected.id)}>
                        {t('groups.header.leave')}
                      </Button>
                    )}
                    {isOwner && (
                      <>
                        <Button
                          variant="secondary"
                          size="xs"
                          onClick={() => handleLeave(selected.id)}
                          title={t('groups.header.leaveOwnerTitle')}
                        >
                          {t('groups.header.leave')}
                        </Button>
                        <Button
                          variant="danger"
                          size="sm"
                          onClick={() => handleDelete(selected.id)}
                          title={t('groups.header.popTitle')}
                        >
                          {t('groups.header.pop')}
                        </Button>
                      </>
                    )}
                  </div>
                </div>
                <TabBar active={activeTab} onChange={setActiveTab} />
              </header>

              <section className="flex-1 overflow-hidden flex flex-col">
                {activeTab === 'chat' && (
                  <ChatPanel
                    key={selected.id}
                    groupId={selected.id}
                    room={selectedRoom}
                    meId={me?.id ?? null}
                    isMember={isMember}
                    onError={setError}
                    onUnreadChanged={refreshRooms}
                  />
                )}
                {activeTab === 'calendar' && (
                  <CalendarPanel
                    key={selected.id}
                    groupId={selected.id}
                    meId={me?.id ?? null}
                    isOwner={isOwner}
                    isMember={isMember}
                    chatRoomId={selectedRoom?.id ?? null}
                    onError={setError}
                    onShared={refreshRooms}
                  />
                )}
                {activeTab === 'files' && (
                  <FilesPanel
                    key={selected.id}
                    groupId={selected.id}
                    isOwner={isOwner}
                    meId={me?.id ?? null}
                    onError={setError}
                  />
                )}
                {activeTab === 'members' && (
                  <MembersPanel
                    members={selectedMembers}
                    isOwner={isOwner}
                    me={me?.id ?? null}
                    onAdd={(uid) => handleAddMember(selected.id, uid)}
                    onRemove={(uid) => handleRemoveMember(selected.id, uid)}
                    onTransfer={(uid) => handleTransfer(selected.id, uid)}
                  />
                )}
              </section>
            </>
          )}
      </main>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Tab bar
// ---------------------------------------------------------------------------

interface TabBarProps {
  active: HubTab
  onChange: (t: HubTab) => void
}

function TabBar({ active, onChange }: TabBarProps) {
  const { t } = useTranslation()
  const tabs: { key: HubTab; icon: string }[] = [
    { key: 'chat', icon: '💬' },
    { key: 'calendar', icon: '📅' },
    { key: 'files', icon: '📁' },
    { key: 'members', icon: '👥' },
  ]
  return (
    <div className="flex gap-1.5 text-sm bg-surface-muted p-1.5 rounded-full w-fit border border-line">
      {tabs.map((tab) => {
        const isActive = active === tab.key
        return (
          <button
            key={tab.key}
            onClick={() => onChange(tab.key)}
            className={`px-4 py-1.5 rounded-full flex items-center gap-1.5 transition-all bubble-pop ${
              isActive
                ? 'bg-brand-gradient-strong text-on-brand font-semibold shadow-sm'
                : 'text-muted hover:text-base'
            }`}
          >
            <span>{tab.icon}</span>
            {t(`groups.tabs.${tab.key}`)}
          </button>
        )
      })}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Members tab
// ---------------------------------------------------------------------------

interface MembersPanelProps {
  members: GroupMember[]
  isOwner: boolean
  me: string | null
  onAdd: (userId: string) => void
  onRemove: (userId: string) => void
  onTransfer: (userId: string) => void
}

function MembersPanel({ members, isOwner, me, onAdd, onRemove, onTransfer }: MembersPanelProps) {
  const { t } = useTranslation()
  const [newMemberId, setNewMemberId] = useState('')

  return (
    <div className="p-6 overflow-y-auto">
      <ul className="flex flex-col gap-1 text-sm max-w-2xl">
        {members.length === 0 && (
          <li className="text-muted">{t('groups.members.empty')}</li>
        )}
        {members.map((m) => (
          <li key={m.userId} className="flex justify-between items-center py-2 bg-surface rounded-xl px-3 border border-line">
            <span>
              <span className="font-mono text-xs text-muted" dir="ltr">{m.userId.slice(0, 8)}…</span>{' '}
              {m.userId === me && <span className="text-xs text-primary-600">{t('groups.members.youSuffix')}</span>}{' '}
              <span className={`text-xs px-2 py-0.5 rounded-md ms-1 ${
                m.role === 'OWNER' ? 'bg-primary-100 text-primary-700' : 'bg-surface-muted text-secondary'
              }`}>
                {m.role}
              </span>
            </span>
            {isOwner && m.userId !== me && (
              <div className="flex gap-2">
                <button onClick={() => onTransfer(m.userId)} className="text-primary-600 text-xs hover:underline">
                  {t('groups.members.makeOwner')}
                </button>
                <button onClick={() => onRemove(m.userId)} className="text-danger text-xs hover:underline">
                  {t('groups.members.remove')}
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
      {isOwner && (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            if (!newMemberId.trim()) return
            onAdd(newMemberId.trim())
            setNewMemberId('')
          }}
          className="flex gap-2 mt-4 max-w-2xl"
        >
          <input
            placeholder={t('groups.members.addPlaceholder')}
            value={newMemberId}
            onChange={(e) => setNewMemberId(e.target.value)}
            className="border border-line rounded-full px-4 py-2 text-sm flex-1 font-mono bg-surface focus:outline-none focus:border-primary-400"
            dir="ltr"
          />
          <Button type="submit" size="sm">
            {t('groups.members.addButton')}
          </Button>
        </form>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Chat tab
// ---------------------------------------------------------------------------

const PAGE_SIZE = 50

interface ChatPanelProps {
  groupId: string
  room: ChatRoom | null
  meId: string | null
  isMember: boolean
  onError: (msg: string) => void
  /** Called after the user marks-read or after a new message arrives — parent re-fetches rooms to refresh badges. */
  onUnreadChanged: () => void
}

function ChatPanel({ groupId, room, meId, isMember, onError, onUnreadChanged }: ChatPanelProps) {
  const { t } = useTranslation()
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [content, setContent] = useState('')
  const [hasMoreOlder, setHasMoreOlder] = useState(true)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [showLinkPicker, setShowLinkPicker] = useState(false)
  /** Snapshot taken when the room is first opened, used to render the "New messages" divider. Cleared on mark-read. */
  const [initialUnread, setInitialUnread] = useState(0)
  /** Resolved calendar-event lookup shared by every LINK card so duplicate ids fetch once. */
  const [eventCache, setEventCache] = useState<Map<string, CalendarEvent | 'unavailable'>>(new Map())
  const inflightEvents = useRef<Set<string>>(new Set())
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

  // Initial load + subscribe when room changes.
  useEffect(() => {
    if (!isMember || !roomId) return
    let cancelled = false
    let unsub: (() => void) | null = null
    setMessages([])
    setHasMoreOlder(true)
    setLoadingOlder(false)
    setInitialUnread(room?.unreadCount ?? 0)
    stickToBottomRef.current = true
    lastMarkedReadIdRef.current = null

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
    })()

    return () => {
      cancelled = true
      unsub?.()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roomId, isMember])

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
    setContent('')
    try {
      const msg = await sendTextMessage(roomId, text)
      dedupeAppend(msg)
      stickToBottomRef.current = true
      queueMicrotask(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
        markReadAndRefresh(msg.id)
      })
    } catch {
      onError(t('groups.error.send'))
      setContent(text)
    }
  }

  async function handlePickLink(linkTargetType: ChatLinkTargetType, linkTargetId: string, caption?: string) {
    setShowLinkPicker(false)
    if (!roomId) return
    try {
      const msg = await sendLinkMessage(roomId, linkTargetType, linkTargetId, caption)
      dedupeAppend(msg)
      stickToBottomRef.current = true
      queueMicrotask(() => {
        bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
        markReadAndRefresh(msg.id)
      })
    } catch {
      onError(t('groups.error.shareLink'))
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
      <div
        ref={scrollerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto p-5 flex flex-col gap-3"
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
              eventCache={eventCache}
              onNeedEvent={resolveEvent}
            />
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
      <form
        onSubmit={handleSend}
        className="bg-surface border-t border-line px-5 py-3 flex gap-2 items-center"
      >
        <button
          type="button"
          onClick={() => setShowLinkPicker(true)}
          className="text-lg text-muted hover:text-primary-600 w-10 h-10 flex items-center justify-center rounded-full hover:bg-surface-hover bubble-pop"
          aria-label={t('groups.chat.linkAria')}
          title={t('groups.chat.linkTitle')}
        >
          🔗
        </button>
        <input
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder={t('groups.chat.messagePlaceholder')}
          className="flex-1 border border-line bg-surface-muted text-base rounded-full px-5 py-2.5 text-sm focus:outline-none focus:border-primary-400"
        />
        <button
          type="submit"
          className="bg-brand-gradient-strong text-on-brand rounded-full w-12 h-12 flex items-center justify-center shadow-themed bubble-pop rtl:rotate-180"
          aria-label={t('groups.chat.sendAria')}
        >
          ➤
        </button>
      </form>
      {showLinkPicker && (
        <LinkPickerModal
          groupId={groupId}
          onPick={handlePickLink}
          onCancel={() => setShowLinkPicker(false)}
        />
      )}
    </div>
  )
}

interface ChatMessageRowProps {
  message: ChatMessage
  meId: string | null
  eventCache: Map<string, CalendarEvent | 'unavailable'>
  onNeedEvent: (id: string) => void
}

function ChatMessageRow({ message: m, meId, eventCache, onNeedEvent }: ChatMessageRowProps) {
  const { t } = useTranslation()
  if (m.messageType === 'SYSTEM_JOIN' || m.messageType === 'SYSTEM_LEAVE') {
    const phrase = m.messageType === 'SYSTEM_JOIN' ? t('dashboard.kind.joinedBubble') : t('dashboard.kind.leftBubble')
    const emoji = m.messageType === 'SYSTEM_JOIN' ? '🫧' : '👋'
    return (
      <p className="text-center text-xs text-muted italic">
        {emoji} <span className="font-mono" dir="ltr">{m.content || m.subjectUserId?.slice(0, 8) || '?'}</span>{' '}{phrase}
      </p>
    )
  }

  const mine = m.senderId === meId
  return (
    <div className={`flex ${mine ? 'justify-end' : 'justify-start'}`}>
      <div className={`max-w-[60%] px-4 py-2.5 rounded-3xl shadow-themed text-sm ${
        mine
          ? 'bg-brand-gradient-strong text-on-brand rounded-br-md'
          : 'bg-surface border border-line text-base rounded-bl-md'
      }`}>
        {!mine && m.senderId && (
          <p className="text-xs font-semibold text-primary-600 mb-1 font-mono">
            {m.senderId.slice(0, 8)}…
          </p>
        )}
        {m.messageType === 'LINK' && m.linkTargetType === 'CALENDAR_EVENT' && m.linkTargetId ? (
          <CalendarLinkCard
            eventId={m.linkTargetId}
            caption={m.content}
            mine={mine}
            cache={eventCache}
            onNeedEvent={onNeedEvent}
          />
        ) : (
          <p className="leading-snug whitespace-pre-wrap">{m.content}</p>
        )}
      </div>
    </div>
  )
}

interface CalendarLinkCardProps {
  eventId: string
  caption: string
  mine: boolean
  /** Shared lookup owned by the ChatPanel; identical event ids only fetch once across the room. */
  cache: Map<string, CalendarEvent | 'unavailable'>
  /** Asks the panel to fetch this event id if it isn't already cached or in-flight. */
  onNeedEvent: (id: string) => void
}

function CalendarLinkCard({ eventId, caption, mine, cache, onNeedEvent }: CalendarLinkCardProps) {
  useEffect(() => { onNeedEvent(eventId) }, [eventId, onNeedEvent])

  const cached = cache.get(eventId)
  const unavailable = cached === 'unavailable'
  const event: CalendarEvent | null = cached && cached !== 'unavailable' ? cached : null

  const containerCls = mine
    ? 'border border-white/40 bg-white/25 backdrop-blur-sm'
    : 'border border-line bg-surface-muted'

  if (unavailable) {
    return (
      <div className={`rounded-lg p-2 text-xs ${mine ? 'text-on-brand/80' : 'text-muted'}`}>
        🔗 Link unavailable
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
  return (
    <div className={`rounded-lg p-2 flex flex-col gap-1 ${containerCls}`}>
      <div className="flex items-center gap-2 text-xs">
        <span className={mine ? 'text-on-brand/85' : 'text-muted'}>📅</span>
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
    </div>
  )
}

interface LinkPickerModalProps {
  groupId: string
  onPick: (type: ChatLinkTargetType, id: string, caption?: string) => void
  onCancel: () => void
}

function LinkPickerModal({ groupId, onPick, onCancel }: LinkPickerModalProps) {
  const { t } = useTranslation()
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [loading, setLoading] = useState(true)
  const [caption, setCaption] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)

  useEffect(() => {
    const from = new Date().toISOString()
    const to = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString()
    listEvents('GROUP', groupId, from, to)
      .then((evs) => setEvents(evs.slice().sort((a, b) => a.startsAt.localeCompare(b.startsAt))))
      .catch(() => setEvents([]))
      .finally(() => setLoading(false))
  }, [groupId])

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onCancel}>
      <div
        className="bg-surface rounded-3xl shadow-bubble w-[28rem] max-h-[80vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center justify-between">
          <h3 className="font-semibold">{t('groups.chat.modalTitle')}</h3>
          <button onClick={onCancel} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>
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
                onClick={() => setSelectedId(ev.id)}
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
            <Button
              size="sm"
              disabled={!selectedId}
              onClick={() => selectedId && onPick('CALENDAR_EVENT', selectedId, caption.trim() || undefined)}
            >
              {t('common.share')}
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Calendar tab
// ---------------------------------------------------------------------------

const TYPE_COLORS: Record<CalendarEventType, string> = {
  STUDY_SESSION: 'bg-primary-100 text-primary-700',
  MEETING: 'bg-primary-50 text-primary-700',
  DEADLINE: 'bg-danger-soft text-danger',
  EXAM: 'bg-amber-100 text-amber-800',
  ASSIGNMENT: 'bg-primary-200 text-primary-800',
  REMINDER: 'bg-surface-muted text-secondary',
  OTHER: 'bg-surface-muted text-secondary',
}

function monthRange(year: number, month0: number): { from: Date; to: Date } {
  return {
    from: new Date(Date.UTC(year, month0, 1)),
    to: new Date(Date.UTC(year, month0 + 1, 1)),
  }
}

function fmtRange(start: string, end: string): string {
  const s = new Date(start)
  const e = new Date(end)
  const dateFmt: Intl.DateTimeFormatOptions = { weekday: 'short', month: 'short', day: 'numeric' }
  const timeFmt: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' }
  const sameDay = s.toDateString() === e.toDateString()
  if (sameDay) {
    return `${s.toLocaleDateString(undefined, dateFmt)} · ${s.toLocaleTimeString(undefined, timeFmt)} – ${e.toLocaleTimeString(undefined, timeFmt)}`
  }
  return `${s.toLocaleString(undefined, { ...dateFmt, ...timeFmt })} – ${e.toLocaleString(undefined, { ...dateFmt, ...timeFmt })}`
}

function toLocalInput(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fromLocalInput(local: string): string {
  return new Date(local).toISOString()
}

interface CalendarPanelProps {
  groupId: string
  meId: string | null
  isOwner: boolean
  /** Whether the current user is a member of this group. Non-members can't share to chat. */
  isMember: boolean
  /** Default chat room id for this group; null if not yet loaded. */
  chatRoomId: string | null
  onError: (msg: string) => void
  /** Called after a successful share so the parent can refresh rooms / unread counts. */
  onShared: () => void
}

function CalendarPanel({ groupId, meId, isOwner, isMember, chatRoomId, onError, onShared }: CalendarPanelProps) {
  const { t, i18n } = useTranslation()
  const now = new Date()
  const [year, setYear] = useState(now.getUTCFullYear())
  const [month, setMonth] = useState(now.getUTCMonth())
  const [events, setEvents] = useState<CalendarEvent[]>([])
  const [showForm, setShowForm] = useState(false)

  const [editingId, setEditingId] = useState<string | null>(null)
  const [eventType, setEventType] = useState<CalendarEventType>('STUDY_SESSION')
  const [description, setDescription] = useState('')
  const defaultStart = useMemo(() => toLocalInput(new Date().toISOString()), [])
  const defaultEnd = useMemo(() => {
    const d = new Date(Date.now() + 60 * 60 * 1000)
    return toLocalInput(d.toISOString())
  }, [])
  const [startsAt, setStartsAt] = useState(defaultStart)
  const [endsAt, setEndsAt] = useState(defaultEnd)

  async function reload() {
    const { from, to } = monthRange(year, month)
    try {
      setEvents(await listEvents('GROUP', groupId, from.toISOString(), to.toISOString()))
    } catch (e: any) {
      const code = e?.response?.data?.error?.code
      onError(code === 'NOT_GROUP_MEMBER' ? t('groups.error.notMember') : t('groups.error.loadEvents'))
      setEvents([])
    }
  }

  useEffect(() => {
    reload()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, year, month])

  function shiftMonth(delta: number) {
    let m = month + delta
    let y = year
    while (m < 0) { m += 12; y -= 1 }
    while (m > 11) { m -= 12; y += 1 }
    setYear(y); setMonth(m)
  }

  function resetForm() {
    setEditingId(null)
    setEventType('STUDY_SESSION')
    setDescription('')
    setStartsAt(defaultStart)
    setEndsAt(defaultEnd)
    setShowForm(false)
  }

  function startEdit(ev: CalendarEvent) {
    setEditingId(ev.id)
    setEventType(ev.eventType)
    setDescription(ev.description ?? '')
    setStartsAt(toLocalInput(ev.startsAt))
    setEndsAt(toLocalInput(ev.endsAt))
    setShowForm(true)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    try {
      if (editingId) {
        await updateEvent(editingId, {
          eventType,
          description: description || undefined,
          startsAt: fromLocalInput(startsAt),
          endsAt: fromLocalInput(endsAt),
        })
      } else {
        await createEvent({
          ownerType: 'GROUP',
          ownerId: groupId,
          eventType,
          description: description || undefined,
          startsAt: fromLocalInput(startsAt),
          endsAt: fromLocalInput(endsAt),
        })
      }
      resetForm()
      reload()
    } catch (e: any) {
      const code = e?.response?.data?.error?.code
      onError(
        code === 'INVALID_EVENT_TIME_RANGE' ? t('groups.error.invalidTimeRange')
        : code === 'NOT_GROUP_MEMBER' ? t('groups.error.notMember')
        : code === 'NOT_EVENT_AUTHOR_OR_OWNER' ? t('groups.error.notEventAuthor')
        : t('groups.error.saveEvent'))
    }
  }

  async function handleDelete(ev: CalendarEvent) {
    if (!confirm(t('groups.calendar.confirmDelete'))) return
    try {
      await deleteEvent(ev.id)
      if (editingId === ev.id) resetForm()
      reload()
    } catch (e: any) {
      const code = e?.response?.data?.error?.code
      onError(code === 'NOT_EVENT_AUTHOR_OR_OWNER'
        ? t('groups.error.notEventAuthorDelete')
        : t('groups.error.deleteEvent'))
    }
  }

  const canMutate = (ev: CalendarEvent) => meId === ev.createdBy || isOwner

  async function handleShareToChat(ev: CalendarEvent) {
    if (!chatRoomId) return
    try {
      await sendLinkMessage(chatRoomId, 'CALENDAR_EVENT', ev.id)
      onShared()
    } catch {
      onError(t('groups.error.shareToChat'))
    }
  }
  const monthLabel = new Date(Date.UTC(year, month, 1)).toLocaleDateString(i18n.language, {
    month: 'long', year: 'numeric', timeZone: 'UTC',
  })

  return (
    <div className="flex-1 flex flex-col overflow-hidden">
      <div className="flex items-center justify-between bg-surface border-b border-line px-5 py-2.5">
        {/* Arrows always point "back / forward in time" — flip in RTL so back-month is still on the inline-start side */}
        <button
          onClick={() => shiftMonth(-1)}
          className="w-9 h-9 hover:bg-surface-hover rounded-full bubble-pop text-base"
          aria-label="previous month"
        >‹</button>
        <span className="font-semibold text-sm text-base">{monthLabel}</span>
        <button
          onClick={() => shiftMonth(1)}
          className="w-9 h-9 hover:bg-surface-hover rounded-full bubble-pop text-base"
          aria-label="next month"
        >›</button>
      </div>

      <div className="flex-1 overflow-y-auto p-5 space-y-3">
        <Button
          variant={showForm ? 'ghost' : 'primary'}
          size={showForm ? 'xs' : 'md'}
          onClick={() => (showForm ? resetForm() : setShowForm(true))}
        >
          {showForm ? t('common.cancel') : t('groups.calendar.newEvent')}
        </Button>

        {showForm && (
          <form onSubmit={handleSubmit} className="bg-surface border border-line p-4 rounded-2xl shadow-themed flex flex-col gap-2">
            <p className="text-xs text-muted">{editingId ? t('groups.calendar.editEvent') : t('groups.calendar.newEventHeading')}</p>
            <select
              value={eventType}
              onChange={(e) => setEventType(e.target.value as CalendarEventType)}
              className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
            >
              {EVENT_TYPES.map((et) => (
                <option key={et} value={et}>{et.replace('_', ' ')}</option>
              ))}
            </select>
            <div className="flex gap-2 text-sm">
              <label className="flex flex-col flex-1">
                <span className="text-xs text-muted">{t('groups.calendar.start')}</span>
                <input
                  type="datetime-local"
                  value={startsAt}
                  onChange={(e) => setStartsAt(e.target.value)}
                  className="border border-line bg-surface rounded-xl px-3 py-2 focus:outline-none focus:border-primary-400"
                  required
                />
              </label>
              <label className="flex flex-col flex-1">
                <span className="text-xs text-muted">{t('groups.calendar.end')}</span>
                <input
                  type="datetime-local"
                  value={endsAt}
                  onChange={(e) => setEndsAt(e.target.value)}
                  className="border border-line bg-surface rounded-xl px-3 py-2 focus:outline-none focus:border-primary-400"
                  required
                />
              </label>
            </div>
            <textarea
              placeholder={t('groups.calendar.descriptionPlaceholder')}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="border border-line bg-surface rounded-xl px-3 py-2 text-sm focus:outline-none focus:border-primary-400"
              rows={2}
            />
            <Button type="submit" size="sm" className="self-start">
              {editingId ? t('common.save') : t('common.create')}
            </Button>
          </form>
        )}

        {events.length === 0 && !showForm && (
          <p className="text-sm text-muted">{t('groups.calendar.noEvents')}</p>
        )}

        <ul className="flex flex-col gap-2">
          {events.map((ev) => (
            <li key={ev.id} className="bg-surface border border-line rounded-2xl p-4 shadow-themed flex items-start gap-3">
              <span className="text-xl" aria-hidden>📅</span>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className={`text-xs px-2 py-0.5 rounded-md ${TYPE_COLORS[ev.eventType]}`}>
                    {ev.eventType.replace('_', ' ')}
                  </span>
                  <span className="text-sm text-base">{fmtRange(ev.startsAt, ev.endsAt)}</span>
                </div>
                {ev.description && <p className="text-sm text-secondary mt-1">{ev.description}</p>}
              </div>
              <div className="flex gap-2 text-xs shrink-0">
                {isMember && chatRoomId && (
                  <button
                    onClick={() => handleShareToChat(ev)}
                    className="text-primary-600 hover:underline"
                    title={t('groups.calendar.shareToChatTitle')}
                  >
                    {t('groups.calendar.shareToChat')}
                  </button>
                )}
                {canMutate(ev) && (
                  <>
                    <button onClick={() => startEdit(ev)} className="text-primary-600 hover:underline">{t('common.edit')}</button>
                    <button onClick={() => handleDelete(ev)} className="text-danger hover:underline">{t('common.delete')}</button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Files tab
// ---------------------------------------------------------------------------

interface FilesPanelProps {
  groupId: string
  isOwner: boolean
  meId: string | null
  onError: (msg: string) => void
}

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

function FilesPanel({ groupId, isOwner, meId, onError }: FilesPanelProps) {
  const { t } = useTranslation()
  const [files, setFiles] = useState<GroupFile[]>([])
  const [uploading, setUploading] = useState(false)

  async function load() {
    try {
      setFiles(await getFiles(groupId))
    } catch {
      onError(t('groups.error.loadFiles'))
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId])

  async function handleUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0]
    if (!f) return
    setUploading(true)
    try {
      await uploadFile(groupId, f)
      await load()
    } catch (err: any) {
      const code = err?.response?.data?.error?.code
      onError(
        code === 'FILE_TOO_LARGE' ? t('groups.error.fileTooLarge')
        : code === 'FILE_TYPE_BLOCKED' ? t('groups.error.fileBlocked')
        : code === 'NOT_GROUP_MEMBER' ? t('groups.error.uploadNotMember')
        : t('groups.error.uploadGeneric'))
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  async function handleDownload(f: GroupFile) {
    try {
      await downloadFile(groupId, f.id, f.originalName)
    } catch {
      onError(t('groups.error.downloadGeneric'))
    }
  }

  async function handleDelete(f: GroupFile) {
    if (!confirm(t('groups.files.confirmDelete', { name: f.originalName }))) return
    try {
      await deleteFile(groupId, f.id)
      await load()
    } catch (err: any) {
      const code = err?.response?.data?.error?.code
      onError(code === 'NOT_FILE_UPLOADER_OR_GROUP_OWNER'
        ? t('groups.error.deleteFileForbidden')
        : t('groups.error.deleteFileGeneric'))
    }
  }

  return (
    <div className="flex-1 overflow-y-auto p-5 space-y-3">
      <label className="inline-flex items-center gap-3">
        <span className={`px-5 py-2.5 rounded-full cursor-pointer text-on-brand text-sm font-semibold shadow-themed bubble-pop ${
          uploading ? 'bg-brand-gradient opacity-70' : 'bg-brand-gradient-strong'
        }`}>
          {uploading ? t('groups.files.uploading') : t('groups.files.uploadButton')}
          <input type="file" className="hidden" onChange={handleUpload} disabled={uploading} />
        </span>
        <span className="text-xs text-muted">{t('groups.files.uploadHint')}</span>
      </label>

      {files.length === 0 && (
        <p className="text-sm text-muted">{t('groups.files.empty')}</p>
      )}

      <ul className="flex flex-col gap-2">
        {files.map((f) => {
          const canDelete = f.uploaderId === meId || isOwner
          return (
            <li key={f.id} className="bg-surface border border-line rounded-2xl p-4 shadow-themed flex items-center gap-3 bubble-pop">
              <span className="text-xl text-primary-600" aria-hidden>📄</span>
              <button
                onClick={() => handleDownload(f)}
                className="text-primary-600 hover:underline text-sm text-start flex-1 truncate"
                title={t('groups.files.downloadTitle')}
              >
                {f.originalName}
              </button>
              <span className="text-xs text-muted">{formatBytes(f.sizeBytes)}</span>
              {canDelete && (
                <button onClick={() => handleDelete(f)} className="text-danger text-xs hover:underline">
                  {t('common.delete')}
                </button>
              )}
            </li>
          )
        })}
      </ul>
    </div>
  )
}
