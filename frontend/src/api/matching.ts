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
