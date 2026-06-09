import { useTranslation } from 'react-i18next'
import { Group } from '../../api/groups'
import { CalendarEvent } from '../../api/calendar'
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
          <Button size="sm" disabled title={t('groups.header.fullTitle')}>
            {t('groups.header.full')}
          </Button>
        ) : (
          <Button size="sm" onClick={onJoin}>
            {t('groups.header.hopIn')}
          </Button>
        )
      )}
    </>
  )

  // Phone: a single compact bar — hamburger, tappable title (opens Bubble Info,
  // which carries the description / members / actions), and the live CTA. The
  // full "group panel" (description, match feedback, members strip) is dropped
  // here so the chat gets the screen.
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
          className="flex-1 min-w-0 flex items-center gap-1.5 text-start px-1.5 py-1 rounded-full hover:bg-surface-hover transition"
        >
          <h1 className="text-base font-semibold truncate">{group.name}</h1>
          <span className="shrink-0 text-[0.7rem] px-1.5 py-0.5 rounded-md bg-surface-muted text-secondary tabular-nums">
            {t('groups.capacity', { count: group.memberCount, max: group.maxMembers })}
          </span>
          <ChevronIcon className="w-4 h-4 shrink-0 text-muted -rotate-90" aria-hidden />
        </button>
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
            <Button size="sm" onClick={onScheduleRoom}>
              {t('groups.header.createLive')}
            </Button>
          )
        )}
        {!isMember && group.visibility === 'PUBLIC' && (
          group.memberCount >= group.maxMembers ? (
            <Button disabled title={t('groups.header.fullTitle')}>
              {t('groups.header.full')}
            </Button>
          ) : (
            <Button onClick={onJoin}>
              {t('groups.header.hopIn')}
            </Button>
          )
        )}
      </div>
    </header>
  )
}
