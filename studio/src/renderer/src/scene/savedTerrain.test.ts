import { Texture } from 'three'
import { describe, expect, it } from 'vitest'
import type { SavedTile } from '../bridge/types'
import type { BlockLook } from './blockLooks'
import { SavedTerrain, type ReadSavedTile, type SavedTerrainColoring } from './savedTerrain'
import { cellAt, type TerrainPictures } from './terrainAtlas'

const GRASS = 'minecraft:grass_block[snowy=false]'
const STONE = 'minecraft:stone'
const FACE = { cell: cellAt(1, 0x808080), tint: 0xffffff, color: 0x808080 }
const LOOK: BlockLook = { top: FACE, side: FACE, below: FACE }
const pictures: TerrainPictures = { texture: new Texture(), plain: cellAt(0, 0xffffff) }

// Two by two columns: grass, stone, then two of stone.
const tile = {
  size: 2,
  blocksPerPixel: 1,
  groundY: Int16Array.of(64, 64, 64, 64),
  waterSurfaceY: Int16Array.of(-1, -1, -1, -1),
  blocks: Int16Array.of(0, 1, 1, 1),
  biomes: Int16Array.of(0, 0, 0, 0),
  blockNames: ['minecraft:grass_block', STONE],
  blocksDrawnAs: [GRASS, STONE],
  biomeLabels: [{ color: 0x00ff00 }],
  noGround: -32768,
  noWater: -1
} as unknown as SavedTile

const read: ReadSavedTile = {
  tile,
  blockColors: new Map([
    [GRASS, 0x4a7a2a],
    [STONE, 0x7f7f7f]
  ]),
  blockLooks: new Map([[GRASS, LOOK]])
}

function terrain(coloring: SavedTerrainColoring, level = 0): SavedTerrain {
  return new SavedTerrain(read, { level, tileX: 0, tileZ: 0 }, coloring, null, pictures)
}

describe('saved terrain looks', () => {
  it('shows the look of the block of a column when coloured by blocks and a column is a block', () => {
    expect(terrain('blocks').lookAt(0, 0)).toBe(LOOK)
  })

  it('has no look for a block without one', () => {
    expect(terrain('blocks').lookAt(1, 0)).toBeNull()
  })

  it('has none in the other colourings', () => {
    expect(terrain('biomes').lookAt(0, 0)).toBeNull()
    expect(terrain('height').lookAt(0, 0)).toBeNull()
  })

  it('has none where a column is wider than a block', () => {
    expect(terrain('blocks', 1).lookAt(0, 0)).toBeNull()
  })

  it('still colours by block where there is no look', () => {
    expect(terrain('blocks').colorAt(0, 0)).toBe(0x4a7a2a)
    expect(terrain('blocks').colorAt(1, 0)).toBe(0x7f7f7f)
  })
})
