import type { BridgeClient } from '../bridge/BridgeClient'
import type { BlockSource, MapTile } from '../bridge/types'
import type { BlockArt } from '../scene/blockArt'
import { stylePixels, styleKey, type TileStyle } from './tileStyle'
import type { TileSource } from './TileStore'

/** What one map tile request needs besides where the tile is. */
export interface TileLayer {
  view: string
  /** The region the region view previews everywhere, or null for the world as it is. */
  preview: string | null
  heights: boolean
  pickNoise: boolean
  /** Where the tiles' blocks come from, or null for tiles without them. */
  blocks: BlockSource | null
  /** Whether tiles of a block a pixel with blocks also carry every block seen from the open air, to draw one by one. */
  voxels?: boolean
  /** Bumped when the pack is hotloaded, so older tiles are not used again. */
  revision: number
}

/**
 * The coarsest tiles the map asks for blocks; coarser ones come without. Every chunk under a tile is generated for its
 * blocks, so a map zoomed out over a few screens would keep the server busy for minutes.
 */
const MAP_MOST_BLOCKS_PER_PIXEL = 1
const TEXTURED_BLOCKS_PER_PIXEL = 1

function voxelBytesOf(tile: MapTile): number {
  const voxels = tile.blocks?.voxels
  return voxels === null || voxels === undefined ? 0 : voxels.x.byteLength * 3 + voxels.y.byteLength * 2
}

/** Whether the map's tiles this coarse carry blocks. */
export function hasBlocksAt(blocksPerPixel: number): boolean {
  return blocksPerPixel <= MAP_MOST_BLOCKS_PER_PIXEL
}

/**
 * The world map's tiles: the engine's streams, drawn in the map's styles, their blocks painted with {@code art}.
 *
 * @param mostBlocksPerPixel the coarsest tiles that carry blocks; at most the 8 the server generates them for
 */
export function mapTileSource(client: BridgeClient, tileSize: number, art: BlockArt, mostBlocksPerPixel = MAP_MOST_BLOCKS_PER_PIXEL): TileSource<MapTile, TileLayer, TileStyle> {
  return {
    layerKey: (layer) => [layer.revision, layer.view, layer.preview ?? '', layer.heights, layer.pickNoise, layer.blocks ?? '', layer.voxels === true].join('/'),
    fetch: async (address, layer, signal) => {
      const blocks = layer.blocks !== null && address.blocksPerPixel <= mostBlocksPerPixel ? layer.blocks : null
      const tile = await client.tile({ ...layer, ...address, size: tileSize, blocks }, signal)
      if (tile.blocks !== null) {
        await art.paint(tile.blocks, address.blocksPerPixel === TEXTURED_BLOCKS_PER_PIXEL)
      }
      return tile
    },
    bytesOf: (tile) =>
      tile.subjects.byteLength * 2 + (tile.heights?.byteLength ?? 0) + (tile.pickNoise?.byteLength ?? 0) + (tile.blocks?.ground.byteLength ?? 0) * 2 + (tile.blocks?.raisedStart.byteLength ?? 0) + (tile.blocks?.raised.byteLength ?? 0) * 2 + voxelBytesOf(tile) + tile.size * tile.size * 4,
    styleKey,
    pixels: (tile, style) => new ImageData(stylePixels(tile, style), tile.size, tile.size)
  }
}
