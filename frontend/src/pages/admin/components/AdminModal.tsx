import { ReactNode, useEffect } from 'react'
import { useTranslation } from 'react-i18next'
import { IconButton } from '../../../components/Button'
import { CloseIcon } from '../../../components/Icons'

interface Props {
  title: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
}

const SIZES: Record<NonNullable<Props['size']>, string> = {
  sm: 'max-w-md',
  md: 'max-w-2xl',
  lg: 'max-w-5xl',
}

export default function AdminModal({ title, onClose, children, footer, size = 'md' }: Props) {
  const { t } = useTranslation()

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div
      role="dialog"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-3 tablet:p-4 animate-fade-in"
      onClick={onClose}
    >
      <div
        className={`relative w-full ${SIZES[size]} max-h-[90vh] overflow-hidden rounded-3xl bg-surface border border-line shadow-bubble flex flex-col animate-pop-in`}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between gap-3 px-5 py-4 border-b border-line">
          <h2 className="text-base font-semibold text-base truncate">{title}</h2>
          <IconButton
            type="button"
            size="sm"
            variant="ghost"
            onClick={onClose}
            aria-label={t('common.close')}
          >
            <CloseIcon className="w-4 h-4" />
          </IconButton>
        </div>
        <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="px-5 py-4 border-t border-line bg-surface-muted">{footer}</div>}
      </div>
    </div>
  )
}
