import { Box3, Vector3, type Ray } from 'three'
import type { TerrainSurface } from './terrainMesh'

const NO_VALUE = Number.NaN
const MARCH_STEPS_PER_BLOCK = 2
const MARCH_REFINEMENTS = 16

/**
 * The surface read once into columns of one block: where each column's ground and water faces stand, and its colour.
 * A top face stands one above the block's Y, so ground and water sit where a player stands on them. Where nothing is
 * known, the column has no ground and no water.
 */
export class ColumnField {
  readonly columns: number
  readonly rows: number
  readonly originX: number
  readonly originZ: number
  readonly groundTop: Float32Array
  readonly waterTop: Float32Array
  readonly color: Int32Array

  constructor(surface: TerrainSurface) {
    this.columns = surface.columns
    this.rows = surface.rows
    this.originX = surface.originX
    this.originZ = surface.originZ
    const count = this.columns * this.rows
    this.groundTop = new Float32Array(count).fill(NO_VALUE)
    this.waterTop = new Float32Array(count).fill(NO_VALUE)
    this.color = new Int32Array(count)
    for (let row = 0; row < this.rows; row++) {
      for (let column = 0; column < this.columns; column++) {
        const ground = surface.groundAt(column, row)
        if (ground === null) {
          continue
        }
        const water = surface.waterAt(column, row)
        const index = this.indexOf(column, row)
        this.groundTop[index] = ground + 1
        this.waterTop[index] = water === null ? NO_VALUE : water + 1
        this.color[index] = surface.colorAt(column, row)
      }
    }
  }

  indexOf(column: number, row: number): number {
    return row * this.columns + column
  }

  contains(column: number, row: number): boolean {
    return column >= 0 && row >= 0 && column < this.columns && row < this.rows
  }

  hasGround(column: number, row: number): boolean {
    return this.contains(column, row) && !Number.isNaN(this.groundTop[this.indexOf(column, row)])
  }

  /** The face the column shows to the sky: its water where it has some, else its ground; null where nothing is known. */
  surfaceTop(column: number, row: number): number | null {
    if (!this.hasGround(column, row)) {
      return null
    }
    const index = this.indexOf(column, row)
    return Number.isNaN(this.waterTop[index]) ? this.groundTop[index] : this.waterTop[index]
  }

  lowestTop(): number {
    let lowest = Infinity
    for (const top of this.groundTop) {
      if (!Number.isNaN(top)) {
        lowest = Math.min(lowest, top)
      }
    }
    return Number.isFinite(lowest) ? lowest : 0
  }

  /** The box round the field from {@code base} to its highest ground or water. */
  bounds(base: number): Box3 {
    let highest = base
    for (const tops of [this.groundTop, this.waterTop]) {
      for (const top of tops) {
        if (!Number.isNaN(top)) {
          highest = Math.max(highest, top)
        }
      }
    }
    return new Box3(new Vector3(this.originX, base, this.originZ), new Vector3(this.originX + this.columns, highest, this.originZ + this.rows))
  }

  /**
   * Where the ray first comes down onto the ground or the water over it, found by marching along it through the
   * {@code bounds}, so a pick does not have to test every face. A ray that starts below the surface inside the bounds,
   * or runs over columns where nothing is known and out of the field, finds nothing there.
   */
  surfaceAlong(ray: Ray, bounds: Box3): Vector3 | null {
    const start = bounds.containsPoint(ray.origin) ? ray.origin.clone() : ray.intersectBox(bounds, new Vector3())
    if (start === null) {
      return null
    }
    const step = 1 / MARCH_STEPS_PER_BLOCK
    const reach = bounds.getSize(new Vector3()).length()
    const pointAt = (distance: number): Vector3 => ray.direction.clone().multiplyScalar(distance).add(start)
    const isBelow = (point: Vector3): boolean => {
      const top = this.surfaceTop(Math.floor(point.x - this.originX), Math.floor(point.z - this.originZ))
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
}
