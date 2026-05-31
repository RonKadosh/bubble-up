import { useEffect, useRef } from 'react'
import { useTranslation } from 'react-i18next'
import type { BubbleRoom } from '../../api/room'
import { publishToRoomPresence } from '../../api/ws'
import { useAuthStore } from '../../store/authStore'

/** Minimal shape we touch on the global Jitsi API loaded via <script>. */
declare global {
  interface Window {
    JitsiMeetExternalAPI?: new (domain: string, options: Record<string, unknown>) => {
      dispose: () => void
      addListener: (event: string, fn: (...args: unknown[]) => void) => void
    }
  }
}

const SCRIPT_ID = 'jitsi-external-api'
let scriptPromise: Promise<void> | null = null

function loadExternalApiScript(serverUrl: string, appId: string): Promise<void> {
  if (typeof window === 'undefined') return Promise.resolve()
  if (window.JitsiMeetExternalAPI) return Promise.resolve()
  if (scriptPromise) return scriptPromise
  scriptPromise = new Promise((resolve, reject) => {
    const existing = document.getElementById(SCRIPT_ID) as HTMLScriptElement | null
    if (existing) {
      existing.addEventListener('load', () => resolve())
      existing.addEventListener('error', () => reject(new Error('Failed to load Jitsi external API')))
      return
    }
    const tag = document.createElement('script')
    tag.id = SCRIPT_ID
    tag.async = true
    // JaaS hosts the external_api.js under /{appId}/external_api.js
    tag.src = `${serverUrl}/${appId}/external_api.js`
    tag.addEventListener('load', () => resolve())
    tag.addEventListener('error', () => reject(new Error('Failed to load Jitsi external API')))
    document.head.appendChild(tag)
  })
  return scriptPromise
}

interface Props {
  room: BubbleRoom
}

export function VideoPanel({ room }: Props) {
  const { t } = useTranslation()
  const containerRef = useRef<HTMLDivElement | null>(null)
  const user = useAuthStore((s) => s.user)

  useEffect(() => {
    if (!containerRef.current) return
    if (!room.jitsiAppId || !room.jitsiJwt) return
    let api: { dispose: () => void; addListener: (event: string, fn: (...args: unknown[]) => void) => void } | null = null
    let cancelled = false

    const host = (() => {
      try {
        return new URL(room.jitsiServerUrl).host
      } catch {
        return '8x8.vc'
      }
    })()

    loadExternalApiScript(room.jitsiServerUrl, room.jitsiAppId)
      .then(() => {
        if (cancelled || !window.JitsiMeetExternalAPI || !containerRef.current) return
        api = new window.JitsiMeetExternalAPI(host, {
          roomName: `${room.jitsiAppId}/${room.jitsiRoomName}`,
          jwt: room.jitsiJwt,
          parentNode: containerRef.current,
          width: '100%',
          height: '100%',
          userInfo: user
            ? { displayName: user.displayName || user.email, email: user.email }
            : undefined,
          configOverwrite: {
            prejoinPageEnabled: false,
            startWithAudioMuted: true,
            startWithVideoMuted: false,
            // BubbleUp has its own chat / member roster / whiteboard — keep
            // Jitsi as a thin a/v + screen-share surface.
            disableInviteFunctions: true,
            disablePolls: true,
            disableReactions: true,
            readOnlyName: true,
            toolbarButtons: [
              'microphone',
              'camera',
              'desktop',
              'tileview',
              'fullscreen',
              'hangup',
            ],
            // Strip the settings menu down to device pickers only — no profile,
            // no moderator, no calendar, no Etherpad.
            settings: { sections: ['devices'] },
          },
          interfaceConfigOverwrite: {
            // Older builds still read TOOLBAR_BUTTONS from interfaceConfig.
            TOOLBAR_BUTTONS: [
              'microphone',
              'camera',
              'desktop',
              'tileview',
              'fullscreen',
              'hangup',
            ],
            SETTINGS_SECTIONS: ['devices'],
            SHOW_CHROME_EXTENSION_BANNER: false,
            DISABLE_FOCUS_INDICATOR: true,
            HIDE_INVITE_MORE_HEADER: true,
            MOBILE_APP_PROMO: false,
          },
        })
        // Report call presence off Jitsi's own conference events so the room
        // shows a live "N in call" count. Crash/tab-close is covered server-side
        // by the WebSocket disconnect cleanup.
        api.addListener('videoConferenceJoined', () => publishToRoomPresence(room.id, 'JOIN'))
        api.addListener('videoConferenceLeft', () => publishToRoomPresence(room.id, 'LEAVE'))
      })
      .catch((e) => console.error('[VideoPanel] Jitsi load failed', e))

    return () => {
      cancelled = true
      // Backstop LEAVE in case videoConferenceLeft didn't fire before teardown.
      publishToRoomPresence(room.id, 'LEAVE')
      try { api?.dispose() } catch (e) { console.warn('[VideoPanel] dispose failed', e) }
    }
  }, [room.id, room.jitsiAppId, room.jitsiJwt, room.jitsiRoomName, room.jitsiServerUrl, user])

  if (!room.jitsiAppId || !room.jitsiJwt) {
    return (
      <div className="flex items-center justify-center h-full text-soft text-sm p-6 text-center">
        {t('room.videoNotConfigured')}
      </div>
    )
  }

  return <div ref={containerRef} className="w-full h-full bg-black" />
}
