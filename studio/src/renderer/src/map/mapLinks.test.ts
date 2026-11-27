import { describe, expect, it } from 'vitest'
import { MapLinks } from './mapLinks'

const SPOT = { centerX: 10, centerZ: -4, blocksPerPixel: 2 }

describe('linked maps', () => {
  it('moves the other linked maps with the one moved', () => {
    const links = new MapLinks()
    const seen: string[] = []
    links.join('a', () => seen.push('a'))
    links.join('b', () => seen.push('b'))
    links.moved('a', SPOT)
    expect(seen).toEqual(['b'])
  })

  it('ignores a map that is not linked', () => {
    const links = new MapLinks()
    const seen: string[] = []
    links.join('a', () => seen.push('a'))
    links.moved('b', SPOT)
    expect(seen).toEqual([])
  })

  it('tells a map joining where the linked maps look', () => {
    const links = new MapLinks()
    links.join('a', () => undefined)
    expect(links.join('b', () => undefined)).toBeNull()
    links.moved('a', SPOT)
    links.leave('b')
    expect(links.join('b', () => undefined)).toEqual(SPOT)
  })
})
