import type { BridgeClient } from '../bridge/BridgeClient'
import { NO_PATTERN_BLOCK, type PatternTile } from '../bridge/types'
import { TiledView, type TiledFrame } from '../map/TiledView'
import type { TileSource } from '../map/TileStore'
import type { TileAddress } from '../map/viewport'
import type { BlockColors } from '../scene/blockColors'

/** Which entry, at which depth, the tiles lay out. */
export interface PatternLayer {
  viewId: string
  key: string
  depth: number
  /** Bumped when the pack is hotloaded, so older tiles are not used again. */
  revision: number
}

/** A pattern tile with what it needs to be drawn: each block's colour, and its texture once a block spans pixels. */
export interface PatternPicture {
  pattern: PatternTile
  colors: number[]
  /** Per block, its top texture; empty while a block is a pixel or smaller. */
  textures: (ImageData | null)[]
  /** How many pixels across a column is drawn; 1 when it is a pixel or smaller. */
  pixelsPerColumn: number
}

/** Patterns have one look; the style only satisfies the tile store. */
export type PatternStyle = 'blocks'
export type PatternView = TiledView<PatternPicture, PatternLayer, PatternStyle>

/** What is under the cursor: a block, nothing placed there, or not known before its tile arrived. */
export type PatternSpot = { kind: 'block'; block: string } | { kind: 'none' } | { kind: 'unknown' }

export interface PatternStatus extends TiledFrame {
  spot: PatternSpot
}

/** The blocks a tile placed with their colours, and whether it left some columns without a block. */
export interface PatternBlocks {
  colors: Map<string, number>
  someNone: boolean
}

export interface PatternListener {
  onStatus(status: PatternStatus): void
  /** What a tile of the layer showed, for the legend. */
  onBlocks(layer: PatternLayer, blocks: PatternBlocks): void
  onError(message: string): void
}

export const PATTERN_TILE_PIXELS = 128
const DEFAULT_PATTERN_BLOCKS_PER_PIXEL = 1
// A block texture is 16 pixels across; drawing a block that wide shows it whole.
const FINEST_PATTERN_BLOCKS_PER_PIXEL = 1 / 16
// The bridge lays out a column at most every 64 blocks; views stop a level short so their previews stay within it.
const COARSEST_PATTERN_BLOCKS_PER_PIXEL = 32
const NO_COLOR = 0x000000
const OPAQUE = 0xff
const OPAQUE_ENOUGH_ALPHA = 128

export function createPatternView(canvas: HTMLCanvasElement, client: BridgeClient, blockColors: BlockColors, listener: PatternListener): PatternView {
  const view: PatternView = new TiledView(canvas, {
    source: patternTileSource(client, blockColors, listener),
    tileSize: PATTERN_TILE_PIXELS,
    defaultBlocksPerPixel: DEFAULT_PATTERN_BLOCKS_PER_PIXEL,
    finestRenderBlocksPerPixel: FINEST_PATTERN_BLOCKS_PER_PIXEL,
    coarsestBlocksPerPixel: COARSEST_PATTERN_BLOCKS_PER_PIXEL,
    onFrame: (frame) => listener.onStatus({ ...frame, spot: spotAt(view, frame.cursorX, frame.cursorZ) }),
    onError: (message) => listener.onError(message)
  })
  return view
}

function spotAt(view: PatternView, worldX: number, worldZ: number): PatternSpot {
  const spot = view.spotAt(worldX, worldZ)
  if (spot === null) {
    return { kind: 'unknown' }
  }
  const { pattern, pixelsPerColumn } = spot.tile
  const column = Math.floor(spot.pixelX / pixelsPerColumn)
  const row = Math.floor(spot.pixelZ / pixelsPerColumn)
  const pick = pattern.picks[row * pattern.columns + column]
  return pick === NO_PATTERN_BLOCK ? { kind: 'none' } : { kind: 'block', block: pattern.blocks[pick] }
}

function patternTileSource(client: BridgeClient, blockColors: BlockColors, listener: PatternListener): TileSource<PatternPicture, PatternLayer, PatternStyle> {
  return {
    layerKey: (layer) => `${layer.revision}/${layer.viewId}/${layer.key}/${layer.depth}`,
    fetch: async (address, layer, signal) => {
      const picture = await fetchPicture(client, blockColors, address, layer, signal)
      listener.onBlocks(layer, {
        colors: new Map(picture.pattern.blocks.map((block, index) => [block, picture.colors[index]])),
        someNone: picture.pattern.picks.includes(NO_PATTERN_BLOCK)
      })
      return picture
    },
    bytesOf: (picture) => picture.pattern.picks.byteLength + PATTERN_TILE_PIXELS * PATTERN_TILE_PIXELS * 4,
    styleKey: (style) => style,
    pixels: (picture) => patternPixels(picture)
  }
}

/**
 * A tile a pixel per column where a column is a pixel or more, and otherwise a column per block, each drawn a few
 * pixels wide in its texture.
 */
async function fetchPicture(client: BridgeClient, blockColors: BlockColors, address: TileAddress, layer: PatternLayer, signal: AbortSignal): Promise<PatternPicture> {
  const blocksPerColumn = Math.max(1, address.blocksPerPixel)
  const pixelsPerColumn = Math.round(blocksPerColumn / address.blocksPerPixel)
  const columns = PATTERN_TILE_PIXELS / pixelsPerColumn
  const pattern = await client.pattern({ ...layer, tileX: address.tileX, tileZ: address.tileZ, blocksPerColumn, columns }, signal)
  const colors = await Promise.all(pattern.blocks.map((block) => blockColors.colorOf(block)))
  const textures = pixelsPerColumn > 1 ? await Promise.all(pattern.blocks.map((block) => blockColors.topTexture(block))) : []
  return { pattern, colors, textures, pixelsPerColumn }
}

function patternPixels(picture: PatternPicture): ImageData {
  const pixels = new ImageData(PATTERN_TILE_PIXELS, PATTERN_TILE_PIXELS)
  const { pattern, pixelsPerColumn } = picture
  for (let pixelZ = 0; pixelZ < PATTERN_TILE_PIXELS; pixelZ++) {
    for (let pixelX = 0; pixelX < PATTERN_TILE_PIXELS; pixelX++) {
      const pick = pattern.picks[Math.floor(pixelZ / pixelsPerColumn) * pattern.columns + Math.floor(pixelX / pixelsPerColumn)]
      if (pick !== NO_PATTERN_BLOCK) {
        writePixel(pixels, (pixelZ * PATTERN_TILE_PIXELS + pixelX) * 4, picture, pick, pixelX % pixelsPerColumn, pixelZ % pixelsPerColumn)
      }
    }
  }
  return pixels
}

/**
 * The block's texture at a spot within its column, and its colour where it has no texture or the texture is see
 * through, such as between leaves, so a palette reads as solid ground.
 */
function writePixel(pixels: ImageData, offset: number, picture: PatternPicture, pick: number, insideX: number, insideZ: number): void {
  const texture = picture.textures[pick] ?? null
  if (texture !== null) {
    const textureX = Math.floor((insideX * texture.width) / picture.pixelsPerColumn)
    const textureZ = Math.floor((insideZ * texture.height) / picture.pixelsPerColumn)
    const from = (textureZ * texture.width + textureX) * 4
    if (texture.data[from + 3] >= OPAQUE_ENOUGH_ALPHA) {
      pixels.data.set(texture.data.subarray(from, from + 3), offset)
      pixels.data[offset + 3] = OPAQUE
      return
    }
  }
  const color = picture.colors[pick] ?? NO_COLOR
  pixels.data[offset] = (color >> 16) & 0xff
  pixels.data[offset + 1] = (color >> 8) & 0xff
  pixels.data[offset + 2] = color & 0xff
  pixels.data[offset + 3] = OPAQUE
}
