import { Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ExcalidrawSnapshot } from '../../api/room'
import { getWhiteboard, pushWhiteboard } from '../../api/room'
import { subscribeToRoomWhiteboard, publishToRoomWhiteboard, onWsConnect } from '../../api/ws'

// Excalidraw v0.17+ ships its CSS as a separate file you must import or the
// toolbar UI renders as bare HTML on top of the canvas (broken layout, raw
// labels like "Stroke width" leaking through). The package's exports map
// resolves this to the dev or prod stylesheet depending on Vite's mode.
import '@excalidraw/excalidraw/index.css'

// Excalidraw's element types are sealed; we treat scenes as opaque element lists.
type Element = unknown
type ReconcileFn = (
  local: readonly Element[],
  remote: readonly Element[],
  localAppState: unknown,
) => Element[]
type HashFn = (elements: readonly Element[]) => number

// These collab helpers live inside the Excalidraw package. We capture them when
// the lazy chunk resolves rather than importing them statically — a static
// `import { reconcileElements } from '@excalidraw/excalidraw'` would pull the
// whole ~1.5MB bundle into the main chunk and defeat the code-split below.
let reconcileElements: ReconcileFn | null = null
let hashElementsVersion: HashFn | null = null

// Code-split the Excalidraw bundle — it's ~1.5MB gzipped and shouldn't load
// for users who never enter a room.
const Excalidraw = lazy(() =>
  import('@excalidraw/excalidraw').then((m) => {
    reconcileElements = m.reconcileElements as unknown as ReconcileFn
    hashElementsVersion = m.hashElementsVersion as unknown as HashFn
    return { default: m.Excalidraw }
  })
)

interface Props {
  roomId: string
  /**
   * When false the canvas mounts with Excalidraw's view-only mode — the user
   * still sees live strokes but can't draw. Default true (group rooms; expert
   * sessions pass the host-or-granted flag from the parent).
   */
  isWriter?: boolean
}

const DEBOUNCE_MS = 150

type ExcalidrawApi = {
  updateScene: (s: { elements: readonly Element[] }) => void
  getSceneElements: () => readonly Element[]
  getAppState: () => unknown
}

export function WhiteboardPanel({ roomId, isWriter = true }: Props) {
  const { t } = useTranslation()
  const [initialSnapshot, setInitialSnapshot] = useState<ExcalidrawSnapshot | null>(null)
  const apiRef = useRef<ExcalidrawApi | null>(null)
  /**
   * Hash of the element-version state we last pushed or applied from a remote.
   * `onChange` compares the current scene's hash against this: an echo of a scene
   * we just applied/pushed hashes equal and is skipped; a genuine local edit hashes
   * different and is pushed. Because it's a content hash — not a counter — it can't
   * drift when Excalidraw fires zero, one, or many onChange callbacks per updateScene.
   */
  const lastSyncedVersionRef = useRef<number | null>(null)
  const pendingPushRef = useRef<number | null>(null)

  /**
   * Merge an incoming snapshot into the local scene by element id + version
   * (Excalidraw's own `reconcileElements`), instead of blindly replacing it. This
   * keeps a remote snapshot that lacks our just-drawn element from erasing it (and
   * vice versa) when both sides draw within the same debounce + latency window.
   */
  const applyRemote = (remoteElements: readonly Element[]) => {
    const api = apiRef.current
    if (!api || !reconcileElements || !hashElementsVersion) return
    const merged = reconcileElements(api.getSceneElements(), remoteElements, api.getAppState())
    api.updateScene({ elements: merged })
    lastSyncedVersionRef.current = hashElementsVersion(merged)
  }

  // Seed the canvas from the snapshot the server already has.
  // After WS connects (and on reconnect) re-snapshot so a join-race doesn't leave us stale.
  useEffect(() => {
    let cancelled = false
    const fetchSnapshot = async () => {
      try {
        const snap = await getWhiteboard(roomId)
        if (cancelled) return
        if (apiRef.current) {
          applyRemote(snap.elements)
        } else {
          setInitialSnapshot(snap)
        }
      } catch (e) {
        console.warn('[Whiteboard] snapshot fetch failed', e)
      }
    }
    fetchSnapshot()
    const offConnect = onWsConnect(fetchSnapshot)
    return () => {
      cancelled = true
      offConnect()
    }
  }, [roomId])

  // Live updates from other clients.
  useEffect(() => {
    const off = subscribeToRoomWhiteboard(roomId, (snap) => applyRemote(snap.elements))
    return off
  }, [roomId])

  const initialData = useMemo(() => {
    if (!initialSnapshot) return undefined
    // Excalidraw's element types are sealed; we treat snapshots as opaque.
    return { elements: initialSnapshot.elements, appState: initialSnapshot.appState ?? {} } as any
  }, [initialSnapshot])

  const onChange = (elements: readonly Element[]) => {
    // Read-only viewers still receive onChange (e.g. for camera pan); never push.
    if (!isWriter || !hashElementsVersion) return
    const version = hashElementsVersion(elements)
    // Echo of a scene we just pushed or applied from a remote — nothing new to send.
    if (version === lastSyncedVersionRef.current) return
    if (pendingPushRef.current) window.clearTimeout(pendingPushRef.current)
    pendingPushRef.current = window.setTimeout(() => {
      pendingPushRef.current = null
      const api = apiRef.current
      // Re-read the freshest scene at flush time: if a remote merge landed during the
      // debounce window, push the merged result — not the stale `elements` captured when
      // this push was scheduled, which would clobber the remote element we just merged in.
      const latest = api ? api.getSceneElements() : elements
      lastSyncedVersionRef.current = hashElementsVersion!(latest)
      const snap = { elements: [...latest] as ExcalidrawSnapshot['elements'], appState: {} }
      // Prefer the open WebSocket; fall back to HTTP only when it isn't connected.
      if (!publishToRoomWhiteboard(roomId, snap)) {
        pushWhiteboard(roomId, snap).catch((e) => {
          console.warn('[Whiteboard] push failed', e)
        })
      }
    }, DEBOUNCE_MS)
  }

  return (
    // Excalidraw measures against its parent's box. Inside a min-h-0 flex chain
    // (the BentoCell content area) the natural height collapses to 0 and the
    // canvas renders 0×0. Absolute-position the canvas inside a relative shell
    // so it always fills the cell.
    <div className="relative w-full h-full">
      <div className="absolute inset-0">
        <Suspense fallback={
          <div className="flex items-center justify-center h-full text-soft text-sm">{t('room.loadingWhiteboard')}</div>
        }>
          <Excalidraw
            excalidrawAPI={(api) => {
              apiRef.current = api as unknown as ExcalidrawApi
              // Baseline the sync version to whatever we mounted with (empty scene or
              // the seeded initialData) so the mount's first onChange isn't pushed as
              // if it were a fresh local edit.
              if (hashElementsVersion) {
                lastSyncedVersionRef.current = hashElementsVersion(apiRef.current.getSceneElements())
              }
            }}
            initialData={initialData}
            onChange={onChange}
            viewModeEnabled={!isWriter}
          />
        </Suspense>
      </div>
    </div>
  )
}
