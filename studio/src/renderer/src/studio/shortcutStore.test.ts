import { describe, expect, it } from 'vitest'
import { createShortcutStore, type ShortcutStorage } from './shortcutStore'
import type { Bindings } from './shortcuts'

function storageHolding(stored: unknown): ShortcutStorage & { saved: Bindings[] } {
  const saved: Bindings[] = []
  return { saved, load: () => Promise.resolve(stored), save: (bindings) => void saved.push(bindings) }
}

describe('createShortcutStore', () => {
  it('starts with the defaults and reads the saved keys on load', async () => {
    const store = createShortcutStore(storageHolding({ grid: ['x'], help: 'nonsense' }))
    expect(store.snapshot()).toEqual({})

    await store.load()

    expect(store.snapshot()).toEqual({ grid: ['x'] })
  })

  it('keeps the defaults when the saved keys cannot be read', async () => {
    const store = createShortcutStore({ load: () => Promise.reject(new Error('unreadable')), save: () => undefined })
    await store.load()
    expect(store.snapshot()).toEqual({})
  })

  it('saves and announces every change', () => {
    const storage = storageHolding({})
    const store = createShortcutStore(storage)
    let announced = 0
    store.subscribe(() => announced++)

    store.rebind('grid', ['x'])
    store.rebind('redo', [])

    expect(store.snapshot()).toEqual({ grid: ['x'], redo: [] })
    expect(storage.saved).toEqual([{ grid: ['x'] }, { grid: ['x'], redo: [] }])
    expect(announced).toBe(2)
  })

  it('drops a binding that is the default again, and resets one or all', () => {
    const storage = storageHolding({})
    const store = createShortcutStore(storage)
    store.rebind('grid', ['x'])
    store.rebind('zoomIn', ['z'])

    store.rebind('grid', ['g'])
    expect(store.snapshot()).toEqual({ zoomIn: ['z'] })

    store.reset('zoomIn')
    store.reset('zoomIn')
    expect(store.snapshot()).toEqual({})
    expect(storage.saved).toHaveLength(4)

    store.rebind('grid', ['x'])
    store.resetAll()
    expect(store.snapshot()).toEqual({})
  })

  it('stops announcing to a listener that unsubscribed', () => {
    const store = createShortcutStore(storageHolding({}))
    let announced = 0
    const unsubscribe = store.subscribe(() => announced++)
    unsubscribe()
    store.rebind('grid', ['x'])
    expect(announced).toBe(0)
  })
})
