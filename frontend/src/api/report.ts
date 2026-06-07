import client, { ApiSuccess } from './client'

export type ReportCategory = 'ABUSE' | 'HARASSMENT' | 'SPAM' | 'SAFETY' | 'TECHNICAL_ISSUE' | 'OTHER'
export type ReportStatus = 'PENDING' | 'RESOLVED' | 'DISMISSED'

export const REPORT_CATEGORIES: ReportCategory[] = [
  'ABUSE',
  'HARASSMENT',
  'SPAM',
  'SAFETY',
  'TECHNICAL_ISSUE',
  'OTHER',
]

export interface CreateReportPayload {
  category: ReportCategory
  subject: string
  description: string
}

export interface Report {
  id: string
  category: ReportCategory
  subject: string
  description: string
  status: ReportStatus
  hasAttachment: boolean
  createdAt: string
}

export async function submitReport(payload: CreateReportPayload): Promise<Report> {
  const res = await client.post<ApiSuccess<Report>>('/reports', payload)
  return res.data.data
}

/** Optional follow-up: attach a single screenshot to a report you just created. */
export async function attachReportImage(reportId: string, file: File): Promise<Report> {
  const form = new FormData()
  form.append('file', file)
  const res = await client.post<ApiSuccess<Report>>(`/reports/${reportId}/image`, form)
  return res.data.data
}
