import { useCallback, useState } from 'react'

const remembered = new Map<string, unknown>()

/**
 * State that outlives the component holding it, by {@code key}: a list's search stays as it was when the list comes
 * back for another tab of the same view, and a tab moved to another pane keeps what it showed.
 */
export function useRememberedState<Value>(key: string, initial: Value): [Value, (next: Value | ((current: Value) => Value)) => void] {
  const [value, setValue] = useState<Value>(() => (remembered.has(key) ? (remembered.get(key) as Value) : initial))
  const update = useCallback(
    (next: Value | ((current: Value) => Value)) =>
      setValue((current) => {
        const value = typeof next === 'function' ? (next as (current: Value) => Value)(current) : next
        remembered.set(key, value)
        return value
      }),
    [key]
  )
  return [value, update]
}

/** A ref that outlives the component holding it, by {@code key}, as {@link useRememberedState} does for state. */
export function useRememberedRef<Value>(key: string, initial: Value): { current: Value } {
  const known = remembered.get(key) as { current: Value } | undefined
  if (known !== undefined) {
    return known
  }
  const created = { current: initial }
  remembered.set(key, created)
  return created
}

/** Forgets what a closed tab remembered, whose keys start with its id. */
export function forgetTab(tabId: string): void {
  const prefix = `${tabId}:`
  for (const key of remembered.keys()) {
    if (key.startsWith(prefix)) {
      remembered.delete(key)
    }
  }
}
