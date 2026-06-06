import client, { ApiSuccess } from './client'

// ─── Shared types ────────────────────────────────────────────────────────────

export type UserRole = 'STUDENT' | 'EXPERT' | 'ADMIN'
export type UserStatus = 'ACTIVE' | 'SUSPENDED' | 'BANNED'
export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED'
export type Visibility = 'PUBLIC' | 'PRIVATE'
export type GroupStatus = 'ACTIVE' | 'ARCHIVED' | 'SUSPENDED'
export type TermKind = 'FALL' | 'SPRING' | 'SUMMER' | 'WINTER' | 'OTHER'
export type MembershipRole = 'OWNER' | 'MEMBER'

export interface PageResponse<T> {
  content: T[]
  currentPage: number
  totalPages: number
  totalElements: number
  pageSize: number
  first: boolean
  last: boolean
}

// ─── Overview ────────────────────────────────────────────────────────────────

export interface OverviewKpis {
  totalUsers: number
  totalGroups: number
  totalCourses: number
  verifiedExperts: number
  newUsersThisWeek: number
  newUsersLastWeek: number
  weekOverWeekDelta: number
}

export type ActivityKind = 'USER_REGISTERED' | 'GROUP_CREATED' | 'COURSE_CREATED'

export interface ActivityEntry {
  kind: ActivityKind
  id: string
  label: string
  at: string
}

export interface Overview {
  kpis: OverviewKpis
  roleDistribution: Record<UserRole, number>
  recentActivity: ActivityEntry[]
}

export async function getOverview(): Promise<Overview> {
  const res = await client.get<ApiSuccess<Overview>>('/admin/overview')
  return res.data.data
}

// ─── Users ───────────────────────────────────────────────────────────────────

export interface AdminUser {
  id: string
  email: string
  role: UserRole
  displayName: string
  bio: string | null
  avatarFileId: string | null
  universityId: string | null
  departmentId: string | null
  enrollmentYear: number | null
  status: UserStatus
  suspendedUntil: string | null
  statusReason: string | null
  createdAt: string
}

export interface UserSearchParams {
  role?: UserRole
  status?: UserStatus
  q?: string
  createdAfter?: string
  page?: number
  size?: number
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
}

export async function searchUsers(params: UserSearchParams = {}): Promise<PageResponse<AdminUser>> {
  const res = await client.get<ApiSuccess<PageResponse<AdminUser>>>('/admin/users', { params })
  return res.data.data
}

export async function getUser(id: string): Promise<AdminUser> {
  const res = await client.get<ApiSuccess<AdminUser>>(`/admin/users/${id}`)
  return res.data.data
}

export async function changeUserRole(id: string, newRole: UserRole, reason: string): Promise<AdminUser> {
  const res = await client.patch<ApiSuccess<AdminUser>>(`/admin/users/${id}/role`, { newRole, reason })
  return res.data.data
}
export async function suspendUser(id: string, reason: string, suspendedUntil?: string): Promise<AdminUser> {
  const res = await client.post<ApiSuccess<AdminUser>>(`/admin/users/${id}/suspend`, { reason, suspendedUntil })
  return res.data.data
}
export async function banUser(id: string, reason: string): Promise<AdminUser> {
  const res = await client.post<ApiSuccess<AdminUser>>(`/admin/users/${id}/ban`, { reason })
  return res.data.data
}
export async function reactivateUser(id: string, reason: string): Promise<AdminUser> {
  const res = await client.post<ApiSuccess<AdminUser>>(`/admin/users/${id}/reactivate`, { reason })
  return res.data.data
}

// ─── Catalog ─────────────────────────────────────────────────────────────────

export interface University {
  id: string
  name: string
  shortCode: string
  country: string
  createdAt: string
}

export interface Department {
  id: string
  universityId: string
  name: string
  shortCode: string
  createdAt: string
}

export interface Term {
  id: string
  universityId: string
  code: string
  name: string
  kind: TermKind
  academicYear: number
  startsOn: string
  endsOn: string
  createdAt: string
}

export interface Course {
  id: string
  universityId: string
  code: string
  name: string
  creditPoints: number | null
  description: string | null
  createdAt: string
}

export interface Offering {
  id: string
  courseId: string
  termId: string
  createdAt: string
}

export interface CourseDepartmentLink {
  courseId: string
  departmentId: string
  primary: boolean
  createdAt: string
}

export interface CourseDetail {
  course: Course
  offerings: Offering[]
  departmentLinks: CourseDepartmentLink[]
}

// Universities
export async function listUniversities(): Promise<University[]> {
  const res = await client.get<ApiSuccess<University[]>>('/admin/catalog/universities')
  return res.data.data
}
export async function createUniversity(body: { name: string; shortCode: string; country: string }): Promise<University> {
  const res = await client.post<ApiSuccess<University>>('/admin/catalog/universities', body)
  return res.data.data
}
export async function updateUniversity(id: string, body: { name?: string; shortCode?: string; country?: string }): Promise<University> {
  const res = await client.patch<ApiSuccess<University>>(`/admin/catalog/universities/${id}`, body)
  return res.data.data
}
export async function deleteUniversity(id: string, reason: string): Promise<void> {
  await client.delete(`/admin/catalog/universities/${id}`, { data: { reason } })
}

// Departments
export async function listDepartments(universityId: string): Promise<Department[]> {
  const res = await client.get<ApiSuccess<Department[]>>(`/admin/catalog/universities/${universityId}/departments`)
  return res.data.data
}
export async function createDepartment(universityId: string, body: { name: string; shortCode: string }): Promise<Department> {
  const res = await client.post<ApiSuccess<Department>>(`/admin/catalog/universities/${universityId}/departments`, body)
  return res.data.data
}
export async function updateDepartment(id: string, body: { name?: string; shortCode?: string }): Promise<Department> {
  const res = await client.patch<ApiSuccess<Department>>(`/admin/catalog/departments/${id}`, body)
  return res.data.data
}
export async function deleteDepartment(id: string, reason: string): Promise<void> {
  await client.delete(`/admin/catalog/departments/${id}`, { data: { reason } })
}

// Terms
export async function listTerms(universityId: string): Promise<Term[]> {
  const res = await client.get<ApiSuccess<Term[]>>(`/admin/catalog/universities/${universityId}/terms`)
  return res.data.data
}
export async function createTerm(universityId: string, body: { code: string; name: string; kind: TermKind; academicYear: number; startsOn: string; endsOn: string }): Promise<Term> {
  const res = await client.post<ApiSuccess<Term>>(`/admin/catalog/universities/${universityId}/terms`, body)
  return res.data.data
}
export async function rolloverTerm(sourceTermId: string, body: { code: string; name: string; kind: TermKind; academicYear: number; startsOn: string; endsOn: string; reason: string }): Promise<{ term: Term; copiedOfferings: number; archivedGroups: number }> {
  const res = await client.post<ApiSuccess<{ term: Term; copiedOfferings: number; archivedGroups: number }>>(`/admin/catalog/terms/${sourceTermId}/rollover`, body)
  return res.data.data
}
export async function updateTerm(id: string, body: Partial<{ code: string; name: string; kind: TermKind; academicYear: number; startsOn: string; endsOn: string }>): Promise<Term> {
  const res = await client.patch<ApiSuccess<Term>>(`/admin/catalog/terms/${id}`, body)
  return res.data.data
}
export async function deleteTerm(id: string, reason: string): Promise<void> {
  await client.delete(`/admin/catalog/terms/${id}`, { data: { reason } })
}

// Courses
export interface CourseSearchParams {
  universityId?: string
  q?: string
  page?: number
  size?: number
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
}
export async function searchCourses(params: CourseSearchParams = {}): Promise<PageResponse<Course>> {
  const res = await client.get<ApiSuccess<PageResponse<Course>>>('/admin/catalog/courses', { params })
  return res.data.data
}
export async function getCourseDetail(id: string): Promise<CourseDetail> {
  const res = await client.get<ApiSuccess<CourseDetail>>(`/admin/catalog/courses/${id}`)
  return res.data.data
}
export async function createCourse(body: { universityId: string; code: string; name: string; creditPoints?: number; description?: string }): Promise<Course> {
  const res = await client.post<ApiSuccess<Course>>('/admin/catalog/courses', body)
  return res.data.data
}
export async function updateCourse(id: string, body: Partial<{ code: string; name: string; creditPoints: number; description: string }>): Promise<Course> {
  const res = await client.patch<ApiSuccess<Course>>(`/admin/catalog/courses/${id}`, body)
  return res.data.data
}
export async function deleteCourse(id: string, reason: string): Promise<void> {
  await client.delete(`/admin/catalog/courses/${id}`, { data: { reason } })
}

// Course ↔ Department links
export async function linkCourseDepartment(courseId: string, departmentId: string, primary: boolean): Promise<CourseDepartmentLink> {
  const res = await client.post<ApiSuccess<CourseDepartmentLink>>(`/admin/catalog/courses/${courseId}/departments`, { departmentId, primary })
  return res.data.data
}
export async function setCourseDepartmentPrimary(courseId: string, departmentId: string, primary: boolean): Promise<CourseDepartmentLink> {
  const res = await client.patch<ApiSuccess<CourseDepartmentLink>>(`/admin/catalog/courses/${courseId}/departments/${departmentId}`, { primary })
  return res.data.data
}
export async function unlinkCourseDepartment(courseId: string, departmentId: string): Promise<void> {
  await client.delete(`/admin/catalog/courses/${courseId}/departments/${departmentId}`)
}

// Offerings
export async function listOfferingsForCourse(courseId: string): Promise<Offering[]> {
  const res = await client.get<ApiSuccess<Offering[]>>(`/admin/catalog/courses/${courseId}/offerings`)
  return res.data.data
}
export async function createOffering(courseId: string, termId: string): Promise<Offering> {
  const res = await client.post<ApiSuccess<Offering>>(`/admin/catalog/courses/${courseId}/offerings`, { termId })
  return res.data.data
}
export async function deleteOffering(id: string, reason: string): Promise<void> {
  await client.delete(`/admin/catalog/offerings/${id}`, { data: { reason } })
}

// ─── Groups ──────────────────────────────────────────────────────────────────

export interface AdminGroup {
  id: string
  name: string
  description: string | null
  visibility: Visibility
  status: GroupStatus
  offeringId: string
  courseId: string | null
  createdBy: string
  createdAt: string
  memberCount: number
}

export interface GroupMemberRow {
  userId: string
  email: string | null
  displayName: string | null
  role: MembershipRole
  joinedAt: string
}

export interface AdminGroupDetail {
  group: AdminGroup
  members: GroupMemberRow[]
}

export interface GroupSearchParams {
  q?: string
  page?: number
  size?: number
  sortBy?: string
  sortDirection?: 'asc' | 'desc'
}

export async function searchGroups(params: GroupSearchParams = {}): Promise<PageResponse<AdminGroup>> {
  const res = await client.get<ApiSuccess<PageResponse<AdminGroup>>>('/admin/groups', { params })
  return res.data.data
}
export async function getGroupDetail(id: string): Promise<AdminGroupDetail> {
  const res = await client.get<ApiSuccess<AdminGroupDetail>>(`/admin/groups/${id}`)
  return res.data.data
}
export async function deleteGroup(id: string, reason: string): Promise<void> {
  await client.delete(`/admin/groups/${id}`, { data: { reason } })
}
export async function archiveGroup(id: string, reason: string): Promise<AdminGroup> {
  const res = await client.post<ApiSuccess<AdminGroup>>(`/admin/groups/${id}/archive`, { reason })
  return res.data.data
}
export async function activateGroup(id: string, reason: string): Promise<AdminGroup> {
  const res = await client.post<ApiSuccess<AdminGroup>>(`/admin/groups/${id}/activate`, { reason })
  return res.data.data
}
export async function archiveTermGroups(termId: string, reason: string): Promise<{ affectedGroups: number }> {
  const res = await client.post<ApiSuccess<{ affectedGroups: number }>>(`/admin/groups/terms/${termId}/archive`, { reason })
  return res.data.data
}

// ─── Quiz ────────────────────────────────────────────────────────────────────

export interface QuizQuestion {
  id: string
  textEn: string
  textHe: string | null
  orderIndex: number
  active: boolean
}

export interface QuizOption {
  id: string
  questionId: string
  textEn: string
  textHe: string | null
  weightLeader: number
  weightPlanner: number
  weightExpert: number
  weightCreative: number
  weightCommunicator: number
  weightTeamPlayer: number
  weightChallenger: number
}

export interface QuizQuestionDetail {
  question: QuizQuestion
  options: QuizOption[]
}

export async function listQuiz(): Promise<QuizQuestionDetail[]> {
  const res = await client.get<ApiSuccess<QuizQuestionDetail[]>>('/admin/quiz/questions')
  return res.data.data
}
export async function createQuestion(body: { textEn: string; textHe?: string; orderIndex?: number; active?: boolean }): Promise<QuizQuestion> {
  const res = await client.post<ApiSuccess<QuizQuestion>>('/admin/quiz/questions', body)
  return res.data.data
}
export async function updateQuestion(id: string, body: Partial<{ textEn: string; textHe: string; orderIndex: number; active: boolean }>): Promise<QuizQuestion> {
  const res = await client.patch<ApiSuccess<QuizQuestion>>(`/admin/quiz/questions/${id}`, body)
  return res.data.data
}
export async function setQuestionActive(id: string, active: boolean): Promise<QuizQuestion> {
  const res = await client.patch<ApiSuccess<QuizQuestion>>(`/admin/quiz/questions/${id}/active`, { active })
  return res.data.data
}
export async function deleteQuestion(id: string): Promise<void> {
  await client.delete(`/admin/quiz/questions/${id}`)
}
export interface OptionBody {
  textEn: string
  textHe?: string
  weightLeader?: number
  weightPlanner?: number
  weightExpert?: number
  weightCreative?: number
  weightCommunicator?: number
  weightTeamPlayer?: number
  weightChallenger?: number
}
export async function createOption(questionId: string, body: OptionBody): Promise<QuizOption> {
  const res = await client.post<ApiSuccess<QuizOption>>(`/admin/quiz/questions/${questionId}/options`, body)
  return res.data.data
}
export async function updateOption(id: string, body: Partial<OptionBody>): Promise<QuizOption> {
  const res = await client.patch<ApiSuccess<QuizOption>>(`/admin/quiz/options/${id}`, body)
  return res.data.data
}
export async function deleteOption(id: string): Promise<void> {
  await client.delete(`/admin/quiz/options/${id}`)
}

// ─── Expert Verification ─────────────────────────────────────────────────────

export interface AdminExpert {
  id: string
  userId: string
  headline: string
  bio: string | null
  expertiseTags: string[]
  verificationStatus: VerificationStatus
  verifiedAt: string | null
  verifiedBy: string | null
  appliedAt: string
}

export async function listExperts(status: VerificationStatus): Promise<AdminExpert[]> {
  const res = await client.get<ApiSuccess<AdminExpert[]>>('/admin/experts', { params: { status } })
  return res.data.data
}
export async function getExpert(userId: string): Promise<AdminExpert> {
  const res = await client.get<ApiSuccess<AdminExpert>>(`/admin/experts/${userId}`)
  return res.data.data
}
export async function verifyExpert(userId: string, reason?: string): Promise<AdminExpert> {
  const res = await client.post<ApiSuccess<AdminExpert>>(`/admin/experts/${userId}/verify`, { reason })
  return res.data.data
}
export async function rejectExpert(userId: string, reason: string): Promise<void> {
  await client.post(`/admin/experts/${userId}/reject`, { reason })
}
export async function revokeExpert(userId: string, reason: string): Promise<AdminExpert> {
  const res = await client.post<ApiSuccess<AdminExpert>>(`/admin/experts/${userId}/revoke`, { reason })
  return res.data.data
}

// ─── Audit ───────────────────────────────────────────────────────────────────

export interface AuditLogEntry {
  id: string
  actorId: string | null
  actorEmail: string | null
  action: string
  targetType: string
  targetId: string | null
  reason: string | null
  metadata: string | null
  createdAt: string
}

export interface AuditSearchParams {
  action?: string
  targetType?: string
  actorId?: string
  createdAfter?: string
  page?: number
  size?: number
}

export async function searchAudit(params: AuditSearchParams = {}): Promise<PageResponse<AuditLogEntry>> {
  const res = await client.get<ApiSuccess<PageResponse<AuditLogEntry>>>('/admin/audit', { params })
  return res.data.data
}
