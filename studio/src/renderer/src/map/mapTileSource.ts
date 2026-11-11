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
  /** Bumped when the pack is hotloaded, so older tiles are not used again. */
  revision: number
}

/** The coarsest tiles a block source is asked for; coarser ones come without blocks. */
const MOST_BLOCKS_PER_PIXEL: Record<BlockSource, number> = { generated: 1 }
const TEXTURED_BLOCKS_PER_PIXEL = 1

/** Whether tiles this coarse carry blocks from {@code source}. */
export function hasBlocksAt(source: BlockSource, blocksPerPixel: number): boolean {
  return blocksPerPixel <= MOST_BLOCKS_PER_PIXEL[source]
}

/** The world map's tiles: the engine's streams, drawn in the map's styles, their blocks painted with {@code art}. */
export function mapTileSource(client: BridgeClient, tileSize: number, art: BlockArt): TileSource<MapTile, TileLayer, TileStyle> {
  return {
    layerKey: (layer) => [layer.revision, layer.view, layer.preview ?? '', layer.heights, layer.pickNoise, layer.blocks ?? ''].join('/'),
    fetch: async (address, layer, signal) => {
      const blocks = layer.blocks !== null && hasBlocksAt(layer.blocks, address.blocksPerPixel) ? layer.blocks : null
      const tile = await client.tile({ ...layer, ...address, size: tileSize, blocks }, signal)
      if (tile.blocks !== null) {
        await art.paint(tile.blocks, address.blocksPerPixel === TEXTURED_BLOCKS_PER_PIXEL)
      }
      return tile
    },
    bytesOf: (tile) =>
      tile.subjects.byteLength * 2 + (tile.heights?.byteLength ?? 0) + (tile.pickNoise?.byteLength ?? 0) + (tile.blocks?.ground.byteLength ?? 0) * 2 + tile.size * tile.size * 4,
    styleKey,
    pixels: (tile, style) => new ImageData(stylePixels(tile, style), tile.size, tile.size)
  }
}
