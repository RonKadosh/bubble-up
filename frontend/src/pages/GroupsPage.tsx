import { useEffect, useMemo, useState } from 'react'
import {
  Group,
  GroupMember,
  Visibility,
  addMember,
  createGroup,
  deleteGroup,
  getMyGroups,
  getMembers,
  joinGroup,
  leaveGroup,
  removeMember,
  transferOwnership,
} from '../api/groups'
import { ChatRoom, getRooms } from '../api/chat'
import { PresenceEntry, getPresence } from '../api/presence'
import { onWsConnect, subscribeToPresence } from '../api/ws'
import { describeError } from '../api/errors'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import { useBentoLayoutStore, type BentoKey } from '../store/bentoLayoutStore'
import { useViewportStore } from '../store/viewportStore'
import { useActiveRoomStore } from '../store/activeRoomStore'
import { BentoCell } from '../components/BentoCell'
import { GroupSidebar } from './groups/GroupSidebar'
import { GroupHeader } from './groups/GroupHeader'
import { MembersStrip } from './groups/MembersStrip'
import { ManageMembersModal } from './groups/ManageMembersModal'
import { FilesPanel } from './groups/FilesPanel'
import { CalendarPanel } from './groups/CalendarPanel'
import { ChatPanel } from './groups/ChatPanel'
import { ScheduleRoomModal } from './groups/ScheduleRoomModal'
import { LiveSessionBanner } from './groups/LiveSessionBanner'
import { CalendarEvent, listEvents } from '../api/calendar'

/**
 * Merge a fresh REST snapshot into the live presence map, keeping live deltas that
 * are newer than the snapshot. The race we're guarding: a live event can arrive
 * between snapshot request and snapshot resolve; replacing state outright would
 * clobber that fresher data.
 */
function mergePresenceSnapshot(
  prev: Record<string, PresenceEntry>,
  snapshot: PresenceEntry[],
): Record<string, PresenceEntry> {
  const next: Record<string, PresenceEntry> = {}
  for (const e of snapshot) next[e.userId] = e
  for (const [userId, p] of Object.entries(prev)) {
    const fromSnapshot = next[userId]
    if (!fromSnapshot) continue   // user no longer a member — drop
    const prevTime = p.lastSeenAt ? new Date(p.lastSeenAt).getTime() : 0
    const snapTime = fromSnapshot.lastSeenAt ? new Date(fromSnapshot.lastSeenAt).getTime() : 0
    if (prevTime > snapTime) next[userId] = p
  }
  return next
}

type CellPlacement = { className: string }
interface BentoLayout {
  sectionClass: string
  chat: CellPlacement
  calendar: CellPlacement
  files: CellPlacement
}

/**
 * Picks a grid template + per-cell placement classes based on which bento box
 * is currently the user-chosen focus. One layout per focus key — three total.
 * Single-focus model: exactly one cell is "big", the other two are compact.
 */
function getBentoLayout(focused: BentoKey): BentoLayout {
  switch (focused) {
    case 'chat':
      return {
        sectionClass: 'flex-1 min-h-0 p-3 grid gap-3 grid-cols-[2fr_1fr] grid-rows-2 bg-bento-grid',
        chat:     { className: 'row-span-2' },
        calendar: { className: '' },
        files:    { className: '' },
      }
    case 'calendar':
      return {
        sectionClass: 'flex-1 min-h-0 p-3 grid gap-3 grid-cols-[1fr_2fr] grid-rows-2 bg-bento-grid',
        chat:     { className: '' },
        files:    { className: '' },
        calendar: { className: 'row-span-2 col-start-2 row-start-1' },
      }
    case 'files':
      return {
        sectionClass: 'flex-1 min-h-0 p-3 grid gap-3 grid-cols-[1fr_2fr] grid-rows-2 bg-bento-grid',
        chat:     { className: '' },
        calendar: { className: '' },
        files:    { className: 'row-span-2 col-start-2 row-start-1' },
      }
  }
}

/**
 * The hub page. Owns:
 *  - `groups` (top-level list) + `selectedId`
 *  - `membersById` cache for role checks
 *  - `rooms` snapshot used to derive sidebar unread badges
 *  - all CRUD handlers (group + member mutations) — passed down to children
 *
 * Sub-features (chat / calendar / files / members) live as separate components
 * in `./groups/`. This file is intentionally a thin orchestrator.
 */
export default function GroupsPage() {
  const { t } = useTranslation()
  const me = useAuthStore((s) => s.user)
  const focused = useBentoLayoutStore((s) => s.focused)
  const setFocused = useBentoLayoutStore((s) => s.setFocused)
  const isPhone = useViewportStore((s) => s.tier === 'phone')
  const activeRoomGroupId = useActiveRoomStore((s) => s.groupId)
  const [groups, setGroups] = useState<Group[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [membersById, setMembersById] = useState<Record<string, GroupMember[]>>({})
  const [rooms, setRooms] = useState<ChatRoom[]>([])
  /** Per-userId presence for the *currently selected* group. Reset when selectedId changes. */
  const [presence, setPresence] = useState<Record<string, PresenceEntry>>({})
  const [error, setError] = useState('')
  /** Phone/tablet: drawer-open state for GroupSidebar. Always closed at desktop+. */
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)
  /** Open dialog state for "Schedule a Room". */
  const [scheduleRoomOpen, setScheduleRoomOpen] = useState(false)
  /** Open dialog state for "Manage members" (owner only). */
  const [manageMembersOpen, setManageMembersOpen] = useState(false)
  /** Currently-live STUDY_SESSION event for the selected bubble (in the open window), or null. */
  const [liveSession, setLiveSession] = useState<CalendarEvent | null>(null)
  /**
   * When a user clicks a FileLinkCard in chat, we focus the Files tile and hand
   * the file id down to FilesPanel so it can navigate + open the viewer. The
   * panel calls back to clear this once consumed.
   */
  const [pendingFileToOpen, setPendingFileToOpen] = useState<string | null>(null)

  function handleOpenFileFromChat(fileId: string) {
    setFocused('files')
    setPendingFileToOpen(fileId)
  }

  // Auto-pick the focused panel as the active tab on phone — picking a group
  // from the drawer should land you on Chat by default if nothing else was set.
  useEffect(() => {
    if (mobileSidebarOpen && selectedId) setMobileSidebarOpen(false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedId])

  // Per-group unread count (Σ unreadCount over that group's rooms). Sidebar badges read this.
  const unreadByGroup = useMemo(() => {
    const acc: Record<string, number> = {}
    for (const r of rooms) {
      if (!r.groupId) continue
      acc[r.groupId] = (acc[r.groupId] ?? 0) + r.unreadCount
    }
    return acc
  }, [rooms])

  const selected = groups.find((g) => g.id === selectedId) ?? null
  const selectedMembers = selectedId ? membersById[selectedId] ?? [] : []
  const isOwner = !!selected && me?.id === selected.ownerId
  const isMember = isOwner || selectedMembers.some((m) => m.userId === me?.id)

  async function refreshRooms() {
    try { setRooms(await getRooms()) } catch {/* sidebar badges will catch up on next refresh */}
  }

  async function loadGroups() {
    try {
      setGroups(await getMyGroups())
    } catch {
      setError(t('groups.error.loadList'))
    }
  }

  useEffect(() => {
    loadGroups()
    refreshRooms()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // If a Bubble Room is currently active and the user just landed on /groups
  // (typically via "Open Bubble" from the Room header), pre-select that
  // bubble so they don't have to re-find it in the sidebar. Only auto-selects
  // when nothing is selected and the bubble is in our loaded list.
  useEffect(() => {
    if (!activeRoomGroupId || selectedId) return
    if (groups.some((g) => g.id === activeRoomGroupId)) {
      setSelectedId(activeRoomGroupId)
    }
  }, [activeRoomGroupId, selectedId, groups])

  // Keep members cached for the selected group so role checks render correctly
  useEffect(() => {
    if (!selectedId || membersById[selectedId]) return
    getMembers(selectedId)
      .then((m) => setMembersById((prev) => ({ ...prev, [selectedId]: m })))
      .catch(() => {/* ignore — user may not be a member yet */})
  }, [selectedId, membersById])

  // Poll for a currently-joinable session in the selected bubble. Two kinds:
  //   - STUDY_SESSION (own GROUP event) — opens at startsAt - 15min.
  //   - EXPERT_SESSION (enrolled — surfaced via CalendarQueryService's merge) —
  //     opens at startsAt - 5min (chat + whiteboard; video opens at startsAt).
  // First match wins; both should never overlap in practice (the "one active
  // session per group at a time" rule blocks it server-side).
  // Skipped entirely for non-members: the calendar endpoint 403s for them and
  // there's no banner to show anyway.
  useEffect(() => {
    if (!selectedId || !isMember) { setLiveSession(null); return }
    const groupId = selectedId
    let cancelled = false
    const tick = async () => {
      try {
        const now = Date.now()
        const from = new Date(now - 30 * 60_000).toISOString()
        const to = new Date(now + 60 * 60_000).toISOString()
        const events = await listEvents('GROUP', groupId, from, to)
        if (cancelled) return
        const open = events.find((e) => {
          const startsAtMs = new Date(e.startsAt).getTime()
          const endsAtMs = new Date(e.endsAt).getTime()
          if (e.eventType === 'STUDY_SESSION') {
            return now >= startsAtMs - 15 * 60_000 && now <= endsAtMs
          }
          if (e.eventType === 'EXPERT_SESSION') {
            return now >= startsAtMs - 5 * 60_000 && now <= endsAtMs
          }
          return false
        }) ?? null
        setLiveSession(open)
      } catch {
        // Transient — silent. The banner just won't show.
      }
    }
    tick()
    const interval = window.setInterval(tick, 30_000)
    return () => { cancelled = true; window.clearInterval(interval) }
  }, [selectedId, isMember])

  // Presence: seed snapshot + live subscribe whenever the selected group changes.
  // Membership is enforced server-side (GET 403 + STOMP SUBSCRIBE rejected for non-members),
  // so a non-member just sees an empty presence map — that's fine.
  //
  // The snapshot is re-fetched on every WS (re)connect because the user's *own* session may
  // not be in SimpUserRegistry yet at the moment of the first snapshot — and the broadcast
  // that would correct that races ahead of our SUBSCRIBE frame, so we'd miss it without
  // the re-snapshot. Live deltas after that handle the rest. Snapshot is merged (not
  // replaced) so a live event that beat the snapshot home isn't clobbered by stale data.
  useEffect(() => {
    if (!selectedId) {
      setPresence({})
      return
    }
    let cancelled = false
    setPresence({})

    const groupId = selectedId
    const snapshot = () => {
      getPresence(groupId)
        .then((entries) => {
          if (cancelled) return
          setPresence((prev) => mergePresenceSnapshot(prev, entries))
        })
        .catch(() => {/* non-member or transient — silent */})
    }
    snapshot()
    const unsubWs = onWsConnect(snapshot)
    const unsubSub = subscribeToPresence(groupId, (e) => {
      setPresence((prev) => ({ ...prev, [e.userId]: e }))
    })
    return () => {
      cancelled = true
      unsubWs()
      unsubSub()
    }
  }, [selectedId])

  async function refreshMembers(groupId: string) {
    try {
      const m = await getMembers(groupId)
      setMembersById((prev) => ({ ...prev, [groupId]: m }))
    } catch {/* ignore */}
  }

  async function handleCreate(input: { name: string; description?: string; visibility: Visibility; courseId: string }) {
    setError('')
    try {
      const created = await createGroup(input)
      await loadGroups()
      refreshRooms()
      setSelectedId(created.id)
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
    } catch (e) {
      setError(describeError(e, t,
        { OWNER_MUST_TRANSFER_OR_EMPTY: 'groups.error.leaveOwner' },
        'groups.error.leave'))
    }
  }

  async function handleDelete(groupId: string) {
    if (!confirm(t('groups.confirm.popBubble'))) return
    try {
      await deleteGroup(groupId)
      setSelectedId(null)
      await loadGroups()
      refreshRooms()
    } catch (e) {
      setError(describeError(e, t,
        { GROUP_NOT_EMPTY: 'groups.error.popNotEmpty' },
        'groups.error.pop'))
    }
  }

  async function handleAddMember(groupId: string, userId: string) {
    try {
      await addMember(groupId, userId)
      await loadGroups()
      refreshMembers(groupId)
      refreshRooms()
    } catch (e) {
      setError(describeError(e, t,
        {
          USER_NOT_FOUND: 'groups.error.addMissingUser',
          ALREADY_GROUP_MEMBER: 'groups.error.addAlreadyMember',
        },
        'groups.error.addGeneric'))
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

  // Memoized so refreshRooms() doesn't churn a new object reference on every parent
  // re-render — ChatPanel/CalendarPanel see a stable `room` prop until id actually changes.
  const selectedRoom = useMemo(() => {
    if (!selectedId) return null
    return rooms
      .filter((r) => r.groupId === selectedId)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))[0] ?? null
  }, [rooms, selectedId])

  function renderPanel() {
    if (!selected) return null
    switch (focused) {
      case 'chat':
        return (
          <ChatPanel
            key={selected.id}
            groupId={selected.id}
            room={selectedRoom}
            meId={me?.id ?? null}
            isMember={isMember}
            onError={setError}
            onUnreadChanged={refreshRooms}
            onOpenFile={handleOpenFileFromChat}
            compact={false}
          />
        )
      case 'calendar':
        return (
          <CalendarPanel
            key={selected.id}
            groupId={selected.id}
            meId={me?.id ?? null}
            isOwner={isOwner}
            isMember={isMember}
            chatRoomId={selectedRoom?.id ?? null}
            onError={setError}
            onShared={refreshRooms}
            compact={false}
          />
        )
      case 'files':
        return (
          <FilesPanel
            key={selected.id}
            groupId={selected.id}
            isOwner={isOwner}
            isMember={isMember}
            meId={me?.id ?? null}
            onError={setError}
            compact={false}
            pendingOpenFileId={pendingFileToOpen}
            onPendingOpened={() => setPendingFileToOpen(null)}
          />
        )
    }
  }

  const phoneTabs: Array<{ key: typeof focused; icon: string; label: string }> = [
    { key: 'chat',     icon: '💬', label: t('groups.tabs.chat') },
    { key: 'calendar', icon: '📅', label: t('groups.tabs.calendar') },
    { key: 'files',    icon: '📁', label: t('groups.tabs.files') },
  ]

  return (
    <div className="flex flex-1 overflow-hidden relative">
      <GroupSidebar
        groups={groups}
        selectedId={selectedId}
        meId={me?.id ?? null}
        unreadByGroup={unreadByGroup}
        onSelect={setSelectedId}
        onCreate={handleCreate}
        mobileOpen={mobileSidebarOpen}
        onMobileClose={() => setMobileSidebarOpen(false)}
      />

      <main className="flex-1 flex flex-col bg-base overflow-hidden min-w-0">
        {error && (
          <div className="bg-danger-soft border-b border-line text-danger text-sm px-4 py-2 flex justify-between">
            <span>{error}</span>
            <button onClick={() => setError('')} className="text-danger">×</button>
          </div>
        )}

        {!selected ? (
          <div className="flex-1 flex flex-col items-center justify-center text-muted text-sm gap-3 px-6 text-center">
            <span>{t('groups.pickFromSidebar')}</span>
            <button
              type="button"
              onClick={() => setMobileSidebarOpen(true)}
              className="desktop:hidden bubble-pop rounded-full bg-brand-gradient-strong text-on-brand text-sm font-semibold px-5 py-2 shadow-themed"
            >
              {t('groups.openBubbleList')}
            </button>
          </div>
        ) : (
          <>
            <LiveSessionBanner liveSession={liveSession} onError={setError} />
            <GroupHeader
              group={selected}
              isOwner={isOwner}
              isMember={isMember}
              onJoin={() => handleJoin(selected.id)}
              onLeave={() => handleLeave(selected.id)}
              onDelete={() => handleDelete(selected.id)}
              onScheduleRoom={() => setScheduleRoomOpen(true)}
              onOpenSidebar={() => setMobileSidebarOpen(true)}
            />

            <MembersStrip
              members={selectedMembers}
              presence={presence}
              me={me?.id ?? null}
              isOwner={isOwner}
              onManage={() => setManageMembersOpen(true)}
            />

            {isPhone ? (
              <>
                <nav className="flex shrink-0 border-b border-line bg-surface overflow-x-auto">
                  {phoneTabs.map((tab) => {
                    const active = focused === tab.key
                    return (
                      <button
                        key={tab.key}
                        type="button"
                        onClick={() => setFocused(tab.key)}
                        aria-pressed={active}
                        className={`flex-1 min-w-[5rem] flex flex-col items-center justify-center gap-0.5 py-2 text-xs font-medium transition-colors ${
                          active
                            ? 'text-primary-600 border-b-2 border-primary-500'
                            : 'text-muted hover:text-base border-b-2 border-transparent'
                        }`}
                      >
                        <span className="text-base leading-none" aria-hidden="true">{tab.icon}</span>
                        <span>{tab.label}</span>
                      </button>
                    )
                  })}
                </nav>
                <section className="flex-1 min-h-0 p-2 bg-bento-grid flex">
                  <div className="ring-iridescent p-[1.5px] bento-cell-radius flex-1 flex flex-col min-h-0 overflow-hidden shadow-themed">
                    <div className="flex-1 min-h-0 bg-surface bento-cell-inner-radius flex flex-col overflow-hidden">
                      {renderPanel()}
                    </div>
                  </div>
                </section>
              </>
            ) : (
              (() => {
                const layout = getBentoLayout(focused)
                const promoteLabel = t('groups.bento.maximize')
                return (
                  <section className={layout.sectionClass}>
                    <BentoCell
                      icon="💬"
                      label={t('groups.tabs.chat')}
                      className={layout.chat.className}
                      isFocused={focused === 'chat'}
                      onFocus={() => setFocused('chat')}
                      promoteLabel={promoteLabel}
                    >
                      <ChatPanel
                        key={selected.id}
                        groupId={selected.id}
                        room={selectedRoom}
                        meId={me?.id ?? null}
                        isMember={isMember}
                        onError={setError}
                        onUnreadChanged={refreshRooms}
                        onOpenFile={handleOpenFileFromChat}
                        compact={focused !== 'chat'}
                      />
                    </BentoCell>
                    <BentoCell
                      icon="📅"
                      label={t('groups.tabs.calendar')}
                      className={layout.calendar.className}
                      isFocused={focused === 'calendar'}
                      onFocus={() => setFocused('calendar')}
                      promoteLabel={promoteLabel}
                    >
                      <CalendarPanel
                        key={selected.id}
                        groupId={selected.id}
                        meId={me?.id ?? null}
                        isOwner={isOwner}
                        isMember={isMember}
                        chatRoomId={selectedRoom?.id ?? null}
                        onError={setError}
                        onShared={refreshRooms}
                        compact={focused !== 'calendar'}
                      />
                    </BentoCell>
                    <BentoCell
                      icon="📁"
                      label={t('groups.tabs.files')}
                      className={layout.files.className}
                      isFocused={focused === 'files'}
                      onFocus={() => setFocused('files')}
                      promoteLabel={promoteLabel}
                    >
                      <FilesPanel
                        key={selected.id}
                        groupId={selected.id}
                        isOwner={isOwner}
                        isMember={isMember}
                        meId={me?.id ?? null}
                        onError={setError}
                        compact={focused !== 'files'}
                        pendingOpenFileId={pendingFileToOpen}
                        onPendingOpened={() => setPendingFileToOpen(null)}
                      />
                    </BentoCell>
                  </section>
                )
              })()
            )}
          </>
        )}
      </main>

      {scheduleRoomOpen && selected && (
        <ScheduleRoomModal
          groupId={selected.id}
          groupName={selected.name}
          onClose={() => setScheduleRoomOpen(false)}
          onScheduled={() => {/* polling effect will pick up the new room */}}
          onError={setError}
        />
      )}

      {manageMembersOpen && selected && isOwner && (
        <ManageMembersModal
          members={selectedMembers}
          presence={presence}
          me={me?.id ?? null}
          onAdd={(uid) => handleAddMember(selected.id, uid)}
          onRemove={(uid) => handleRemoveMember(selected.id, uid)}
          onTransfer={(uid) => handleTransfer(selected.id, uid)}
          onClose={() => setManageMembersOpen(false)}
        />
      )}
    </div>
  )
}
