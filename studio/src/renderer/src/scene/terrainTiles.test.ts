import { describe, expect, it } from 'vitest'
import { addressOf, ancestorAt, childrenOf, columnsUnder, isInside, originXOf, originZOf, tileBlocks, tileContaining, tileKey } from './terrainTiles'

describe('terrain tiles', () => {
  it('lie edge to edge at every level, 128 columns of 2^level blocks across', () => {
    expect(tileBlocks(0)).toBe(128)
    expect(tileBlocks(3)).toBe(1024)
    expect([originXOf({ level: 2, tileX: -1, tileZ: 3 }), originZOf({ level: 2, tileX: -1, tileZ: 3 })]).toEqual([-512, 1536])
  })

  it('are found by the block they hold, also left and above the origin', () => {
    expect(tileContaining(127.9, 128, 0)).toEqual({ level: 0, tileX: 0, tileZ: 1 })
    expect(tileContaining(-0.5, -128.5, 0)).toEqual({ level: 0, tileX: -1, tileZ: -2 })
    expect(tileContaining(1000, -1000, 3)).toEqual({ level: 3, tileX: 0, tileZ: -1 })
  })

  it('ask the server for the same blocks a column to a pixel', () => {
    expect(addressOf({ level: 3, tileX: -2, tileZ: 5 })).toEqual({ tileX: -2, tileZ: 5, blocksPerPixel: 8 })
  })

  it('split into the four tiles of the level below that cover them', () => {
    const tile = { level: 2, tileX: -1, tileZ: 1 }
    const children = childrenOf(tile)
    expect(children.map(tileKey)).toEqual(['1/-2/2', '1/-1/2', '1/-2/3', '1/-1/3'])
    expect(children.every((child) => ancestorAt(child, 2).tileX === -1 && isInside(child, tile))).toBe(true)
  })

  it('belong to the coarser tile that holds them, however many levels up', () => {
    expect(ancestorAt({ level: 0, tileX: -1, tileZ: 5 }, 3)).toEqual({ level: 3, tileX: -1, tileZ: 0 })
    expect(isInside({ level: 1, tileX: 3, tileZ: 3 }, { level: 2, tileX: 1, tileZ: 1 })).toBe(true)
    expect(isInside({ level: 1, tileX: 4, tileZ: 3 }, { level: 2, tileX: 1, tileZ: 1 })).toBe(false)
    expect(isInside({ level: 2, tileX: 1, tileZ: 1 }, { level: 2, tileX: 1, tileZ: 1 })).toBe(false)
  })

  it('cover the columns of a coarser tile that line up with them', () => {
    const coarse = { level: 2, tileX: 1, tileZ: -1 }
    expect(columnsUnder(coarse, { level: 1, tileX: 3, tileZ: -1 })).toEqual({ fromColumn: 64, fromRow: 64, toColumn: 128, toRow: 128 })
    expect(columnsUnder(coarse, { level: 1, tileX: 2, tileZ: -2 })).toEqual({ fromColumn: 0, fromRow: 0, toColumn: 64, toRow: 64 })
    expect(columnsUnder(coarse, { level: 0, tileX: 7, tileZ: -3 })).toEqual({ fromColumn: 96, fromRow: 32, toColumn: 128, toRow: 64 })
  })

  it('cover one column of a tile seven levels coarser, and no less', () => {
    const coarse = { level: 7, tileX: 0, tileZ: 0 }
    expect(columnsUnder(coarse, { level: 0, tileX: 5, tileZ: 9 })).toEqual({ fromColumn: 5, fromRow: 9, toColumn: 6, toRow: 10 })
  })
})
