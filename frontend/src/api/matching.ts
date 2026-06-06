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

export async function getNextQuestion(): Promise<NextQuestion> {
  const res = await client.get<ApiSuccess<NextQuestion>>('/matching/quiz/next')
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
