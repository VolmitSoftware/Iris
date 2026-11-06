import type { BridgeClient } from '../bridge/BridgeClient'
import type { MapTile } from '../bridge/types'
import { stylePixels, styleKey, type TileStyle } from './tileStyle'
import type { TileSource } from './TileStore'

/** What one map tile request needs besides where the tile is. */
export interface TileLayer {
  view: string
  /** The region the region view previews everywhere, or null for the world as it is. */
  preview: string | null
  heights: boolean
  pickNoise: boolean
  /** Bumped when the pack is hotloaded, so older tiles are not used again. */
  revision: number
}

/** The world map's tiles: the engine's streams, drawn in the map's styles. */
export function mapTileSource(client: BridgeClient, tileSize: number): TileSource<MapTile, TileLayer, TileStyle> {
  return {
    layerKey: (layer) => [layer.revision, layer.view, layer.preview ?? '', layer.heights, layer.pickNoise].join('/'),
    fetch: (address, layer, signal) => client.tile({ ...layer, ...address, size: tileSize }, signal),
    bytesOf: (tile) => tile.subjects.byteLength * 2 + (tile.heights?.byteLength ?? 0) + (tile.pickNoise?.byteLength ?? 0) + tile.size * tile.size * 4,
    styleKey,
    pixels: (tile, style) => new ImageData(stylePixels(tile, style), tile.size, tile.size)
  }
}
