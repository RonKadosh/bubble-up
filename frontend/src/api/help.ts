import client, { ApiSuccess } from './client'

export interface HelpAction {
  label: string
  route: string
}

export interface HelpTopic {
  id: string
  category: string
  audience: 'STUDENT' | 'EXPERT'
  title: string
  summary: string
  steps: string[]
  actions: HelpAction[]
  tags: string[]
}

export interface HelpAskResponse {
  answer: string
  source: 'LOCAL' | 'OPENAI' | 'CACHE'
  topics: HelpTopic[]
  actions: HelpAction[]
}

export interface HelpQuestion {
  id: string
  question: string
  answer: string
  source: 'LOCAL' | 'OPENAI' | 'CACHE'
  locale: string
  currentPath: string
  createdAt: string
}

export async function getHelpTopics(q?: string, currentPath?: string): Promise<HelpTopic[]> {
  const res = await client.get<ApiSuccess<HelpTopic[]>>('/help/topics', {
    params: {
      q: q?.trim() || undefined,
      currentPath: currentPath || undefined,
    },
  })
  return res.data.data
}

export async function askHelp(question: string, locale: string, currentPath: string): Promise<HelpAskResponse> {
  const res = await client.post<ApiSuccess<HelpAskResponse>>('/help/ask', {
    question,
    locale,
    currentPath,
  })
  return res.data.data
}

export async function getHelpQuestions(q?: string): Promise<HelpQuestion[]> {
  const res = await client.get<ApiSuccess<HelpQuestion[]>>('/help/questions', {
    params: {
      q: q?.trim() || undefined,
      limit: 8,
    },
  })
  return res.data.data
}
