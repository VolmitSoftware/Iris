import { Box3, BufferAttribute, BufferGeometry, DoubleSide, Group, Mesh, MeshBasicMaterial, Vector3, type Ray } from 'three'
import type { ViewportContent } from '../studio/SceneViewport'

/**
 * A height field to draw, one vertex per map pixel, row by row from the north west: the ground, the water over it,
 * and the ground's colour. Where nothing is known, the ground is null and the field has a hole.
 */
export interface TerrainSurface {
  readonly columns: number
  readonly rows: number
  worldX(column: number): number
  worldZ(row: number): number
  groundAt(column: number, row: number): number | null
  /** The water's surface over the vertex, or null where it is dry. */
  waterAt(column: number, row: number): number | null
  colorAt(column: number, row: number): number
}

export interface TerrainMesh {
  content: ViewportContent
  dispose(): void
}

const WATER_COLOR = 0x3f76e4
const WATER_OPACITY = 0.55
// Light from high in the south west, so slopes read as relief; the darkest side keeps some light.
const LIGHT = new Vector3(-0.6, 1, 0.4).normalize()
const AMBIENT_LIGHT = 0.5
const WALL_DEPTH_BLOCKS = 8
const WALL_TOP_LIGHT = 0.55
const WALL_BASE_LIGHT = 0.25
const NO_VALUE = Number.NaN
const MARCH_STEPS_PER_CELL = 2
const MARCH_REFINEMENTS = 16

/**
 * The surface as ground lit by its slopes, see-through water over it, and walls round the rim down to below the lowest
 * ground, so the square reads as a solid block cut from the world.
 */
export function buildTerrainMesh(surface: TerrainSurface): TerrainMesh {
  const field = HeightField.of(surface)
  const base = field.lowest() - WALL_DEPTH_BLOCKS
  const ground = groundGeometry(field)
  const water = waterGeometry(field)
  const landWalls = rimWall(field, (vertex) => field.ground[vertex], () => base)
  const seaWalls = rimWall(field, (vertex) => field.water[vertex], (vertex) => Math.min(field.ground[vertex], field.water[vertex]))
  const groundMaterial = new MeshBasicMaterial({ vertexColors: true, side: DoubleSide })
  const waterMaterial = new MeshBasicMaterial({ color: WATER_COLOR, transparent: true, opacity: WATER_OPACITY, depthWrite: false, side: DoubleSide })
  const group = new Group()
  group.add(new Mesh(ground, groundMaterial), new Mesh(landWalls, groundMaterial), new Mesh(water, waterMaterial), new Mesh(seaWalls, waterMaterial))
  const geometries = [ground, water, landWalls, seaWalls]
  const bounds = field.bounds(base)
  return {
    content: { object: group, bounds, surfaceAlong: (ray) => field.surfaceAlong(ray, bounds) },
    dispose: () => {
      geometries.forEach((geometry) => geometry.dispose())
      groundMaterial.dispose()
      waterMaterial.dispose()
    }
  }
}

function groundGeometry(field: HeightField): BufferGeometry {
  const { surface, ground } = field
  const positions = new Float32Array(ground.length * 3)
  const colors = new Float32Array(ground.length * 3)
  for (let vertex = 0; vertex < ground.length; vertex++) {
    if (Number.isNaN(ground[vertex])) {
      continue
    }
    const [column, row] = field.columnAndRow(vertex)
    positions.set([surface.worldX(column), ground[vertex], surface.worldZ(row)], vertex * 3)
    const light = AMBIENT_LIGHT + (1 - AMBIENT_LIGHT) * Math.max(0, field.normal(column, row).dot(LIGHT))
    colors.set(rgb(surface.colorAt(column, row), light), vertex * 3)
  }
  return indexedGeometry(positions, colors, field.quadsWhere((corners) => corners.every((vertex) => !Number.isNaN(ground[vertex]))))
}

// Only quads holding water of their own; the vertices next to water carry its level, so it meets the shore.
function waterGeometry(field: HeightField): BufferGeometry {
  const { surface, water, ownWater } = field
  const positions = new Float32Array(water.length * 3)
  for (let vertex = 0; vertex < water.length; vertex++) {
    if (!Number.isNaN(water[vertex])) {
      const [column, row] = field.columnAndRow(vertex)
      positions.set([surface.worldX(column), water[vertex], surface.worldZ(row)], vertex * 3)
    }
  }
  const quads = field.quadsWhere((corners) => corners.every((vertex) => !Number.isNaN(water[vertex])) && corners.some((vertex) => ownWater[vertex]))
  return indexedGeometry(positions, null, quads)
}

/** A wall round the field's rim from {@code topOf} down to {@code bottomOf} each rim vertex, darkening downwards. */
function rimWall(field: HeightField, topOf: (vertex: number) => number, bottomOf: (vertex: number) => number): BufferGeometry {
  const rim = field.rimVertices()
  const positions = new Float32Array(rim.length * 2 * 3)
  const colors = new Float32Array(rim.length * 2 * 3)
  // A rim vertex without a top or a bottom, such as dry land for the water's wall, holds no wall and keeps the origin.
  const standing = rim.map((vertex) => !Number.isNaN(topOf(vertex)) && !Number.isNaN(bottomOf(vertex)))
  rim.forEach((vertex, index) => {
    if (!standing[index]) {
      return
    }
    const [column, row] = field.columnAndRow(vertex)
    const [x, z] = [field.surface.worldX(column), field.surface.worldZ(row)]
    positions.set([x, topOf(vertex), z, x, bottomOf(vertex), z], index * 6)
    const color = field.surface.colorAt(column, row)
    colors.set([...rgb(color, WALL_TOP_LIGHT), ...rgb(color, WALL_BASE_LIGHT)], index * 6)
  })
  const indices: number[] = []
  for (let index = 0; index < rim.length; index++) {
    const next = (index + 1) % rim.length
    if (standing[index] && standing[next]) {
      const [upper, lower, nextUpper, nextLower] = [index * 2, index * 2 + 1, next * 2, next * 2 + 1]
      indices.push(upper, lower, nextUpper, nextUpper, lower, nextLower)
    }
  }
  return indexedGeometry(positions, colors, indices)
}

function indexedGeometry(positions: Float32Array, colors: Float32Array | null, indices: number[] | Uint32Array): BufferGeometry {
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(positions, 3))
  if (colors !== null) {
    geometry.setAttribute('color', new BufferAttribute(colors, 3))
  }
  geometry.setIndex(new BufferAttribute(indices instanceof Uint32Array ? indices : Uint32Array.from(indices), 1))
  return geometry
}

function rgb(color: number, light: number): [number, number, number] {
  return [(((color >> 16) & 0xff) / 255) * light, (((color >> 8) & 0xff) / 255) * light, ((color & 0xff) / 255) * light]
}

/** The surface's ground and water read once per vertex, with no value as NaN, and how its vertices stand together. */
class HeightField {
  readonly ground: Float32Array
  readonly water: Float32Array
  readonly ownWater: Uint8Array

  private constructor(readonly surface: TerrainSurface) {
    const count = surface.columns * surface.rows
    this.ground = new Float32Array(count)
    this.water = new Float32Array(count)
    this.ownWater = new Uint8Array(count)
    for (let vertex = 0; vertex < count; vertex++) {
      const [column, row] = this.columnAndRow(vertex)
      const ground = surface.groundAt(column, row)
      const water = ground === null ? null : surface.waterAt(column, row)
      this.ground[vertex] = ground ?? NO_VALUE
      this.water[vertex] = water ?? NO_VALUE
      this.ownWater[vertex] = water === null ? 0 : 1
    }
    this.extendWaterToShores()
  }

  static of(surface: TerrainSurface): HeightField {
    return new HeightField(surface)
  }

  columnAndRow(vertex: number): [number, number] {
    return [vertex % this.surface.columns, Math.floor(vertex / this.surface.columns)]
  }

  lowest(): number {
    let lowest = Infinity
    for (let vertex = 0; vertex < this.ground.length; vertex++) {
      if (!Number.isNaN(this.ground[vertex])) {
        lowest = Math.min(lowest, this.ground[vertex], Number.isNaN(this.water[vertex]) ? Infinity : this.water[vertex])
      }
    }
    return Number.isFinite(lowest) ? lowest : 0
  }

  /** The box round the field from {@code base} to its highest ground or water; vertices left out of it do not count. */
  bounds(base: number): Box3 {
    let highest = base
    for (let vertex = 0; vertex < this.ground.length; vertex++) {
      for (const height of [this.ground[vertex], this.water[vertex]]) {
        if (!Number.isNaN(height)) {
          highest = Math.max(highest, height)
        }
      }
    }
    const { columns, rows } = this.surface
    return new Box3(
      new Vector3(this.surface.worldX(0), base, this.surface.worldZ(0)),
      new Vector3(this.surface.worldX(columns - 1), highest, this.surface.worldZ(rows - 1))
    )
  }

  /**
   * Where the ray first comes down onto the ground or the water over it, found by marching along it through the
   * {@code bounds}, so a pick does not have to test every triangle. A ray that starts below the surface inside the
   * bounds, or runs over holes and out of the field, finds nothing there.
   */
  surfaceAlong(ray: Ray, bounds: Box3): Vector3 | null {
    const start = bounds.containsPoint(ray.origin) ? ray.origin.clone() : ray.intersectBox(bounds, new Vector3())
    if (start === null) {
      return null
    }
    const cell = Math.abs(this.surface.worldX(1) - this.surface.worldX(0)) || 1
    const step = cell / MARCH_STEPS_PER_CELL
    const reach = bounds.getSize(new Vector3()).length()
    const pointAt = (distance: number): Vector3 => ray.direction.clone().multiplyScalar(distance).add(start)
    const isBelow = (point: Vector3): boolean => {
      const top = this.topAt(point.x, point.z)
      return top !== null && point.y <= top
    }
    if (isBelow(start)) {
      return bounds.containsPoint(ray.origin) ? null : start
    }
    for (let distance = step; distance <= reach; distance += step) {
      if (!isBelow(pointAt(distance))) {
        continue
      }
      let above = distance - step
      let below = distance
      for (let refinement = 0; refinement < MARCH_REFINEMENTS; refinement++) {
        const middle = (above + below) / 2
        if (isBelow(pointAt(middle))) {
          below = middle
        } else {
          above = middle
        }
      }
      return pointAt(below)
    }
    return null
  }

  /** The height of the ground, or of the water over it, at a place in the field, blended between its four vertices. */
  private topAt(x: number, z: number): number | null {
    const { columns, rows } = this.surface
    if (columns < 2 || rows < 2) {
      return null
    }
    const columnAt = (x - this.surface.worldX(0)) / ((this.surface.worldX(columns - 1) - this.surface.worldX(0)) / (columns - 1))
    const rowAt = (z - this.surface.worldZ(0)) / ((this.surface.worldZ(rows - 1) - this.surface.worldZ(0)) / (rows - 1))
    if (!(columnAt >= 0 && columnAt <= columns - 1 && rowAt >= 0 && rowAt <= rows - 1)) {
      return null
    }
    const column = Math.min(Math.floor(columnAt), columns - 2)
    const row = Math.min(Math.floor(rowAt), rows - 2)
    const [alongColumn, alongRow] = [columnAt - column, rowAt - row]
    const northWest = row * columns + column
    const tops = [northWest, northWest + 1, northWest + columns, northWest + columns + 1].map((vertex) =>
      Number.isNaN(this.water[vertex]) ? this.ground[vertex] : Math.max(this.ground[vertex], this.water[vertex])
    )
    if (tops.some(Number.isNaN)) {
      return null
    }
    const north = tops[0] + (tops[1] - tops[0]) * alongColumn
    const south = tops[2] + (tops[3] - tops[2]) * alongColumn
    return north + (south - north) * alongRow
  }

  /** The two triangles of each grid square whose four corners pass {@code wanted}. */
  quadsWhere(wanted: (corners: [number, number, number, number]) => boolean): Uint32Array {
    const { columns, rows } = this.surface
    const indices: number[] = []
    for (let row = 0; row < rows - 1; row++) {
      for (let column = 0; column < columns - 1; column++) {
        const northWest = row * columns + column
        const southWest = northWest + columns
        if (wanted([northWest, northWest + 1, southWest, southWest + 1])) {
          indices.push(northWest, southWest, northWest + 1, northWest + 1, southWest, southWest + 1)
        }
      }
    }
    return Uint32Array.from(indices)
  }

  normal(column: number, row: number): Vector3 {
    const { columns, rows } = this.surface
    const here = this.ground[row * columns + column]
    const at = (atColumn: number, atRow: number): number => {
      const height = this.ground[Math.min(rows - 1, Math.max(0, atRow)) * columns + Math.min(columns - 1, Math.max(0, atColumn))]
      return Number.isNaN(height) ? here : height
    }
    const spacing = Math.abs(this.surface.worldX(Math.min(columns - 1, 1)) - this.surface.worldX(0)) || 1
    return new Vector3(at(column - 1, row) - at(column + 1, row), 2 * spacing, at(column, row - 1) - at(column, row + 1)).normalize()
  }

  /** The vertices on the edge of the field, once each, going round clockwise from the north west corner. */
  rimVertices(): number[] {
    const { columns, rows } = this.surface
    const rim: number[] = []
    for (let column = 0; column < columns - 1; column++) rim.push(column)
    for (let row = 0; row < rows - 1; row++) rim.push(row * columns + columns - 1)
    for (let column = columns - 1; column > 0; column--) rim.push((rows - 1) * columns + column)
    for (let row = rows - 1; row > 0; row--) rim.push(row * columns)
    return rim
  }

  // A dry vertex next to water takes the highest water beside it, so the water's edge runs under the shore.
  private extendWaterToShores(): void {
    const { columns, rows } = this.surface
    const own = Float32Array.from(this.water)
    for (let row = 0; row < rows; row++) {
      for (let column = 0; column < columns; column++) {
        const vertex = row * columns + column
        if (this.ownWater[vertex] === 1 || Number.isNaN(this.ground[vertex])) {
          continue
        }
        let highest = NO_VALUE
        for (let offsetRow = -1; offsetRow <= 1; offsetRow++) {
          for (let offsetColumn = -1; offsetColumn <= 1; offsetColumn++) {
            const [nearColumn, nearRow] = [column + offsetColumn, row + offsetRow]
            if (nearColumn < 0 || nearRow < 0 || nearColumn >= columns || nearRow >= rows) {
              continue
            }
            const near = own[nearRow * columns + nearColumn]
            if (!Number.isNaN(near) && (Number.isNaN(highest) || near > highest)) {
              highest = near
            }
          }
        }
        this.water[vertex] = highest
      }
    }
  }
}
