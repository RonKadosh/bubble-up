import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { BentoCell } from '../../components/BentoCell'
import { useRoomLayoutStore, type RoomBentoKey } from '../../store/roomLayoutStore'
import { useViewportStore } from '../../store/viewportStore'

interface LayoutSpec {
  sectionClass: string
  video: { className: string }
  whiteboard: { className: string }
  chat: { className: string }
}

/**
 * Returns the bento grid template + per-cell placement classes for the room.
 * Two-col × two-row grid: the focused cell takes a full column (both rows),
 * the other two stack on the opposite column.
 */
function getRoomBentoLayout(focused: RoomBentoKey): LayoutSpec {
  const base = 'flex-1 min-h-0 grid p-3 gap-3 bg-bento-grid'
  switch (focused) {
    case 'video':
      return {
        sectionClass: `${base} grid-cols-[2fr_1fr] grid-rows-2`,
        video: { className: 'col-start-1 row-start-1 row-span-2 min-h-0' },
        whiteboard: { className: 'col-start-2 row-start-1 min-h-0' },
        chat: { className: 'col-start-2 row-start-2 min-h-0' },
      }
    case 'whiteboard':
      return {
        sectionClass: `${base} grid-cols-[1fr_2fr] grid-rows-2`,
        video: { className: 'col-start-1 row-start-1 min-h-0' },
        chat: { className: 'col-start-1 row-start-2 min-h-0' },
        whiteboard: { className: 'col-start-2 row-start-1 row-span-2 min-h-0' },
      }
    case 'chat':
      return {
        sectionClass: `${base} grid-cols-[1fr_2fr] grid-rows-2`,
        video: { className: 'col-start-1 row-start-1 min-h-0' },
        whiteboard: { className: 'col-start-1 row-start-2 min-h-0' },
        chat: { className: 'col-start-2 row-start-1 row-span-2 min-h-0' },
      }
  }
}

const PHONE_TABS: { key: RoomBentoKey; labelKey: string }[] = [
  { key: 'video', labelKey: 'room.video' },
  { key: 'whiteboard', labelKey: 'room.whiteboard' },
  { key: 'chat', labelKey: 'room.chat' },
]

interface Props {
  header: ReactNode
  videoSlot: ReactNode
  whiteboardSlot: ReactNode
  chatSlot: ReactNode
  /** Optional row below the bento (e.g. ExpertHostControls). */
  footerSlot?: ReactNode
  /** Optional banner between header and bento (e.g. transient error). */
  errorBanner?: ReactNode
}

/**
 * Shared bento layout for room-style pages (group {@link BubbleRoomPage} and
 * expert {@link ExpertRoomPage}). Owns the desktop grid, the phone tab strip,
 * the focus-to-promote behaviour, and the persistent-video anchor positioning
 * — page-specific concerns (header, chat loader, host controls) are passed in
 * as slots so both pages stay visually identical.
 */
export function RoomBentoShell({
  header,
  videoSlot,
  whiteboardSlot,
  chatSlot,
  footerSlot,
  errorBanner,
}: Props) {
  const { t } = useTranslation()
  const focused = useRoomLayoutStore((s) => s.focused)
  const setFocused = useRoomLayoutStore((s) => s.setFocused)
  const isPhone = useViewportStore((s) => s.tier === 'phone')

  const renderPanel = () => {
    switch (focused) {
      case 'video': return videoSlot
      case 'whiteboard': return whiteboardSlot
      case 'chat': return chatSlot
    }
  }

  return (
    <div className="flex flex-col h-full bg-base">
      {header}

      {errorBanner}

      {isPhone ? (
        <>
          <nav className="flex shrink-0 border-b border-line bg-surface overflow-x-auto">
            {PHONE_TABS.map((tab) => {
              const active = focused === tab.key
              return (
                <button
                  key={tab.key}
                  type="button"
                  onClick={() => setFocused(tab.key)}
                  aria-pressed={active}
                  className={`flex-1 min-w-[5rem] flex items-center justify-center py-2.5 text-xs font-medium transition-colors ${
                    active
                      ? 'text-primary-600 border-b-2 border-primary-500'
                      : 'text-muted hover:text-base border-b-2 border-transparent'
                  }`}
                >
                  <span>{t(tab.labelKey)}</span>
                </button>
              )
            })}
          </nav>
          <section className="flex-1 min-h-0 p-2 bg-bento-grid flex">
            <div className="ring-iridescent p-[1.5px] bento-cell-radius flex-1 flex flex-col min-h-0 overflow-hidden shadow-themed">
              <div className="flex-1 min-h-0 bg-surface bento-cell-inner-radius flex flex-col overflow-hidden">
                {renderPanel()}
              </div>
            </div>
          </section>
        </>
      ) : (
        (() => {
          const layout = getRoomBentoLayout(focused)
          return (
            <section className={layout.sectionClass}>
              <BentoCell
                label={t('room.video')}
                className={layout.video.className}
                isFocused={focused === 'video'}
                onFocus={() => setFocused('video')}
                promoteLabel={t('room.focusVideo')}
              >
                {videoSlot}
              </BentoCell>
              <BentoCell
                label={t('room.whiteboard')}
                className={layout.whiteboard.className}
                isFocused={focused === 'whiteboard'}
                onFocus={() => setFocused('whiteboard')}
                promoteLabel={t('room.focusWhiteboard')}
              >
                {whiteboardSlot}
              </BentoCell>
              <BentoCell
                label={t('room.chat')}
                className={layout.chat.className}
                isFocused={focused === 'chat'}
                onFocus={() => setFocused('chat')}
                promoteLabel={t('room.focusChat')}
              >
                {chatSlot}
              </BentoCell>
            </section>
          )
        })()
      )}

      {footerSlot}
    </div>
  )
}
