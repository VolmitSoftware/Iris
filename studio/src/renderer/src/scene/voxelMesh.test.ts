import type { BufferGeometry } from 'three'
import { describe, expect, it } from 'vitest'
import type { TileVoxels } from '../bridge/types'
import type { Direction } from './blockModels'
import type { BlockLook } from './blockLooks'
import { ColumnField } from './columnField'
import type { CoverFace, CoverLook } from './coverLooks'
import { cellAt } from './terrainAtlas'
import type { TerrainSurface } from './terrainMesh'
import { voxelGeometries, type SurfaceVoxels } from './voxelMesh'

const GROUND = 10
const DOWN = 1
const UP = 1 << 1
const NORTH = 1 << 2
const STONE = 0
const LEAVES = 1
const GRASS = 2
const WATER = 3
const plain = cellAt(0, 0xffffff)
const stoneLook: BlockLook = {
  top: { cell: cellAt(1, 0x808080), tint: 0xffffff, color: 0x808080 },
  side: { cell: cellAt(2, 0x808080), tint: 0xffffff, color: 0x808080 },
  below: { cell: cellAt(2, 0x808080), tint: 0xffffff, color: 0x808080 }
}

function face(cullFace: Direction | null, gappy: boolean): CoverFace {
  return { corners: [[0, 1, 0], [0, 0, 0], [1, 0, 0], [1, 1, 0]], uvs: [[0, 0], [0, 1], [1, 1], [1, 0]], cell: cellAt(3, 0x00ff00, gappy), color: [0, 1, 0], cullFace }
}

const leaves: CoverLook = { faces: (['down', 'up', 'north', 'south', 'west', 'east'] as const).map((side) => face(side, true)) }
const grass: CoverLook = { faces: [face(null, true), face(null, true)] }

const surface: TerrainSurface = {
  columns: 4,
  rows: 4,
  originX: 100,
  originZ: 200,
  blocksPerColumn: 1,
  groundAt: () => GROUND,
  waterAt: () => null,
  colorAt: () => 0x808080
}

function voxelsOf(blocks: number[], faces: number[]): SurfaceVoxels {
  const voxels: TileVoxels = {
    x: Uint8Array.from(blocks.map((_, at) => at)),
    z: new Uint8Array(blocks.length),
    y: new Int16Array(blocks.length).fill(GROUND),
    blocks: Int16Array.from(blocks),
    faces: Uint8Array.from(faces)
  }
  return { voxels, models: [null, leaves, grass, null], looks: [stoneLook, null, null, null], colors: [0x808080, 0, 0, 0], isWater: [false, false, false, true] }
}

const faceCount = (geometry: BufferGeometry | null): number => (geometry === null ? 0 : geometry.getIndex()!.count / 6)

describe('voxel mesh', () => {
  it('draws a block without a model as a cube of its look, on the sides that face the open air', () => {
    const { opaque, cutOut } = voxelGeometries(new ColumnField(surface), voxelsOf([STONE], [UP]), plain)

    expect(faceCount(opaque)).toBe(1)
    expect(faceCount(cutOut)).toBe(0)
    expect(opaque!.getAttribute('position').getY(0)).toBe(GROUND + 1)
    expect(opaque!.getAttribute('position').getX(0)).toBe(100)
  })

  it('leaves out the faces of a model a neighbour hides, and cuts out the faces of a picture with gaps', () => {
    const { opaque, cutOut } = voxelGeometries(new ColumnField(surface), voxelsOf([LEAVES], [UP | NORTH]), plain)

    expect(faceCount(opaque)).toBe(0)
    expect(faceCount(cutOut)).toBe(2)
  })

  it('draws every face of a model that nothing hides, such as a plant, whatever sides face the air', () => {
    const { cutOut } = voxelGeometries(new ColumnField(surface), voxelsOf([GRASS], [0]), plain)

    expect(faceCount(cutOut)).toBe(2)
  })

  it('draws water as the sides of its sheet that face the air', () => {
    const { water, opaque } = voxelGeometries(new ColumnField(surface), voxelsOf([WATER], [UP | DOWN]), plain)

    expect(faceCount(water)).toBe(2)
    expect(faceCount(opaque)).toBe(0)
  })
})
