import client from './client'

export interface GroupFile {
  id: string
  groupId: string
  uploaderId: string
  originalName: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
}

export async function getFiles(groupId: string): Promise<GroupFile[]> {
  const res = await client.get<{ success: boolean; data: GroupFile[] }>(`/groups/${groupId}/files`)
  return res.data.data
}

export async function uploadFile(groupId: string, file: File): Promise<GroupFile> {
  const fd = new FormData()
  fd.append('file', file)
  const res = await client.post<{ success: boolean; data: GroupFile }>(
    `/groups/${groupId}/files`,
    fd,
    { headers: { 'Content-Type': 'multipart/form-data' } }
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

export async function deleteFile(groupId: string, fileId: string): Promise<void> {
  await client.delete(`/groups/${groupId}/files/${fileId}`)
}
