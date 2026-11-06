import type { BridgeClient } from '../bridge/BridgeClient'
import type { NoiseTile } from '../bridge/types'
import { drawVisibleTiles } from '../map/tileDrawing'
import { TileStore, type TileSource } from '../map/TileStore'
import { clampBlocksPerPixel, renderBlocksPerPixel, screenToWorldX, screenToWorldZ, type TileAddress, type Viewport } from '../map/viewport'
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

export interface NoiseStatus {
  blocksPerPixel: number
  tilesReady: number
  tilesTotal: number
  cursorX: number
  cursorZ: number
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

/** The noise from above on a canvas, fetched in tiles as the view moves. Kept outside React, as it redraws on every drag. */
export class NoiseController {
  private readonly context: CanvasRenderingContext2D
  private readonly store: TileStore<NoiseTile, NoiseLayer, NoiseStyle>
  private readonly view: Viewport = { centerX: 0, centerZ: 0, blocksPerPixel: DEFAULT_NOISE_BLOCKS_PER_PIXEL, width: 1, height: 1 }
  private layer: NoiseLayer | null = null
  private style: NoiseStyle | null = null
  private cursor: { x: number; y: number } | null = null
  private lastReady = 0
  private lastTotal = 0
  private frameRequested = false
  private closed = false

  constructor(
    private readonly canvas: HTMLCanvasElement,
    client: BridgeClient,
    private readonly listener: NoiseListener
  ) {
    this.context = canvas.getContext('2d')!
    this.store = new TileStore(noiseTileSource(client), () => this.redraw(), (message) => listener.onError(message))
  }

  close(): void {
    this.closed = true
    this.store.clear()
  }

  resize(width: number, height: number): void {
    const ratio = window.devicePixelRatio
    this.canvas.width = Math.round(width * ratio)
    this.canvas.height = Math.round(height * ratio)
    this.view.width = width
    this.view.height = height
    this.redraw()
  }

  show(layer: NoiseLayer | null, style: NoiseStyle): void {
    this.layer = layer
    this.style = style
    this.redraw()
  }

  panBy(screenDeltaX: number, screenDeltaZ: number): void {
    this.view.centerX -= screenDeltaX * this.view.blocksPerPixel
    this.view.centerZ -= screenDeltaZ * this.view.blocksPerPixel
    this.redraw()
  }

  /** Zooms by {@code factor} (above 1 zooms in) keeping the spot under the screen point where it is. */
  zoomAt(factor: number, screenX = this.view.width / 2, screenZ = this.view.height / 2): void {
    const worldX = screenToWorldX(this.view, screenX)
    const worldZ = screenToWorldZ(this.view, screenZ)
    this.view.blocksPerPixel = clampBlocksPerPixel(this.view.blocksPerPixel / factor)
    this.view.centerX = worldX - (screenX - this.view.width / 2) * this.view.blocksPerPixel
    this.view.centerZ = worldZ - (screenZ - this.view.height / 2) * this.view.blocksPerPixel
    this.redraw()
  }

  resetView(): void {
    this.view.centerX = 0
    this.view.centerZ = 0
    this.view.blocksPerPixel = DEFAULT_NOISE_BLOCKS_PER_PIXEL
    this.redraw()
  }

  setCursor(cursor: { x: number; y: number } | null): void {
    this.cursor = cursor
    this.reportStatus()
  }

  /** Where the view looks and the scale its tiles are drawn at, for the 3D view to sample the same noise. */
  framing(): { centerX: number; centerZ: number; renderScale: number } {
    return { centerX: this.view.centerX, centerZ: this.view.centerZ, renderScale: this.renderScale() }
  }

  private renderScale(): number {
    return renderBlocksPerPixel(this.view.blocksPerPixel, window.devicePixelRatio, FINEST_NOISE_BLOCKS_PER_PIXEL)
  }

  private redraw(): void {
    if (this.frameRequested || this.closed) {
      return
    }
    this.frameRequested = true
    requestAnimationFrame(() => {
      this.frameRequested = false
      if (!this.closed) {
        this.draw()
      }
    })
  }

  private draw(): void {
    const ratio = window.devicePixelRatio
    const context = this.context
    context.setTransform(ratio, 0, 0, ratio, 0, 0)
    context.fillStyle = getComputedStyle(this.canvas).getPropertyValue('--map-background') || '#16171a'
    context.fillRect(0, 0, this.view.width, this.view.height)
    const layer = this.layer
    const style = this.style
    if (layer === null || style === null) {
      this.reportStatus(0, 0)
      return
    }
    const drawn = drawVisibleTiles(context, this.view, this.renderScale(), NOISE_TILE_PIXELS, (address) => this.store.picture(address, layer, style))
    this.store.want(drawn.wanted, layer)
    this.reportStatus(drawn.ready, drawn.tiles.length)
  }

  private valueAt(worldX: number, worldZ: number): number | null {
    if (this.layer === null) {
      return null
    }
    const scale = this.renderScale()
    const span = NOISE_TILE_PIXELS * scale
    const address: TileAddress = { tileX: Math.floor(worldX / span), tileZ: Math.floor(worldZ / span), blocksPerPixel: scale }
    const tile = this.store.get(address, this.layer)
    if (tile === null) {
      return null
    }
    const pixelX = Math.min(tile.size - 1, Math.floor((worldX - address.tileX * span) / scale))
    const pixelZ = Math.min(tile.size - 1, Math.floor((worldZ - address.tileZ * span) / scale))
    return tile.values[pixelZ * tile.size + pixelX]
  }

  private reportStatus(ready = this.lastReady, total = this.lastTotal): void {
    this.lastReady = ready
    this.lastTotal = total
    const cursor = this.cursor ?? { x: this.view.width / 2, y: this.view.height / 2 }
    const worldX = screenToWorldX(this.view, cursor.x)
    const worldZ = screenToWorldZ(this.view, cursor.y)
    this.listener.onStatus({
      blocksPerPixel: this.view.blocksPerPixel,
      tilesReady: ready,
      tilesTotal: total,
      cursorX: worldX,
      cursorZ: worldZ,
      value: this.valueAt(worldX, worldZ)
    })
  }
}
