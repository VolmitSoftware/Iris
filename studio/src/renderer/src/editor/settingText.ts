import type { SettingOption, ValueKind } from '../bridge/types'

export const NOT_SET = ''
export const LIST_SEPARATOR = ', '
export const RANGE_DASH = ' – '
/** The schema type of a stretch along a part from 0 to 1, shown and typed as a range rather than folded open. */
export const SPAN_TYPE = 'art.arcane.iris.generation.procedural.part.ProceduralSpan'

const NUMBER = '(-?\\d+(?:\\.\\d+)?)'
const RANGE = new RegExp(`^\\s*${NUMBER}\\s*(?:–|—|\\.\\.|to|,|-)\\s*${NUMBER}\\s*$`)
const RANGE_SIZE = 2
const SPAN_FROM = 'from'
const SPAN_TO = 'to'
const SPAN_DEFAULT_FROM = 0
const SPAN_DEFAULT_TO = 1

/** Whether {@code kind} is a string naming something, typed as text. */
export function isText(kind: ValueKind): boolean {
  return kind.kind === 'text' || kind.kind === 'reference' || kind.kind === 'resource'
}

export function isNumber(kind: ValueKind): boolean {
  return kind.kind === 'integer' || kind.kind === 'decimal'
}

/** Whether {@code kind} is a list of strings, such as the roles a decoration goes on, which is typed as {@code a, b}. */
export function isStringList(kind: ValueKind): boolean {
  return kind.kind === 'list' && isText(kind.element)
}

/** Whether {@code kind} is a stretch from 0 to 1, written {@code {"from": a, "to": b}} and typed {@code a – b}. */
export function isSpan(kind: ValueKind): boolean {
  return kind.kind === 'object' && kind.type === SPAN_TYPE
}

function isStrings(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((element) => typeof element === 'string')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** The two ends of a {@code [min, max]} value, or of a span; null for anything else. */
export function rangeOf(kind: ValueKind, value: unknown): [number, number] | null {
  if (kind.kind === 'any' && Array.isArray(value) && value.length === RANGE_SIZE && value.every((end) => typeof end === 'number')) {
    return [value[0], value[1]]
  }
  if (!isSpan(kind) || !isRecord(value)) {
    return null
  }
  const from = value[SPAN_FROM] ?? SPAN_DEFAULT_FROM
  const to = value[SPAN_TO] ?? SPAN_DEFAULT_TO
  return typeof from === 'number' && typeof to === 'number' ? [from, to] : null
}

/** What the node shows for the value the file holds: strings unquoted, ranges as {@code 2 – 4}, anything else as JSON. */
export function textOf(kind: ValueKind, value: unknown): string {
  if (value === undefined) {
    return NOT_SET
  }
  if ((isText(kind) || kind.kind === 'enum') && typeof value === 'string') {
    return value
  }
  const range = rangeOf(kind, value)
  if (range !== null) {
    return range.join(RANGE_DASH)
  }
  return isStringList(kind) && isStrings(value) ? value.join(LIST_SEPARATOR) : JSON.stringify(value)
}

/** The strings a list is typed as, each trimmed, and none left empty. */
export function itemsOf(draft: string): string[] {
  return draft
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item !== '')
}

/**
 * What was typed as JSON to write. A range may be typed {@code 2 – 4}, {@code 2-4}, {@code 2 to 4} or {@code 2, 4}; a
 * list typed as {@code a, b} or without its brackets gets them.
 */
export function literalOf(kind: ValueKind, draft: string): string {
  if (isText(kind) || kind.kind === 'enum') {
    return JSON.stringify(draft)
  }
  const typed = draft.trim()
  if (isStringList(kind) && !typed.startsWith('[')) {
    return JSON.stringify(itemsOf(typed))
  }
  const range = RANGE.exec(typed)
  if (range !== null && kind.kind === 'any') {
    return `[${range[1]}, ${range[2]}]`
  }
  if (range !== null && isSpan(kind)) {
    return `{"${SPAN_FROM}": ${range[1]}, "${SPAN_TO}": ${range[2]}}`
  }
  return !isJson(typed) && isJson(`[${typed}]`) ? `[${typed}]` : typed
}

/** What a setting added from the node starts at: its default, or an empty value of its kind when it has none. */
export function defaultLiteral(option: SettingOption): string {
  if (option.defaultValue !== null && option.defaultValue !== undefined) {
    return JSON.stringify(option.defaultValue)
  }
  return placeholderOf(option.kind)
}

function placeholderOf(kind: ValueKind): string {
  switch (kind.kind) {
    case 'boolean':
      return 'false'
    case 'text':
    case 'reference':
    case 'resource':
      return '""'
    case 'enum':
      return JSON.stringify(kind.constants[0]?.name ?? '')
    case 'list':
      return '[]'
    case 'integer':
    case 'decimal':
    case 'any':
      return '0'
    default:
      return '{}'
  }
}

/** What an unset setting falls back to, as the node shows it; empty when it has no default. */
export function defaultText(option: SettingOption): string {
  return option.defaultValue === null || option.defaultValue === undefined ? NOT_SET : textOf(option.kind, option.defaultValue)
}

/** Why {@code draft} can't be written as the value of {@code option}, or null when it can; an empty draft unsets it. */
export function problemWith(option: SettingOption, draft: string): string | null {
  const { kind } = option
  if (draft.trim() === NOT_SET || isText(kind) || kind.kind === 'enum') {
    return null
  }
  if (isNumber(kind)) {
    return numberProblem(option, draft)
  }
  if (isJson(literalOf(kind, draft))) {
    return null
  }
  if (kind.kind === 'any') {
    return `Takes a number, or a range like 4${RANGE_DASH}6`
  }
  return isSpan(kind) ? `Takes a stretch from 0 to 1, like 0.2${RANGE_DASH}0.8` : 'Not JSON'
}

function numberProblem(option: SettingOption, draft: string): string | null {
  const number = Number(draft)
  if (!Number.isFinite(number)) {
    return 'Not a number'
  }
  if (option.kind.kind === 'integer' && !Number.isInteger(number)) {
    return 'Not a whole number'
  }
  if (option.minimum !== null && number < option.minimum) {
    return `At least ${option.minimum}`
  }
  if (option.maximum !== null && number > option.maximum) {
    return `At most ${option.maximum}`
  }
  return null
}

function isJson(text: string): boolean {
  try {
    JSON.parse(text)
    return true
  } catch {
    return false
  }
}
