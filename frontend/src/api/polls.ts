import client, { ApiSuccess } from './client'

export interface PollOption {
  id: string
  text: string
  position: number
  voteCount: number
  voters: string[]
}

export interface Poll {
  id: string
  roomId: string
  messageId: string
  question: string
  allowMultiple: boolean
  closedAt: string | null
  createdByUserId: string
  createdAt: string
  options: PollOption[]
  totalVotes: number
  /** The current caller's optionId picks. */
  myVote: string[]
}

/** WebSocket payload broadcast on {@code /topic/chat/{roomId}/polls}. */
export interface PollUpdate {
  pollId: string
  optionCounts: Record<string, number>
  optionVoters: Record<string, string[]>
  totalVotes: number
  closedAt: string | null
}

export async function createPoll(
  roomId: string,
  payload: { question: string; options: string[]; allowMultiple?: boolean }
): Promise<Poll> {
  const res = await client.post<ApiSuccess<Poll>>(
    `/chat/rooms/${roomId}/polls`,
    payload
  )
  return res.data.data
}

export async function getPoll(pollId: string): Promise<Poll> {
  const res = await client.get<ApiSuccess<Poll>>(`/chat/polls/${pollId}`)
  return res.data.data
}

export async function votePoll(pollId: string, optionIds: string[]): Promise<Poll> {
  const res = await client.post<ApiSuccess<Poll>>(
    `/chat/polls/${pollId}/vote`,
    { optionIds }
  )
  return res.data.data
}

export async function closePoll(pollId: string): Promise<Poll> {
  const res = await client.post<ApiSuccess<Poll>>(`/chat/polls/${pollId}/close`)
  return res.data.data
}
