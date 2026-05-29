import { Suspense, lazy, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ExcalidrawSnapshot } from '../../api/room'
import { getWhiteboard, pushWhiteboard } from '../../api/room'
import { subscribeToRoomWhiteboard, onWsConnect } from '../../api/ws'

// Excalidraw v0.17+ ships its CSS as a separate file you must import or the
// toolbar UI renders as bare HTML on top of the canvas (broken layout, raw
// labels like "Stroke width" leaking through). The package's exports map
// resolves this to the dev or prod stylesheet depending on Vite's mode.
import '@excalidraw/excalidraw/index.css'

// Code-split the Excalidraw bundle — it's ~1.5MB gzipped and shouldn't load
// for users who never enter a room.
const Excalidraw = lazy(() =>
  import('@excalidraw/excalidraw').then((m) => ({ default: m.Excalidraw }))
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

export function WhiteboardPanel({ roomId, isWriter = true }: Props) {
  const { t } = useTranslation()
  const [initialSnapshot, setInitialSnapshot] = useState<ExcalidrawSnapshot | null>(null)
  const apiRef = useRef<{ updateScene: (s: { elements: unknown[] }) => void } | null>(null)
  /** Version counter that we bump on every remote-apply. The next onChange that
   *  fires with the same version is suppressed (it's the echo of the remote scene). */
  const remoteVersionRef = useRef(0)
  const pendingPushRef = useRef<number | null>(null)

  // Seed the canvas from the snapshot the server already has.
  // After WS connects (and on reconnect) re-snapshot so a join-race doesn't leave us stale.
  useEffect(() => {
    let cancelled = false
    const fetchSnapshot = async () => {
      try {
        const snap = await getWhiteboard(roomId)
        if (cancelled) return
        if (apiRef.current) {
          remoteVersionRef.current++
          apiRef.current.updateScene({ elements: snap.elements })
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
    const off = subscribeToRoomWhiteboard(roomId, (snap) => {
      if (apiRef.current) {
        remoteVersionRef.current++
        apiRef.current.updateScene({ elements: snap.elements })
      }
    })
    return off
  }, [roomId])

  const initialData = useMemo(() => {
    if (!initialSnapshot) return undefined
    // Excalidraw's element types are sealed; we treat snapshots as opaque.
    return { elements: initialSnapshot.elements, appState: initialSnapshot.appState ?? {} } as any
  }, [initialSnapshot])

  const onChange = (elements: readonly unknown[]) => {
    // Read-only viewers still receive onChange (e.g. for camera pan); never push.
    if (!isWriter) return
    // Suppress the immediate echo of a remote-applied scene.
    if (remoteVersionRef.current > 0) {
      remoteVersionRef.current--
      return
    }
    if (pendingPushRef.current) window.clearTimeout(pendingPushRef.current)
    pendingPushRef.current = window.setTimeout(() => {
      pendingPushRef.current = null
      pushWhiteboard(roomId, { elements: [...elements], appState: {} }).catch((e) => {
        console.warn('[Whiteboard] push failed', e)
      })
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
            excalidrawAPI={(api) => { apiRef.current = api as unknown as typeof apiRef.current }}
            initialData={initialData}
            onChange={onChange}
            viewModeEnabled={!isWriter}
          />
        </Suspense>
      </div>
    </div>
  )
}
