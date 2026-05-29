/**
 * Stable colored avatar derived from any id (group id, user id, …).
 *
 * Same id always picks the same gradient — visual continuity across pages.
 * Used by the Bubbles list, the activity feed, and member rows.
 *
 * If {@code imageUrl} is provided and the image loads, it replaces the
 * gradient-initial render. On 404 / decode error the {@code onError} handler
 * swaps back so callers don't need to guard.
 */

import { useEffect, useState } from 'react'

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
  /** Optional uploaded image URL. Falls back to gradient + initial if absent or load fails. */
  imageUrl?: string | null
}

export function Avatar({ id, name, size = 'md', className = '', ring = false, imageUrl }: AvatarProps) {
  const [imageFailed, setImageFailed] = useState(false)
  // Reset the "failed" flag when the URL changes, so a re-uploaded avatar
  // (new cache-busted URL) gets a fresh chance to load even if the previous
  // URL had 404'd in the same component instance.
  useEffect(() => { setImageFailed(false) }, [imageUrl])
  const showImage = !!imageUrl && !imageFailed

  const circleClasses = `${SIZE_CLASSES[size]} rounded-full flex items-center justify-center text-white font-bold shrink-0 overflow-hidden ${
    showImage ? 'bg-surface-muted' : gradientFor(id)
  }`

  const inner = showImage ? (
    <img
      src={imageUrl!}
      alt=""
      className="w-full h-full object-cover"
      onError={() => setImageFailed(true)}
    />
  ) : (
    <span aria-hidden="true">{initialOf(name)}</span>
  )

  if (ring) {
    return (
      <div className={`ring-iridescent p-[1.5px] rounded-full shrink-0 shadow-sm ${className}`}>
        <div className={circleClasses} aria-hidden={showImage ? undefined : 'true'}>{inner}</div>
      </div>
    )
  }

  return (
    <div className={`${circleClasses} shadow-sm ${className}`} aria-hidden={showImage ? undefined : 'true'}>
      {inner}
    </div>
  )
}
