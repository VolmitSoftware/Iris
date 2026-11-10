import type { SavedTile } from '../bridge/types'
import { heightColor } from '../map/heightColors'
import type { TerrainSurface } from './terrainMesh'
import { blocksPerColumn, originXOf, originZOf, type TerrainTile } from './terrainTiles'

export type SavedTerrainColoring = 'biomes' | 'blocks' | 'height'

/** A saved tile and the colour of every block it holds. */
export interface ReadSavedTile {
  tile: SavedTile
  blockColors: Map<string, number>
}

// A world without water is measured from where vanilla's sea would be.
const VANILLA_SEA_LEVEL = 63
const UNKNOWN_BLOCK_COLOR = 0x7f7f7f

/** One saved tile as a surface, a column to a pixel, so each is the block it was read from. */
export class SavedTerrain implements TerrainSurface {
  readonly columns: number
  readonly rows: number
  readonly originX: number
  readonly originZ: number
  readonly blocksPerColumn: number

  constructor(
    private readonly read: ReadSavedTile,
    place: TerrainTile,
    private readonly coloring: SavedTerrainColoring,
    private readonly seaLevel: number | null
  ) {
    this.columns = read.tile.size
    this.rows = read.tile.size
    this.originX = originXOf(place)
    this.originZ = originZOf(place)
    this.blocksPerColumn = blocksPerColumn(place.level)
  }

  groundAt(column: number, row: number): number | null {
    const ground = this.read.tile.groundY[this.pixelAt(column, row)]
    return ground === this.read.tile.noGround ? null : ground
  }

  waterAt(column: number, row: number): number | null {
    const water = this.read.tile.waterSurfaceY[this.pixelAt(column, row)]
    return water === this.read.tile.noWater ? null : water
  }

  colorAt(column: number, row: number): number {
    const { tile, blockColors } = this.read
    const pixel = this.pixelAt(column, row)
    switch (this.coloring) {
      case 'height':
        return heightColor(tile.groundY[pixel], this.seaLevel ?? VANILLA_SEA_LEVEL)
      case 'blocks':
        return blockColors.get(tile.blockNames[tile.blocks[pixel]]) ?? UNKNOWN_BLOCK_COLOR
      case 'biomes':
        return tile.biomeLabels[tile.biomes[pixel]].color
    }
  }

  private pixelAt(column: number, row: number): number {
    return row * this.read.tile.size + column
  }
}
