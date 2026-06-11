import { ComponentType, ReactNode, SVGProps, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { Feed, FeedCta, FeedItem, FeedItemKind, FeedSectionKey, getFeed } from '../../api/feed'
import { Group, getGroup, joinGroup } from '../../api/groups'
import { CalendarEvent, getEvent } from '../../api/calendar'
import { listMyCurrentEnrollments } from '../../api/enrollment'
import { describeError } from '../../api/errors'
import { useAuthStore } from '../../store/authStore'
import { useToastStore } from '../../store/toastStore'
import { EventModal, EventViewModal } from './CalendarPanel'
import { Avatar } from '../../components/Avatar'
import { Card } from '../../components/Card'
import { Button } from '../../components/Button'
import { PageShell, SectionLabel } from '../../components/PageHeader'
import {
  VideoIcon, PeopleIcon, CalendarIcon, UserPlusIcon, UserMinusIcon,
  ChatIcon, FileIcon, SparkleIcon, TrendIcon,
} from '../../components/Icons'
import { formatRange } from '../../i18n/datetime'

type Translate = (key: string, opts?: Record<string, unknown>) => string
type IconType = ComponentType<SVGProps<SVGSVGElement>>

/**
 * The cross-Bubble activity feed — the "no Bubble selected" home of the hub.
 * Lives inside GroupsPage (the merged Home + My Bubbles surface), so its CTAs
 * act *in place*: selecting a Bubble or opening the create form is handled by the
 * hub via callbacks rather than a route navigation.
 *
 *  - `onSelectGroup(groupId)` — open that Bubble in the hub (replaces the old
 *    `navigate('/groups', { state: { selectGroupId } })`).
 *  - `onOpenCreate()`         — open the hub's create-Bubble form.
 *
 * Live-room / session CTAs still navigate (`/rooms/:id`, `/sessions/:id`) and the
 * "browse courses" empty state still routes to `/academy` — those are other pages.
 */

// A feed line: a small type glyph + text. `tone` colors the icon (live = green,
// discovery = magenta, activity = muted). Used by every renderer so the icons feel
// like one set instead of scattered emoji. `title` styles the primary (bold) line.
function FeedLine({ icon: Icon, tone = 'text-muted', title = false, children }: {
  icon: IconType
  tone?: string
  title?: boolean
  children: ReactNode
}) {
  return (
    <p className={`flex items-start gap-2 min-w-0 ${title ? 'font-semibold text-base' : 'text-sm text-secondary'}`}>
      <Icon className={`shrink-0 mt-0.5 ${title ? 'w-[1.05rem] h-[1.05rem]' : 'w-4 h-4'} ${tone}`} />
      {/* Wrap to two lines rather than hard-truncating: on phone a single line
          clips the verb + Bubble name off membership/activity lines, losing the
          whole message. Two lines keep meaning while still bounding card height. */}
      <span className="line-clamp-2">{children}</span>
    </p>
  )
}

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

// Whisper tint per section — a flat wash, fainter than the matched-card
// bubble-surface so Discovery stays the loudest. DISCOVERY itself is unmapped:
// its plain cards stay white and its matched cards glow magenta.
const SECTION_TINT: Partial<Record<FeedSectionKey, string>> = {
  LIVE: 'bg-tint-yellow',
  UPCOMING: 'bg-tint-green',
  ACTIVITY: 'bg-tint-blue',
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
  /** Trophy slot at the row end (the matched-Bubble % badge). */
  badge?: ReactNode
}

const ITEM_RENDERERS: Record<FeedItemKind, (item: FeedItem, t: Translate) => Rendered> = {
  liveSession: (item, t) => {
    const n = item.participantCount ?? 0
    return {
      body: (
        <>
          <FeedLine icon={VideoIcon} tone="text-bubble-green" title>{item.title}</FeedLine>
          <p className="text-sm text-muted truncate ps-[1.65rem]">
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
          <FeedLine icon={PeopleIcon} tone="text-bubble-green" title>
            {n > 0 ? t('dashboard.live.roomParticipants', { count: n }) : t('dashboard.live.roomLiveNow')}
          </FeedLine>
          <p className="text-sm text-muted truncate ps-[1.65rem]">{item.groupName}</p>
        </>
      ),
      ctaLabel: n > 0 ? t('dashboard.cta.hopIn') : t('dashboard.cta.joinRoom'),
    }
  },

  upcomingEvent: (item, t) => ({
    body: (
      <>
        <FeedLine icon={CalendarIcon} tone="text-bubble-magenta" title>
          {item.title || humanizeType(item.eventType)}
        </FeedLine>
        <p className="text-sm text-muted truncate ps-[1.65rem]">
          {item.startsAt ? formatRange(item.startsAt, item.endsAt) : ''}{item.groupName ? ` · ${item.groupName}` : ''}
        </p>
      </>
    ),
    ctaLabel: t('dashboard.cta.viewEvent'),
  }),

  memberJoin: (item, t) => ({
    body: <FeedLine icon={UserPlusIcon}>{membershipText(item, t, 'joined')}</FeedLine>,
  }),

  memberLeave: (item, t) => ({
    body: <FeedLine icon={UserMinusIcon}>{membershipText(item, t, 'left')}</FeedLine>,
  }),

  unread: (item, t) => ({
    body: (
      <FeedLine icon={ChatIcon}>
        {t('dashboard.unread.count', { count: item.unreadCount ?? 0, bubble: item.groupName })}
      </FeedLine>
    ),
    ctaLabel: t('dashboard.cta.open'),
  }),

  file: (item, t) => {
    const labels = item.collapsedLabels ?? []
    const total = item.collapsedCount ?? labels.length
    const lead = labels[0] ?? ''
    const extra = total - 1
    const key = extra > 0 ? 'dashboard.file.uploadedOverflow' : 'dashboard.file.uploaded'
    return {
      body: <FeedLine icon={FileIcon}>{t(key, { name: lead, count: extra, bubble: item.groupName })}</FeedLine>,
    }
  },

  recommendation: (item, t) => {
    const trending = item.displayMode === 'TRENDING'
    const course = courseLabel(item)
    // A trustworthy match % is the card's trophy — pulled out of the meta line
    // into a glowing badge ("Spark in the Calm").
    const matched = !trending && item.matchPercent != null
    return {
      body: (
        <>
          <p className="text-xs text-bubble-magenta flex items-center gap-1.5">
            {trending
              ? <TrendIcon className="w-3.5 h-3.5 shrink-0" />
              : <SparkleIcon className="w-3.5 h-3.5 shrink-0" />}
            {t(trending ? 'dashboard.discovery.trendingBadge' : 'dashboard.discovery.recommended')}
          </p>
          <p className="font-semibold text-base truncate">{item.title}</p>
          {course && <p className="text-sm text-muted truncate">{t('dashboard.discovery.fromCourse', { course })}</p>}
          <p className="text-sm text-muted truncate">{discoveryMeta(item, t, true, !matched)}</p>
        </>
      ),
      badge: matched ? <MatchBadge percent={item.matchPercent!} t={t} /> : undefined,
      ctaLabel: t('dashboard.cta.viewBubble'),
    }
  },
}

/** The match-score trophy: a soft glowing pill, not secondary text. */
function MatchBadge({ percent, t }: { percent: number; t: Translate }) {
  return (
    <span className="shrink-0 inline-flex items-center gap-1 rounded-full bg-bubble-magenta-soft border border-bubble-magenta/30 px-2.5 py-1 text-xs font-semibold text-accent-magenta">
      <SparkleIcon className="w-3.5 h-3.5" />
      {t('dashboard.discovery.matchPercent', { percent })}
    </span>
  )
}

// "CS101 · Operating Systems" when a code exists, else just the course name.
function courseLabel(item: FeedItem): string {
  if (!item.courseName) return ''
  return item.courseCode ? `${item.courseCode} · ${item.courseName}` : item.courseName
}

export function HubFeed({
  onSelectGroup,
  onOpenCreate,
  onOpenBubbleList,
  onJoined,
}: {
  onSelectGroup: (groupId: string) => void
  onOpenCreate: () => void
  /** Phone/tablet only: opens the Bubble-list drawer. Rendered below the header. */
  onOpenBubbleList?: () => void
  /** Discovery join completed; lets the parent refresh My Bubbles before selection. */
  onJoined?: (groupId: string) => void | Promise<void>
}) {
  const { t } = useTranslation()
  const meId = useAuthStore((s) => s.user?.id ?? null)
  const showToast = useToastStore((s) => s.show)

  const [feed, setFeed] = useState<Feed | null>(null)
  const [loading, setLoading] = useState(true)
  /** The event opened from an UPCOMING card — view first, edit on demand. */
  const [eventModal, setEventModal] = useState<{ mode: 'view' | 'edit'; event: CalendarEvent; groupId: string } | null>(null)
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

  async function refreshFeed() {
    try { setFeed(await getFeed()) } catch { /* keep the stale feed on a transient failure */ }
  }

  async function handleDiscoveryJoined(groupId: string) {
    setFeed((prev) => prev
      ? {
          sections: prev.sections.map((section) => section.key === 'DISCOVERY'
            ? {
                ...section,
                items: section.items.filter((item) => (item.groupId ?? item.cta?.targetId) !== groupId),
              }
            : section),
        }
      : prev)
    await onJoined?.(groupId)
  }

  // Open the event detail modal for an UPCOMING card (resolve the event by id).
  async function handleViewEvent(eventId: string, groupId?: string) {
    if (!groupId) return
    try {
      const event = await getEvent(eventId)
      setEventModal({ mode: 'view', event, groupId })
    } catch {
      showToast(t('dashboard.event.loadError'), 'error')
    }
  }

  const itemsByKey: Partial<Record<FeedSectionKey, FeedItem[]>> = {}
  for (const s of feed?.sections ?? []) itemsByKey[s.key] = s.items

  return (
    <>
      <PageShell title={t('dashboard.title')} subtitle={t('dashboard.subtitle')}>
        {/* Bubble-list entry — phone/tablet only, sitting just below the header. */}
        {onOpenBubbleList && (
          <div className="desktop:hidden mb-4">
            <button
              type="button"
              onClick={onOpenBubbleList}
              className="bubble-pop rounded-full bg-brand-gradient-strong text-on-brand text-sm font-semibold px-5 py-2 shadow-themed"
            >
              {t('groups.openBubbleList')}
            </button>
          </div>
        )}
        {loading ? (
          <FeedSkeleton />
        ) : (
          <div className="flex flex-col gap-8">
            {SECTION_ORDER.map((key) => (
              <section key={key}>
                <SectionLabel className="mb-3 ms-1">
                  {t(SECTION_META[key].labelKey)}
                </SectionLabel>
                <SectionItems
                  items={itemsByKey[key] ?? []}
                  emptyKey={SECTION_META[key].emptyKey}
                  sectionKey={key}
                  enrollmentCount={enrollmentCount}
                  onPreview={setPreview}
                  onSelectGroup={onSelectGroup}
                  onOpenCreate={onOpenCreate}
                  onViewEvent={handleViewEvent}
                />
              </section>
            ))}
          </div>
        )}
      </PageShell>

      {preview && (
        <PublicBubbleModal
          item={preview}
          onClose={() => setPreview(null)}
          onSelectGroup={onSelectGroup}
          onJoined={handleDiscoveryJoined}
        />
      )}

      {eventModal?.mode === 'view' && (
        <EventViewModal
          event={eventModal.event}
          meId={meId}
          isOwner={false}
          isMember
          groupId={eventModal.groupId}
          chatRoomId={null}
          onEdit={() => setEventModal({ ...eventModal, mode: 'edit' })}
          onClose={() => setEventModal(null)}
          onSaved={() => { setEventModal(null); refreshFeed() }}
          onShared={() => setEventModal(null)}
          onError={(msg) => showToast(msg, 'error')}
        />
      )}
      {eventModal?.mode === 'edit' && (
        <EventModal
          groupId={eventModal.groupId}
          meId={meId}
          isOwner={false}
          isMember
          mode="edit"
          initialEvent={eventModal.event}
          onClose={() => setEventModal(null)}
          onSaved={() => { setEventModal(null); refreshFeed() }}
          onError={(msg) => showToast(msg, 'error')}
        />
      )}
    </>
  )
}

// One section's body in the stacked list: the feed-item cards, the actionable
// Discovery empty state, or a plain dashed empty hint for the other sections.
function SectionItems({
  items,
  emptyKey,
  sectionKey,
  enrollmentCount,
  onPreview,
  onSelectGroup,
  onOpenCreate,
  onViewEvent,
}: {
  items: FeedItem[]
  emptyKey: string
  sectionKey: FeedSectionKey
  enrollmentCount: number | null
  onPreview: (item: FeedItem) => void
  onSelectGroup: (groupId: string) => void
  onOpenCreate: () => void
  onViewEvent: (eventId: string, groupId?: string) => void
}) {
  const { t } = useTranslation()
  if (items.length === 0) {
    // Discovery's empty state is actionable: it routes the user toward either
    // enrolling (no enrolments) or starting the first Bubble (enrolled, no Bubbles).
    if (sectionKey === 'DISCOVERY') {
      return <DiscoveryEmpty enrollmentCount={enrollmentCount} onOpenCreate={onOpenCreate} />
    }
    return (
      <p className="rounded-2xl border border-dashed border-line bg-surface/40 px-4 py-6 text-center text-sm text-muted">
        {t(emptyKey)}
      </p>
    )
  }
  return (
    <div className="flex flex-col gap-3">
      {items.map((item, i) => (
        <FeedItemCard
          key={`${i}-${item.cta?.targetId ?? item.groupId ?? ''}`}
          item={item}
          tint={SECTION_TINT[sectionKey]}
          onPreview={onPreview}
          onSelectGroup={onSelectGroup}
          onViewEvent={onViewEvent}
        />
      ))}
    </div>
  )
}

// Actionable empty state for Discovery. Two distinct dead-ends, two distinct
// nudges: no enrolments → go enroll; enrolled but no Bubbles → start the first
// one (opens the create form on the hub). Membership is enrollment-gated, so
// these are the only two ways the section can be empty.
function DiscoveryEmpty({
  enrollmentCount,
  onOpenCreate,
}: {
  enrollmentCount: number | null
  onOpenCreate: () => void
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const enrolled = (enrollmentCount ?? 0) > 0
  return (
    <div className="rounded-2xl border border-dashed border-line bg-surface/40 px-4 py-6 text-center flex flex-col items-center gap-3">
      <p className="text-sm text-muted max-w-[42ch]">
        {t(enrolled ? 'dashboard.empty.discoveryEnrolled' : 'dashboard.empty.discovery')}
      </p>
      <Button
        size="sm"
        variant={enrolled ? 'primary' : 'secondary'}
        onClick={() => enrolled ? onOpenCreate() : navigate('/academy')}
      >
        {t(enrolled ? 'dashboard.discovery.startBubble' : 'dashboard.discovery.browseCourses')}
      </Button>
    </div>
  )
}

function FeedItemCard({
  item,
  tint,
  onPreview,
  onSelectGroup,
  onViewEvent,
}: {
  item: FeedItem
  /** Section whisper-tint class (see SECTION_TINT). Undefined = plain surface. */
  tint?: string
  onPreview: (item: FeedItem) => void
  onSelectGroup: (groupId: string) => void
  onViewEvent: (eventId: string, groupId?: string) => void
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const rendered = ITEM_RENDERERS[item.kind](item, t)
  const showCta = rendered.ctaLabel && item.cta

  // One activation path for both the whole-card click and the CTA button:
  //  - discovery recommendation → open the public preview (you're not a member yet)
  //  - upcoming event           → open the event detail modal
  //  - live room/session        → jump straight into the call
  //  - anything else with a group → open that Bubble in the hub (in-place select)
  function activate() {
    if (item.kind === 'recommendation') { onPreview(item); return }
    const cta = item.cta
    if (cta?.type === 'VIEW_EVENT' && cta.targetId) { onViewEvent(cta.targetId, item.groupId); return }
    if (cta?.type === 'JOIN_SESSION' && cta.targetId) { navigate(`/sessions/${cta.targetId}`, { state: item.groupId ? { fromGroupId: item.groupId } : undefined }); return }
    if (cta?.type === 'JOIN_ROOM' && cta.targetId) { navigate(`/rooms/${cta.targetId}`); return }
    if (item.groupId) onSelectGroup(item.groupId)
  }

  const clickable = item.kind === 'recommendation' || !!item.groupId

  // Matched recommendations are the one feed card allowed to glow: the soapy
  // bubble-surface (same treatment as the admin Bubbles KPI) + a soft colored
  // outer glow instead of the flat themed shadow.
  const matched = item.kind === 'recommendation' && item.displayMode === 'MATCHED'

  return (
    <Card
      size="lg"
      interactive={clickable}
      className={`p-4 ${clickable ? 'cursor-pointer text-start w-full' : ''} ${
        matched ? 'relative overflow-hidden bubble-surface glow-match' : tint ?? ''
      }`}
      onClick={clickable ? activate : undefined}
    >
      <div className="flex items-center gap-3">
        {item.groupId && (
          <Avatar id={item.groupId} name={item.groupName ?? '?'} imageUrl={item.groupImageUrl} size="md" ring />
        )}
        <div className="flex-1 min-w-0">{rendered.body}</div>
        {rendered.badge && <div className="shrink-0">{rendered.badge}</div>}
        {/* Tablet+: CTA sits inline at the end of the row. */}
        {showCta && (
          <div className="shrink-0 hidden tablet:block">
            <Button
              size="sm"
              variant={isJoinCta(item.cta!) ? 'deep' : 'secondary'}
              onClick={(e) => { e.stopPropagation(); activate() }}
            >
              {rendered.ctaLabel}
            </Button>
          </div>
        )}
      </div>
      {/* Phone: the CTA stacks full-width below the content instead of beside it —
          otherwise it crushes the text into a sliver of "…" on narrow widths. */}
      {showCta && (
        <div className="tablet:hidden mt-3">
          <Button
            size="sm"
            variant={isJoinCta(item.cta!) ? 'deep' : 'secondary'}
            className="w-full"
            onClick={(e) => { e.stopPropagation(); activate() }}
          >
            {rendered.ctaLabel}
          </Button>
        </div>
      )}
    </Card>
  )
}

// ---------------------------------------------------------------------------
// Public Bubble preview — opened from a DISCOVERY recommendation. The user is
// not a member, so we never drop them into the hub; instead we fetch the public
// group details and offer to join. Joining selects the Bubble in the hub (via
// the onSelectGroup callback).
// ---------------------------------------------------------------------------

function PublicBubbleModal({
  item,
  onClose,
  onSelectGroup,
  onJoined,
}: {
  item: FeedItem
  onClose: () => void
  onSelectGroup: (groupId: string) => void
  onJoined?: (groupId: string) => void | Promise<void>
}) {
  const { t } = useTranslation()
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
      await onJoined?.(groupId)
      onClose()
      onSelectGroup(groupId)
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
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-3 tablet:p-4 animate-fade-in" onClick={onClose}>
      <div
        className="bg-surface rounded-3xl shadow-bubble animate-pop-in w-full max-w-[28rem] max-h-[85vh] flex flex-col border border-line"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-4 py-3 border-b border-line flex items-center gap-3">
          <Avatar id={groupId} name={item.groupName ?? item.title ?? '?'} imageUrl={group?.imageUrl ?? item.groupImageUrl} size="md" ring />
          <div className="flex-1 min-w-0">
            <p className="text-xs text-bubble-magenta">{badge}</p>
            <h3 className="font-semibold truncate">{item.groupName ?? item.title}</h3>
            {courseLabel(item) && (
              <p className="text-xs text-muted truncate">{t('dashboard.discovery.fromCourse', { course: courseLabel(item) })}</p>
            )}
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
            <Button variant="deep" type="button" size="sm" onClick={handleJoin} disabled={loading || joining}>
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

// Collapsed membership rollup → localized string. Lists up to the lead names the
// backend sent, then "+N others" via the plural overflow key. Falls back to the
// "someone" label when no actor name resolved (deleted user / lookup miss).
function membershipText(item: FeedItem, t: Translate, verb: 'joined' | 'left'): string {
  const labels = item.collapsedLabels ?? []
  const total = item.collapsedCount ?? labels.length
  const names = labels.length > 0 ? labels : [t('dashboard.membership.someone')]
  const extra = total - names.length
  const key = extra > 0
    ? `dashboard.membership.${verb}Overflow`
    : `dashboard.membership.${verb}`
  return t(key, { names: names.join(', '), count: extra, bubble: item.groupName })
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

function discoveryMeta(item: FeedItem, t: Translate, includeMembers = true, includePercent = true): string {
  const parts: string[] = []
  if (item.displayMode === 'MATCHED' && item.matchPercent != null) {
    // Only MATCHED bubbles earn a trustworthy percent. The feed card opts out
    // (includePercent=false) because it shows the % as a badge instead.
    if (includePercent) parts.push(t('dashboard.discovery.matchPercent', { percent: item.matchPercent }))
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

// Stacked-list loading placeholder — one header bar + two card blocks per section,
// matching the loaded layout so the load → loaded transition doesn't jump.
function FeedSkeleton() {
  return (
    <div className="flex flex-col gap-8">
      {SECTION_ORDER.map((key) => (
        <section key={key}>
          <div className="h-4 w-24 bg-surface-muted/60 rounded mb-3 ms-1 animate-pulse" />
          <div className="flex flex-col gap-3">
            {[0, 1].map((i) => (
              <div key={i} className="h-20 rounded-2xl bg-surface-muted/50 animate-pulse" />
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
