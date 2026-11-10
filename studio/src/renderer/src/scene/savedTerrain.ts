import type { SavedTile } from '../bridge/types'
import { heightColor } from '../map/heightColors'
import type { TerrainSurface } from './terrainMesh'

export type SavedTerrainColoring = 'biomes' | 'blocks' | 'height'

/** A square of saved tiles at one block a pixel, row by row from the north west. */
export interface SavedTerrainArea {
  tiles: SavedTile[][]
  /** The world block of the first tile's first pixel. */
  originX: number
  originZ: number
}

// A world without water is measured from where vanilla's sea would be.
const VANILLA_SEA_LEVEL = 63
const UNKNOWN_BLOCK_COLOR = 0x7f7f7f

/**
 * The saved columns as one surface, a column to a pixel, so each is the block it was read from.
 */
export class SavedTerrain implements TerrainSurface {
  readonly columns: number
  readonly rows: number
  readonly originX: number
  readonly originZ: number
  private readonly size: number

  constructor(
    private readonly area: SavedTerrainArea,
    private readonly coloring: SavedTerrainColoring,
    private readonly seaLevel: number | null,
    private readonly blockColors: Map<string, number>
  ) {
    this.size = area.tiles[0][0].size
    this.originX = area.originX
    this.originZ = area.originZ
    this.columns = area.tiles[0].length * this.size
    this.rows = area.tiles.length * this.size
  }

  groundAt(column: number, row: number): number | null {
    const { tile, pixel } = this.pixelAt(column, row)
    const ground = tile.groundY[pixel]
    return ground === tile.noGround ? null : ground
  }

  waterAt(column: number, row: number): number | null {
    const { tile, pixel } = this.pixelAt(column, row)
    const water = tile.waterSurfaceY[pixel]
    return water === tile.noWater ? null : water
  }

  colorAt(column: number, row: number): number {
    const { tile, pixel } = this.pixelAt(column, row)
    switch (this.coloring) {
      case 'height':
        return heightColor(tile.groundY[pixel], this.seaLevel ?? VANILLA_SEA_LEVEL)
      case 'blocks':
        return this.blockColors.get(tile.blockNames[tile.blocks[pixel]]) ?? UNKNOWN_BLOCK_COLOR
      case 'biomes':
        return tile.biomeLabels[tile.biomes[pixel]].color
    }
  }

  private pixelAt(column: number, row: number): { tile: SavedTile; pixel: number } {
    const tile = this.area.tiles[Math.floor(row / this.size)][Math.floor(column / this.size)]
    return { tile, pixel: (row % this.size) * this.size + (column % this.size) }
  }
}
