import { NO_LABEL, type MapTile } from '../bridge/types'
import { heightColor } from '../map/heightColors'
import type { TerrainSurface } from './terrainMesh'

export type MapTerrainColoring = 'biomes' | 'regions' | 'height'

/** A square of map tiles with heights, row by row from the north west. */
export interface MapTerrainArea {
  tiles: MapTile[][]
  /** The world spot of the first tile's first pixel. */
  originX: number
  originZ: number
}

const UNLABELLED_COLOR = 0x5a5f6b

/** The area's tiles as one surface, without the ring each tile carries for its neighbours; the sea fills up to fluid height. */
export class MapTerrain implements TerrainSurface {
  readonly columns: number
  readonly rows: number
  private readonly size: number
  private readonly blocksPerPixel: number

  constructor(
    private readonly area: MapTerrainArea,
    private readonly coloring: MapTerrainColoring,
    private readonly fluidHeight: number
  ) {
    const first = area.tiles[0][0]
    this.size = first.size
    this.blocksPerPixel = first.blocksPerPixel
    this.columns = area.tiles[0].length * this.size
    this.rows = area.tiles.length * this.size
  }

  worldX(column: number): number {
    return this.area.originX + column * this.blocksPerPixel
  }

  worldZ(row: number): number {
    return this.area.originZ + row * this.blocksPerPixel
  }

  groundAt(column: number, row: number): number {
    const { tile, cell } = this.cellAt(column, row)
    return tile.heights![cell]
  }

  waterAt(column: number, row: number): number | null {
    return this.groundAt(column, row) < this.fluidHeight ? this.fluidHeight : null
  }

  colorAt(column: number, row: number): number {
    if (this.coloring === 'height') {
      return heightColor(this.groundAt(column, row), this.fluidHeight)
    }
    const { tile, cell } = this.cellAt(column, row)
    const index = this.coloring === 'regions' ? tile.regions[cell] : tile.subjects[cell]
    return index === NO_LABEL ? UNLABELLED_COLOR : tile.labels[index].color
  }

  private cellAt(column: number, row: number): { tile: MapTile; cell: number } {
    const tile = this.area.tiles[Math.floor(row / this.size)][Math.floor(column / this.size)]
    const gridSize = this.size + 2
    return { tile, cell: ((row % this.size) + 1) * gridSize + (column % this.size) + 1 }
  }
}
