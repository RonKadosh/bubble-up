import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '../../components/Button'
import { submitMatchFeedback, getMatchFeedbackStatus, type FeedbackSentiment } from '../../api/matching'

/**
 * "Is this bubble a good fit for you?" — a lightweight thumbs control shown in the
 * group view. Asked ONCE per user per bubble: on mount it checks whether the caller
 * already rated this bubble (server-persisted) and stays hidden if so. On click it
 * posts the verdict (the backend keeps the first vote) and shows a thanks. Best-effort:
 * a failed submit is silent. Remount per bubble (key on group id).
 */
export function MatchFeedbackControl({ groupId }: { groupId: string }) {
  const { t } = useTranslation()
  // 'loading' until we know; 'unrated' shows the prompt; 'rated' hides it permanently.
  const [phase, setPhase] = useState<'loading' | 'unrated' | 'rated'>('loading')
  const [submitted, setSubmitted] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    getMatchFeedbackStatus(groupId)
      .then((s) => { if (!cancelled) setPhase(s.rated ? 'rated' : 'unrated') })
      .catch(() => { if (!cancelled) setPhase('unrated') })   // best-effort: show on failure
    return () => { cancelled = true }
  }, [groupId])

  async function rate(sentiment: FeedbackSentiment) {
    if (busy || submitted) return
    setBusy(true)
    try {
      await submitMatchFeedback(groupId, sentiment)
      setSubmitted(true)
    } catch {
      /* best-effort — silent */
    } finally {
      setBusy(false)
    }
  }

  if (submitted) {
    return <span className="text-xs text-muted">{t('groups.feedback.thanks')}</span>
  }
  // Already rated in a prior session, or still checking — show nothing.
  if (phase !== 'unrated') return null

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
