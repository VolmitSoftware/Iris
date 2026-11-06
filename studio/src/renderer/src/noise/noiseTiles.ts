import type { BridgeClient } from '../bridge/BridgeClient'
import type { NoiseTile } from '../bridge/types'
import { TiledView, type TiledFrame } from '../map/TiledView'
import type { TileSource } from '../map/TileStore'
import type { NoiseScale } from './noiseScale'

/** Which noise the tiles hold. */
export interface NoiseLayer {
  sourceId: string
  /** A 64 bit seed, as text so no digit is lost. */
  seed: string
  /** Bumped when the pack is hotloaded, so older tiles are not used again. */
  revision: number
}

export interface NoiseStyle {
  /** Tells styles apart, so a tile is recoloured only when its style changed. */
  key: string
  scale: NoiseScale
}

export type NoiseView = TiledView<NoiseTile, NoiseLayer, NoiseStyle>

export interface NoiseStatus extends TiledFrame {
  /** The noise at the cursor, or null before its tile arrived. */
  value: number | null
}

export interface NoiseListener {
  onStatus(status: NoiseStatus): void
  onError(message: string): void
}

export const NOISE_TILE_PIXELS = 128
const DEFAULT_NOISE_BLOCKS_PER_PIXEL = 1
// Noise has detail finer than a block, so it is drawn finer than the world map's one block per pixel.
const FINEST_NOISE_BLOCKS_PER_PIXEL = 1 / 16

export function createNoiseView(canvas: HTMLCanvasElement, client: BridgeClient, listener: NoiseListener): NoiseView {
  const view: NoiseView = new TiledView(canvas, {
    source: noiseTileSource(client),
    tileSize: NOISE_TILE_PIXELS,
    defaultBlocksPerPixel: DEFAULT_NOISE_BLOCKS_PER_PIXEL,
    finestRenderBlocksPerPixel: FINEST_NOISE_BLOCKS_PER_PIXEL,
    onFrame: (frame) => listener.onStatus({ ...frame, value: valueAt(view, frame.cursorX, frame.cursorZ) }),
    onError: (message) => listener.onError(message)
  })
  return view
}

function valueAt(view: NoiseView, worldX: number, worldZ: number): number | null {
  const spot = view.spotAt(worldX, worldZ)
  return spot === null ? null : spot.tile.values[spot.pixelZ * spot.tile.size + spot.pixelX]
}

function noiseTileSource(client: BridgeClient): TileSource<NoiseTile, NoiseLayer, NoiseStyle> {
  return {
    layerKey: (layer) => `${layer.revision}/${layer.sourceId}/${layer.seed}`,
    fetch: (address, layer, signal) =>
      client.noiseTile(layer.sourceId, layer.seed, address.tileX, address.tileZ, address.blocksPerPixel, NOISE_TILE_PIXELS, signal),
    bytesOf: (tile) => tile.values.byteLength + tile.size * tile.size * 4,
    styleKey: (style) => style.key,
    pixels: (tile, style) => noisePixels(tile, style.scale)
  }
}

function noisePixels(tile: NoiseTile, scale: NoiseScale): ImageData {
  const pixels = new ImageData(tile.size, tile.size)
  for (let index = 0; index < tile.values.length; index++) {
    const color = scale.colorOf(tile.values[index])
    pixels.data[index * 4] = (color >> 16) & 0xff
    pixels.data[index * 4 + 1] = (color >> 8) & 0xff
    pixels.data[index * 4 + 2] = color & 0xff
    pixels.data[index * 4 + 3] = 0xff
  }
  return pixels
}
