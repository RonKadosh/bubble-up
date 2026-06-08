import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '../store/authStore'
import { useQuizPromptStore } from '../store/quizPromptStore'
import { useOnboardingStore, isOnboarded } from '../store/onboardingStore'
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
  // Gate on the shared onboarding store (no dedicated HTTP — it's already hydrated
  // app-wide). Inert during L1–L4 so the first question only pops at L5.
  const ensureOnboarding = useOnboardingStore((s) => s.ensureHydrated)
  const refreshOnboarding = useOnboardingStore((s) => s.refresh)
  const onbStatus = useOnboardingStore((s) => s.status)
  const wizardLevel = useOnboardingStore((s) => s.wizardLevel)
  const onboarded = onbStatus ? isOnboarded(onbStatus) : false
  const active = onboarded || wizardLevel === 5
  const [question, setQuestion] = useState<NextQuestion | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<number | null>(null)

  useEffect(() => { ensureOnboarding() }, [ensureOnboarding])

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
    // Auto-poll only once onboarded (ongoing Daily Drops). During the wizard the
    // first question must NOT pop on its own — it surfaces only when the user
    // clicks "Answer a Daily Drop" on L5 (the openSignal trigger below).
    if (!accessToken || !onboarded) {
      clearTimer()
      setQuestion(null)
      return
    }
    poll()
    return clearTimer
  }, [accessToken, onboarded, poll, clearTimer])

  // Explicit trigger: the L5 "Answer a Daily Drop" button (or any post-onboarding
  // re-open). Polls to surface a question; no-op if none is available.
  const openSignal = useQuizPromptStore((s) => s.openSignal)
  useEffect(() => {
    if (openSignal > 0 && accessToken && active) poll()
  }, [openSignal, accessToken, active, poll])

  async function onAnswer(answerId: string) {
    if (!question?.questionId) return
    setSubmitting(true)
    setError(null)
    try {
      await submitAnswer(question.questionId, answerId)
      const scheduleAtIso = question.nextAvailableAt
      setQuestion(null)
      if (onboarded) {
        // Ongoing Daily Drops — reschedule the next poll.
        scheduleAt(scheduleAtIso, poll)
      } else {
        // L5 first answer — don't auto-reschedule (no surprise pop while still on
        // L5); just refresh so the wizard's `answeredQuestions` updates and Finish
        // enables.
        refreshOnboarding()
      }
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
            wrap
            disabled={submitting}
            onClick={() => onAnswer(opt.id)}
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
