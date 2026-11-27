import { useEffect, useMemo, useReducer, type DependencyList } from 'react'

const DISPOSED = new WeakSet<object>()

/**
 * What {@code create} makes, given up with {@code dispose} when the component's effects end: when it unmounts, and while
 * an Activity hides it, as it does a tab that is not shown. A memoised value outlives its effects, so once they start
 * again a disposed one is made anew instead of being handed back.
 */
export function useDisposable<T extends object>(create: () => T, dispose: (value: T) => void, deps: DependencyList): T {
  const [generation, makeAnew] = useReducer((count: number) => count + 1, 0)
  const value = useMemo(create, [...deps, generation])
  useEffect(() => {
    if (DISPOSED.has(value)) {
      makeAnew()
      return
    }
    return () => {
      dispose(value)
      DISPOSED.add(value)
    }
  }, [value])
  return value
}
