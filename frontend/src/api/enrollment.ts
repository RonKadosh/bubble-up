import client, { ApiSuccess } from './client'

export interface Enrollment {
  id: string
  userId: string
  offeringId: string
  courseId: string | null
  courseCode: string | null
  courseName: string | null
  termId: string | null
  termCode: string | null
  createdAt: string
}

export async function listMyEnrollments(): Promise<Enrollment[]> {
  const res = await client.get<ApiSuccess<Enrollment[]>>('/enrollments/me')
  return res.data.data
}

export async function listMyCurrentEnrollments(): Promise<Enrollment[]> {
  const res = await client.get<ApiSuccess<Enrollment[]>>('/enrollments/me/current')
  return res.data.data
}

export async function enrollInCourse(courseId: string): Promise<Enrollment> {
  const res = await client.post<ApiSuccess<Enrollment>>('/enrollments', { courseId })
  return res.data.data
}

export async function unenroll(enrollmentId: string): Promise<void> {
  await client.delete(`/enrollments/${enrollmentId}`)
}
