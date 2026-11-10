import type { NoiseSource, NoiseTile } from '../bridge/types'
import type { TerrainSurface } from '../scene/terrainMesh'
import type { NoiseScale } from './noiseScale'

/** A square of noise tiles at one block a value, row by row from the north west, and the block its first value is taken at. */
export interface NoiseArea {
  tiles: NoiseTile[][]
  originX: number
  originZ: number
}

// The fluid of noise measured in blocks lies at its zero, as the world adds the overlay above its fluid height.
const FLUID_BLOCKS = 0

/**
 * Noise as terrain (v3e's 3D noise): noise measured in blocks stands at its own height with water at the fluid;
 * other noise rises from its scale's minimum to {@code reliefBlocks} at its maximum, with no water.
 */
export class NoiseTerrain implements TerrainSurface {
  readonly columns: number
  readonly rows: number
  readonly originX: number
  readonly originZ: number
  private readonly tileSize: number

  constructor(
    private readonly area: NoiseArea,
    private readonly source: NoiseSource,
    private readonly scale: NoiseScale,
    private readonly reliefBlocks: number
  ) {
    this.tileSize = area.tiles[0][0].size
    this.originX = area.originX
    this.originZ = area.originZ
    this.rows = area.tiles.length * this.tileSize
    this.columns = area.tiles[0].length * this.tileSize
  }

  groundAt(column: number, row: number): number | null {
    const value = this.valueAt(column, row)
    return Number.isFinite(value) ? Math.floor(this.heightOf(value)) : null
  }

  waterAt(column: number, row: number): number | null {
    if (this.source.blockRange === null) {
      return null
    }
    const ground = this.groundAt(column, row)
    return ground !== null && ground < FLUID_BLOCKS ? FLUID_BLOCKS : null
  }

  colorAt(column: number, row: number): number {
    return this.scale.colorOf(this.valueAt(column, row))
  }

  private heightOf(value: number): number {
    if (this.source.blockRange !== null) {
      return value
    }
    const span = this.scale.maximum - this.scale.minimum
    return ((value - this.scale.minimum) / span) * this.reliefBlocks
  }

  private valueAt(column: number, row: number): number {
    const tile = this.area.tiles[Math.floor(row / this.tileSize)][Math.floor(column / this.tileSize)]
    return tile.values[(row % this.tileSize) * tile.size + (column % this.tileSize)]
  }
}
