import client, { ApiSuccess } from './client'

export interface GroupFile {
  id: string
  groupId: string
  uploaderId: string
  folderId: string | null
  originalName: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
}

export interface GroupFolder {
  id: string
  groupId: string
  parentId: string | null
  name: string
  createdById: string
  createdAt: string
}

/**
 * List group files. By default returns every file (used by the agenda + link picker).
 * Pass {@code folderId} to scope to a folder, or {@code scope='root'} to scope to
 * files with no folder. {@code folderId} wins when both are present.
 */
export async function getFiles(
  groupId: string,
  opts?: { folderId?: string | null; scope?: 'root' }
): Promise<GroupFile[]> {
  const params: Record<string, string> = {}
  if (opts?.folderId) params.folderId = opts.folderId
  else if (opts?.scope === 'root') params.scope = 'root'
  const res = await client.get<ApiSuccess<GroupFile[]>>(`/groups/${groupId}/files`, { params })
  return res.data.data
}

/** One-shot metadata fetch — used by FileLinkCard to resolve a shared file. */
export async function getFile(groupId: string, fileId: string): Promise<GroupFile> {
  const res = await client.get<ApiSuccess<GroupFile>>(`/groups/${groupId}/files/${fileId}`)
  return res.data.data
}

export async function uploadFile(
  groupId: string,
  file: File,
  folderId?: string | null
): Promise<GroupFile> {
  const fd = new FormData()
  fd.append('file', file)
  const params: Record<string, string> = {}
  if (folderId) params.folderId = folderId
  const res = await client.post<ApiSuccess<GroupFile>>(
    `/groups/${groupId}/files`,
    fd,
    { headers: { 'Content-Type': 'multipart/form-data' }, params }
  )
  return res.data.data
}

export async function downloadFile(groupId: string, fileId: string, suggestedName: string): Promise<void> {
  const res = await client.get(`/groups/${groupId}/files/${fileId}/download`, { responseType: 'blob' })
  const blob = res.data as Blob
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = suggestedName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/**
 * Fetch bytes as an object URL for inline viewing (PDF / image). Caller MUST call
 * {@code revoke()} when the viewer unmounts to release the blob.
 */
export async function fetchFileBlobUrl(
  groupId: string,
  fileId: string
): Promise<{ url: string; contentType: string; revoke(): void }> {
  const res = await client.get(`/groups/${groupId}/files/${fileId}/download`, {
    responseType: 'blob',
    params: { disposition: 'inline' },
  })
  const blob = res.data as Blob
  const url = URL.createObjectURL(blob)
  return {
    url,
    contentType: blob.type,
    revoke: () => URL.revokeObjectURL(url),
  }
}

export async function deleteFile(groupId: string, fileId: string): Promise<void> {
  await client.delete(`/groups/${groupId}/files/${fileId}`)
}

// ---------------------------------------------------------------------------
// Folders
// ---------------------------------------------------------------------------

export async function listFolders(groupId: string): Promise<GroupFolder[]> {
  const res = await client.get<ApiSuccess<GroupFolder[]>>(`/groups/${groupId}/folders`)
  return res.data.data
}

export async function createFolder(
  groupId: string,
  name: string,
  parentId: string | null
): Promise<GroupFolder> {
  const res = await client.post<ApiSuccess<GroupFolder>>(`/groups/${groupId}/folders`, {
    name,
    parentId,
  })
  return res.data.data
}

export async function deleteFolder(groupId: string, folderId: string): Promise<void> {
  await client.delete(`/groups/${groupId}/folders/${folderId}`)
}

// ---------------------------------------------------------------------------
// Display helpers
// ---------------------------------------------------------------------------

/** Format a byte count to a short human label (B / KB / MB). Co-located with `sizeBytes`. */
export function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / (1024 * 1024)).toFixed(1)} MB`
}

/** True for content types we can render inline (PDF + images). */
export function isPreviewable(contentType: string): boolean {
  if (!contentType) return false
  return contentType === 'application/pdf' || contentType.startsWith('image/')
}

/** Emoji icon for a file row, matched to its content type. */
export function fileIcon(contentType: string): string {
  if (!contentType) return '📄'
  if (contentType === 'application/pdf') return '📕'
  if (contentType.startsWith('image/')) return '🖼️'
  if (contentType.startsWith('video/')) return '🎬'
  if (contentType.startsWith('audio/')) return '🎵'
  if (contentType.startsWith('text/')) return '📝'
  return '📄'
}
