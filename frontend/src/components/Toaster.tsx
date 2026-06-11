import { useToastStore, type ToastKind } from '../store/toastStore'
import { Avatar } from './Avatar'
import { BubbleEmoji, BUBBLE_EMOJI_BY_SHORTCODE } from './BubbleEmojis'
import { CloseIcon } from './Icons'

/**
 * Renders the transient toast stack from {@link useToastStore}. Mounted once at the
 * Layout root (next to PersistentVideo / UserProfileCard). Bottom-center, stacked,
 * pointer-events only on the cards so it never blocks the page underneath.
 *
 * On-brand "bubble" styling: a rounded-full pill with the bubble-pop hover + the
 * brand shadow, fronted by the relevant member Avatar (add / remove) or a
 * Bubble emoji that matches the toast's tone.
 */
const KIND_EMOJI: Record<ToastKind, string> = {
  success: 'bubble-approving',
  error: 'bubble-sad',
  info: 'bubble-smiling',
}

export function Toaster() {
  const toasts = useToastStore((s) => s.toasts)
  const dismiss = useToastStore((s) => s.dismiss)
  if (toasts.length === 0) return null
  return (
    <div className="fixed z-[70] bottom-4 inset-x-0 flex flex-col items-center gap-2 px-4 pointer-events-none">
      {toasts.map((toast) => {
        const emojiDef = BUBBLE_EMOJI_BY_SHORTCODE.get(KIND_EMOJI[toast.kind])
        return (
          <div
            key={toast.id}
            role="status"
            className="pointer-events-auto max-w-[20rem] flex items-center gap-2.5 rounded-full bg-surface border border-line shadow-bubble ps-1.5 pe-3 py-1.5 bubble-pop animate-toast-in"
          >
            {toast.avatar ? (
              <Avatar id={toast.avatar.id} name={toast.avatar.name} imageUrl={toast.avatar.imageUrl} size="sm" />
            ) : emojiDef ? (
              <BubbleEmoji def={emojiDef} className="w-7 h-7 shrink-0" />
            ) : null}
            <span className="flex-1 text-sm text-base">{toast.message}</span>
            <button
              type="button"
              onClick={() => dismiss(toast.id)}
              aria-label="Dismiss"
              className="shrink-0 text-muted hover:text-secondary"
            >
              <CloseIcon className="w-4 h-4" />
            </button>
          </div>
        )
      })}
    </div>
  )
}
