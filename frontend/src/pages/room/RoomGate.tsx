/**
 * The pre-room gate states shared by BubbleRoomPage and ExpertRoomPage: a
 * styled "can't enter yet / not found / ended" notice and the session-loading
 * indicator. Replaces the old bare centered text (which used the undefined
 * `text-link` class) with the app's empty-state language — an iridescent
 * bubble icon, a calm message, and a real Button back out.
 */
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import { BubbleLoader } from '../../components/BubbleLoader'
import { ClockIcon } from '../../components/Icons'

export function RoomGateError({
  message,
  backTo,
  backLabel,
}: {
  message: string
  backTo: string
  backLabel: string
}) {
  const navigate = useNavigate()
  return (
    <div className="flex-1 h-full flex items-center justify-center p-6 bg-base">
      <div className="flex flex-col items-center text-center gap-4 max-w-sm animate-rise-in">
        <div className="ring-iridescent p-[2px] rounded-full shadow-themed">
          <span className="grid place-items-center w-16 h-16 rounded-full bg-surface text-primary-600">
            <ClockIcon className="w-7 h-7" />
          </span>
        </div>
        <p className="text-base font-semibold text-base leading-snug">{message}</p>
        <Button variant="secondary" size="sm" onClick={() => navigate(backTo)}>
          {backLabel}
        </Button>
      </div>
    </div>
  )
}

export function RoomGateLoading({ label }: { label: string }) {
  return (
    <div className="flex-1 h-full flex items-center justify-center p-6 bg-base">
      <div className="flex flex-col items-center gap-3 text-sm text-muted">
        <BubbleLoader size={56} />
        <span>{label}</span>
      </div>
    </div>
  )
}
