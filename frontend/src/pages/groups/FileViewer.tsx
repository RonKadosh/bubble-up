import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Document, Page, pdfjs } from 'react-pdf'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import 'react-pdf/dist/Page/TextLayer.css'
import workerSrc from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { GroupFile, downloadFile, fetchFileBlobUrl } from '../../api/files'
import { Button } from '../../components/Button'

// pdf.js worker is loaded as a Vite asset URL. The .mjs variant is what react-pdf
// peers against; .min.js is stale advice. Mismatch with pdfjs-dist would otherwise
// silently hang in "Loading PDF…".
pdfjs.GlobalWorkerOptions.workerSrc = workerSrc

interface FileViewerProps {
  groupId: string
  file: GroupFile
  isFullscreen: boolean
  onToggleFullscreen: () => void
  onClose: () => void
}

/**
 * In-app viewer for PDFs and images. Same component used inline (inside the
 * Files tile) and in fullscreen (wrapped by the parent in a fixed-overlay
 * shell). The viewer doesn't know which mode it's in beyond what the
 * fullscreen toggle button shows.
 */
export function FileViewer({ groupId, file, isFullscreen, onToggleFullscreen, onClose }: FileViewerProps) {
  const { t } = useTranslation()
  const [url, setUrl] = useState<string | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [numPages, setNumPages] = useState<number | null>(null)
  const [pageNumber, setPageNumber] = useState(1)
  const [pageWidth, setPageWidth] = useState<number | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  const isPdf = file.contentType === 'application/pdf'
  const isImage = file.contentType.startsWith('image/')

  // Fetch blob URL on file change. Track the URL we created so cleanup revokes
  // exactly that URL even if the user navigates files quickly.
  useEffect(() => {
    let revoke: (() => void) | null = null
    let cancelled = false
    setUrl(null)
    setLoadError(null)
    setNumPages(null)
    setPageNumber(1)
    fetchFileBlobUrl(groupId, file.id)
      .then((res) => {
        if (cancelled) {
          res.revoke()
          return
        }
        revoke = res.revoke
        setUrl(res.url)
      })
      .catch(() => {
        if (!cancelled) setLoadError(t('groups.files.viewerLoadFailed'))
      })
    return () => {
      cancelled = true
      if (revoke) revoke()
    }
  }, [groupId, file.id, t])

  // Measure container width for fit-width PDF rendering. Re-measure on
  // fullscreen toggle and window resize.
  useEffect(() => {
    function measure() {
      if (!containerRef.current) return
      // Reserve 24px for horizontal padding/scrollbar
      const w = containerRef.current.clientWidth - 24
      setPageWidth(w > 0 ? w : null)
    }
    measure()
    const ro = new ResizeObserver(measure)
    if (containerRef.current) ro.observe(containerRef.current)
    return () => ro.disconnect()
  }, [isFullscreen])

  function nextPage() {
    setPageNumber((p) => Math.min((numPages ?? p) , p + 1))
  }
  function prevPage() {
    setPageNumber((p) => Math.max(1, p - 1))
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-surface-muted">
      {/* Toolbar */}
      <div className="flex items-center gap-2 px-3 py-2 border-b border-line bg-surface shrink-0">
        <span className="text-sm font-semibold truncate flex-1" title={file.originalName}>
          {file.originalName}
        </span>
        {isPdf && numPages !== null && (
          <div className="flex items-center gap-1 text-xs text-secondary">
            <button
              type="button"
              onClick={prevPage}
              disabled={pageNumber <= 1}
              className="px-2 py-1 rounded hover:bg-surface-hover disabled:opacity-40"
              aria-label={t('groups.files.viewerPrevPage')}
            >
              ‹
            </button>
            <span className="tabular-nums">
              {pageNumber} / {numPages}
            </span>
            <button
              type="button"
              onClick={nextPage}
              disabled={pageNumber >= numPages}
              className="px-2 py-1 rounded hover:bg-surface-hover disabled:opacity-40"
              aria-label={t('groups.files.viewerNextPage')}
            >
              ›
            </button>
          </div>
        )}
        <button
          type="button"
          onClick={() => downloadFile(groupId, file.id, file.originalName)}
          className="text-xs text-secondary hover:text-base px-2 py-1 rounded hover:bg-surface-hover"
          aria-label={t('groups.files.viewerDownload')}
          title={t('groups.files.viewerDownload')}
        >
          ⤓
        </button>
        <button
          type="button"
          onClick={onToggleFullscreen}
          className="text-xs text-secondary hover:text-base px-2 py-1 rounded hover:bg-surface-hover"
          aria-label={isFullscreen ? t('groups.files.viewerExitFullscreen') : t('groups.files.viewerFullscreen')}
          title={isFullscreen ? t('groups.files.viewerExitFullscreen') : t('groups.files.viewerFullscreen')}
        >
          {isFullscreen ? '⤬' : '⤢'}
        </button>
        <button
          type="button"
          onClick={onClose}
          className="text-secondary hover:text-base text-lg leading-none px-2 py-0.5 rounded hover:bg-surface-hover"
          aria-label={t('common.close')}
          title={t('common.close')}
        >
          ×
        </button>
      </div>

      {/* Body */}
      <div ref={containerRef} className="flex-1 min-h-0 overflow-auto p-3 flex justify-center">
        {loadError && (
          <div className="self-center text-sm text-danger">{loadError}</div>
        )}
        {!loadError && !url && (
          <div className="self-center text-sm text-muted">{t('groups.files.viewerLoading')}</div>
        )}
        {url && isPdf && (
          <Document
            file={url}
            onLoadSuccess={({ numPages: n }) => setNumPages(n)}
            onLoadError={() => setLoadError(t('groups.files.viewerLoadFailed'))}
            loading={<div className="text-sm text-muted">{t('groups.files.viewerLoading')}</div>}
          >
            <Page
              pageNumber={pageNumber}
              width={pageWidth ?? undefined}
              renderAnnotationLayer={true}
              renderTextLayer={true}
            />
          </Document>
        )}
        {url && isImage && (
          <img
            src={url}
            alt={file.originalName}
            className="max-w-full max-h-full object-contain"
          />
        )}
        {url && !isPdf && !isImage && (
          <div className="self-center flex flex-col items-center gap-3">
            <span className="text-sm text-muted">{t('groups.files.viewerUnsupported')}</span>
            <Button variant="secondary" size="sm" onClick={() => downloadFile(groupId, file.id, file.originalName)}>
              {t('groups.files.viewerDownload')}
            </Button>
          </div>
        )}
      </div>
    </div>
  )
}
