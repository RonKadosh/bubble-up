import { ReactNode, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Feed, FeedCta, FeedItem, FeedItemKind, FeedSectionKey, getFeed } from '../api/feed'
import { Group, getGroup, joinGroup } from '../api/groups'
import { listMyCurrentEnrollments } from '../api/enrollment'
import { describeError } from '../api/errors'
import { Avatar } from '../components/Avatar'
import { Card } from '../components/Card'
import { Button } from '../components/Button'
import { BentoCell } from '../components/BentoCell'
import { useFeedLayoutStore } from '../store/feedLayoutStore'
import { useViewportStore } from '../store/viewportStore'

type Translate = (key: string, opts?: Record<string, unknown>) => string

// ---------------------------------------------------------------------------
// Section presentation. Sections render in this fixed order and are *always*
// shown — even when empty — so the dashboard layout stays stable and the user
// learns where each kind of update will surface. The backend omits empty
// sections from the payload; we fill the gaps with a per-section empty hint.
// ---------------------------------------------------------------------------

const SECTION_ORDER: FeedSectionKey[] = ['LIVE', 'UPCOMING', 'ACTIVITY', 'DISCOVERY']

const SECTION_META: Record<FeedSectionKey, { labelKey: string; emptyKey: string }> = {
  LIVE: { labelKey: 'dashboard.section.live', emptyKey: 'dashboard.empty.live' },
  UPCOMING: { labelKey: 'dashboard.section.upcoming', emptyKey: 'dashboard.empty.upcoming' },
  ACTIVITY: { labelKey: 'dashboard.section.activity', emptyKey: 'dashboard.empty.activity' },
  DISCOVERY: { labelKey: 'dashboard.section.discovery', emptyKey: 'dashboard.empty.discovery' },
}

// ---------------------------------------------------------------------------
// Bento layout. Mirrors GroupsPage's single-focus model, generalized to four
// cells: the focused section spans all three rows of one column; the other
// three stack in the opposite column (rows 1–3) in SECTION_ORDER order. The
// focus column flips left↔right so the maximized cell always reads as primary.
// ---------------------------------------------------------------------------

type FeedBentoLayout = {
  sectionClass: string
  cells: Record<FeedSectionKey, string>
}

// Literal class strings (not interpolated) so Tailwind's content scanner emits
// them — same reason GroupsPage's getBentoLayout spells each placement out.
// The maximized cell ALWAYS occupies the big left column; promoting another
// section just swaps which one sits there. The other three stack on the right
// in SECTION_ORDER (minus the focused one).
const FOCUS_LEFT = 'flex-1 min-h-0 p-3 grid gap-3 grid-rows-3 grid-cols-[2fr_1fr] bg-bento-grid'

const FEED_BENTO_LAYOUTS: Record<FeedSectionKey, FeedBentoLayout> = {
  LIVE: {
    sectionClass: FOCUS_LEFT,
    cells: {
      LIVE: 'col-start-1 row-start-1 row-span-3',
      UPCOMING: 'col-start-2 row-start-1',
      ACTIVITY: 'col-start-2 row-start-2',
      DISCOVERY: 'col-start-2 row-start-3',
    },
  },
  UPCOMING: {
    sectionClass: FOCUS_LEFT,
    cells: {
      UPCOMING: 'col-start-1 row-start-1 row-span-3',
      LIVE: 'col-start-2 row-start-1',
      ACTIVITY: 'col-start-2 row-start-2',
      DISCOVERY: 'col-start-2 row-start-3',
    },
  },
  ACTIVITY: {
    sectionClass: FOCUS_LEFT,
    cells: {
      ACTIVITY: 'col-start-1 row-start-1 row-span-3',
      LIVE: 'col-start-2 row-start-1',
      UPCOMING: 'col-start-2 row-start-2',
      DISCOVERY: 'col-start-2 row-start-3',
    },
  },
  DISCOVERY: {
    sectionClass: FOCUS_LEFT,
    cells: {
      DISCOVERY: 'col-start-1 row-start-1 row-span-3',
      LIVE: 'col-start-2 row-start-1',
      UPCOMING: 'col-start-2 row-start-2',
      ACTIVITY: 'col-start-2 row-start-3',
    },
  },
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
        <p className="text-xs text-muted">
          {t(item.displayMode === 'TRENDING'
            ? 'dashboard.discovery.trendingBadge'
            : 'dashboard.discovery.recommended')}
        </p>
        <p className="font-semibold text-base truncate">🫧 {item.title}</p>
        <p className="text-sm text-muted truncate">{discoveryMeta(item, t)}</p>
      </>
    ),
    ctaLabel: t('dashboard.cta.viewBubble'),
  }),
}

export default function DashboardPage() {
  const { t } = useTranslation()
  const focused = useFeedLayoutStore((s) => s.focused)
  const setFocused = useFeedLayoutStore((s) => s.setFocused)
  const isPhone = useViewportStore((s) => s.tier === 'phone')
  const [feed, setFeed] = useState<Feed | null>(null)
  const [loading, setLoading] = useState(true)
  /**
   * Current-term enrolment count. Lets the empty Discovery state tell apart
   * "you haven't enrolled in anything" (→ enroll) from "your courses just have
   * no Bubbles yet" (→ start one). Null while loading.
   */
  const [enrollmentCount, setEnrollmentCount] = useState<number | null>(null)
  /** When set, a non-member is previewing a discovery Bubble before joining. */
  const [preview, setPreview] = useState<FeedItem | null>(null)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const [data, enrollments] = await Promise.all([
          getFeed(),
          listMyCurrentEnrollments().catch(() => []),
        ])
        if (!cancelled) { setFeed(data); setEnrollmentCount(enrollments.length) }
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => { cancelled = true }
  }, [])

  const itemsByKey: Partial<Record<FeedSectionKey, FeedItem[]>> = {}
  for (const s of feed?.sections ?? []) itemsByKey[s.key] = s.items

  const layout = FEED_BENTO_LAYOUTS[focused]
  const promoteLabel = t('dashboard.bento.maximize')

  return (
    <div className="flex-1 min-h-0 flex flex-col">
      <header className="shrink-0 px-4 tablet:px-6 desktop:px-8 pt-6 tablet:pt-8 pb-4">
        <div className="flex items-center gap-3 mb-1">
          <div className="w-2.5 h-2.5 rounded-full bg-bubble-magenta shadow-sm" />
          <div className="w-1.5 h-1.5 rounded-full bg-bubble-green" />
          <h1 className="text-2xl font-bold text-base">{t('dashboard.title')}</h1>
        </div>
        <p className="text-sm text-muted ms-[1.6rem]">{t('dashboard.subtitle')}</p>
      </header>

      {loading ? (
        <FeedSkeleton />
      ) : isPhone ? (
        <>
          <nav className="flex shrink-0 border-b border-line bg-surface overflow-x-auto">
            {SECTION_ORDER.map((key) => {
              const active = focused === key
              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setFocused(key)}
                  aria-pressed={active}
                  className={`flex-1 min-w-[5rem] flex items-center justify-center py-2.5 text-xs font-medium transition-colors ${
                    active
                      ? 'text-primary-600 border-b-2 border-primary-500'
                      : 'text-muted hover:text-base border-b-2 border-transparent'
                  }`}
                >
                  <span>{t(SECTION_META[key].labelKey)}</span>
                </button>
              )
            })}
          </nav>
          <section className="flex-1 min-h-0 p-2 bg-bento-grid flex">
            <div className="ring-iridescent p-[1.5px] bento-cell-radius flex-1 flex flex-col min-h-0 overflow-hidden shadow-themed">
              <div className="flex-1 min-h-0 bg-surface bento-cell-inner-radius flex flex-col overflow-hidden">
                <SectionBody
                  items={itemsByKey[focused] ?? []}
                  emptyKey={SECTION_META[focused].emptyKey}
                  sectionKey={focused}
                  enrollmentCount={enrollmentCount}
                  onPreview={setPreview}
                />
              </div>
            </div>
          </section>
        </>
      ) : (
        <section className={layout.sectionClass}>
          {SECTION_ORDER.map((key) => (
            <BentoCell
              key={key}
              label={t(SECTION_META[key].labelKey)}
              className={layout.cells[key]}
              isFocused={focused === key}
              onFocus={() => setFocused(key)}
              promoteLabel={promoteLabel}
            >
              <SectionBody
                items={itemsByKey[key] ?? []}
                emptyKey={SECTION_META[key].emptyKey}
                sectionKey={key}
                enrollmentCount={enrollmentCount}
                onPreview={setPreview}
              />
            </BentoCell>
          ))}
        </section>
      )}

      {preview && <PublicBubbleModal item={preview} onClose={() => setPreview(null)} />}
    </div>
  )
}

// One section's scrollable body — the feed-item list, or the dashed empty hint
// when the backend sent nothing for this section. Reused by the desktop bento
// grid and the phone single-cell view.
function SectionBody({
  items,
  emptyKey,
  sectionKey,
  enrollmentCount,
  onPreview,
}: {
  items: FeedItem[]
  emptyKey: string
  sectionKey: FeedSectionKey
  enrollmentCount: number | null
  onPreview: (item: FeedItem) => void
}) {
  const { t } = useTranslation()
  if (items.length === 0) {
    // Discovery's empty state is actionable: it routes the user toward either
    // enrolling (no enrolments) or starting the first Bubble (enrolled, no Bubbles).
    if (sectionKey === 'DISCOVERY') {
      return <DiscoveryEmpty enrollmentCount={enrollmentCount} />
    }
    return (
      <div className="flex-1 min-h-0 overflow-y-auto p-3">
        <p className="rounded-2xl border border-dashed border-line bg-surface/40 px-4 py-6 text-center text-sm text-muted">
          {t(emptyKey)}
        </p>
      </div>
    )
  }
  return (
    <div className="flex-1 min-h-0 overflow-y-auto p-3 flex flex-col gap-3">
      {items.map((item, i) => (
        <FeedItemCard
          key={`${i}-${item.cta?.targetId ?? item.groupId ?? ''}`}
          item={item}
          onPreview={onPreview}
        />
      ))}
    </div>
  )
}

// Actionable empty state for Discovery. Two distinct dead-ends, two distinct
// nudges: no enrolments → go enroll; enrolled but no Bubbles → start the first
// one (opens the create form on the hub). Membership is enrollment-gated, so
// these are the only two ways the section can be empty.
function DiscoveryEmpty({ enrollmentCount }: { enrollmentCount: number | null }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const enrolled = (enrollmentCount ?? 0) > 0
  return (
    <div className="flex-1 min-h-0 overflow-y-auto p-3 flex items-center justify-center">
      <div className="rounded-2xl border border-dashed border-line bg-surface/40 px-4 py-6 text-center max-w-[42ch] flex flex-col items-center gap-3">
        <p className="text-sm text-muted">
          {t(enrolled ? 'dashboard.empty.discoveryEnrolled' : 'dashboard.empty.discovery')}
        </p>
        <Button
          size="sm"
          variant={enrolled ? 'primary' : 'secondary'}
          onClick={() => enrolled
            ? navigate('/groups', { state: { openCreate: true } })
            : navigate('/academy')}
        >
          {t(enrolled ? 'dashboard.discovery.startBubble' : 'dashboard.discovery.browseCourses')}
        </Button>
      </div>
    </div>
  )
}

function FeedItemCard({ item, onPreview }: { item: FeedItem; onPreview: (item: FeedItem) => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const rendered = ITEM_RENDERERS[item.kind](item, t)
  const showCta = rendered.ctaLabel && item.cta

  // One activation path for both the whole-card click and the CTA button:
  //  - discovery recommendation → open the public preview (you're not a member yet)
  //  - live room/session        → jump straight into the call
  //  - anything else with a group → open that Bubble in the hub (correctly selected)
  function activate() {
    if (item.kind === 'recommendation') { onPreview(item); return }
    const cta = item.cta
    if (cta?.type === 'JOIN_SESSION' && cta.targetId) { navigate(`/sessions/${cta.targetId}`); return }
    if (cta?.type === 'JOIN_ROOM' && cta.targetId) { navigate(`/rooms/${cta.targetId}`); return }
    if (item.groupId) navigate('/groups', { state: { selectGroupId: item.groupId } })
  }

  const clickable = item.kind === 'recommendation' || !!item.groupId

  return (
    <Card
      size="lg"
      interactive={clickable}
      className={`p-4 ${clickable ? 'cursor-pointer text-start w-full' : ''}`}
      onClick={clickable ? activate : undefined}
    >
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
            onClick={(e) => { e.stopPropagation(); activate() }}
          >
            {rendered.ctaLabel}
          </Button>
        )}
      </div>
    </Card>
  )
}

// ---------------------------------------------------------------------------
// Public Bubble preview — opened from a DISCOVERY recommendation. The user is
// not a member, so we never drop them into the hub; instead we fetch the public
// group details and offer to join. Joining lands them in the hub with the
// Bubble already selected (via /groups location state).
// ---------------------------------------------------------------------------

function PublicBubbleModal({ item, onClose }: { item: FeedItem; onClose: () => void }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const groupId = item.groupId ?? item.cta?.targetId ?? ''
  const [group, setGroup] = useState<Group | null>(null)
  const [loading, setLoading] = useState(true)
  const [joining, setJoining] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    getGroup(groupId)
      .then((g) => { if (!cancelled) setGroup(g) })
      .catch(() => { if (!cancelled) setError(t('dashboard.publicBubble.loadError')) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [groupId, t])

  async function handleJoin() {
    if (joining) return
    setJoining(true)
    setError('')
    try {
      await joinGroup(groupId)
      navigate('/groups', { state: { selectGroupId: groupId } })
    } catch (e) {
      setError(describeError(e, t, {
        GROUP_IS_FULL: 'dashboard.publicBubble.full',
        GROUP_NOT_PUBLIC: 'dashboard.publicBubble.privateNote',
        ALREADY_GROUP_MEMBER: 'dashboard.publicBubble.alreadyMember',
        NOT_ENROLLED_IN_COURSE: 'dashboard.publicBubble.notEnrolled',
      }, 'dashboard.publicBubble.joinError'))
      setJoining(false)
    }
  }

  const isPrivate = group?.visibility === 'PRIVATE'
  const meta = discoveryMeta(item, t, false)
  const badge = t(item.displayMode === 'TRENDING'
    ? 'dashboard.discovery.trendingBadge'
    : 'dashboard.discovery.recommended')

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-3 tablet:p-4" onClick={onClose}>
      <div
        className="bg-surface rounded-3xl shadow-bubble w-full max-w-[28rem] max-h-[85vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center gap-3">
          <Avatar id={groupId} name={item.groupName ?? item.title ?? '?'} size="md" ring />
          <div className="flex-1 min-w-0">
            <p className="text-xs text-muted">{badge}</p>
            <h3 className="font-semibold truncate">{item.groupName ?? item.title}</h3>
          </div>
          <button type="button" onClick={onClose} className="text-muted hover:text-secondary text-xl leading-none">×</button>
        </div>

        <div className="flex-1 overflow-y-auto p-4 flex flex-col gap-4">
          {meta && <p className="text-sm text-muted">{meta}</p>}

          <div className="flex flex-wrap items-center gap-2 text-xs">
            <span className="rounded-full bg-surface-muted px-2.5 py-1 text-secondary">
              {t(isPrivate ? 'dashboard.publicBubble.private' : 'dashboard.publicBubble.public')}
            </span>
            {group && (
              <span className="rounded-full bg-surface-muted px-2.5 py-1 text-secondary">
                {t('dashboard.discovery.members', { count: group.memberCount })}
              </span>
            )}
          </div>

          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-muted mb-1">
              {t('dashboard.publicBubble.about')}
            </p>
            <p className="text-sm text-secondary whitespace-pre-line">
              {loading
                ? '…'
                : (group?.description?.trim() || t('dashboard.publicBubble.noDescription'))}
            </p>
          </div>

          {isPrivate && (
            <p className="text-sm text-muted">{t('dashboard.publicBubble.privateNote')}</p>
          )}
          {error && <p className="text-sm text-danger">{error}</p>}
        </div>

        <div className="px-4 py-3 border-t border-line flex items-center justify-end gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          {!isPrivate && (
            <Button type="button" size="sm" onClick={handleJoin} disabled={loading || joining}>
              {joining ? t('dashboard.publicBubble.joining') : t('dashboard.publicBubble.join')}
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// CTA styling helper
// ---------------------------------------------------------------------------

function isJoinCta(cta: FeedCta): boolean {
  return cta.type === 'JOIN_SESSION' || cta.type === 'JOIN_ROOM' || cta.type === 'JOIN_BUBBLE'
}

function liveLabel(item: FeedItem, t: Translate): string {
  const mins = minutesUntil(item.startsAt)
  return mins <= 0 ? t('dashboard.live.liveNow') : t('dashboard.live.startsIn', { count: mins })
}

// Trending reason codes (from the backend) → i18n keys. Unknown codes are dropped.
const TRENDING_REASON_KEYS: Record<string, string> = {
  TRENDING_ACTIVE: 'dashboard.discovery.trending.active',
  TRENDING_GROWING: 'dashboard.discovery.trending.growing',
  TRENDING_POPULAR: 'dashboard.discovery.trending.popular',
  TRENDING_UPCOMING: 'dashboard.discovery.trending.upcoming',
}

function discoveryMeta(item: FeedItem, t: Translate, includeMembers = true): string {
  const parts: string[] = []
  if (item.displayMode === 'MATCHED' && item.matchPercent != null) {
    // Only MATCHED bubbles earn a trustworthy percent.
    parts.push(t('dashboard.discovery.matchPercent', { percent: item.matchPercent }))
  } else if (item.reasonLabels && item.reasonLabels.length > 0) {
    const reasons = item.reasonLabels
      .map((code) => TRENDING_REASON_KEYS[code])
      .filter(Boolean)
      .map((key) => t(key))
    if (reasons.length > 0) parts.push(reasons.join(' · '))
  }
  // The modal opts out (includeMembers=false): it shows the authoritative live
  // count from getGroup() in a chip, which can differ from the feed snapshot's
  // memberCount. Showing both would surface that disagreement to the user.
  if (includeMembers && item.memberCount != null) {
    parts.push(t('dashboard.discovery.members', { count: item.memberCount }))
  }
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
  // Four pulsing bento-shaped blocks filling the grid area, matching the
  // default (DISCOVERY-focused) layout so the load → loaded transition doesn't jump.
  const layout = FEED_BENTO_LAYOUTS.DISCOVERY
  return (
    <section className={layout.sectionClass}>
      {SECTION_ORDER.map((key) => (
        <div
          key={key}
          className={`bento-cell-radius bg-surface-muted/50 animate-pulse ${layout.cells[key]}`}
        />
      ))}
    </section>
  )
}
