const FOLDER_SEPARATOR = '/'
const KEY = /^[a-z0-9_-]+(\/[a-z0-9_-]+)*$/

/** Why `key` can't name a new entry beside the `existing` ones, or null when it can; the server checks the same. */
export function refusalOfNewKey(key: string, existing: readonly string[]): string | null {
  if (key.trim() === '') {
    return 'Give it a name'
  }
  if (!KEY.test(key)) {
    return 'Use lower case letters, digits, - and _, with / between folders'
  }
  return existing.includes(key) ? `${key} already exists` : null
}

/** The folder of `key` as a start for naming a new entry in it, such as `trees/`; empty for an entry in no folder. */
export function folderPrefixOf(key: string): string {
  return key.slice(0, key.lastIndexOf(FOLDER_SEPARATOR) + 1)
}
