import type { BlockLook } from './blockLooks'
import type { TerrainPictures } from './terrainAtlas'
import type { TerrainSurface } from './terrainMesh'
import type { ColumnArea } from './terrainTiles'

const NO_VALUE = Number.NaN

/**
 * The surface read once into columns: where each column's ground and water faces stand, and its colour. A top face
 * stands one above the block's Y, so ground and water sit where a player stands on them. Where nothing is known, or
 * where the columns are {@code holes} left to finer ground, the column has no ground and no water.
 */
export class ColumnField {
  readonly columns: number
  readonly rows: number
  readonly originX: number
  readonly originZ: number
  readonly blocksPerColumn: number
  readonly groundTop: Float32Array
  readonly waterTop: Float32Array
  readonly color: Int32Array
  /** What each column's block looks like, or null to show its colour. */
  readonly looks: (BlockLook | null)[]
  /** The atlas of the looks, or null where no column has one. */
  readonly pictures: TerrainPictures | null

  constructor(surface: TerrainSurface, holes: readonly ColumnArea[] = []) {
    this.columns = surface.columns
    this.rows = surface.rows
    this.originX = surface.originX
    this.originZ = surface.originZ
    this.blocksPerColumn = surface.blocksPerColumn
    const count = this.columns * this.rows
    this.groundTop = new Float32Array(count).fill(NO_VALUE)
    this.waterTop = new Float32Array(count).fill(NO_VALUE)
    this.color = new Int32Array(count)
    this.looks = new Array<BlockLook | null>(count).fill(null)
    const hasPictures = surface.pictures !== undefined
    let anyLook = false
    const inHole = this.holeMask(holes)
    for (let row = 0; row < this.rows; row++) {
      for (let column = 0; column < this.columns; column++) {
        const index = this.indexOf(column, row)
        const ground = inHole[index] === 1 ? null : surface.groundAt(column, row)
        if (ground === null) {
          continue
        }
        const water = surface.waterAt(column, row)
        this.groundTop[index] = ground + 1
        this.waterTop[index] = water === null ? NO_VALUE : water + 1
        this.color[index] = surface.colorAt(column, row)
        const look = hasPictures ? (surface.lookAt?.(column, row) ?? null) : null
        this.looks[index] = look
        anyLook ||= look !== null
      }
    }
    this.pictures = anyLook ? (surface.pictures ?? null) : null
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

  /** Whether any column has ground. */
  hasAnyGround(): boolean {
    return this.groundTop.some((top) => !Number.isNaN(top))
  }

  /** The top face of the water over the column, or null where it is dry, holds none or lies outside the field. */
  waterTopAt(column: number, row: number): number | null {
    if (!this.contains(column, row)) {
      return null
    }
    const top = this.waterTop[this.indexOf(column, row)]
    return Number.isNaN(top) ? null : top
  }

  /** The face the column shows to the sky: its water where it has some, else its ground; null where nothing is known. */
  surfaceTop(column: number, row: number): number | null {
    if (!this.hasGround(column, row)) {
      return null
    }
    const index = this.indexOf(column, row)
    return Number.isNaN(this.waterTop[index]) ? this.groundTop[index] : this.waterTop[index]
  }

  /** The face shown to the sky over the world block, or null where the field has none. */
  surfaceTopOver(worldX: number, worldZ: number): number | null {
    return this.surfaceTop(this.columnOf(worldX), this.rowOf(worldZ))
  }

  /** The top face of the ground under any water over the world block, or null where the field has none. */
  groundTopOver(worldX: number, worldZ: number): number | null {
    const column = this.columnOf(worldX)
    const row = this.rowOf(worldZ)
    return this.hasGround(column, row) ? this.groundTop[this.indexOf(column, row)] : null
  }

  private columnOf(worldX: number): number {
    return Math.floor((worldX - this.originX) / this.blocksPerColumn)
  }

  private rowOf(worldZ: number): number {
    return Math.floor((worldZ - this.originZ) / this.blocksPerColumn)
  }

  private holeMask(holes: readonly ColumnArea[]): Uint8Array {
    const mask = new Uint8Array(this.columns * this.rows)
    for (const hole of holes) {
      for (let row = hole.fromRow; row < hole.toRow; row++) {
        mask.fill(1, this.indexOf(hole.fromColumn, row), this.indexOf(hole.toColumn - 1, row) + 1)
      }
    }
    return mask
  }
}
