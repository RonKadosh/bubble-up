/**
 * A small confirm dialog in the Bubble.up dialog shell — the same overlay +
 * `rounded-3xl shadow-bubble` panel + `<Button>` footer used by the room /
 * expert modals. Replaces native `window.confirm()` so destructive prompts
 * match the rest of the app.
 *
 * All copy is passed in (already translated) so this stays i18n-correct without
 * owning any keys itself.
 */
import type { ButtonVariant } from './Button'
import { Button } from './Button'

interface ConfirmDialogProps {
  open: boolean
  title: string
  body?: string
  confirmLabel: string
  cancelLabel: string
  /** Visual weight of the confirm action. Defaults to `danger`. */
  confirmVariant?: ButtonVariant
  busy?: boolean
  onConfirm: () => void
  onClose: () => void
}

export function ConfirmDialog({
  open,
  title,
  body,
  confirmLabel,
  cancelLabel,
  confirmVariant = 'danger',
  busy = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  if (!open) return null

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-3 tablet:p-4"
      onClick={onClose}
    >
      <div
        className="bg-surface rounded-3xl shadow-bubble border border-line w-full max-w-[26rem] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="px-5 pt-5 pb-2">
          <h3 className="font-semibold text-base">{title}</h3>
          {body && <p className="text-sm text-muted mt-1.5">{body}</p>}
        </div>
        <div className="px-5 pb-5 pt-3 flex items-center justify-end gap-2">
          <Button type="button" variant="ghost" size="sm" onClick={onClose} disabled={busy}>
            {cancelLabel}
          </Button>
          <Button type="button" variant={confirmVariant} size="sm" onClick={onConfirm} disabled={busy}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  )
}
