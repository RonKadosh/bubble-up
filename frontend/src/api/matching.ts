import client, { ApiSuccess } from './client'

export interface QuizOption {
  id: string
  text: string
}

export interface NextQuestion {
  hasQuestion: boolean
  questionId: string | null
  questionText: string | null
  options: QuizOption[] | null
  /**
   * ISO-8601 instant when the next question becomes available, or null when the
   * pool is exhausted (cap hit / all answered). The client uses this to schedule
   * its next poll without needing to know the server-side cooldown.
   */
  nextAvailableAt: string | null
}

/**
 * Next unanswered quiz question. Pass {@code ignoreCooldown} for the deliberate
 * "build my profile" flow (Settings → Matching) so the user can answer consecutively;
 * the passive Daily Drop omits it and keeps the server-paced cadence.
 */
export async function getNextQuestion(ignoreCooldown = false): Promise<NextQuestion> {
  const res = await client.get<ApiSuccess<NextQuestion>>('/matching/quiz/next', {
    params: ignoreCooldown ? { ignoreCooldown: true } : undefined,
  })
  return res.data.data
}

export async function submitAnswer(questionId: string, answerId: string): Promise<void> {
  await client.post('/matching/quiz/answers', { questionId, answerId })
}

/**
 * The caller's own private "profile strength" in the matching system — the part
 * of confidence they control (Daily Drops answered + activity). Shown to the user
 * only (never about anyone else) so they can track progression toward MATCHED picks.
 */
export interface Reliability {
  /** user_confidence in [0,1]. */
  confidence: number
  /** matched-display threshold in [0,1]; at/above it, Matched picks can appear. */
  threshold: number
  /** confidence ≥ threshold — the user has reached the unlock level. */
  matched: boolean
  answeredQuestions: number
  questionCap: number
}

export async function getReliability(): Promise<Reliability> {
  const res = await client.get<ApiSuccess<Reliability>>('/matching/reliability')
  return res.data.data
}

export type FeedbackSentiment = 'GOOD_FIT' | 'BAD_FIT'

/**
 * The caller's explicit verdict on a bubble's fit, from the group view. The backend
 * resolves the match % at click time (cache or live) and stores it alongside the rating,
 * feeding the admin matching-feedback funnel.
 */
export async function submitMatchFeedback(groupId: string, sentiment: FeedbackSentiment): Promise<void> {
  await client.post('/matching/feedback', { groupId, sentiment })
}

export interface MatchFeedbackStatus {
  rated: boolean
  sentiment: FeedbackSentiment | null
}

/** Whether the caller already rated this bubble's fit — used to show the prompt only once. */
export async function getMatchFeedbackStatus(groupId: string): Promise<MatchFeedbackStatus> {
  const res = await client.get<ApiSuccess<MatchFeedbackStatus>>(`/matching/feedback/${groupId}`)
  return res.data.data
}
