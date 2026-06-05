import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  GroupFile,
  GroupFolder,
  createFolder,
  deleteFile,
  deleteFolder,
  downloadFile,
  fileIcon,
  formatBytes,
  getFiles,
  isPreviewable,
  listFolders,
  uploadFile,
} from '../../api/files'
import { describeError } from '../../api/errors'
import { Button } from '../../components/Button'
import { FileViewer } from './FileViewer'

interface FilesPanelProps {
  groupId: string
  isOwner: boolean
  /** Whether the current user is a member of this group. Non-members get 403 on
   *  every files endpoint, so we skip fetching entirely for them. */
  isMember: boolean
  meId: string | null
  onError: (msg: string) => void
  /** True when this panel is *not* the bento focus (i.e. minimized). */
  compact?: boolean
  /**
   * When set, the browser view should auto-navigate to (and preview if previewable)
   * this file id once. Used by ChatPanel's FileLinkCard click handler via
   * GroupsPage to "open" a shared file.
   */
  pendingOpenFileId?: string | null
  /** Called after we've consumed a pendingOpenFileId so GroupsPage can clear it. */
  onPendingOpened?: () => void
}

/**
 * Routes to {@link FilesAgendaView} (compact) or {@link FilesBrowserView}
 * (focused) based on the bento focus flag. Both sub-views own their own data
 * fetch and never share state — switching focus remounts.
 */
export function FilesPanel(props: FilesPanelProps) {
  if (props.compact) return <FilesAgendaView {...props} />
  return <FilesBrowserView {...props} />
}

// ---------------------------------------------------------------------------
// FilesAgendaView — compact: top-N most recently uploaded, read-only.
// ---------------------------------------------------------------------------

const AGENDA_MAX_VISIBLE = 5

function FilesAgendaView({ groupId, isMember, onError }: FilesPanelProps) {
  const { t } = useTranslation()
  const [files, setFiles] = useState<GroupFile[]>([])

  useEffect(() => {
    if (!isMember) { setFiles([]); return }
    let cancelled = false
    getFiles(groupId)
      .then((all) => {
        if (cancelled) return
        const sorted = [...all].sort((a, b) => b.uploadedAt.localeCompare(a.uploadedAt))
        setFiles(sorted)
      })
      .catch(() => {
        if (!cancelled) {
          setFiles([])
          onError(t('groups.error.loadFiles'))
        }
      })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, isMember])

  const visible = files.slice(0, AGENDA_MAX_VISIBLE)
  const hiddenCount = Math.max(0, files.length - AGENDA_MAX_VISIBLE)

  return (
    <div className="flex-1 flex flex-col overflow-y-auto p-3 gap-2">
      {files.length === 0 ? (
        <p className="text-sm text-muted">{t('groups.files.empty')}</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {visible.map((f) => (
            <li
              key={f.id}
              className="flex items-center gap-3 bg-surface border border-line rounded-2xl px-3 py-2"
            >
              <span className="text-lg" aria-hidden>{fileIcon(f.contentType)}</span>
              <div className="flex-1 min-w-0">
                <p className="text-sm truncate" title={f.originalName}>{f.originalName}</p>
                <p className="text-xs text-muted">{formatBytes(f.sizeBytes)}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
      {hiddenCount > 0 && (
        <p className="text-xs text-muted text-center mt-1">
          {t('groups.files.moreFiles', { count: hiddenCount })}
        </p>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// FilesBrowserView — focused: folder tree + files in current folder + viewer.
// ---------------------------------------------------------------------------

interface BreadcrumbEntry {
  id: string | null
  name: string
}

function FilesBrowserView({
  groupId,
  isOwner,
  isMember,
  meId,
  onError,
  pendingOpenFileId,
  onPendingOpened,
}: FilesPanelProps) {
  const { t } = useTranslation()
  const [folders, setFolders] = useState<GroupFolder[]>([])
  const [files, setFiles] = useState<GroupFile[]>([])
  const [currentFolderId, setCurrentFolderId] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [creatingFolder, setCreatingFolder] = useState(false)
  const [newFolderName, setNewFolderName] = useState('')
  const [viewerFile, setViewerFile] = useState<GroupFile | null>(null)
  const [viewerFullscreen, setViewerFullscreen] = useState(false)
  const folderById = useMemo(() => {
    const m = new Map<string, GroupFolder>()
    for (const f of folders) m.set(f.id, f)
    return m
  }, [folders])

  // ---- data loaders ----
  async function loadFolders() {
    try {
      setFolders(await listFolders(groupId))
    } catch {
      setFolders([])
      onError(t('groups.error.loadFolders'))
    }
  }
  async function loadFiles(folderId: string | null) {
    try {
      setFiles(await getFiles(groupId, folderId ? { folderId } : { scope: 'root' }))
    } catch {
      setFiles([])
      onError(t('groups.error.loadFiles'))
    }
  }

  useEffect(() => {
    if (!isMember) { setFolders([]); setFiles([]); return }
    loadFolders()
    loadFiles(currentFolderId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [groupId, currentFolderId, isMember])

  // Auto-open a file when navigated from the chat link card. The pending id
  // can point at any folder — we (a) fetch the file's metadata, (b) navigate
  // into its folder, and (c) open the viewer if previewable. Non-previewable
  // types stay parked in the folder so the user can choose to download.
  //
  // Dedupe uses a `stale` flag local to each effect call (not a persistent
  // ref) so that re-clicking the same file link works AND so that StrictMode's
  // dev mount-cleanup-remount doesn't lose the open: the first invocation is
  // cancelled, the second one (with a fresh `stale=false`) finishes the job.
  useEffect(() => {
    if (!pendingOpenFileId || !isMember) return
    const targetId = pendingOpenFileId
    let stale = false
    getFiles(groupId)
      .then((all) => {
        if (stale) return
        const f = all.find((x) => x.id === targetId)
        if (!f) {
          onError(t('groups.error.loadFiles'))
          return
        }
        setCurrentFolderId(f.folderId ?? null)
        if (isPreviewable(f.contentType)) {
          setViewerFile(f)
        }
      })
      .catch(() => { if (!stale) onError(t('groups.error.loadFiles')) })
      .finally(() => { if (!stale) onPendingOpened?.() })
    return () => { stale = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pendingOpenFileId])

  // ---- breadcrumb ----
  const breadcrumb: BreadcrumbEntry[] = useMemo(() => {
    const chain: BreadcrumbEntry[] = [{ id: null, name: t('groups.files.root') }]
    let cursor = currentFolderId
    const visited = new Set<string>()
    const stack: BreadcrumbEntry[] = []
    while (cursor && !visited.has(cursor)) {
      visited.add(cursor)
      const f = folderById.get(cursor)
      if (!f) break
      stack.unshift({ id: f.id, name: f.name })
      cursor = f.parentId
    }
    return [...chain, ...stack]
  }, [currentFolderId, folderById, t])

  // ---- subfolders inside the current folder ----
  const subfolders = useMemo(
    () => folders.filter((f) => f.parentId === currentFolderId)
                 .sort((a, b) => a.name.localeCompare(b.name)),
    [folders, currentFolderId]
  )

  // ---- handlers ----
  async function handleUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0]
    if (!f) return
    setUploading(true)
    try {
      await uploadFile(groupId, f, currentFolderId)
      await loadFiles(currentFolderId)
    } catch (err) {
      onError(describeError(err, t, {
        FILE_TOO_LARGE: 'groups.error.fileTooLarge',
        FILE_TYPE_BLOCKED: 'groups.error.fileBlocked',
        NOT_GROUP_MEMBER: 'groups.error.uploadNotMember',
        FOLDER_NOT_FOUND: 'groups.error.folderGone',
        TOO_MANY_REQUESTS: 'common.rateLimited',
      }, 'groups.error.uploadGeneric'))
    } finally {
      setUploading(false)
      e.target.value = ''
    }
  }

  async function handleCreateFolder() {
    const name = newFolderName.trim()
    if (!name) return
    try {
      await createFolder(groupId, name, currentFolderId)
      setNewFolderName('')
      setCreatingFolder(false)
      await loadFolders()
    } catch (err) {
      onError(describeError(err, t, {
        FOLDER_NAME_TAKEN: 'groups.error.folderNameTaken',
        FOLDER_NAME_INVALID: 'groups.error.folderNameInvalid',
      }, 'groups.error.folderCreateGeneric'))
    }
  }

  async function handleDeleteFolder(folder: GroupFolder) {
    if (!confirm(t('groups.files.confirmDeleteFolder', { name: folder.name }))) return
    try {
      await deleteFolder(groupId, folder.id)
      await loadFolders()
    } catch (err) {
      onError(describeError(err, t, {
        FOLDER_NOT_EMPTY: 'groups.error.folderNotEmpty',
        NOT_FOLDER_CREATOR_OR_GROUP_OWNER: 'groups.error.folderDeleteForbidden',
      }, 'groups.error.folderDeleteGeneric'))
    }
  }

  async function handleDeleteFile(f: GroupFile) {
    if (!confirm(t('groups.files.confirmDelete', { name: f.originalName }))) return
    try {
      await deleteFile(groupId, f.id)
      if (viewerFile?.id === f.id) setViewerFile(null)
      await loadFiles(currentFolderId)
    } catch (err) {
      onError(describeError(err, t, {
        NOT_FILE_UPLOADER_OR_GROUP_OWNER: 'groups.error.deleteFileForbidden',
      }, 'groups.error.deleteFileGeneric'))
    }
  }

  function handleOpenFile(f: GroupFile) {
    if (isPreviewable(f.contentType)) {
      setViewerFile(f)
      return
    }
    downloadFile(groupId, f.id, f.originalName).catch(() => onError(t('groups.error.downloadGeneric')))
  }

  // ---- viewer overlay (fullscreen) is rendered into a fixed shell from the
  // same component tree so it sits above the bento. Inline viewer renders
  // inside the cell.
  const viewerPane = viewerFile && (
    <FileViewer
      groupId={groupId}
      file={viewerFile}
      isFullscreen={viewerFullscreen}
      onToggleFullscreen={() => setViewerFullscreen((v) => !v)}
      onClose={() => { setViewerFile(null); setViewerFullscreen(false) }}
    />
  )

  // When the inline viewer is shown, hide the listing to give it the full
  // tile (the user is reading; the file picker is two clicks away).
  if (viewerFile && !viewerFullscreen) {
    return viewerPane
  }

  return (
    <>
      <div className="flex-1 min-h-0 flex flex-col overflow-hidden">
        {/* Toolbar: breadcrumb + actions */}
        <div className="px-3 py-2 border-b border-line flex items-center gap-2 flex-wrap shrink-0">
          <nav className="flex items-center gap-1 text-xs text-secondary flex-1 min-w-0 flex-wrap">
            {breadcrumb.map((b, idx) => {
              const last = idx === breadcrumb.length - 1
              return (
                <span key={b.id ?? 'root'} className="flex items-center gap-1 min-w-0">
                  {idx > 0 && <span className="text-muted">/</span>}
                  {last ? (
                    <span className="font-semibold text-base truncate" title={b.name}>{b.name}</span>
                  ) : (
                    <button
                      type="button"
                      onClick={() => setCurrentFolderId(b.id)}
                      className="hover:text-base hover:underline truncate"
                      title={b.name}
                    >
                      {b.name}
                    </button>
                  )}
                </span>
              )
            })}
          </nav>
          <div className="flex items-center gap-2 shrink-0">
            <Button
              size="xs"
              variant="cell"
              onClick={() => setCreatingFolder((v) => !v)}
            >
              {t('groups.files.newFolder')}
            </Button>
            <label className="inline-flex">
              <span
                className={`inline-flex items-center justify-center gap-2 rounded-full bubble-pop text-xs font-semibold cursor-pointer px-3 py-1.5 border border-line bg-surface-muted text-base hover:bg-surface-hover hover:border-line-strong ${uploading ? 'opacity-60 pointer-events-none' : ''}`}
              >
                {uploading ? t('groups.files.uploading') : t('groups.files.uploadButton')}
                <input type="file" className="hidden" onChange={handleUpload} disabled={uploading} />
              </span>
            </label>
          </div>
        </div>

        {creatingFolder && (
          <div className="px-3 py-2 border-b border-line bg-surface-muted flex items-center gap-2 shrink-0">
            <input
              type="text"
              autoFocus
              placeholder={t('groups.files.newFolderPlaceholder')}
              value={newFolderName}
              onChange={(e) => setNewFolderName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleCreateFolder()
                if (e.key === 'Escape') { setCreatingFolder(false); setNewFolderName('') }
              }}
              className="flex-1 border border-line bg-surface rounded-full px-3 py-1 text-sm focus:outline-none focus:border-primary-400"
              maxLength={120}
            />
            <Button size="xs" onClick={handleCreateFolder} disabled={!newFolderName.trim()}>
              {t('common.create')}
            </Button>
            <Button size="xs" variant="ghost" onClick={() => { setCreatingFolder(false); setNewFolderName('') }}>
              {t('common.cancel')}
            </Button>
          </div>
        )}

        <p className="text-xs text-muted px-3 pt-2">{t('groups.files.uploadHint')}</p>

        {/* Listing */}
        <div className="flex-1 min-h-0 overflow-y-auto p-3 space-y-2">
          {subfolders.length === 0 && files.length === 0 && (
            <p className="text-sm text-muted">{t('groups.files.emptyFolder')}</p>
          )}
          {subfolders.map((folder) => {
            const canDelete = folder.createdById === meId || isOwner
            return (
              <div
                key={folder.id}
                className="bg-surface border border-line rounded-3xl p-3 shadow-themed flex items-center gap-3 bubble-pop"
              >
                <span className="text-xl" aria-hidden>📁</span>
                <button
                  type="button"
                  onClick={() => setCurrentFolderId(folder.id)}
                  className="text-primary-600 hover:underline text-sm text-start flex-1 truncate"
                  title={folder.name}
                >
                  {folder.name}
                </button>
                {canDelete && (
                  <Button variant="danger" size="xs" onClick={() => handleDeleteFolder(folder)}>
                    {t('common.delete')}
                  </Button>
                )}
              </div>
            )
          })}
          {files.map((f) => {
            const canDelete = f.uploaderId === meId || isOwner
            return (
              <div
                key={f.id}
                className="bg-surface border border-line rounded-3xl p-3 shadow-themed flex items-center gap-3 bubble-pop"
              >
                <span className="text-xl" aria-hidden>{fileIcon(f.contentType)}</span>
                <button
                  onClick={() => handleOpenFile(f)}
                  className="text-primary-600 hover:underline text-sm text-start flex-1 truncate"
                  title={isPreviewable(f.contentType) ? t('groups.files.previewTitle') : t('groups.files.downloadTitle')}
                >
                  {f.originalName}
                </button>
                <span className="text-xs text-muted shrink-0">{formatBytes(f.sizeBytes)}</span>
                {canDelete && (
                  <Button variant="danger" size="xs" onClick={() => handleDeleteFile(f)}>
                    {t('common.delete')}
                  </Button>
                )}
              </div>
            )
          })}
        </div>
      </div>

      {/* Fullscreen overlay — sibling of the listing so it sits above. */}
      {viewerFile && viewerFullscreen && (
        <div className="fixed inset-0 z-50 bg-base/95 backdrop-blur-sm flex flex-col">
          {viewerPane}
        </div>
      )}
    </>
  )
}
