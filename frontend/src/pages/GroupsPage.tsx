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
import { useOnboardingStore, isOnboarded } from '../store/onboardingStore'
import { useBentoLayoutStore, type BentoKey } from '../store/bentoLayoutStore'
import { useViewportStore } from '../store/viewportStore'
import { useActiveRoomStore } from '../store/activeRoomStore'
import { useToastStore } from '../store/toastStore'
import { BentoCell } from '../components/BentoCell'
import { BubbleLoader } from '../components/BubbleLoader'
import { GroupSidebar } from './groups/GroupSidebar'
import { GroupHeader } from './groups/GroupHeader'
import { MembersStrip } from './groups/MembersStrip'
import { BubbleInfoDrawer } from './groups/BubbleInfoDrawer'
import { FilesPanel } from './groups/FilesPanel'
import { CalendarPanel } from './groups/CalendarPanel'
import { ChatPanel } from './groups/ChatPanel'
import { HubFeed } from './groups/HubFeed'
import { ScheduleRoomModal } from './groups/ScheduleRoomModal'
import { CalendarEvent, listEvents } from '../api/calendar'
import { getLiveGroupIds, getRoomForEvent } from '../api/room'
import { useLocation, useNavigate } from 'react-router-dom'

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

/**
 * Pick the session that is joinable *now* from a calendar window. STUDY_SESSIONs
 * open 15 min before start; enrolled EXPERT_SESSIONs open 5 min before. Shared by
 * the steady 30s poll and the fast "preparing live" poll so both agree.
 */
function findOpenSession(events: CalendarEvent[], now: number): CalendarEvent | null {
  return events.find((e) => {
    const startsAtMs = new Date(e.startsAt).getTime()
    const endsAtMs = new Date(e.endsAt).getTime()
    if (e.eventType === 'STUDY_SESSION') return now >= startsAtMs - 15 * 60_000 && now <= endsAtMs
    if (e.eventType === 'EXPERT_SESSION') return now >= startsAtMs - 5 * 60_000 && now <= endsAtMs
    return false
  }) ?? null
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
        sectionClass: 'flex-1 min-h-0 p-3 grid gap-3 grid-cols-[2fr_1fr] grid-rows-2',
        chat:     { className: 'row-span-2' },
        calendar: { className: '' },
        files:    { className: '' },
      }
    case 'calendar':
      return {
        sectionClass: 'flex-1 min-h-0 p-3 grid gap-3 grid-cols-[1fr_2fr] grid-rows-2',
        chat:     { className: '' },
        files:    { className: '' },
        calendar: { className: 'row-span-2 col-start-2 row-start-1' },
      }
    case 'files':
      return {
        sectionClass: 'flex-1 min-h-0 p-3 grid gap-3 grid-cols-[1fr_2fr] grid-rows-2',
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
  const navigate = useNavigate()
  const location = useLocation()
  const me = useAuthStore((s) => s.user)
  // The hub doubles as Home: when no Bubble is selected, an onboarded user sees
  // the cross-Bubble activity feed. A not-yet-onboarded user (here only via the
  // wizard's L4 "create a Bubble" link-out) sees the lightweight placeholder.
  const onbStatus = useOnboardingStore((s) => s.status)
  const ensureOnboarding = useOnboardingStore((s) => s.ensureHydrated)
  useEffect(() => { ensureOnboarding() }, [ensureOnboarding])
  const onboarded = !!onbStatus && isOnboarded(onbStatus)
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
  /** True while a just-started Live Bubble is spinning up — drives the "preparing" overlay. */
  const [preparingLive, setPreparingLive] = useState(false)
  /** Open state for the Bubble Info drawer (member roster + management + leave/pop). */
  const [bubbleInfoOpen, setBubbleInfoOpen] = useState(false)
  /** Currently-live STUDY_SESSION event for the selected bubble (in the open window), or null. */
  const [liveSession, setLiveSession] = useState<CalendarEvent | null>(null)
  /** Group IDs that are live now (Bubble Room or expert session). Drives the sidebar red marker. */
  const [liveGroupIds, setLiveGroupIds] = useState<Set<string>>(new Set())
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

  // Close the Bubble Info drawer whenever the selected bubble changes.
  useEffect(() => { setBubbleInfoOpen(false) }, [selectedId])

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

  // Home: clicking the sidebar logo navigates here with `state.home`, which clears
  // the selection so the activity feed (the no-Bubble-selected state) shows even
  // when a Bubble was open. Clear the nav state so back/refresh doesn't re-trigger.
  const homeRequested = (location.state as { home?: boolean } | null)?.home ?? false
  useEffect(() => {
    if (!homeRequested) return
    setSelectedId(null)
    navigate('/groups', { replace: true, state: null })
  }, [homeRequested, navigate])

  // Deep-link from the dashboard: arriving with `state.selectGroupId` opens that
  // Bubble directly (e.g. clicking a Bubble-activity card, or joining a Bubble
  // from the discovery preview). Wait until the group is in our loaded list, then
  // select it and clear the state so a refresh/back doesn't re-trigger it.
  const requestedGroupId = (location.state as { selectGroupId?: string } | null)?.selectGroupId ?? null
  useEffect(() => {
    if (!requestedGroupId) return
    if (groups.some((g) => g.id === requestedGroupId)) {
      setSelectedId(requestedGroupId)
      navigate('/groups', { replace: true, state: null })
    }
  }, [requestedGroupId, groups, navigate])

  // Deep-link from the dashboard / a course page: open the create form, optionally
  // pre-targeted to a course (the empty-Discovery and empty-course "start a Bubble"
  // CTAs route here). Consumed once by GroupSidebar, then we clear the nav state.
  const createState = (location.state as
    { openCreate?: boolean; courseId?: string; deptId?: string } | null) ?? null
  const initialCreate = createState?.openCreate
    ? { open: true, courseId: createState.courseId, deptId: createState.deptId }
    : null

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
        setLiveSession(findOpenSession(events, now))
      } catch {
        // Transient — silent. The banner just won't show.
      }
    }
    tick()
    const interval = window.setInterval(tick, 30_000)
    return () => { cancelled = true; window.clearInterval(interval) }
  }, [selectedId, isMember])

  // Right after "Go Live", poll fast (1.5s) so the live banner appears promptly
  // instead of waiting up to 30s for the steady cadence. The "preparing" overlay
  // covers the gap. Gives up after 20s so it can never hang on a future session.
  useEffect(() => {
    if (!preparingLive) return
    if (!selectedId || !isMember) { setPreparingLive(false); return }
    const groupId = selectedId
    let cancelled = false
    const startedAt = Date.now()
    const tick = async () => {
      try {
        const now = Date.now()
        const from = new Date(now - 30 * 60_000).toISOString()
        const to = new Date(now + 60 * 60_000).toISOString()
        const events = await listEvents('GROUP', groupId, from, to)
        if (cancelled) return
        setLiveSession(findOpenSession(events, now))
      } catch {
        // Transient — keep retrying until detected or timed out.
      }
    }
    tick()
    const interval = window.setInterval(() => {
      if (Date.now() - startedAt > 20_000) setPreparingLive(false)
      else tick()
    }, 1500)
    return () => { cancelled = true; window.clearInterval(interval) }
  }, [preparingLive, selectedId, isMember])

  // Dismiss the "preparing" overlay the moment a live session is detected.
  useEffect(() => {
    if (preparingLive && liveSession) setPreparingLive(false)
  }, [preparingLive, liveSession])

  // Poll which of my bubbles are live now (Bubble Room or expert session) for the
  // sidebar red marker. One cheap call covers every group, so it's independent of
  // the selected bubble. 30s cadence matches the live-session banner poll.
  useEffect(() => {
    let cancelled = false
    const tick = async () => {
      try {
        const ids = await getLiveGroupIds()
        if (!cancelled) setLiveGroupIds(new Set(ids))
      } catch {
        // Transient — keep the last known set; the marker just won't update this tick.
      }
    }
    tick()
    const interval = window.setInterval(tick, 30_000)
    return () => { cancelled = true; window.clearInterval(interval) }
  }, [])

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

  async function handleCreate(input: { name: string; description?: string; visibility: Visibility; maxMembers: number; courseId: string }) {
    setError('')
    try {
      const created = await createGroup(input)
      await loadGroups()
      refreshRooms()
      setSelectedId(created.id)
    } catch (e) {
      setError(describeError(e, t,
        { NOT_ENROLLED_IN_COURSE: 'groups.error.notEnrolled' },
        'groups.error.create'))
    }
  }

  async function handleJoin(groupId: string) {
    try {
      await joinGroup(groupId)
      await loadGroups()
      refreshMembers(groupId)
      refreshRooms()
    } catch (e) {
      setError(describeError(e, t,
        { GROUP_IS_FULL: 'groups.error.full', NOT_ENROLLED_IN_COURSE: 'groups.error.notEnrolled' },
        'groups.error.join'))
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

  // Join the session that's live right now in the selected bubble. STUDY_SESSIONs
  // open a Bubble Room (/rooms/{id}); enrolled EXPERT_SESSIONs open at /sessions/{id}.
  async function handleJoinLive() {
    if (!liveSession) return
    try {
      const room = await getRoomForEvent(liveSession.id)
      if (room.scope === 'EXPERT_SESSION' && room.expertSessionId) {
        navigate(`/sessions/${room.expertSessionId}`, { state: selectedId ? { fromGroupId: selectedId } : undefined })
      } else {
        navigate(`/rooms/${room.id}`)
      }
    } catch (e) {
      setError(describeError(e, t,
        {
          ROOM_NOT_YET_OPEN: 'groups.error.roomNotYetOpen',
          EXPERT_SESSION_NOT_OPEN_FOR_JOIN_YET: 'groups.error.roomNotYetOpen',
          ROOM_ENDED: 'groups.error.roomEnded',
          NOT_GROUP_MEMBER: 'groups.error.notMember',
          FORBIDDEN: 'groups.error.notMember',
          JITSI_NOT_CONFIGURED: 'groups.error.jitsiNotConfigured',
        },
        'groups.error.openRoom'))
    }
  }

  async function handleAddMember(groupId: string, userId: string) {
    try {
      const member = await addMember(groupId, userId)
      await loadGroups()
      refreshMembers(groupId)
      refreshRooms()
      const name = member.displayName ?? `${userId.slice(0, 8)}…`
      useToastStore.getState().show(t('groups.toast.added', { name }), 'success', {
        id: userId, name, imageUrl: member.avatarUrl,
      })
    } catch (e) {
      setError(describeError(e, t,
        {
          USER_NOT_FOUND: 'groups.error.addMissingUser',
          ALREADY_GROUP_MEMBER: 'groups.error.addAlreadyMember',
          GROUP_IS_FULL: 'groups.error.full',
        },
        'groups.error.addGeneric'))
    }
  }

  async function handleRemoveMember(groupId: string, userId: string) {
    // Resolve the member before the row disappears from the cache (name + avatar for the toast).
    const removed = (membersById[groupId] ?? []).find((m) => m.userId === userId)
    const name = removed?.displayName ?? `${userId.slice(0, 8)}…`
    try {
      await removeMember(groupId, userId)
      await loadGroups()
      refreshMembers(groupId)
      refreshRooms()
      useToastStore.getState().show(t('groups.toast.removed', { name }), 'success', {
        id: userId, name, imageUrl: removed?.avatarUrl,
      })
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

  const phoneTabs: Array<{ key: typeof focused; label: string }> = [
    { key: 'chat',     label: t('groups.tabs.chat') },
    { key: 'calendar', label: t('groups.tabs.calendar') },
    { key: 'files',    label: t('groups.tabs.files') },
  ]

  return (
    <div className="flex flex-1 overflow-hidden relative">
      <main className="flex-1 flex flex-col bg-base overflow-hidden min-w-0">
        {error && (
          <div className="bg-danger-soft border-b border-line text-danger text-sm px-4 py-2 flex justify-between">
            <span>{error}</span>
            <button onClick={() => setError('')} className="text-danger">×</button>
          </div>
        )}

        {!selected ? (
          !onbStatus ? (
            // Onboarding status still loading — avoid flashing the placeholder
            // before we know whether to show the feed (onboarded) or not.
            <div className="flex-1 flex items-center justify-center">
              <BubbleLoader size={56} />
            </div>
          ) : onboarded ? (
            // Home: the cross-Bubble activity feed. Its "open this Bubble" CTAs
            // select in place (no navigation, since we're already in the hub).
            // The Bubble-list entry sits just below the feed header (HubFeed).
            <div className="flex-1 min-h-0">
              <HubFeed
                onSelectGroup={setSelectedId}
                onOpenCreate={() => navigate('/groups', { state: { openCreate: true } })}
                onOpenBubbleList={() => setMobileSidebarOpen(true)}
              />
            </div>
          ) : (
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
          )
        ) : (
          <>
            <GroupHeader
              group={selected}
              isOwner={isOwner}
              isMember={isMember}
              onJoin={() => handleJoin(selected.id)}
              liveSession={liveSession}
              onScheduleRoom={() => setScheduleRoomOpen(true)}
              onJoinLive={handleJoinLive}
              onOpenSidebar={() => { setBubbleInfoOpen(false); setMobileSidebarOpen(true) }}
              onOpenInfo={() => setBubbleInfoOpen((v) => !v)}
            />

            {/* Members strip is part of the desktop/tablet "group panel"; on phone
                it's folded into the Bubble Info drawer (opened from the header)
                so the chat keeps the screen. */}
            {!isPhone && (
              <MembersStrip
                members={selectedMembers}
                presence={presence}
                me={me?.id ?? null}
                isOwner={isOwner}
                onOpenInfo={() => setBubbleInfoOpen((v) => !v)}
              />
            )}

            <div className="relative flex-1 min-h-0 flex flex-col overflow-hidden">
            <BubbleInfoDrawer
              open={bubbleInfoOpen}
              group={selected}
              members={selectedMembers}
              presence={presence}
              me={me?.id ?? null}
              isOwner={isOwner}
              isMember={isMember}
              onClose={() => setBubbleInfoOpen(false)}
              onAdd={(uid) => handleAddMember(selected.id, uid)}
              onRemove={(uid) => handleRemoveMember(selected.id, uid)}
              onTransfer={(uid) => handleTransfer(selected.id, uid)}
              onLeave={() => handleLeave(selected.id)}
              onDelete={() => handleDelete(selected.id)}
              onUpdated={(g) => setGroups((prev) => prev.map((x) => (x.id === g.id ? g : x)))}
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
                        className={`flex-1 min-w-[5rem] flex items-center justify-center py-2.5 text-xs font-medium transition-colors ${
                          active
                            ? 'text-primary-600 border-b-2 border-primary-500'
                            : 'text-muted hover:text-base border-b-2 border-transparent'
                        }`}
                      >
                        <span>{tab.label}</span>
                      </button>
                    )
                  })}
                </nav>
                <section className="flex-1 min-h-0 p-2 flex">
                  <div className="ring-iridescent p-[1.5px] rounded-3xl flex-1 flex flex-col min-h-0 overflow-hidden shadow-themed">
                    <div className="flex-1 min-h-0 bg-surface rounded-[calc(1.75rem-1.5px)] flex flex-col overflow-hidden">
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
            </div>
          </>
        )}
      </main>

      {/* My Bubbles list — placed at the end (opposite the app's icon rail) so the
          hub's Home/group content starts flush with the rail, matching every other
          page's header alignment. On phone/tablet it's still a start-anchored drawer. */}
      <GroupSidebar
        groups={groups}
        selectedId={selectedId}
        meId={me?.id ?? null}
        unreadByGroup={unreadByGroup}
        liveGroupIds={liveGroupIds}
        onSelect={setSelectedId}
        onCreate={handleCreate}
        mobileOpen={mobileSidebarOpen}
        onMobileClose={() => setMobileSidebarOpen(false)}
        initialCreate={initialCreate}
        onInitialCreateConsumed={() => navigate('/groups', { replace: true, state: null })}
      />

      {scheduleRoomOpen && selected && (
        <ScheduleRoomModal
          groupId={selected.id}
          groupName={selected.name}
          onClose={() => setScheduleRoomOpen(false)}
          onScheduled={(opensNow) => { if (opensNow) setPreparingLive(true) }}
          onError={setError}
        />
      )}

      {preparingLive && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm animate-fade-in">
          <div className="flex flex-col items-center gap-5 rounded-3xl border border-line bg-surface px-10 py-8 shadow-bubble animate-pop-in">
            <BubbleLoader size={72} />
            <p className="text-sm font-medium text-base">{t('room.schedule.preparing')}</p>
          </div>
        </div>
      )}
    </div>
  )
}
