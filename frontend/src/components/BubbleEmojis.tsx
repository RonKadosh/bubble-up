/**
 * Bubble-themed emojis.
 *
 * Storage shape: shortcodes like `:bubble-smiling:` ride inline inside the existing
 * ChatMessage.content text — no backend schema change. The parser splits content on
 * BUBBLE_EMOJI_REGEX and substitutes a <BubbleEmoji /> for known shortcodes.
 * Unknown shortcodes pass through as raw text so a typo isn't silently dropped.
 *
 * Each emoji is defined as raw SVG inner markup (a string) so the same definition
 * can drive *two* rendering paths:
 *   1. <BubbleEmoji /> — for React-rendered surfaces (chat history, snippets).
 *   2. bubbleEmojiSpanHTML() — for the contentEditable composer, which manages its
 *      own DOM tree imperatively (React doesn't reconcile inside contentEditable).
 *
 * To add a new bubble emoji: write its inner SVG markup string below, add one entry
 * to BUBBLE_EMOJIS, and add `groups.chat.emoji.<name>` to both i18n bundles.
 */
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import i18n from '../i18n'

const FILL = '#dceefe'
const STROKE = '#7fb9ed'
const FACE = '#1a2a3f'
const HEART = '#e84d8a'
const TEAR = '#5aa6e0'

const BUBBLE_BODY = `
  <circle cx="12" cy="12" r="10" fill="${FILL}" stroke="${STROKE}" stroke-width="1"/>
  <ellipse cx="8.5" cy="8" rx="2.2" ry="1.3" fill="white" fill-opacity="0.85" transform="rotate(-30 8.5 8)"/>
`

const SMILING = `
  ${BUBBLE_BODY}
  <circle cx="9" cy="11" r="0.9" fill="${FACE}"/>
  <circle cx="15" cy="11" r="0.9" fill="${FACE}"/>
  <path d="M 8.5 14 Q 12 17 15.5 14" stroke="${FACE}" stroke-width="1.2" fill="none" stroke-linecap="round"/>
`

const SAD = `
  ${BUBBLE_BODY}
  <circle cx="9" cy="11" r="0.9" fill="${FACE}"/>
  <circle cx="15" cy="11" r="0.9" fill="${FACE}"/>
  <path d="M 8.5 16 Q 12 13.5 15.5 16" stroke="${FACE}" stroke-width="1.2" fill="none" stroke-linecap="round"/>
`

const APPROVING = `
  ${BUBBLE_BODY}
  <path d="M 7.5 11.5 Q 9 10 10.5 11.5" stroke="${FACE}" stroke-width="1.3" fill="none" stroke-linecap="round"/>
  <path d="M 13.5 11.5 Q 15 10 16.5 11.5" stroke="${FACE}" stroke-width="1.3" fill="none" stroke-linecap="round"/>
  <path d="M 8 14 Q 12 18 16 14" stroke="${FACE}" stroke-width="1.4" fill="none" stroke-linecap="round"/>
`

const DISAPPROVING = `
  ${BUBBLE_BODY}
  <path d="M 7 10 L 10 11" stroke="${FACE}" stroke-width="1.3" stroke-linecap="round"/>
  <path d="M 14 11 L 17 10" stroke="${FACE}" stroke-width="1.3" stroke-linecap="round"/>
  <circle cx="9" cy="12.6" r="0.8" fill="${FACE}"/>
  <circle cx="15" cy="12.6" r="0.8" fill="${FACE}"/>
  <path d="M 8.5 16.5 Q 12 14.5 15.5 16.5" stroke="${FACE}" stroke-width="1.2" fill="none" stroke-linecap="round"/>
`

const LAUGHING = `
  ${BUBBLE_BODY}
  <path d="M 7 10.5 Q 8.5 9 10 10.5" stroke="${FACE}" stroke-width="1.3" fill="none" stroke-linecap="round"/>
  <path d="M 14 10.5 Q 15.5 9 17 10.5" stroke="${FACE}" stroke-width="1.3" fill="none" stroke-linecap="round"/>
  <path d="M 8 13.5 Q 12 18.5 16 13.5 Z" fill="${FACE}"/>
  <path d="M 19 9.5 Q 20 11 19 12 Q 18 11 19 9.5 Z" fill="${TEAR}"/>
`

const CRYING = `
  ${BUBBLE_BODY}
  <circle cx="9" cy="12" r="0.9" fill="${FACE}"/>
  <circle cx="15" cy="12" r="0.9" fill="${FACE}"/>
  <path d="M 9 16 Q 12 14 15 16" stroke="${FACE}" stroke-width="1.2" fill="none" stroke-linecap="round"/>
  <path d="M 8 13.5 Q 9 17 8 18.5 Q 7 17 8 13.5 Z" fill="${TEAR}"/>
  <path d="M 16 13.5 Q 17 17 16 18.5 Q 15 17 16 13.5 Z" fill="${TEAR}"/>
`

const LOVING = `
  ${BUBBLE_BODY}
  <path d="M 9 12.5 C 6.7 10.5 7.8 8.3 9 9.7 C 10.2 8.3 11.3 10.5 9 12.5 Z" fill="${HEART}"/>
  <path d="M 15 12.5 C 12.7 10.5 13.8 8.3 15 9.7 C 16.2 8.3 17.3 10.5 15 12.5 Z" fill="${HEART}"/>
  <path d="M 8.5 15 Q 12 17.5 15.5 15" stroke="${FACE}" stroke-width="1.2" fill="none" stroke-linecap="round"/>
`

export interface BubbleEmojiDef {
  /** Token form used inside chat content text, without the surrounding colons. */
  shortcode: string
  /** i18n key for display name (used as aria-label, title, picker label). */
  nameKey: string
  /** Inner SVG markup (children of `<svg viewBox="0 0 24 24">`). */
  innerSvg: string
}

/**
 * The registry. Picker iterates this; parser looks up by shortcode.
 * Order here is the order shown in the picker.
 */
export const BUBBLE_EMOJIS: BubbleEmojiDef[] = [
  { shortcode: 'bubble-smiling', nameKey: 'groups.chat.emoji.smiling', innerSvg: SMILING },
  { shortcode: 'bubble-laughing', nameKey: 'groups.chat.emoji.laughing', innerSvg: LAUGHING },
  { shortcode: 'bubble-loving', nameKey: 'groups.chat.emoji.loving', innerSvg: LOVING },
  { shortcode: 'bubble-approving', nameKey: 'groups.chat.emoji.approving', innerSvg: APPROVING },
  { shortcode: 'bubble-disapproving', nameKey: 'groups.chat.emoji.disapproving', innerSvg: DISAPPROVING },
  { shortcode: 'bubble-sad', nameKey: 'groups.chat.emoji.sad', innerSvg: SAD },
  { shortcode: 'bubble-crying', nameKey: 'groups.chat.emoji.crying', innerSvg: CRYING },
]

export const BUBBLE_EMOJI_BY_SHORTCODE: Map<string, BubbleEmojiDef> = new Map(
  BUBBLE_EMOJIS.map((e) => [e.shortcode, e])
)

/**
 * Matches `:any-shortcode:`. Reset .lastIndex before each use because the /g flag is stateful.
 */
export const BUBBLE_EMOJI_REGEX = /:([a-z][a-z0-9-]*):/g

interface BubbleEmojiProps {
  def: BubbleEmojiDef
  /** Override sizing; defaults to inline ≈ line-height. */
  className?: string
}

export function BubbleEmoji({ def, className }: BubbleEmojiProps) {
  const { t } = useTranslation()
  const name = t(def.nameKey)
  return (
    <span
      role="img"
      aria-label={name}
      title={name}
      className={`inline-block align-text-bottom shrink-0 ${className ?? 'w-5 h-5'}`}
    >
      <svg
        viewBox="0 0 24 24"
        className="w-full h-full block"
        dangerouslySetInnerHTML={{ __html: def.innerSvg }}
      />
    </span>
  )
}

/**
 * Split chat content into a stream of strings + <BubbleEmoji /> nodes.
 * Unknown shortcodes are left as raw text in the surrounding slice.
 */
export function renderBubbleContent(text: string): ReactNode[] {
  if (!text) return [text]
  const out: ReactNode[] = []
  let lastIndex = 0
  let key = 0
  BUBBLE_EMOJI_REGEX.lastIndex = 0
  let match: RegExpExecArray | null
  while ((match = BUBBLE_EMOJI_REGEX.exec(text)) !== null) {
    const [full, name] = match
    const def = BUBBLE_EMOJI_BY_SHORTCODE.get(name)
    if (!def) continue
    if (match.index > lastIndex) {
      out.push(text.slice(lastIndex, match.index))
    }
    out.push(<BubbleEmoji key={`be-${match.index}-${key++}`} def={def} />)
    lastIndex = match.index + full.length
  }
  if (lastIndex < text.length) {
    out.push(text.slice(lastIndex))
  }
  if (out.length === 0) out.push(text)
  return out
}

function escapeAttr(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/**
 * HTML for a single emoji span — used by the contentEditable composer to inject
 * an atomic, non-editable emoji at the caret position. Matches the rendered
 * output of <BubbleEmoji /> in size, alignment, and aria semantics.
 *
 * The span carries `data-shortcode="..."` so the composer can serialize the DOM
 * back to a `:shortcode:` token at send time.
 */
export function bubbleEmojiSpanHTML(def: BubbleEmojiDef): string {
  const name = escapeAttr(i18n.t(def.nameKey))
  return (
    `<span role="img" aria-label="${name}" title="${name}" ` +
    `contenteditable="false" data-shortcode="${escapeAttr(def.shortcode)}" ` +
    `class="inline-block align-text-bottom shrink-0 w-5 h-5">` +
    `<svg viewBox="0 0 24 24" class="w-full h-full block">${def.innerSvg}</svg>` +
    `</span>`
  )
}
