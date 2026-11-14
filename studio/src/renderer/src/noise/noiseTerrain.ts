import type { NoiseSource, NoiseTile } from '../bridge/types'
import type { TerrainSurface } from '../scene/terrainMesh'
import { blocksPerColumn, originXOf, originZOf, type TerrainTile } from '../scene/terrainTiles'
import type { NoiseScale } from './noiseScale'

// The fluid of noise measured in blocks lies at its zero, as the world adds the overlay above its fluid height.
const FLUID_BLOCKS = 0

/**
 * One tile of noise as terrain (v3e's 3D noise): noise measured in blocks stands at its own height with water at the
 * fluid; other noise rises from its scale's minimum to {@code reliefBlocks} at its maximum, with no water.
 */
export class NoiseTerrain implements TerrainSurface {
  readonly columns: number
  readonly rows: number
  readonly originX: number
  readonly originZ: number
  readonly blocksPerColumn: number

  constructor(
    private readonly tile: NoiseTile,
    place: TerrainTile,
    private readonly source: NoiseSource,
    private readonly scale: NoiseScale,
    private readonly reliefBlocks: number
  ) {
    this.columns = tile.size
    this.rows = tile.size
    this.originX = originXOf(place)
    this.originZ = originZOf(place)
    this.blocksPerColumn = blocksPerColumn(place.level)
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
    return this.tile.values[row * this.tile.size + column]
  }
}
