import { useCallback, useSyncExternalStore } from 'react'
import { createShortcutStore } from './shortcutStore'
import { keyTextOf, type Bindings } from './shortcuts'

export const shortcutStore = createShortcutStore({
  load: () => window.studio.loadShortcuts(),
  save: (bindings) => window.studio.saveShortcuts(bindings)
})

/** The keys the user rebound; components read them through here so a rebound key shows and works at once. */
export function useBindings(): Bindings {
  return useSyncExternalStore(shortcutStore.subscribe, shortcutStore.snapshot)
}

/** The first key of a shortcut as shown beside it, empty when it has none. */
export function useKeyText(): (id: string) => string {
  const bindings = useBindings()
  return useCallback((id) => keyTextOf(id, bindings), [bindings])
}
