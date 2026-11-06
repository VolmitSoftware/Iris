import type { JsonPath } from '../bridge/types'

/** The value at {@code path} in a parsed file; undefined where nothing is written. */
export function valueAt(root: unknown, path: JsonPath): unknown {
  let current = root
  for (const step of path) {
    if (current === null || typeof current !== 'object') {
      return undefined
    }
    current = (current as Record<string | number, unknown>)[step]
  }
  return current
}

/** The file parsed, or undefined when it is not JSON (the editor then shows why instead). */
export function parsedText(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}
