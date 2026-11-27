import { describe, expect, it, vi } from 'vitest'
import { SuggestionStore } from './suggestionStore'

describe('SuggestionStore', () => {
  it('asks once for a list however often it is read', async () => {
    const fetch = vi.fn().mockResolvedValue(['minecraft:stone'])
    const store = new SuggestionStore(fetch)

    await store.load({ from: 'reference', reference: 'BLOCK' })
    const again = await store.load({ from: 'reference', reference: 'BLOCK' })

    expect(again).toEqual(['minecraft:stone'])
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenCalledWith({ reference: 'BLOCK' })
  })

  it('asks again after a list could not be read', async () => {
    const fetch = vi.fn().mockRejectedValueOnce(new Error('down')).mockResolvedValue(['birch'])
    const store = new SuggestionStore(fetch)

    await expect(store.load({ from: 'resource', resource: 'iris:object' })).rejects.toThrow('down')
    await expect(store.load({ from: 'resource', resource: 'iris:object' })).resolves.toEqual(['birch'])
    expect(fetch).toHaveBeenLastCalledWith({ resource: 'iris:object' })
  })
})
