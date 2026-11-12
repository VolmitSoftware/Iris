import { describe, expect, it } from 'vitest'
import type { MapTile, TileBlocks } from '../bridge/types'
import { MapTerrain } from './mapTerrain'

const SIZE = 2
const CELLS = (SIZE + 2) ** 2
const GROUND_Y = 64
const LEAF_Y = 70
const STONE = 0x808080
const LEAVES = 0x00ff00
// The tile's first column, past the ring of its neighbours' cells.
const FIRST_CELL = SIZE + 2 + 1

// A tile of stone at one height, with a leaf raised over its first column.
function tileWithALeaf(): MapTile {
  const raisedStart = new Int32Array(CELLS + 1)
  raisedStart.fill(1, FIRST_CELL + 1)
  const blocks: TileBlocks = {
    names: ['stone', 'leaves'],
    ground: new Int16Array(CELLS),
    cover: new Int16Array(CELLS).fill(-1),
    raisedStart,
    raisedY: Int16Array.of(LEAF_Y),
    raised: Int16Array.of(1),
    voxels: null,
    colors: [STONE, LEAVES],
    looks: [],
    covers: [],
    models: []
  }
  return {
    size: SIZE,
    blocksPerPixel: 1,
    subjectKind: 'BIOME',
    labels: [],
    subjects: new Int16Array(CELLS),
    regions: new Int16Array(CELLS),
    heights: new Float32Array(CELLS).fill(GROUND_Y),
    pickNoise: null,
    blocks
  }
}

function terrainAt(level: number): MapTerrain {
  return new MapTerrain(tileWithALeaf(), { tileX: 0, tileZ: 0, level }, 'blocks', 0)
}

describe('map terrain', () => {
  it('stands raised blocks on columns a block wide, over the ground they stand on', () => {
    const terrain = terrainAt(0)

    expect(terrain.groundAt(0, 0)).toBe(GROUND_Y)
    expect(terrain.colorAt(0, 0)).toBe(STONE)
    expect(terrain.raisedAt(0, 0)).toEqual([{ y: LEAF_Y, color: LEAVES, look: null }])
  })

  it('takes the highest raised block as the top of a column wider than a block', () => {
    const terrain = terrainAt(3)

    expect(terrain.groundAt(0, 0)).toBe(LEAF_Y)
    expect(terrain.colorAt(0, 0)).toBe(LEAVES)
    expect(terrain.raisedAt(0, 0)).toEqual([])
    expect(terrain.groundAt(1, 0)).toBe(GROUND_Y)
  })
})
