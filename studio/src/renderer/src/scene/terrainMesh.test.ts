import type { Mesh } from 'three'
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
