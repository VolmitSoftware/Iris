import { Ray, Vector3, type Mesh } from 'three'
import { describe, expect, it } from 'vitest'
import { buildTerrainMesh, type TerrainSurface } from './terrainMesh'

const GROUND = 10
const WATER = 12
const BLOCKS_PER_VERTEX = 2

// Three by three vertices: nothing known at the north west corner, a pond at the middle vertex.
const surface: TerrainSurface = {
  columns: 3,
  rows: 3,
  worldX: (column) => column * BLOCKS_PER_VERTEX,
  worldZ: (row) => row * BLOCKS_PER_VERTEX,
  groundAt: (column, row) => (column === 0 && row === 0 ? null : GROUND),
  waterAt: (column, row) => (column === 1 && row === 1 ? WATER : null),
  colorAt: () => 0x808080
}

function meshes(): Mesh[] {
  return buildTerrainMesh(surface).content.object.children as Mesh[]
}

function triangles(mesh: Mesh): number {
  return mesh.geometry.getIndex()!.count / 3
}

describe('terrain mesh', () => {
  it('leaves out the squares that touch a hole', () => {
    const [ground] = meshes()
    expect(triangles(ground)).toBe(3 * 2)
  })

  it('frames the known surface from below its walls to its highest water, not the unused vertices', () => {
    const bounds = buildTerrainMesh(surface).content.bounds
    expect(bounds.min.toArray()).toEqual([0, GROUND - 8, 0])
    expect(bounds.max.toArray()).toEqual([2 * BLOCKS_PER_VERTEX, WATER, 2 * BLOCKS_PER_VERTEX])
  })

  it('carries the water out to the shore around the vertex that holds it, except into the hole', () => {
    const [, , water] = meshes()
    expect(triangles(water)).toBe(3 * 2)
  })

  it('stands walls only between rim vertices that both have ground', () => {
    const [, landWalls] = meshes()
    // The rim has eight segments; the two at the hole's corner fall away.
    expect(triangles(landWalls)).toBe(6 * 2)
  })
})

describe('terrain surface picking', () => {
  const content = buildTerrainMesh(surface).content
  const pick = (origin: Vector3, direction: Vector3): Vector3 | null => content.surfaceAlong!(new Ray(origin, direction.normalize()))

  it('finds where a slanted ray meets the water over the pond', () => {
    const hit = pick(new Vector3(-7, WATER + 10, 3), new Vector3(1, -1, 0))!
    expect(hit.x).toBeCloseTo(3, 3)
    expect(hit.y).toBeCloseTo(WATER, 3)
    expect(hit.z).toBeCloseTo(3, 3)
  })

  it('finds nothing over a hole, beside the field, or from below the ground', () => {
    expect(pick(new Vector3(1, 30, 1), new Vector3(0, -1, 0))).toBeNull()
    expect(pick(new Vector3(100, 30, 3), new Vector3(0, -1, 0))).toBeNull()
    expect(pick(new Vector3(3, GROUND - 2, 3), new Vector3(0, 1, 0))).toBeNull()
  })

  it('meets the wall where a ray comes in from the side below the rim', () => {
    const hit = pick(new Vector3(-10, GROUND - 1, 3), new Vector3(1, 0, 0))!
    expect(hit.x).toBeCloseTo(0, 3)
    expect(hit.y).toBeCloseTo(GROUND - 1, 3)
  })
})
