import client, { ApiSuccess } from './client'

export interface University {
  id: string
  name: string
  shortCode: string
  country: string
}

export interface Department {
  id: string
  universityId: string
  name: string
  shortCode: string
}

export interface Course {
  id: string
  universityId: string
  code: string
  name: string
  creditPoints: number | null
  description: string | null
  departmentIds: string[]
}

export type TermKind = 'FALL' | 'SPRING' | 'SUMMER'

export interface Term {
  id: string
  universityId: string
  code: string
  name: string
  kind: TermKind
  academicYear: number
  startsOn: string
  endsOn: string
}

export interface Offering {
  id: string
  courseId: string
  termId: string
  universityId: string
  courseCode: string
  courseName: string
  termCode: string
}

export async function getUniversities(): Promise<University[]> {
  const res = await client.get<ApiSuccess<University[]>>('/catalog/universities')
  return res.data.data
}

export async function getDepartments(universityId: string): Promise<Department[]> {
  const res = await client.get<ApiSuccess<Department[]>>(`/catalog/universities/${universityId}/departments`)
  return res.data.data
}

export async function getTerms(universityId: string): Promise<Term[]> {
  const res = await client.get<ApiSuccess<Term[]>>(`/catalog/universities/${universityId}/terms`)
  return res.data.data
}

export async function getCurrentTerm(universityId: string): Promise<Term | null> {
  try {
    const res = await client.get<ApiSuccess<Term>>(`/catalog/universities/${universityId}/current-term`)
    return res.data.data
  } catch (e: any) {
    if (e?.response?.status === 404) return null
    throw e
  }
}

export async function getCoursesByDepartment(departmentId: string, termId?: string): Promise<Course[]> {
  const params = termId ? { termId } : undefined
  const res = await client.get<ApiSuccess<Course[]>>(
    `/catalog/departments/${departmentId}/courses`,
    { params }
  )
  return res.data.data
}

export async function getCourse(courseId: string): Promise<Course> {
  const res = await client.get<ApiSuccess<Course>>(`/catalog/courses/${courseId}`)
  return res.data.data
}

export async function getOfferingsForCourse(courseId: string): Promise<Offering[]> {
  const res = await client.get<ApiSuccess<Offering[]>>(`/catalog/courses/${courseId}/offerings`)
  return res.data.data
}
