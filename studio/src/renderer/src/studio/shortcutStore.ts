import { defaultChords, sanitizeBindings, type Bindings, type Chord } from './shortcuts'

/** Where the rebound keys are kept between runs. */
export interface ShortcutStorage {
  load(): Promise<unknown>
  save(bindings: Bindings): void
}

export interface ShortcutStore {
  snapshot(): Bindings
  subscribe(listener: () => void): () => void
  /** Reads the saved keys; the defaults stay when there are none or they cannot be read. */
  load(): Promise<void>
  rebind(id: string, chords: Chord[]): void
  reset(id: string): void
  resetAll(): void
}

/** The keys the user rebound, saved at each change and shared with every component that shows or reads a key. */
export function createShortcutStore(storage: ShortcutStorage): ShortcutStore {
  let bindings: Bindings = {}
  const listeners = new Set<() => void>()

  const publish = (next: Bindings): void => {
    bindings = next
    listeners.forEach((listener) => listener())
  }
  const change = (next: Bindings): void => {
    publish(next)
    storage.save(next)
  }
  const rebind = (id: string, chords: Chord[]): void => {
    if (sameChords(chords, defaultChords(id))) {
      reset(id)
      return
    }
    change({ ...bindings, [id]: chords })
  }
  const reset = (id: string): void => {
    if (bindings[id] === undefined) {
      return
    }
    const rest = { ...bindings }
    delete rest[id]
    change(rest)
  }

  return {
    snapshot: () => bindings,
    subscribe(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    async load() {
      try {
        publish(sanitizeBindings(await storage.load()))
      } catch {
        publish({})
      }
    },
    rebind,
    reset,
    resetAll: () => change({})
  }
}

function sameChords(first: Chord[], second: Chord[]): boolean {
  return first.length === second.length && first.every((chord, index) => chord === second[index])
}
