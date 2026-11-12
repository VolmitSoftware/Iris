import { NO_BLOCK, NO_LABEL, type MapTile } from '../bridge/types'
import { heightColor } from '../map/heightColors'
import type { BlockLook } from './blockLooks'
import type { CoverLook } from './coverLooks'
import type { TerrainPictures } from './terrainAtlas'
import type { RaisedBlock, TerrainSurface } from './terrainMesh'
import type { SurfaceRuns } from './runFaces'
import type { SurfaceVoxels } from './voxelMesh'
import { blocksPerColumn, originXOf, originZOf, type TerrainTile } from './terrainTiles'

export type MapTerrainColoring = 'biomes' | 'regions' | 'height' | 'blocks'

const UNLABELLED_COLOR = 0x5a5f6b
const WATER_NAME = 'minecraft:water'

function isWater(block: string): boolean {
  return block === WATER_NAME || block.startsWith(`${WATER_NAME}[`)
}

/**
 * One map tile with heights as a surface, a column to a pixel, without the ring the tile carries for its neighbours; the
 * sea fills up to fluid height. Coloured by blocks, a tile that carries them shows their colours, and their textures
 * where it has their looks; one too coarse to carry them keeps its biomes' colours. Blocks raised over the ground stand
 * as blocks on columns a block wide, and are the top of wider ones.
 */
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
    private readonly fluidHeight: number,
    readonly pictures?: TerrainPictures
  ) {
    this.columns = tile.size
    this.rows = tile.size
    this.originX = originXOf(place)
    this.originZ = originZOf(place)
    this.blocksPerColumn = blocksPerColumn(place.level)
  }

  groundAt(column: number, row: number): number {
    const highestRaised = this.highestFoldedRaisedAt(column, row)
    return highestRaised === null ? Math.floor(this.heightAt(column, row)) : this.tile.blocks!.raisedY[highestRaised]
  }

  waterAt(column: number, row: number): number | null {
    return this.heightAt(column, row) < this.fluidHeight ? Math.floor(this.fluidHeight) : null
  }

  lookAt(column: number, row: number): BlockLook | null {
    const top = this.topBlockAt(column, row)
    return top === NO_BLOCK ? null : (this.tile.blocks!.looks[top] ?? null)
  }

  coverAt(column: number, row: number): CoverLook | null {
    if (this.groundBlockAt(column, row) === NO_BLOCK || this.highestFoldedRaisedAt(column, row) !== null) {
      return null
    }
    const cover = this.tile.blocks!.cover[this.cellAt(column, row)]
    return cover === NO_BLOCK ? null : (this.tile.blocks!.covers[cover] ?? null)
  }

  raisedAt(column: number, row: number): readonly RaisedBlock[] {
    const blocks = this.coloring === 'blocks' ? this.tile.blocks : null
    if (blocks === null || this.foldsRaisedBlocks()) {
      return []
    }
    const cell = this.cellAt(column, row)
    const raised: RaisedBlock[] = []
    for (let at = blocks.raisedStart[cell]; at < blocks.raisedStart[cell + 1]; at++) {
      const block = blocks.raised[at]
      raised.push({ y: blocks.raisedY[at], color: blocks.colors[block], look: blocks.looks[block] ?? null })
    }
    return raised
  }

  voxels(): SurfaceVoxels | null {
    const blocks = this.coloring === 'blocks' && this.blocksPerColumn === 1 ? this.tile.blocks : null
    if (blocks === null || blocks.voxels === null || blocks.models.length === 0) {
      return null
    }
    return {
      voxels: blocks.voxels,
      models: blocks.models,
      looks: blocks.looks,
      colors: blocks.colors,
      isWater: blocks.names.map(isWater)
    }
  }

  runs(): SurfaceRuns | null {
    const blocks = this.coloring === 'blocks' ? this.tile.blocks : null
    if (blocks === null || blocks.runs === null) {
      return null
    }
    return { runs: blocks.runs, cellAt: (column, row) => this.cellAt(column, row), looks: blocks.looks, colors: blocks.colors }
  }

  colorAt(column: number, row: number): number {
    if (this.coloring === 'height') {
      return heightColor(this.heightAt(column, row), this.fluidHeight)
    }
    const top = this.topBlockAt(column, row)
    if (top !== NO_BLOCK) {
      return this.tile.blocks!.colors[top]
    }
    const index = this.coloring === 'regions' ? this.tile.regions[this.cellAt(column, row)] : this.tile.subjects[this.cellAt(column, row)]
    return index === NO_LABEL ? UNLABELLED_COLOR : this.tile.labels[index].color
  }

  // A block raised on a column wider than a block would stand as a slab as wide as the column, so such columns take
  // their highest raised block as their top, as a forest seen from afar is its canopy.
  private foldsRaisedBlocks(): boolean {
    return this.blocksPerColumn > 1
  }

  /** Where the column's highest raised block lies in the tile's raised blocks, where the column takes it as its top. */
  private highestFoldedRaisedAt(column: number, row: number): number | null {
    if (!this.foldsRaisedBlocks() || this.groundBlockAt(column, row) === NO_BLOCK) {
      return null
    }
    const cell = this.cellAt(column, row)
    const { raisedStart } = this.tile.blocks!
    return raisedStart[cell] < raisedStart[cell + 1] ? raisedStart[cell] : null
  }

  private topBlockAt(column: number, row: number): number {
    const highestRaised = this.highestFoldedRaisedAt(column, row)
    return highestRaised === null ? this.groundBlockAt(column, row) : this.tile.blocks!.raised[highestRaised]
  }

  private groundBlockAt(column: number, row: number): number {
    return this.coloring === 'blocks' && this.tile.blocks !== null ? this.tile.blocks.ground[this.cellAt(column, row)] : NO_BLOCK
  }

  private heightAt(column: number, row: number): number {
    return this.tile.heights![this.cellAt(column, row)]
  }

  private cellAt(column: number, row: number): number {
    const gridSize = this.tile.size + 2
    return (row + 1) * gridSize + column + 1
  }
}
