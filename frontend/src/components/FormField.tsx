import type { ReactNode } from 'react'

/**
 * Shared form-field primitive used across standalone forms (Report Center,
 * become-expert) so they stay visually cohesive. Wraps a labelled control with
 * an optional hint / inline error. Pair the control with {@link fieldInputClass}
 * for the matching input/textarea/select styling.
 */
export const fieldInputClass =
  'w-full border border-line bg-base text-base rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-primary-500'

interface FormFieldProps {
  label: ReactNode
  required?: boolean
  hint?: ReactNode
  error?: ReactNode
  children: ReactNode
}

export function FormField({ label, required, hint, error, children }: FormFieldProps) {
  return (
    <label className="block">
      <span className="block text-sm font-medium text-base mb-1">
        {label}
        {required && <span className="text-bubble-magenta"> *</span>}
      </span>
      {children}
      {hint && !error && <span className="block text-xs text-muted mt-1">{hint}</span>}
      {error && <span className="block text-xs text-danger mt-1">{error}</span>}
    </label>
  )
}
