import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import { getNextQuestion, submitAnswer, type NextQuestion } from '../api/matching'
import { Button } from './Button'

/**
 * Floating, non-blocking prompt that surfaces one quiz question at a time on a
 * server-configured cadence (`app.matching.quiz-cooldown`).
 *
 * Lifecycle:
 *   - On mount + on auth change → calls GET /api/matching/quiz/next.
 *   - If `hasQuestion` → renders the card; answering hides it and reschedules.
 *   - If on cooldown → stays hidden, reschedules a poll at `nextAvailableAt`.
 *   - If exhausted (cap hit / no unanswered) → stays hidden, no further polling.
 *
 * No skip button: the card is only dismissable by answering. The submit POST
 * fires the backend's async cascade (recompute user profile → group profiles →
 * user match cache), but the UI doesn't wait on that.
 */
const MIN_DELAY_MS = 1_000      // never poll sooner than 1s after a reply
const MAX_DELAY_MS = 24 * 60 * 60 * 1000   // cap setTimeout at 24h so we re-sync daily

export function QuizPrompt() {
  const { t } = useTranslation()
  const accessToken = useAuthStore((s) => s.accessToken)
  const [question, setQuestion] = useState<NextQuestion | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<number | null>(null)

  const clearTimer = useCallback(() => {
    if (timerRef.current != null) {
      window.clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }, [])

  const scheduleAt = useCallback((iso: string | null, poll: () => void) => {
    clearTimer()
    if (!iso) return
    const delta = new Date(iso).getTime() - Date.now()
    const ms = Math.min(MAX_DELAY_MS, Math.max(MIN_DELAY_MS, delta))
    timerRef.current = window.setTimeout(poll, ms)
  }, [clearTimer])

  const poll = useCallback(async () => {
    try {
      const next = await getNextQuestion()
      if (next.hasQuestion) {
        setQuestion(next)
        setError(null)
      } else {
        setQuestion(null)
        scheduleAt(next.nextAvailableAt, poll)
      }
    } catch (e) {
      console.warn('[QuizPrompt] poll failed', e)
      // Quiet on transient errors — try again in a minute.
      clearTimer()
      timerRef.current = window.setTimeout(poll, 60_000)
    }
  }, [scheduleAt, clearTimer])

  useEffect(() => {
    if (!accessToken) {
      clearTimer()
      setQuestion(null)
      return
    }
    poll()
    return clearTimer
  }, [accessToken, poll, clearTimer])

  async function onAnswer(answerId: string) {
    if (!question?.questionId) return
    setSubmitting(true)
    setError(null)
    try {
      await submitAnswer(question.questionId, answerId)
      const scheduleAtIso = question.nextAvailableAt
      setQuestion(null)
      scheduleAt(scheduleAtIso, poll)
    } catch (e) {
      console.warn('[QuizPrompt] submit failed', e)
      setError(t('matching.error'))
    } finally {
      setSubmitting(false)
    }
  }

  if (!accessToken || !question?.hasQuestion) return null

  return (
    <div
      role="dialog"
      aria-label={t('matching.title')}
      className="fixed z-40 bottom-4 start-4 max-w-[min(22rem,calc(100vw-2rem))] bg-surface border border-line shadow-bubble rounded-3xl p-4"
    >
      <div className="flex items-center gap-2 mb-2">
        <span className="inline-block w-2 h-2 rounded-full bg-brand-gradient-strong" aria-hidden />
        <span className="text-xs font-semibold uppercase tracking-wide text-secondary">
          {t('matching.title')}
        </span>
      </div>
      <p className="text-sm text-base mb-3 leading-snug">{question.questionText}</p>
      <div className="flex flex-col gap-2">
        {(question.options ?? []).map((opt) => (
          <Button
            key={opt.id}
            variant="secondary"
            size="sm"
            disabled={submitting}
            onClick={() => onAnswer(opt.id)}
            className="justify-start text-start"
          >
            {opt.text}
          </Button>
        ))}
      </div>
      {submitting && (
        <p className="mt-2 text-xs text-secondary">{t('matching.submitting')}</p>
      )}
      {error && (
        <p className="mt-2 text-xs text-danger">{error}</p>
      )}
    </div>
  )
}
