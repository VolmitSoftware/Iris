import { drawVisibleTiles } from './tileDrawing'
import { TileStore, type TileSource } from './TileStore'
import {
  MAXIMUM_BLOCKS_PER_PIXEL,
  MINIMUM_VIEW_BLOCKS_PER_PIXEL,
  renderBlocksPerPixel,
  screenToWorldX,
  screenToWorldZ,
  type TileAddress,
  type Viewport
} from './viewport'

export interface TiledViewOptions<Tile, Layer, Style> {
  source: TileSource<Tile, Layer, Style>
  tileSize: number
  defaultBlocksPerPixel: number
  /** The finest scale tiles are asked for at; closer views magnify them. */
  finestRenderBlocksPerPixel: number
  /** How far the view may zoom out. */
  coarsestBlocksPerPixel?: number
  onFrame(frame: TiledFrame): void
  onError(message: string): void
}

export interface TiledFrame {
  blocksPerPixel: number
  tilesReady: number
  tilesTotal: number
  /** The world spot under the cursor, or under the middle when the cursor is away. */
  cursorX: number
  cursorZ: number
}

/** A tile and the pixel of it at a world spot. */
export interface TileSpot<Tile> {
  tile: Tile
  pixelX: number
  pixelZ: number
}

/** Where a view looks and the scale its tiles are drawn at. */
export interface TiledFraming {
  centerX: number
  centerZ: number
  renderScale: number
}

/**
 * A pan and zoom view of tiles on a canvas, fetched as the view moves and drawn in the current style. Kept outside
 * React, as it redraws on every drag and wheel step.
 */
export class TiledView<Tile, Layer, Style> {
  private readonly context: CanvasRenderingContext2D
  private readonly store: TileStore<Tile, Layer, Style>
  private readonly view: Viewport
  private layer: Layer | null = null
  private style: Style | null = null
  private cursor: { x: number; y: number } | null = null
  private drawn = { tilesReady: 0, tilesTotal: 0 }
  private frameRequested = false
  private closed = false

  constructor(
    private readonly canvas: HTMLCanvasElement,
    private readonly options: TiledViewOptions<Tile, Layer, Style>
  ) {
    this.context = canvas.getContext('2d')!
    this.view = { centerX: 0, centerZ: 0, blocksPerPixel: options.defaultBlocksPerPixel, width: 1, height: 1 }
    this.store = new TileStore(options.source, () => this.redraw(), options.onError)
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

  show(layer: Layer | null, style: Style): void {
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
    const coarsest = this.options.coarsestBlocksPerPixel ?? MAXIMUM_BLOCKS_PER_PIXEL
    this.view.blocksPerPixel = Math.min(coarsest, Math.max(MINIMUM_VIEW_BLOCKS_PER_PIXEL, this.view.blocksPerPixel / factor))
    this.view.centerX = worldX - (screenX - this.view.width / 2) * this.view.blocksPerPixel
    this.view.centerZ = worldZ - (screenZ - this.view.height / 2) * this.view.blocksPerPixel
    this.redraw()
  }

  resetView(): void {
    this.view.centerX = 0
    this.view.centerZ = 0
    this.view.blocksPerPixel = this.options.defaultBlocksPerPixel
    this.redraw()
  }

  setCursor(cursor: { x: number; y: number } | null): void {
    this.cursor = cursor
    this.reportFrame()
  }

  framing(): TiledFraming {
    return { centerX: this.view.centerX, centerZ: this.view.centerZ, renderScale: this.renderScale() }
  }

  /** The tile drawn at a world spot at the current scale and its pixel there; null before it arrived. */
  spotAt(worldX: number, worldZ: number): TileSpot<Tile> | null {
    if (this.layer === null) {
      return null
    }
    const scale = this.renderScale()
    const span = this.options.tileSize * scale
    const address: TileAddress = { tileX: Math.floor(worldX / span), tileZ: Math.floor(worldZ / span), blocksPerPixel: scale }
    const tile = this.store.get(address, this.layer)
    if (tile === null) {
      return null
    }
    const last = this.options.tileSize - 1
    const pixelX = Math.min(last, Math.floor((worldX - address.tileX * span) / scale))
    const pixelZ = Math.min(last, Math.floor((worldZ - address.tileZ * span) / scale))
    return { tile, pixelX, pixelZ }
  }

  private renderScale(): number {
    return renderBlocksPerPixel(this.view.blocksPerPixel, window.devicePixelRatio, this.options.finestRenderBlocksPerPixel)
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
      this.drawn = { tilesReady: 0, tilesTotal: 0 }
      this.reportFrame()
      return
    }
    const drawn = drawVisibleTiles(context, this.view, this.renderScale(), this.options.tileSize, (address) => this.store.picture(address, layer, style))
    this.store.want(drawn.wanted, layer)
    this.drawn = { tilesReady: drawn.ready, tilesTotal: drawn.tiles.length }
    this.reportFrame()
  }

  private reportFrame(): void {
    const cursor = this.cursor ?? { x: this.view.width / 2, y: this.view.height / 2 }
    this.options.onFrame({
      blocksPerPixel: this.view.blocksPerPixel,
      ...this.drawn,
      cursorX: screenToWorldX(this.view, cursor.x),
      cursorZ: screenToWorldZ(this.view, cursor.y)
    })
  }
}
