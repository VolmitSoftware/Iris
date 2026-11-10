import { Vector3, type BufferGeometry, type Mesh } from 'three'
import { describe, expect, it } from 'vitest'
import { buildTerrainMesh, type TerrainSurface } from './terrainMesh'
import { submergedTint, tintAt } from './waterTint'

const GROUND = 10
const POND_GROUND = 7
const WATER = 9
const BASE_DEPTH = 8
const BASE = POND_GROUND + 1 - BASE_DEPTH
const GREY = 0x808080
const RED = 0xff0000
const GREY_LIGHT = 0x80 / 255
const SIDE_LIGHT = 0.6
const DEEP_RED = 0x0f / 255

// Three by three columns: nothing known at the north west corner, a pond at the middle column.
function surfaceOf(colorAt: TerrainSurface['colorAt'] = () => GREY): TerrainSurface {
  return {
    columns: 3,
    rows: 3,
    originX: 100,
    originZ: 200,
    blocksPerColumn: 1,
    groundAt: (column, row) => {
      if (column === 0 && row === 0) {
        return null
      }
      return column === 1 && row === 1 ? POND_GROUND : GROUND
    },
    waterAt: (column, row) => (column === 1 && row === 1 ? WATER : null),
    colorAt
  }
}

const surface = surfaceOf()
const dry: TerrainSurface = { ...surface, waterAt: () => null }

function meshes(terrain: TerrainSurface = surface): { ground: Mesh; water: Mesh } {
  const [ground, water] = buildTerrainMesh(terrain, BASE).object.children as Mesh[]
  return { ground, water }
}

function quads(mesh: Mesh): number {
  return mesh.geometry.getIndex()!.count / 6
}

// The quads of a geometry as the point in the middle of each and the way it faces.
function faces(geometry: BufferGeometry): { middle: Vector3; normal: Vector3 }[] {
  const positions = geometry.getAttribute('position')
  const found: { middle: Vector3; normal: Vector3 }[] = []
  for (let first = 0; first < positions.count; first += 4) {
    const corner = (index: number): Vector3 => new Vector3().fromBufferAttribute(positions, first + index)
    const [origin, up, along, far] = [corner(0), corner(1), corner(2), corner(3)]
    found.push({
      middle: origin.clone().add(far).multiplyScalar(0.5),
      normal: up.clone().sub(origin).cross(along.clone().sub(origin)).normalize()
    })
  }
  return found
}

function redOf(geometry: BufferGeometry, vertex: number): number {
  return geometry.getAttribute('color').getX(vertex)
}

// The red of the grey ground after sinking by the tint, lit as given.
function sunkRed(tint: number, light = 1): number {
  return GREY_LIGHT * light + (DEEP_RED - GREY_LIGHT * light) * tint
}

describe('terrain mesh', () => {
  it('draws one top for each run of neighbouring columns at one height', () => {
    const { ground } = meshes()
    const tops = faces(ground.geometry).filter((face) => face.normal.y === 1)
    expect(tops).toHaveLength(5)
    expect(tops.filter((top) => top.middle.y === GROUND + 1)).toHaveLength(4)
  })

  it('draws a side only where a column stands above its neighbour or beside nothing known', () => {
    const { ground } = meshes(dry)
    expect(quads(ground) - faces(ground.geometry).filter((face) => face.normal.y === 1).length).toBe(10)
  })

  it('keeps columns of other colours apart', () => {
    const striped = surfaceOf((column) => (column === 2 ? RED : GREY))
    const tops = faces(meshes(striped).ground.geometry).filter((face) => face.normal.y === 1)
    expect(tops).toHaveLength(7)
  })

  it('faces every side outwards from the column it belongs to', () => {
    const { ground } = meshes(dry)
    const sides = faces(ground.geometry).filter((face) => face.normal.y === 0)
    const pondSide = sides.find((side) => side.middle.x === 101 + 1 && side.normal.x === -1 && side.middle.z === 201.5)!
    expect(pondSide.middle.y).toBe((POND_GROUND + 1 + GROUND + 1) / 2)
    const edge = sides.find((side) => side.normal.z === -1 && side.middle.z === 200)!
    expect(edge.middle.y).toBe((GROUND + 1 + POND_GROUND + 1 - BASE_DEPTH) / 2)
  })

  it('lights tops fully and sides less', () => {
    const colors = meshes(dry).ground.geometry.getAttribute('color')
    const lights = new Set<number>()
    for (let vertex = 0; vertex < colors.count; vertex += 4) {
      lights.add(Math.round((colors.getX(vertex) * 255 * 100) / 0x80) / 100)
    }
    expect([...lights].sort()).toEqual([0.6, 0.8, 1])
  })

  it('draws the water of a column as its top, and no side where the ground beside it stands higher', () => {
    const { water } = meshes()
    expect(quads(water)).toBe(1)
    expect(faces(water.geometry)[0].middle.toArray()).toEqual([101.5, WATER + 1, 201.5])
  })

  it('draws a water side where the water stands above what lies beside it, but not beside nothing known', () => {
    const lake = { ...surface, groundAt: (column: number, row: number) => (column === 1 && row === 1 ? POND_GROUND : column === 0 && row === 1 ? null : WATER - 3) }
    const sides = faces(meshes(lake).water.geometry).filter((face) => face.normal.y === 0)
    expect(sides).toHaveLength(3)
    expect(sides.every((side) => side.middle.y === (POND_GROUND + 1 + WATER + 1) / 2)).toBe(true)
  })

  it('draws no water side at the edge of the field, where the water goes on in the next one', () => {
    const shore = { ...surface, groundAt: () => WATER - 3, waterAt: () => WATER }
    const sides = faces(meshes(shore).water.geometry).filter((face) => face.normal.y === 0)
    expect(sides).toHaveLength(0)
  })

  it('spreads columns over as many blocks as they are wide', () => {
    const coarse = { ...surface, blocksPerColumn: 4 }
    const tops = faces(meshes(coarse).ground.geometry).filter((face) => face.normal.y === 1)
    const xs = tops.map((top) => top.middle.x)
    expect(Math.min(...xs)).toBeGreaterThanOrEqual(100)
    expect(Math.max(...xs)).toBeLessThanOrEqual(112)
    expect(faces(meshes(coarse).water.geometry)[0].middle.toArray()).toEqual([106, WATER + 1, 206])
  })

  it('leaves out the columns of a hole and walls what stands beside it', () => {
    const holed = buildTerrainMesh(surface, BASE, [{ fromColumn: 2, fromRow: 2, toColumn: 3, toRow: 3 }])
    const [ground] = holed.object.children as Mesh[]
    const tops = faces(ground.geometry).filter((face) => face.normal.y === 1)
    expect(tops.every((top) => !(top.middle.x === 102.5 && top.middle.z === 202.5))).toBe(true)
    expect(holed.field.surfaceTop(2, 2)).toBeNull()
    expect(faces(ground.geometry).filter((face) => face.normal.y === 0 && face.middle.x === 102.5 && face.middle.z === 202)).toHaveLength(1)
  })

  it('sinks the ground under water toward the deep colour, the more the deeper it lies', () => {
    const pondTopRed = (terrain: TerrainSurface): number => {
      const ground = meshes(terrain).ground.geometry
      const pond = faces(ground).findIndex((face) => face.normal.y === 1 && face.middle.x === 101.5 && face.middle.z === 201.5)
      return redOf(ground, pond * 4)
    }
    const shallow = pondTopRed(surface)
    const deep = pondTopRed({ ...surface, waterAt: () => POND_GROUND + 30 })
    expect(pondTopRed(dry)).toBeCloseTo(GREY_LIGHT)
    expect(shallow).toBeCloseTo(sunkRed(tintAt(WATER - POND_GROUND)))
    expect(deep).toBeLessThan(shallow)
  })

  it('leaves ground level with the water dry', () => {
    const level = { ...surface, waterAt: (column: number, row: number) => (column === 1 && row === 1 ? POND_GROUND : null) }
    const ground = meshes(level).ground.geometry
    const pond = faces(ground).findIndex((face) => face.normal.y === 1 && face.middle.x === 101.5 && face.middle.z === 201.5)
    expect(redOf(ground, pond * 4)).toBeCloseTo(GREY_LIGHT)
    expect(tintAt(0)).toBe(0)
  })

  it('sinks only the part of a side under the water, deeper toward its bottom', () => {
    const ground = meshes().ground.geometry
    const toPond = faces(ground)
      .map((face, quad) => ({ ...face, quad }))
      .filter((face) => face.normal.x === -1 && face.middle.x === 102 && face.middle.z === 201.5)
    expect(toPond).toHaveLength(2)
    const [wet, above] = toPond.sort((first, second) => first.middle.y - second.middle.y)
    const [wetBottom, wetTop] = [redOf(ground, wet.quad * 4), redOf(ground, wet.quad * 4 + 1)]
    expect(wet.middle.y).toBe((POND_GROUND + 1 + WATER + 1) / 2)
    expect(wetBottom).toBeLessThan(wetTop)
    expect(wetTop).toBeCloseTo(sunkRed(submergedTint(0), SIDE_LIGHT))
    expect(redOf(ground, above.quad * 4)).toBeCloseTo(GREY_LIGHT * SIDE_LIGHT)
    expect(redOf(ground, above.quad * 4 + 1)).toBeCloseTo(GREY_LIGHT * SIDE_LIGHT)
  })

  it('has no mesh where no column has ground', () => {
    const nothing = buildTerrainMesh({ ...surface, groundAt: () => null }, BASE)
    expect(nothing.object.children).toHaveLength(0)
  })
})
