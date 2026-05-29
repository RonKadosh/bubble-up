import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Group, getGroups } from '../api/groups'
import { ChatMessage, getMessages, getRooms } from '../api/chat'
import { CalendarEvent, CalendarEventType, listEvents } from '../api/calendar'
import { GroupFile, formatBytes, getFiles } from '../api/files'
import { useAuthStore } from '../store/authStore'
import { Avatar } from '../components/Avatar'
import { Card } from '../components/Card'
import { LinkButton } from '../components/Button'
import { renderBubbleContent } from '../components/BubbleEmojis'

function relTime(iso: string): string {
  const ms = Date.now() - new Date(iso).getTime()
  if (ms < 0) return 'just now'
  const s = Math.floor(ms / 1000)
  if (s < 60) return 'just now'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}h`
  const d = Math.floor(h / 24)
  if (d < 7) return `${d}d`
  return new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

const TYPE_BADGE: Record<CalendarEventType, string> = {
  STUDY_SESSION: 'bg-primary-100 text-primary-700',
  EXPERT_SESSION: 'bg-purple-100 text-purple-800 dark:bg-purple-900/40 dark:text-purple-200',
  MEETING: 'bg-primary-50 text-primary-700',
  DEADLINE: 'bg-danger-soft text-danger',
  EXAM: 'bg-amber-100 text-amber-800',
  ASSIGNMENT: 'bg-primary-200 text-primary-800',
  REMINDER: 'bg-surface-muted text-secondary',
  OTHER: 'bg-surface-muted text-secondary',
}

type FeedItem =
  | { kind: 'message'; ts: string; group: Group; message: ChatMessage }
  | { kind: 'system'; ts: string; group: Group; message: ChatMessage }
  | { kind: 'event'; ts: string; group: Group; event: CalendarEvent }
  | { kind: 'file'; ts: string; group: Group; file: GroupFile }

const GROUP_LIMIT = 8
const MSG_PER_ROOM = 15
const FEED_SIZE = 40

export default function DashboardPage() {
  const { t } = useTranslation()
  const user = useAuthStore((s) => s.user)
  const [items, setItems] = useState<FeedItem[]>([])
  const [groupsCount, setGroupsCount] = useState(0)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const [groups, rooms] = await Promise.all([getGroups(), getRooms()])
        if (cancelled) return
        setGroupsCount(groups.length)

        const groupById = new Map(groups.map((g) => [g.id, g] as const))
        const scopedRooms = rooms
          .filter((r) => r.groupId !== null && groupById.has(r.groupId))
          .slice(0, GROUP_LIMIT)
        const scopedGroups = groups.slice(0, GROUP_LIMIT)

        const from = new Date(Date.now() - 14 * 24 * 60 * 60 * 1000).toISOString()
        const to = new Date(Date.now() + 90 * 24 * 60 * 60 * 1000).toISOString()

        const [msgBuckets, fileBuckets, eventBuckets] = await Promise.all([
          Promise.all(scopedRooms.map((r) =>
            getMessages(r.id, { size: MSG_PER_ROOM })
              .then((msgs) => ({ room: r, msgs }))
              .catch(() => ({ room: r, msgs: [] as ChatMessage[] }))
          )),
          Promise.all(scopedGroups.map((g) =>
            getFiles(g.id)
              .then((files) => ({ group: g, files }))
              .catch(() => ({ group: g, files: [] as GroupFile[] }))
          )),
          Promise.all(scopedGroups.map((g) =>
            listEvents('GROUP', g.id, from, to)
              .then((events) => ({ group: g, events }))
              .catch(() => ({ group: g, events: [] as CalendarEvent[] }))
          )),
        ])
        if (cancelled) return

        const feed: FeedItem[] = []

        for (const bucket of msgBuckets) {
          if (!bucket.room.groupId) continue
          const group = groupById.get(bucket.room.groupId)
          if (!group) continue
          for (const m of bucket.msgs) {
            if (m.messageType === 'SYSTEM_JOIN' || m.messageType === 'SYSTEM_LEAVE') {
              feed.push({ kind: 'system', ts: m.sentAt, group, message: m })
            } else {
              feed.push({ kind: 'message', ts: m.sentAt, group, message: m })
            }
          }
        }
        for (const bucket of fileBuckets) {
          for (const f of bucket.files) {
            feed.push({ kind: 'file', ts: f.uploadedAt, group: bucket.group, file: f })
          }
        }
        for (const bucket of eventBuckets) {
          for (const ev of bucket.events) {
            feed.push({ kind: 'event', ts: ev.createdAt, group: bucket.group, event: ev })
          }
        }

        feed.sort((a, b) => b.ts.localeCompare(a.ts))
        setItems(feed.slice(0, FEED_SIZE))
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [user?.id])

  return (
    <div className="flex-1 overflow-y-auto">
      <div className="max-w-2xl mx-auto px-4 tablet:px-6 desktop:px-8 py-6 tablet:py-8">
        <header className="mb-6">
          <div className="flex items-center gap-3 mb-1">
            <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
            <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
            <h1 className="text-2xl font-bold text-base">{t('dashboard.title')}</h1>
          </div>
          <p className="text-sm text-muted ms-[1.6rem]">{t('dashboard.subtitle')}</p>
        </header>

        {loading && <FeedSkeleton />}

        {!loading && items.length === 0 && (
          <Card size="lg" className="px-6 py-14 text-center shadow-bubble">
            <div className="ring-iridescent p-[2px] rounded-full w-fit mx-auto mb-5 shadow-sm">
              <div className="w-20 h-20 rounded-full bg-brand-gradient-soft flex items-center justify-center text-3xl">
                🫧
              </div>
            </div>
            <p className="text-base font-semibold">{t('dashboard.emptyHeading')}</p>
            <p className="text-sm text-muted mt-1.5 mb-6">
              {groupsCount === 0 ? t('dashboard.emptyNoBubbles') : t('dashboard.emptyWaiting')}
            </p>
            <LinkButton href="/groups" size="md" className="shadow-bubble">
              {groupsCount === 0 ? t('dashboard.findBubble') : t('dashboard.openMyBubbles')}
            </LinkButton>
          </Card>
        )}

        {!loading && items.length > 0 && (
          <div className="flex flex-col gap-3">
            {items.map((item, i) => (
              <FeedCard key={feedKey(item, i)} item={item} meId={user?.id ?? null} />
            ))}
            <p className="text-center text-xs text-muted py-4">{t('dashboard.caughtUp')}</p>
          </div>
        )}
      </div>
    </div>
  )
}

function feedKey(item: FeedItem, i: number): string {
  switch (item.kind) {
    case 'message':
    case 'system': return `m:${item.message.id}`
    case 'event':  return `e:${item.event.id}`
    case 'file':   return `f:${item.file.id}`
    default:       return `i:${i}`
  }
}

function FeedCard({ item, meId }: { item: FeedItem; meId: string | null }) {
  const { group } = item
  return (
    <Link to="/groups" className="block">
      <Card size="lg" interactive className="p-4">
        <div className="flex gap-3">
          <Avatar id={group.id} name={group.name} size="lg" ring />
          <div className="flex-1 min-w-0">
            <div className="flex items-baseline gap-2 flex-wrap">
              <span className="font-semibold text-base truncate">{group.name}</span>
              <span className="text-muted text-xs">·</span>
              <span className="text-xs text-muted">{relTime(item.ts)}</span>
            </div>
            <FeedBody item={item} meId={meId} />
          </div>
        </div>
      </Card>
    </Link>
  )
}

function FeedBody({ item, meId }: { item: FeedItem; meId: string | null }) {
  const { t } = useTranslation()
  switch (item.kind) {
    case 'message': {
      const m = item.message
      const isMine = m.senderId === meId
      const author = isMine ? t('common.you') : (m.senderId?.slice(0, 8) ?? '?') + '…'
      if (m.messageType === 'LINK') {
        return (
          <>
            <p className="text-xs text-muted mt-0.5">
              <span className="font-mono" dir="ltr">{author}</span> {t('dashboard.kind.sharedEvent')}
            </p>
            {m.content && (
              <p className="text-sm text-secondary mt-1 italic">"{renderBubbleContent(m.content)}"</p>
            )}
            <div className="mt-2 inline-flex items-center gap-2 bg-primary-50 text-primary-700 text-xs px-2.5 py-1 rounded-lg border border-primary-100">
              🔗 {t('dashboard.linkedEvent')}
            </div>
          </>
        )
      }
      return (
        <>
          <p className="text-xs text-muted mt-0.5">
            <span className="font-mono" dir="ltr">{author}</span> {t('dashboard.kind.sentMessage')}
          </p>
          <p className="text-sm text-secondary mt-1.5 line-clamp-3 whitespace-pre-wrap">
            {renderBubbleContent(m.content)}
          </p>
        </>
      )
    }
    case 'system': {
      const m = item.message
      const who = m.content || m.subjectUserId?.slice(0, 8) || '?'
      const verb = m.messageType === 'SYSTEM_JOIN' ? t('dashboard.kind.joinedBubble') : t('dashboard.kind.leftBubble')
      const emoji = m.messageType === 'SYSTEM_JOIN' ? '🫧' : '👋'
      return (
        <p className="text-sm text-secondary mt-1">
          {emoji} <span className="font-mono" dir="ltr">{who}</span> {verb}
        </p>
      )
    }
    case 'event': {
      const ev = item.event
      return (
        <>
          <p className="text-xs text-muted mt-0.5">{t('dashboard.kind.newEventScheduled')}</p>
          <div className="mt-2 flex items-center gap-2 flex-wrap">
            <span className={`text-xs px-2 py-0.5 rounded-lg ${TYPE_BADGE[ev.eventType]}`}>
              {ev.eventType.replace('_', ' ')}
            </span>
            <span className="text-xs text-secondary">{fmtEventRange(ev.startsAt, ev.endsAt)}</span>
          </div>
          {ev.description && (
            <p className="text-sm text-secondary mt-1.5 line-clamp-2">{ev.description}</p>
          )}
        </>
      )
    }
    case 'file': {
      const f = item.file
      return (
        <>
          <p className="text-xs text-muted mt-0.5">{t('dashboard.kind.fileUploaded')}</p>
          <div className="mt-2 flex items-center gap-2 bg-surface-muted border border-line rounded-xl px-3 py-2">
            <span className="text-lg">📄</span>
            <span className="text-sm text-base truncate flex-1">{f.originalName}</span>
            <span className="text-xs text-muted shrink-0">{formatBytes(f.sizeBytes)}</span>
          </div>
        </>
      )
    }
  }
}

function fmtEventRange(start: string, end: string): string {
  const s = new Date(start)
  const e = new Date(end)
  const dateFmt: Intl.DateTimeFormatOptions = { weekday: 'short', month: 'short', day: 'numeric' }
  const timeFmt: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' }
  const sameDay = s.toDateString() === e.toDateString()
  if (sameDay) {
    return `${s.toLocaleDateString(undefined, dateFmt)} · ${s.toLocaleTimeString(undefined, timeFmt)} – ${e.toLocaleTimeString(undefined, timeFmt)}`
  }
  return `${s.toLocaleDateString(undefined, dateFmt)} – ${e.toLocaleDateString(undefined, dateFmt)}`
}

function FeedSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      {[0, 1, 2, 3].map((i) => (
        <Card key={i} size="lg" className="p-4 animate-pulse">
          <div className="flex gap-3">
            <div className="w-12 h-12 rounded-full bg-surface-muted shrink-0" />
            <div className="flex-1 space-y-2">
              <div className="h-3 bg-surface-muted rounded-full w-1/3" />
              <div className="h-3 bg-surface-muted rounded-full w-1/4" />
              <div className="h-3 bg-surface-muted rounded-full w-full mt-2" />
              <div className="h-3 bg-surface-muted rounded-full w-4/5" />
            </div>
          </div>
        </Card>
      ))}
    </div>
  )
}
