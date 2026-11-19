import type { ValueKind } from '../bridge/types'

export const NOT_SET = ''
export const LIST_SEPARATOR = ', '

/** Whether {@code kind} is a string naming something, typed as text. */
export function isText(kind: ValueKind): boolean {
  return kind.kind === 'text' || kind.kind === 'reference' || kind.kind === 'resource'
}

/** Whether {@code kind} is a list of strings, such as the roles a decoration goes on, which is typed as {@code a, b}. */
export function isStringList(kind: ValueKind): boolean {
  return kind.kind === 'list' && isText(kind.element)
}

function isStrings(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((element) => typeof element === 'string')
}

/** What the field shows for the value the file holds. */
export function textOf(kind: ValueKind, value: unknown): string {
  if (value === undefined) {
    return NOT_SET
  }
  if (isText(kind) && typeof value === 'string') {
    return value
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

/** What the field holds as JSON to write; a list typed as {@code a, b} is written as an array unless it already is one. */
export function literalOf(kind: ValueKind, draft: string): string {
  if (isText(kind)) {
    return JSON.stringify(draft)
  }
  if (isStringList(kind) && !draft.trim().startsWith('[')) {
    return JSON.stringify(itemsOf(draft))
  }
  return draft.trim()
}
