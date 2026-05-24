/**
 * Stable colored avatar derived from any id (group id, user id, …).
 *
 * Same id always picks the same gradient — visual continuity across pages.
 * Used by the Bubbles list, the activity feed, and member rows.
 */

const AVATAR_GRADIENTS = [
  'bg-gradient-to-br from-primary-300 to-primary-500',
  'bg-gradient-to-br from-primary-200 to-primary-400',
  'bg-gradient-to-br from-primary-400 to-primary-600',
  'bg-gradient-to-br from-sky-300 to-primary-500',
  'bg-gradient-to-br from-cyan-300 to-primary-500',
  'bg-gradient-to-br from-primary-300 to-sky-500',
  'bg-gradient-to-br from-primary-400 to-cyan-600',
  'bg-gradient-to-br from-sky-200 to-primary-400',
]

function hashCode(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return Math.abs(h)
}

export function gradientFor(id: string): string {
  return AVATAR_GRADIENTS[hashCode(id) % AVATAR_GRADIENTS.length]
}

export function initialOf(name: string): string {
  return name.trim()[0]?.toUpperCase() ?? '?'
}

type AvatarSize = 'sm' | 'md' | 'lg'

const SIZE_CLASSES: Record<AvatarSize, string> = {
  sm: 'w-8 h-8 text-sm',
  md: 'w-11 h-11 text-base',
  lg: 'w-12 h-12 text-lg',
}

interface AvatarProps {
  id: string
  name: string
  size?: AvatarSize
  className?: string
  ring?: boolean
}

export function Avatar({ id, name, size = 'md', className = '', ring = false }: AvatarProps) {
  return (
    <div
      className={`${SIZE_CLASSES[size]} rounded-full flex items-center justify-center text-white font-bold shrink-0 shadow-sm ${gradientFor(id)} ${ring ? 'ring-on-brand' : ''} ${className}`}
      aria-hidden="true"
    >
      {initialOf(name)}
    </div>
  )
}
