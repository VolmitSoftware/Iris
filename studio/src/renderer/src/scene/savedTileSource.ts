import type { BridgeClient } from '../bridge/BridgeClient'
import type { TileSource } from '../map/TileStore'
import type { BlockColors } from './blockColors'
import type { BlockLook, BlockLooks } from './blockLooks'
import { FINEST_BLOCKS_PER_COLUMN, type ReadSavedTile } from './savedTerrain'
import { TILE_COLUMNS } from './terrainTiles'

/** Which saved world the tiles are read from. */
export interface SavedLayer {
  worldId: string
  /** Bumped to read the region files again, so older tiles are not used again. */
  readings: number
}

// The block and biome names and colours a tile carries, as far as they add up.
const ESTIMATED_EXTRA_BYTES_PER_COLUMN = 4

/** A saved world's tiles, each with the colour of every block in it, and their looks where a column is one block. */
export function savedTileSource(client: BridgeClient, blockColors: BlockColors, blockLooks: BlockLooks): TileSource<ReadSavedTile, SavedLayer, never> {
  return {
    layerKey: (layer) => `${layer.worldId}/${layer.readings}`,
    fetch: async (address, layer, signal) => {
      const tile = await client.savedTile(layer.worldId, address.tileX, address.tileZ, address.blocksPerPixel, TILE_COLUMNS, signal)
      const textured = address.blocksPerPixel === FINEST_BLOCKS_PER_COLUMN
      const [colors, looks] = await Promise.all([blockColors.colorsOf(tile.blocksDrawnAs), textured ? blockLooks.looksOf(tile.blocksDrawnAs) : new Map<string, BlockLook | null>()])
      return { tile, blockColors: colors, blockLooks: looks }
    },
    bytesOf: ({ tile }) =>
      tile.groundY.byteLength + tile.waterSurfaceY.byteLength + tile.blocks.byteLength + tile.biomes.byteLength + tile.size * tile.size * ESTIMATED_EXTRA_BYTES_PER_COLUMN
  }
}
