import { Ray, Vector3 } from 'three'
import { describe, expect, it } from 'vitest'
import { ColumnField } from './columnField'
import { marchOntoSurface } from './surfaceMarch'

const GROUND = 10
const POND_GROUND = 7
const WATER = 9
const REACH = 500
const CEILING = 100

// Three by three columns from (100, 200): nothing known at the north west corner, a pond at the middle column.
const field = new ColumnField({
  columns: 3,
  rows: 3,
  originX: 100,
  originZ: 200,
  blocksPerColumn: 1,
  groundAt: (column, row) => (column === 0 && row === 0 ? null : column === 1 && row === 1 ? POND_GROUND : GROUND),
  waterAt: (column, row) => (column === 1 && row === 1 ? WATER : null),
  colorAt: () => 0
})

function pick(origin: Vector3, direction: Vector3): Vector3 | null {
  return marchOntoSurface(new Ray(origin, direction.normalize()), REACH, CEILING, (x, z) => field.surfaceTopOver(x, z))
}

describe('marching onto the surface', () => {
  it('finds the water over the pond and the top of the ground beside it', () => {
    const water = pick(new Vector3(101.5, 30, 201.5), new Vector3(0, -1, 0))!
    expect(water.x).toBeCloseTo(101.5, 3)
    expect(water.y).toBeCloseTo(WATER + 1, 3)
    expect(water.z).toBeCloseTo(201.5, 3)
    expect(pick(new Vector3(102.5, 30, 201.5), new Vector3(0, -1, 0))!.y).toBeCloseTo(GROUND + 1, 3)
  })

  it('finds where a slanted ray meets the side of a column', () => {
    expect(pick(new Vector3(95, GROUND, 202.5), new Vector3(1, -0.01, 0))!.x).toBeCloseTo(100, 1)
  })

  it('finds nothing over an unknown column, beside the field, or from below the ground', () => {
    expect(pick(new Vector3(100.5, 30, 200.5), new Vector3(0, -1, 0))).toBeNull()
    expect(pick(new Vector3(300, 30, 202.5), new Vector3(0, -1, 0))).toBeNull()
    expect(pick(new Vector3(102.5, GROUND - 2, 202.5), new Vector3(0, 1, 0))).toBeNull()
  })

  it('meets the side where a ray comes in from the side below the top', () => {
    const hit = pick(new Vector3(90, GROUND - 1, 202.5), new Vector3(1, 0, 0))!
    expect(hit.x).toBeCloseTo(100, 3)
    expect(hit.y).toBeCloseTo(GROUND - 1, 3)
  })

  it('gives up on a ray that climbs above the highest ground', () => {
    expect(pick(new Vector3(95, 50, 202.5), new Vector3(1, 1, 0))).toBeNull()
  })

  it('reaches ground far away, where it steps further at a time', () => {
    const far = new ColumnField({
      columns: 1,
      rows: 1,
      originX: 400,
      originZ: 0,
      blocksPerColumn: 64,
      groundAt: () => GROUND,
      waterAt: () => null,
      colorAt: () => 0
    })
    const hit = marchOntoSurface(new Ray(new Vector3(0, 100, 30), new Vector3(1, -0.2, 0).normalize()), 5000, CEILING, (x, z) => far.surfaceTopOver(x, z))!
    expect(hit.y).toBeCloseTo(GROUND + 1, 2)
    expect(hit.x).toBeGreaterThan(400)
  })
})
