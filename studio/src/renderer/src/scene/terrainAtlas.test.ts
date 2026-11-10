import { describe, expect, it } from 'vitest'
import { cellAt, MOST_CELLS } from './terrainAtlas'

const CELLS_PER_SIDE = Math.sqrt(MOST_CELLS)

describe('terrain atlas cells', () => {
  it('counts cells row by row from the top left', () => {
    expect(cellAt(0, 0)).toMatchObject({ u: 0, v: 0 })
    expect(cellAt(1, 0).u).toBe(1 / CELLS_PER_SIDE)
    expect(cellAt(CELLS_PER_SIDE, 0)).toMatchObject({ u: 0, v: 1 / CELLS_PER_SIDE })
  })

  it('keeps every cell inside the atlas and apart from the others', () => {
    const last = cellAt(MOST_CELLS - 1, 0)
    expect(last.u + last.width).toBeCloseTo(1)
    expect(last.v + last.height).toBeCloseTo(1)
    expect(cellAt(5, 0).width).toBe(1 / CELLS_PER_SIDE)
  })

  it('carries the average colour of its picture', () => {
    expect(cellAt(3, 0x336699).average).toBe(0x336699)
  })
})
