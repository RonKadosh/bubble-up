import { useTranslation } from 'react-i18next'
import { Group } from '../../api/groups'
import { Button } from '../../components/Button'
import { MenuIcon } from '../../components/Icons'

interface GroupHeaderProps {
  group: Group
  isOwner: boolean
  isMember: boolean
  onJoin: () => void
  onLeave: () => void
  onDelete: () => void
  /** Opens the Schedule-a-Room modal. Only shown to members. */
  onScheduleRoom: () => void
  /** Opens the GroupSidebar drawer below `desktop`. Ignored at desktop+. */
  onOpenSidebar: () => void
}

/**
 * Title bar above the bento grid: group name + visibility pill, plus the
 * Hop in / Leave / Pop action buttons (visibility depends on role).
 *
 * Below `desktop`, the inline-start edge gets a hamburger to open the
 * `GroupSidebar` drawer. Below `tablet`, the action buttons stack under the
 * title instead of sitting beside it so they don't get crushed.
 */
export function GroupHeader({ group, isOwner, isMember, onJoin, onLeave, onDelete, onScheduleRoom, onOpenSidebar }: GroupHeaderProps) {
  const { t } = useTranslation()
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
          </div>
          {group.description && (
            <p className="text-sm text-secondary mt-0.5 truncate">{group.description}</p>
          )}
        </div>
      </div>
      <div className="flex gap-2 shrink-0 items-center flex-wrap">
        {isMember && (
          <Button size="sm" onClick={onScheduleRoom}>
            🎥 Schedule Room
          </Button>
        )}
        {!isMember && group.visibility === 'PUBLIC' && (
          <Button onClick={onJoin}>
            {t('groups.header.hopIn')}
          </Button>
        )}
        {isMember && !isOwner && (
          <Button variant="secondary" size="xs" onClick={onLeave}>
            {t('groups.header.leave')}
          </Button>
        )}
        {isOwner && (
          <>
            <Button variant="secondary" size="xs" onClick={onLeave} title={t('groups.header.leaveOwnerTitle')}>
              {t('groups.header.leave')}
            </Button>
            <Button variant="danger" size="sm" onClick={onDelete} title={t('groups.header.popTitle')} className="glow-danger">
              {t('groups.header.pop')}
            </Button>
          </>
        )}
      </div>
    </header>
  )
}
