import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../../components/Button'
import { submitMatchFeedback, getMatchFeedbackStatus, type FeedbackSentiment } from '../../api/matching'
import { useMatchFeedbackStore } from '../../store/matchFeedbackStore'

/**
 * "Is this bubble a good fit for you?" — a lightweight thumbs control shown in the
 * group view. Asked ONCE per user per bubble. Two instances mount at once (header +
 * Bubble Info drawer), so ratedness is held in a shared {@link useMatchFeedbackStore}
 * keyed by group: the first to mount checks the server, and a vote in either instance
 * flips both to 'rated' immediately — you can't answer twice. On click it posts the
 * verdict (the backend keeps the first vote) and the clicking instance shows a thanks.
 * Best-effort: a failed submit is silent.
 */
export function MatchFeedbackControl({ groupId }: { groupId: string }) {
  const { t } = useTranslation()
  const status = useMatchFeedbackStore((s) => s.statusByGroup[groupId])
  const setStatus = useMatchFeedbackStore((s) => s.setStatus)
  // True only on the instance the user actually clicked — it shows "thanks";
  // the sibling instance just disappears.
  const [submitted, setSubmitted] = useState(false)
  const [busy, setBusy] = useState(false)

  // First instance to mount for this group claims the status check (reads the live
  // store via getState so the sibling, mounting in the same commit, sees 'loading'
  // and skips — no double fetch). Best-effort: show the prompt on failure.
  useEffect(() => {
    if (useMatchFeedbackStore.getState().statusByGroup[groupId] !== undefined) return
    setStatus(groupId, 'loading')
    let cancelled = false
    getMatchFeedbackStatus(groupId)
      .then((s) => { if (!cancelled) setStatus(groupId, s.rated ? 'rated' : 'unrated') })
      .catch(() => { if (!cancelled) setStatus(groupId, 'unrated') })
    return () => { cancelled = true }
  }, [groupId, setStatus])

  async function rate(sentiment: FeedbackSentiment) {
    if (busy || status === 'rated') return
    setBusy(true)
    setSubmitted(true)
    setStatus(groupId, 'rated')   // optimistic — hides both instances at once
    try {
      await submitMatchFeedback(groupId, sentiment)
    } catch {
      /* best-effort — silent; the optimistic 'rated' stands so we don't re-ask */
    } finally {
      setBusy(false)
    }
  }

  if (status === 'rated') {
    // The instance that submitted thanks the user; the other shows nothing.
    return submitted ? <span className="text-xs text-muted">{t('groups.feedback.thanks')}</span> : null
  }
  // Still checking, or not yet known — show nothing until we know it's unrated.
  if (status !== 'unrated') return null

  return (
    <div className="flex items-center gap-1.5 flex-wrap">
      <span className="text-xs text-muted">{t('groups.feedback.prompt')}</span>
      <Button variant="ghost" size="xs" disabled={busy} onClick={() => rate('GOOD_FIT')}>
        {t('groups.feedback.good')}
      </Button>
      <Button variant="ghost" size="xs" disabled={busy} onClick={() => rate('BAD_FIT')}>
        {t('groups.feedback.bad')}
      </Button>
    </div>
  )
}
