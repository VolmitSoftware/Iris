import { NO_LABEL, type MapTile } from '../bridge/types'
import { heightColor } from '../map/heightColors'
import type { TerrainSurface } from './terrainMesh'

export type MapTerrainColoring = 'biomes' | 'regions' | 'height'

/** A square of map tiles with heights at one block a pixel, row by row from the north west. */
export interface MapTerrainArea {
  tiles: MapTile[][]
  /** The world block of the first tile's first pixel. */
  originX: number
  originZ: number
}

const UNLABELLED_COLOR = 0x5a5f6b

/** The area's tiles as one surface, a column to a pixel, without the ring each tile carries for its neighbours; the sea fills up to fluid height. */
export class MapTerrain implements TerrainSurface {
  readonly columns: number
  readonly rows: number
  readonly originX: number
  readonly originZ: number
  private readonly size: number

  constructor(
    private readonly area: MapTerrainArea,
    private readonly coloring: MapTerrainColoring,
    private readonly fluidHeight: number
  ) {
    this.size = area.tiles[0][0].size
    this.originX = area.originX
    this.originZ = area.originZ
    this.columns = area.tiles[0].length * this.size
    this.rows = area.tiles.length * this.size
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
    const { tile, cell } = this.cellAt(column, row)
    const index = this.coloring === 'regions' ? tile.regions[cell] : tile.subjects[cell]
    return index === NO_LABEL ? UNLABELLED_COLOR : tile.labels[index].color
  }

  private heightAt(column: number, row: number): number {
    const { tile, cell } = this.cellAt(column, row)
    return tile.heights![cell]
  }

  private cellAt(column: number, row: number): { tile: MapTile; cell: number } {
    const tile = this.area.tiles[Math.floor(row / this.size)][Math.floor(column / this.size)]
    const gridSize = this.size + 2
    return { tile, cell: ((row % this.size) + 1) * gridSize + (column % this.size) + 1 }
  }
}
