import { Ray, Vector3, type BufferGeometry, type Mesh } from 'three'
import { describe, expect, it } from 'vitest'
import { buildTerrainMesh, type TerrainSurface } from './terrainMesh'

const GROUND = 10
const POND_GROUND = 7
const WATER = 9
const BASE_DEPTH = 8
const GREY = 0x808080
const RED = 0xff0000

// Three by three columns: nothing known at the north west corner, a pond at the middle column.
function surfaceOf(colorAt: TerrainSurface['colorAt'] = () => GREY): TerrainSurface {
  return {
    columns: 3,
    rows: 3,
    originX: 100,
    originZ: 200,
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

function meshes(terrain: TerrainSurface = surface): { ground: Mesh; water: Mesh } {
  const [ground, water] = buildTerrainMesh(terrain).content.object.children as Mesh[]
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

describe('terrain mesh', () => {
  it('draws one top for each run of neighbouring columns at one height', () => {
    const { ground } = meshes()
    const tops = faces(ground.geometry).filter((face) => face.normal.y === 1)
    expect(tops).toHaveLength(5)
    expect(tops.filter((top) => top.middle.y === GROUND + 1)).toHaveLength(4)
  })

  it('draws a side only where a column stands above its neighbour or beside nothing known', () => {
    const { ground } = meshes()
    expect(quads(ground) - faces(ground.geometry).filter((face) => face.normal.y === 1).length).toBe(10)
  })

  it('keeps columns of other colours apart', () => {
    const striped = surfaceOf((column) => (column === 2 ? RED : GREY))
    const tops = faces(meshes(striped).ground.geometry).filter((face) => face.normal.y === 1)
    expect(tops).toHaveLength(7)
  })

  it('faces every side outwards from the column it belongs to', () => {
    const { ground } = meshes()
    const sides = faces(ground.geometry).filter((face) => face.normal.y === 0)
    const pondSide = sides.find((side) => side.middle.x === 101 + 1 && side.normal.x === -1 && side.middle.z === 201.5)!
    expect(pondSide.middle.y).toBe((POND_GROUND + 1 + GROUND + 1) / 2)
    const edge = sides.find((side) => side.normal.z === -1 && side.middle.z === 200)!
    expect(edge.middle.y).toBe((GROUND + 1 + POND_GROUND + 1 - BASE_DEPTH) / 2)
  })

  it('lights tops fully and sides less', () => {
    const colors = meshes().ground.geometry.getAttribute('color')
    const lights = new Set<number>()
    for (let vertex = 0; vertex < colors.count; vertex += 4) {
      lights.add(Math.round((colors.getX(vertex) * 255 * 100) / 0x80) / 100)
    }
    expect([...lights].sort()).toEqual([0.6, 0.8, 1])
  })

  it('frames the known columns from below their sides to the highest water or ground', () => {
    const bounds = buildTerrainMesh(surface).content.bounds
    expect(bounds.min.toArray()).toEqual([100, POND_GROUND + 1 - BASE_DEPTH, 200])
    expect(bounds.max.toArray()).toEqual([103, GROUND + 1, 203])
  })

  it('draws the water of a column as its top, and no side where the ground beside it stands higher', () => {
    const { water } = meshes()
    expect(quads(water)).toBe(1)
    expect(faces(water.geometry)[0].middle.toArray()).toEqual([101.5, WATER + 1, 201.5])
  })

  it('draws a water side where the water stands above what lies beside it', () => {
    const lake = { ...surface, groundAt: (column: number, row: number) => (column === 1 && row === 1 ? POND_GROUND : column === 0 && row === 0 ? null : WATER - 3) }
    const sides = faces(meshes(lake).water.geometry).filter((face) => face.normal.y === 0)
    expect(sides).toHaveLength(4)
    expect(sides.every((side) => side.middle.y === (POND_GROUND + 1 + WATER + 1) / 2)).toBe(true)
  })
})

describe('terrain surface picking', () => {
  const content = buildTerrainMesh(surface).content
  const pick = (origin: Vector3, direction: Vector3): Vector3 | null => content.surfaceAlong!(new Ray(origin, direction.normalize()))

  it('finds the water over the pond and the top of the ground beside it', () => {
    const water = pick(new Vector3(101.5, 30, 201.5), new Vector3(0, -1, 0))!
    expect(water.x).toBeCloseTo(101.5, 3)
    expect(water.y).toBeCloseTo(WATER + 1, 3)
    expect(water.z).toBeCloseTo(201.5, 3)
    expect(pick(new Vector3(102.5, 30, 201.5), new Vector3(0, -1, 0))!.y).toBeCloseTo(GROUND + 1, 3)
  })

  it('finds where a slanted ray meets the side of a column', () => {
    const hit = pick(new Vector3(95, GROUND, 202.5), new Vector3(1, -0.01, 0))!
    expect(hit.x).toBeCloseTo(100, 1)
  })

  it('finds nothing over an unknown column, beside the field, or from below the ground', () => {
    expect(pick(new Vector3(100.5, 30, 200.5), new Vector3(0, -1, 0))).toBeNull()
    expect(pick(new Vector3(300, 30, 202.5), new Vector3(0, -1, 0))).toBeNull()
    expect(pick(new Vector3(102.5, GROUND - 2, 202.5), new Vector3(0, 1, 0))).toBeNull()
  })

  it('meets the side where a ray comes in from the side below the rim', () => {
    const hit = pick(new Vector3(90, GROUND - 1, 202.5), new Vector3(1, 0, 0))!
    expect(hit.x).toBeCloseTo(100, 3)
    expect(hit.y).toBeCloseTo(GROUND - 1, 3)
  })
})
