import { ReactNode, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Feed, FeedCta, FeedItem, FeedItemKind, FeedSectionKey, getFeed } from '../api/feed'
import { Avatar } from '../components/Avatar'
import { Card } from '../components/Card'
import { Button, LinkButton } from '../components/Button'

type Translate = (key: string, opts?: Record<string, unknown>) => string

// ---------------------------------------------------------------------------
// Section presentation
// ---------------------------------------------------------------------------

const SECTION_META: Record<FeedSectionKey, { icon: string; labelKey: string }> = {
  LIVE: { icon: '⚡', labelKey: 'dashboard.section.live' },
  UPCOMING: { icon: '📅', labelKey: 'dashboard.section.upcoming' },
  ACTIVITY: { icon: '👥', labelKey: 'dashboard.section.activity' },
  DISCOVERY: { icon: '🫧', labelKey: 'dashboard.section.discovery' },
}

// ---------------------------------------------------------------------------
// Per-kind renderers. Adding a new feed item kind = one entry here + one type
// in the FeedItemKind union (and a backend FeedSource). The card shell, avatar,
// and CTA routing are shared below.
// ---------------------------------------------------------------------------

interface Rendered {
  body: ReactNode
  /** When set (and the item carries a cta), a CTA button is shown with this label. */
  ctaLabel?: string
}

const ITEM_RENDERERS: Record<FeedItemKind, (item: FeedItem, t: Translate) => Rendered> = {
  liveSession: (item, t) => {
    const n = item.participantCount ?? 0
    return {
      body: (
        <>
          <p className="font-semibold text-base truncate">🎥 {item.title}</p>
          <p className="text-sm text-muted truncate">
            {n > 0 ? t('dashboard.live.roomParticipants', { count: n }) : liveLabel(item, t)}
            {item.groupName ? ` · ${item.groupName}` : ''}
          </p>
        </>
      ),
      ctaLabel: n > 0 ? t('dashboard.cta.hopIn') : t('dashboard.cta.joinSession'),
    }
  },

  liveGroupRoom: (item, t) => {
    const n = item.participantCount ?? 0
    return {
      body: (
        <>
          <p className="font-semibold text-base truncate">
            🟢 {n > 0 ? t('dashboard.live.roomParticipants', { count: n }) : t('dashboard.live.roomLiveNow')}
          </p>
          <p className="text-sm text-muted truncate">{item.groupName}</p>
        </>
      ),
      ctaLabel: n > 0 ? t('dashboard.cta.hopIn') : t('dashboard.cta.joinRoom'),
    }
  },

  upcomingEvent: (item, t) => ({
    body: (
      <>
        <p className="font-semibold text-base truncate">📅 {item.title || humanizeType(item.eventType)}</p>
        <p className="text-sm text-muted truncate">
          {fmtEventRange(item.startsAt, item.endsAt)}{item.groupName ? ` · ${item.groupName}` : ''}
        </p>
      </>
    ),
    ctaLabel: t('dashboard.cta.viewEvent'),
  }),

  memberJoin: (item, t) => ({
    body: (
      <p className="text-sm text-secondary">
        🫧 {t('dashboard.membership.joined', {
          name: item.subtitle || t('dashboard.membership.someone'),
          bubble: item.groupName,
        })}
      </p>
    ),
  }),

  memberLeave: (item, t) => ({
    body: (
      <p className="text-sm text-secondary">
        👋 {t('dashboard.membership.left', {
          name: item.subtitle || t('dashboard.membership.someone'),
          bubble: item.groupName,
        })}
      </p>
    ),
  }),

  unread: (item, t) => ({
    body: (
      <p className="text-sm text-secondary">
        💬 {t('dashboard.unread.count', { count: item.unreadCount ?? 0, bubble: item.groupName })}
      </p>
    ),
    ctaLabel: t('dashboard.cta.open'),
  }),

  file: (item, t) => ({
    body: (
      <p className="text-sm text-secondary truncate">
        📄 {t('dashboard.file.uploaded', { name: item.title, bubble: item.groupName })}
      </p>
    ),
  }),

  recommendation: (item, t) => ({
    body: (
      <>
        <p className="text-xs text-muted">{t('dashboard.discovery.recommended')}</p>
        <p className="font-semibold text-base truncate">🫧 {item.title}</p>
        <p className="text-sm text-muted truncate">{discoveryMeta(item, t)}</p>
      </>
    ),
    ctaLabel: t('dashboard.cta.viewBubble'),
  }),
}

export default function DashboardPage() {
  const { t } = useTranslation()
  const [feed, setFeed] = useState<Feed | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const data = await getFeed()
        if (!cancelled) setFeed(data)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [])

  const sections = feed?.sections ?? []

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

        {!loading && sections.length === 0 && (
          <Card size="lg" className="px-6 py-14 text-center shadow-bubble">
            <div className="ring-iridescent p-[2px] rounded-full w-fit mx-auto mb-5 shadow-sm">
              <div className="w-20 h-20 rounded-full bg-brand-gradient-soft flex items-center justify-center text-3xl">
                🫧
              </div>
            </div>
            <p className="text-base font-semibold">{t('dashboard.emptyHeading')}</p>
            <p className="text-sm text-muted mt-1.5 mb-6">{t('dashboard.emptyWaiting')}</p>
            <LinkButton href="/groups" size="md" className="shadow-bubble">
              {t('dashboard.findBubble')}
            </LinkButton>
          </Card>
        )}

        {!loading && sections.length > 0 && (
          <div className="flex flex-col gap-8">
            {sections.map((section) => (
              <section key={section.key}>
                <div className="flex items-center gap-2 mb-3 ms-1">
                  <span className="text-base">{SECTION_META[section.key].icon}</span>
                  <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">
                    {t(SECTION_META[section.key].labelKey)}
                  </h2>
                </div>
                <div className="flex flex-col gap-3">
                  {section.items.map((item, i) => (
                    <FeedItemCard key={`${section.key}-${i}-${item.cta?.targetId ?? item.groupId ?? ''}`} item={item} />
                  ))}
                </div>
              </section>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function FeedItemCard({ item }: { item: FeedItem }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const rendered = ITEM_RENDERERS[item.kind](item, t)
  const showCta = rendered.ctaLabel && item.cta

  return (
    <Card size="lg" className="p-4">
      <div className="flex items-center gap-3">
        {item.groupId && (
          <Avatar id={item.groupId} name={item.groupName ?? '?'} size="md" ring />
        )}
        <div className="flex-1 min-w-0">{rendered.body}</div>
        {showCta && (
          <Button
            size="sm"
            variant={isJoinCta(item.cta!) ? 'primary' : 'secondary'}
            className="shrink-0"
            onClick={() => navigate(routeForCta(item.cta!))}
          >
            {rendered.ctaLabel}
          </Button>
        )}
      </div>
    </Card>
  )
}

// ---------------------------------------------------------------------------
// CTA routing + small formatting helpers
// ---------------------------------------------------------------------------

function routeForCta(cta: FeedCta): string {
  switch (cta.type) {
    case 'JOIN_SESSION': return `/sessions/${cta.targetId}`
    case 'JOIN_ROOM':    return `/rooms/${cta.targetId}`
    // No /groups/:id deep link yet — the hub is sidebar-driven. Route generically.
    default:             return '/groups'
  }
}

function isJoinCta(cta: FeedCta): boolean {
  return cta.type === 'JOIN_SESSION' || cta.type === 'JOIN_ROOM' || cta.type === 'JOIN_BUBBLE'
}

function liveLabel(item: FeedItem, t: Translate): string {
  const mins = minutesUntil(item.startsAt)
  return mins <= 0 ? t('dashboard.live.liveNow') : t('dashboard.live.startsIn', { count: mins })
}

function discoveryMeta(item: FeedItem, t: Translate): string {
  const parts: string[] = []
  if (item.matchPercent != null) parts.push(t('dashboard.discovery.matchPercent', { percent: item.matchPercent }))
  if (item.memberCount != null) parts.push(t('dashboard.discovery.members', { count: item.memberCount }))
  return parts.join(' · ')
}

function humanizeType(type?: string): string {
  return type ? type.replace(/_/g, ' ') : ''
}

function minutesUntil(iso?: string): number {
  if (!iso) return 0
  return Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 60000))
}

function fmtEventRange(start?: string, end?: string): string {
  if (!start) return ''
  const s = new Date(start)
  const dateFmt: Intl.DateTimeFormatOptions = { weekday: 'short', month: 'short', day: 'numeric' }
  const timeFmt: Intl.DateTimeFormatOptions = { hour: '2-digit', minute: '2-digit' }
  if (!end) return `${s.toLocaleDateString(undefined, dateFmt)} · ${s.toLocaleTimeString(undefined, timeFmt)}`
  const e = new Date(end)
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
            <div className="w-11 h-11 rounded-full bg-surface-muted shrink-0" />
            <div className="flex-1 space-y-2">
              <div className="h-3 bg-surface-muted rounded-full w-1/3" />
              <div className="h-3 bg-surface-muted rounded-full w-2/3 mt-2" />
            </div>
          </div>
        </Card>
      ))}
    </div>
  )
}
