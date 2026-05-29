import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  grantWhiteboardWrite,
  listExpertSessionParticipants,
  revokeWhiteboardWrite,
  type ExpertSessionParticipant,
} from '../../api/expert'
import { Avatar } from '../../components/Avatar'

interface Props {
  sessionId: string
  /** Live-synced set of whiteboard writers (excluding the host implicit-write). */
  writers: Set<string>
}

/**
 * Floating panel rendered on top of the room's bento grid for the expert (host)
 * only. Shows the list of authorized participants (host + every member of every
 * enrolled group) and lets the host toggle whiteboard write per row.
 *
 * <p>The list is fetched once on open (small set; refresh on close/reopen). The
 * `writers` prop is the live STOMP-synced set — that's what drives the "Can
 * draw" / "Grant" button state on each row, so grants and revokes reflect
 * instantly even when somebody else toggles via a future host-controls surface.
 *
 * <p>Not a presence snapshot: rows include everyone authorized, not only those
 * currently connected. Live presence for the room is a known v1 gap.
 */
export function ExpertHostControls({ sessionId, writers }: Props) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [participants, setParticipants] = useState<ExpertSessionParticipant[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [busyUserId, setBusyUserId] = useState<string | null>(null)

  useEffect(() => {
    if (!open) return
    let cancelled = false
    setLoadError(null)
    listExpertSessionParticipants(sessionId)
      .then((rows) => { if (!cancelled) setParticipants(rows) })
      .catch(() => { if (!cancelled) setLoadError(t('expertRoom.host.loadError')) })
    return () => { cancelled = true }
  }, [open, sessionId, t])

  async function handleToggle(p: ExpertSessionParticipant, grant: boolean) {
    if (p.isHost) return
    setBusyUserId(p.userId)
    setActionError(null)
    try {
      if (grant) await grantWhiteboardWrite(sessionId, p.userId)
      else await revokeWhiteboardWrite(sessionId, p.userId)
    } catch {
      setActionError(grant ? t('expertRoom.host.errorGrant') : t('expertRoom.host.errorRevoke'))
    } finally {
      setBusyUserId(null)
    }
  }

  if (!open) {
    return (
      <div className="fixed bottom-4 end-4 z-40">
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="px-4 py-2 rounded-full bg-primary-600 text-white text-xs font-semibold shadow-themed hover:bg-primary-700 transition flex items-center gap-2"
        >
          <span aria-hidden="true">👥</span>
          {t('expertRoom.host.button')}
        </button>
      </div>
    )
  }

  return (
    <div className="fixed bottom-4 end-4 z-40 w-80 max-w-[calc(100vw-2rem)]">
      <div className="bg-surface rounded-2xl shadow-bubble border border-line">
        <div className="flex items-center justify-between px-4 py-3 border-b border-line">
          <h3 className="text-sm font-bold text-base">{t('expertRoom.host.heading')}</h3>
          <button
            type="button"
            onClick={() => setOpen(false)}
            aria-label={t('expertRoom.host.close')}
            className="text-muted hover:text-base text-sm leading-none"
          >
            ✕
          </button>
        </div>
        <p className="px-4 pt-2 text-xs text-muted">{t('expertRoom.host.intro')}</p>
        <div className="px-2 py-2 max-h-72 overflow-y-auto">
          {loadError ? (
            <p className="px-2 py-3 text-xs text-warning">{loadError}</p>
          ) : !participants ? (
            <p className="px-2 py-3 text-xs text-muted italic">{t('common.loading')}</p>
          ) : participants.length === 0 ? (
            <p className="px-2 py-3 text-xs text-muted italic">{t('expertRoom.host.noneYet')}</p>
          ) : (
            <ul className="space-y-1">
              {participants.map((p) => {
                // The host is always a writer (implicit). For non-host rows we read
                // the LIVE writer set so a grant from another tab shows instantly.
                const canDraw = p.isHost || writers.has(p.userId)
                const busy = busyUserId === p.userId
                return (
                  <li key={p.userId} className="flex items-center gap-2 px-2 py-1.5 rounded-lg hover:bg-surface-hover">
                    <Avatar
                      id={p.userId}
                      name={p.displayName}
                      imageUrl={p.avatarUrl}
                      size="sm"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium text-base truncate">{p.displayName}</p>
                      {p.isHost && <p className="text-[10px] text-muted">{t('expertRoom.host.hostBadge')}</p>}
                    </div>
                    {p.isHost ? (
                      <span className="text-[10px] px-2 py-0.5 rounded-full bg-primary-600/10 text-primary-600">
                        {t('expertRoom.host.canDraw')}
                      </span>
                    ) : canDraw ? (
                      <button
                        type="button"
                        onClick={() => handleToggle(p, false)}
                        disabled={busy}
                        className="text-[10px] px-2 py-1 rounded-full border border-warning/40 text-warning hover:bg-warning/10 disabled:opacity-50 transition"
                      >
                        {busy ? t('expertRoom.host.revoking') : t('expertRoom.host.revoke')}
                      </button>
                    ) : (
                      <button
                        type="button"
                        onClick={() => handleToggle(p, true)}
                        disabled={busy}
                        className="text-[10px] px-2 py-1 rounded-full bg-primary-600 text-white hover:bg-primary-700 disabled:opacity-50 transition"
                      >
                        {busy ? t('expertRoom.host.granting') : t('expertRoom.host.grant')}
                      </button>
                    )}
                  </li>
                )
              })}
            </ul>
          )}
        </div>
        {actionError && <div className="px-4 pb-3 text-xs text-warning">{actionError}</div>}
      </div>
    </div>
  )
}
