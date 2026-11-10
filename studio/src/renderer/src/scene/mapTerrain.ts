import { NO_LABEL, type MapTile } from '../bridge/types'
import { heightColor } from '../map/heightColors'
import type { TerrainSurface } from './terrainMesh'
import { blocksPerColumn, originXOf, originZOf, type TerrainTile } from './terrainTiles'

export type MapTerrainColoring = 'biomes' | 'regions' | 'height'

const UNLABELLED_COLOR = 0x5a5f6b

/** One map tile with heights as a surface, a column to a pixel, without the ring the tile carries for its neighbours; the sea fills up to fluid height. */
export class MapTerrain implements TerrainSurface {
  readonly columns: number
  readonly rows: number
  readonly originX: number
  readonly originZ: number
  readonly blocksPerColumn: number

  constructor(
    private readonly tile: MapTile,
    place: TerrainTile,
    private readonly coloring: MapTerrainColoring,
    private readonly fluidHeight: number
  ) {
    this.columns = tile.size
    this.rows = tile.size
    this.originX = originXOf(place)
    this.originZ = originZOf(place)
    this.blocksPerColumn = blocksPerColumn(place.level)
  }

  groundAt(column: number, row: number): number {
    return Math.floor(this.heightAt(column, row))
  }

  waterAt(column: number, row: number): number | null {
    return this.heightAt(column, row) < this.fluidHeight ? Math.floor(this.fluidHeight) : null
  }

  colorAt(column: number, row: number): number {
    if (this.coloring === 'height') {
      return heightColor(this.heightAt(column, row), this.fluidHeight)
    }
    const index = this.coloring === 'regions' ? this.tile.regions[this.cellAt(column, row)] : this.tile.subjects[this.cellAt(column, row)]
    return index === NO_LABEL ? UNLABELLED_COLOR : this.tile.labels[index].color
  }

  private heightAt(column: number, row: number): number {
    return this.tile.heights![this.cellAt(column, row)]
  }

  private cellAt(column: number, row: number): number {
    const gridSize = this.tile.size + 2
    return (row + 1) * gridSize + column + 1
  }
}
