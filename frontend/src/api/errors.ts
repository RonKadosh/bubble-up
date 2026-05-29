/**
 * Shared helpers for unwrapping the backend's error envelope.
 *
 * Every backend response looks like `{ success: false, error: { code, message, category, fields? } }`
 * when an HTTP call fails. Pages used to hand-roll the same `e?.response?.data?.error?.code` chain
 * and the same `if/else` mapping over codes — this module centralizes both.
 *
 * Usage:
 *   import { describeError } from '../api/errors'
 *   try { ... } catch (err) {
 *     setError(describeError(err, t, {
 *       FILE_TOO_LARGE: 'groups.error.fileTooLarge',
 *       FILE_TYPE_BLOCKED: 'groups.error.fileBlocked',
 *     }, 'groups.error.uploadGeneric'))
 *   }
 */

export interface ApiErrorBody {
  code: string
  message: string
  category: string
  fields?: { field: string; message: string }[]
}

/** Pull the backend error code out of a thrown axios error, if present. */
export function errorCode(e: unknown): string | undefined {
  return (e as { response?: { data?: { error?: ApiErrorBody } } })
    ?.response?.data?.error?.code
}

/** Pull the full error body (code + fields + raw message). Used by callers that need fields. */
export function errorBody(e: unknown): ApiErrorBody | undefined {
  return (e as { response?: { data?: { error?: ApiErrorBody } } })
    ?.response?.data?.error
}

/**
 * Map a thrown error to a translated user-facing message.
 *  - `map` keys are backend error codes; values are i18n keys.
 *  - `fallback` is the i18n key used when no code matches.
 */
export function describeError(
  e: unknown,
  t: (k: string) => string,
  map: Record<string, string>,
  fallback: string,
): string {
  const code = errorCode(e)
  return code && map[code] ? t(map[code]) : t(fallback)
}
