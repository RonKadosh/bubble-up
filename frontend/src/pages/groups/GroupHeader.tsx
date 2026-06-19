import { useTranslation } from 'react-i18next'
import { Group } from '../../api/groups'
import { CalendarEvent } from '../../api/calendar'
import { Avatar } from '../../components/Avatar'
import { Button } from '../../components/Button'
import { ChevronIcon, MenuIcon } from '../../components/Icons'
import { useViewportStore } from '../../store/viewportStore'
import { MatchFeedbackControl } from './MatchFeedbackControl'

interface GroupHeaderProps {
  group: Group
  isOwner: boolean
  isMember: boolean
  onJoin: () => void
  /** The session joinable right now in this bubble, or null. Flips the live CTA from Create → Join. */
  liveSession: CalendarEvent | null
  /** Opens the Create-Live-Bubble modal. Only shown to members when nothing is live. */
  onScheduleRoom: () => void
  /** Joins the currently-live session. Only shown to members while a session is live. */
  onJoinLive: () => void
  /** Opens the GroupSidebar drawer below `desktop`. Ignored at desktop+. */
  onOpenSidebar: () => void
  /** Opens the Bubble Info drawer. On phone the compact title bar is the trigger. */
  onOpenInfo: () => void
}

/** Pulsing red dot — the "live now" indicator, reused as the Join button's leading glyph. */
function LiveDot() {
  return (
    <span className="relative flex h-2 w-2" aria-hidden="true">
      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-500 opacity-75" />
      <span className="relative inline-flex rounded-full h-2 w-2 bg-rose-500" />
    </span>
  )
}

/**
 * Title bar above the bento grid: group name + visibility pill, plus the
 * live CTA (members) and Hop in (non-members). Leave / Pop now live in the
 * Bubble Info drawer, opened from the members strip.
 *
 * Below `desktop`, the inline-start edge gets a hamburger to open the
 * `GroupSidebar` drawer. Below `tablet`, the action buttons stack under the
 * title instead of sitting beside it so they don't get crushed.
 */
export function GroupHeader({ group, isOwner, isMember, onJoin, liveSession, onScheduleRoom, onJoinLive, onOpenSidebar, onOpenInfo }: GroupHeaderProps) {
  const { t } = useTranslation()
  const isPhone = useViewportStore((s) => s.tier === 'phone')

  // Shared live/join CTA — identical on phone and desktop, just placed differently.
  const actionCta = (
    <>
      {isMember && (
        liveSession ? (
          <Button
            size="sm"
            onClick={onJoinLive}
            leftIcon={<LiveDot />}
            title={t('groups.header.joinLiveTitle')}
          >
            {t('groups.header.joinLive')}
          </Button>
        ) : (
          <Button size="sm" onClick={onScheduleRoom}>
            {t('groups.header.createLive')}
          </Button>
        )
      )}
      {!isMember && group.visibility === 'PUBLIC' && (
        group.memberCount >= group.maxMembers ? (
          <Button variant="deep" size="sm" disabled title={t('groups.header.fullTitle')}>
            {t('groups.header.full')}
          </Button>
        ) : (
          <Button variant="deep" size="sm" onClick={onJoin}>
            {t('groups.header.hopIn')}
          </Button>
        )
      )}
    </>
  )

  // Phone: a single compact bar — hamburger (Bubble list), the group icon, and
  // the live CTA. Tapping the icon opens the Bubble Info drawer, which carries
  // the name, description, the "is this Bubble a fit?" feedback, members and
  // actions. The full "group panel" is dropped here so the chat gets the screen.
  if (isPhone) {
    return (
      <header className="bg-surface border-b border-line px-2 py-2 flex items-center gap-1.5 shrink-0">
        <button
          type="button"
          onClick={onOpenSidebar}
          aria-label={t('groups.openBubbleListAria')}
          className="shrink-0 w-9 h-9 rounded-full flex items-center justify-center text-base hover:bg-surface-hover transition bubble-pop"
        >
          <MenuIcon className="w-5 h-5" />
        </button>
        <button
          type="button"
          onClick={onOpenInfo}
          aria-haspopup="dialog"
          aria-label={t('groups.info.openStripAria')}
          title={t('groups.info.openStripAria')}
          className="relative shrink-0 rounded-full bubble-pop"
        >
          <Avatar id={group.id} name={group.name} imageUrl={group.imageUrl} size="md" ring />
          {/* Affordance hint: a small chevron badge so it reads as "tap to open Bubble Info"
              on touch, where there's no hover cue. */}
          <span className="absolute -bottom-0.5 -end-0.5 grid place-items-center w-4 h-4 rounded-full bg-surface border border-line text-secondary shadow-sm">
            <ChevronIcon className="w-2.5 h-2.5 -rotate-90" />
          </span>
        </button>
        <div className="flex-1 min-w-0" />
        <div className="shrink-0 flex items-center">{actionCta}</div>
      </header>
    )
  }

  return (
    <header className="bg-surface border-b border-line px-3 tablet:px-6 py-3 tablet:py-4 flex flex-col tablet:flex-row tablet:items-center tablet:justify-between gap-2 tablet:gap-3 shrink-0">
      <div className="flex items-center gap-2 min-w-0">
        <button
          type="button"
          onClick={onOpenSidebar}
          aria-label={t('groups.openBubbleListAria')}
          className="desktop:hidden shrink-0 w-9 h-9 rounded-full flex items-center justify-center text-base hover:bg-surface-hover transition bubble-pop"
        >
          <MenuIcon className="w-5 h-5" />
        </button>
        <div className="min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h1 className="text-lg font-semibold truncate">{group.name}</h1>
            <span className={`text-xs px-2 py-0.5 rounded-md ${
              group.visibility === 'PUBLIC'
                ? 'bg-primary-100 text-primary-700'
                : 'bg-amber-100 text-amber-800'
            }`}>
              {group.visibility === 'PUBLIC' ? t('groups.publicBadge') : t('groups.privateBadge')}
            </span>
            <span className="text-xs px-2 py-0.5 rounded-md bg-surface-muted text-secondary tabular-nums">
              {t('groups.capacity', { count: group.memberCount, max: group.maxMembers })}
            </span>
          </div>
          {group.description && (
            <p className="text-sm text-secondary mt-0.5 truncate">{group.description}</p>
          )}
          {!isOwner && (
            <div className="mt-1.5">
              <MatchFeedbackControl key={group.id} groupId={group.id} />
            </div>
          )}
        </div>
      </div>
      <div className="flex gap-2 shrink-0 items-center flex-wrap">
        {isMember && (
          liveSession ? (
            <Button
              size="sm"
              onClick={onJoinLive}
              leftIcon={<LiveDot />}
              title={t('groups.header.joinLiveTitle')}
            >
              {t('groups.header.joinLive')}
            </Button>
          ) : (
            <span data-tour="bubble-live-room" className="inline-flex">
              <Button size="sm" onClick={onScheduleRoom}>
                {t('groups.header.createLive')}
              </Button>
            </span>
          )
        )}
        {!isMember && group.visibility === 'PUBLIC' && (
          group.memberCount >= group.maxMembers ? (
            <Button variant="deep" disabled title={t('groups.header.fullTitle')}>
              {t('groups.header.full')}
            </Button>
          ) : (
            <Button variant="deep" onClick={onJoin}>
              {t('groups.header.hopIn')}
            </Button>
          )
        )}
      </div>
    </header>
  )
}
