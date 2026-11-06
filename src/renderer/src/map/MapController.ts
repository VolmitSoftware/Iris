import type { BridgeClient, WorldRect } from '../bridge/BridgeClient'
import { gridIndex } from '../bridge/tileDecoding'
import { NO_LABEL, type MapLabel, type Marker, type PickDisplay, type WorldInfo } from '../bridge/types'
import { countShares, type PlacedTile, type Share } from './census'
import { bandIndexOf, HEIGHT_BANDS } from './heightColors'
import type { Highlight, TileStyle } from './tileStyle'
import { TileStore, type TileLayer } from './TileStore'
import {
  clampBlocksPerPixel,
  coarserTile,
  previewsFor,
  DEFAULT_BLOCKS_PER_PIXEL,
  renderBlocksPerPixel,
  screenToWorldX,
  screenToWorldZ,
  visibleTiles,
  worldToScreenX,
  worldToScreenZ,
  type TileAddress,
  type Viewport
} from './viewport'

export interface MapStatus {
  blocksPerPixel: number
  widthBlocks: number
  heightBlocks: number
  tilesReady: number
  tilesTotal: number
  cursorX: number
  cursorZ: number
}

export interface MapListener {
  onStatus(status: MapStatus): void
  onShares(shares: Share[]): void
  /** The world area on screen, reported with the shares: a few times a second while it moves, and once at rest. */
  onArea(area: WorldRect): void
  onError(message: string): void
}

/** The views whose spots a noise picks, so they can draw that noise. */
const PICK_NOISE_VIEWS = new Set(['biomes', 'regions'])

/** What the map shows at a spot, read from the tiles already drawn. */
export interface SpotSubjects {
  subject: Highlight | null
  region: Highlight | null
}

export interface MapToggles {
  shading: boolean
  regionBorders: boolean
  grid: boolean
}

// While a tile is on its way, a coarser one already drawn stands in, up to this many zoom levels out.
const COARSER_FALLBACK_LEVELS = 6
const CENSUS_INTERVAL_MILLIS = 250
const CHUNK_BLOCKS = 16
const REGION_BLOCKS = 512
const SMALLEST_GRID_GAP_PIXELS = 8
const CHUNK_LINE = 'rgba(255, 255, 255, 0.14)'
const REGION_LINE = 'rgba(255, 255, 255, 0.38)'
const PLAYER_FILL = '#3574f0'
const ENTITY_FILL = '#f75464'
const MARKER_OUTLINE = '#ffffff'
const PLAYER_RADIUS_PIXELS = 6
const ENTITY_RADIUS_PIXELS = 3

/**
 * The 2D map on a canvas: which tiles to fetch for the view, drawing them in the current style, and the grid and
 * markers on top. Kept outside React, as it redraws on every drag and wheel step.
 */
export class MapController {
  private readonly context: CanvasRenderingContext2D
  private readonly store: TileStore
  private readonly view: Viewport = { centerX: 0, centerZ: 0, blocksPerPixel: DEFAULT_BLOCKS_PER_PIXEL, width: 1, height: 1 }
  private layer: TileLayer
  private toggles: MapToggles = { shading: true, regionBorders: false, grid: false }
  private pickDisplay: PickDisplay = 'picks'
  private highlight: Highlight | null = null
  private players: Marker[] = []
  private entities: Marker[] = []
  private cursor: { x: number; y: number } | null = null
  private frameRequested = false
  private lastCensusMillis = 0
  private censusTimer: number | undefined
  private closed = false

  constructor(
    private readonly canvas: HTMLCanvasElement,
    client: BridgeClient,
    private readonly world: WorldInfo,
    view: string,
    private readonly listener: MapListener
  ) {
    this.context = canvas.getContext('2d')!
    this.layer = { view, preview: null, heights: this.needsHeights(view, this.toggles), pickNoise: false, revision: 0 }
    this.store = new TileStore(client, world.tileSize, () => this.redraw(), (message) => listener.onError(message))
  }

  close(): void {
    this.closed = true
    window.clearTimeout(this.censusTimer)
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

  /** Shows a view; the region view previews {@code preview} everywhere when it is not null. */
  setView(view: string, preview: string | null = null): void {
    this.layer = {
      ...this.layer,
      view,
      preview: view === 'regions' ? preview : null,
      heights: this.needsHeights(view, this.toggles),
      pickNoise: this.needsPickNoise(view, this.pickDisplay)
    }
    this.redraw()
  }

  setPickDisplay(display: PickDisplay): void {
    this.pickDisplay = display
    this.layer = { ...this.layer, pickNoise: this.needsPickNoise(this.layer.view, display) }
    this.redraw()
  }

  // Tiles only carry their pick noise while it is drawn, so drawing the picks alone costs nothing extra.
  private needsPickNoise(view: string, display: PickDisplay): boolean {
    return display !== 'picks' && PICK_NOISE_VIEWS.has(view)
  }

  setToggles(toggles: MapToggles): void {
    this.toggles = toggles
    this.layer = { ...this.layer, heights: this.needsHeights(this.layer.view, toggles) }
    this.redraw()
  }

  // Heights are the costly part of a tile, so they are only asked for when something draws with them.
  private needsHeights(view: string, toggles: MapToggles): boolean {
    return toggles.shading || view === 'height'
  }

  setHighlight(highlight: Highlight | null): void {
    this.highlight = highlight
    this.redraw()
  }

  setMarkers(players: Marker[], entities: Marker[]): void {
    this.players = players
    this.entities = entities
    this.redraw()
  }

  setCursor(cursor: { x: number; y: number } | null): void {
    this.cursor = cursor
    this.reportStatus()
  }

  /** Draws everything again from the server, after the pack changed. */
  refresh(): void {
    this.store.clear()
    this.layer = { ...this.layer, revision: this.layer.revision + 1 }
    this.redraw()
  }

  panBy(screenDeltaX: number, screenDeltaZ: number): void {
    this.view.centerX -= screenDeltaX * this.view.blocksPerPixel
    this.view.centerZ -= screenDeltaZ * this.view.blocksPerPixel
    this.redraw()
  }

  centerOn(worldX: number, worldZ: number): void {
    this.view.centerX = worldX
    this.view.centerZ = worldZ
    this.redraw()
  }

  /** Zooms by {@code factor} (above 1 zooms in) keeping the world spot under the screen point where it is. */
  zoomAt(factor: number, screenX = this.view.width / 2, screenZ = this.view.height / 2): void {
    const worldX = screenToWorldX(this.view, screenX)
    const worldZ = screenToWorldZ(this.view, screenZ)
    this.view.blocksPerPixel = clampBlocksPerPixel(this.view.blocksPerPixel / factor)
    this.view.centerX = worldX - (screenX - this.view.width / 2) * this.view.blocksPerPixel
    this.view.centerZ = worldZ - (screenZ - this.view.height / 2) * this.view.blocksPerPixel
    this.redraw()
  }

  resetZoom(): void {
    this.view.blocksPerPixel = DEFAULT_BLOCKS_PER_PIXEL
    this.redraw()
  }

  worldAt(screenX: number, screenZ: number): { x: number; z: number } {
    return { x: screenToWorldX(this.view, screenX), z: screenToWorldZ(this.view, screenZ) }
  }

  /** The biome or region and the region at a screen point, from the tile drawn there; nulls before it arrived. */
  subjectsAt(screenX: number, screenZ: number): SpotSubjects {
    const worldX = screenToWorldX(this.view, screenX)
    const worldZ = screenToWorldZ(this.view, screenZ)
    const scale = this.renderScale()
    const span = this.world.tileSize * scale
    const address = { tileX: Math.floor(worldX / span), tileZ: Math.floor(worldZ / span), blocksPerPixel: scale }
    const tile = this.store.get(address, this.layer)
    if (tile === null) {
      return { subject: null, region: null }
    }
    const pixelX = Math.min(tile.size - 1, Math.floor((worldX - address.tileX * span) / scale))
    const pixelZ = Math.min(tile.size - 1, Math.floor((worldZ - address.tileZ * span) / scale))
    const cell = gridIndex(tile, pixelX, pixelZ)
    const region = labelAt(tile.labels, tile.regions[cell])
    if (this.isHeightView() && tile.heights !== null) {
      const band = HEIGHT_BANDS[bandIndexOf(tile.heights[cell], this.world.fluidHeight)]
      return { subject: { kind: 'HEIGHT', key: band.key, name: band.name }, region }
    }
    return { subject: labelAt(tile.labels, tile.subjects[cell]), region }
  }

  private isHeightView(): boolean {
    return this.layer.view === 'height'
  }

  private renderScale(): number {
    return renderBlocksPerPixel(this.view.blocksPerPixel, window.devicePixelRatio)
  }

  private style(): TileStyle {
    return {
      byHeight: this.isHeightView(),
      shading: this.toggles.shading,
      regionBorders: this.toggles.regionBorders,
      highlight: this.highlight,
      seaLevel: this.world.fluidHeight,
      pickDisplay: this.pickDisplay
    }
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

    const scale = this.renderScale()
    const tiles = visibleTiles(this.view, scale, this.world.tileSize)
    const style = this.style()
    context.imageSmoothingEnabled = this.view.blocksPerPixel > scale
    let ready = 0
    const blank: TileAddress[] = []
    for (const address of tiles) {
      const picture = this.store.picture(address, this.layer, style)
      if (picture !== null) {
        this.drawTile(picture, address, 0, 0, picture.width)
        ready++
      } else if (!this.drawFallback(address, style)) {
        blank.push(address)
      }
    }
    this.store.want([...previewsFor(blank), ...tiles], this.layer)
    if (this.toggles.grid) {
      this.drawGrid()
    }
    this.drawMarkers()
    this.reportStatus(ready, tiles.length)
    this.scheduleCensus(tiles)
  }

  /** Draws the part of a tile picture from (sourceX, sourceZ), {@code sourceSize} pixels wide, over {@code address}. */
  private drawTile(picture: HTMLCanvasElement, address: TileAddress, sourceX: number, sourceZ: number, sourceSize: number): void {
    const span = this.world.tileSize * address.blocksPerPixel
    const left = Math.round(worldToScreenX(this.view, address.tileX * span))
    const top = Math.round(worldToScreenZ(this.view, address.tileZ * span))
    const right = Math.round(worldToScreenX(this.view, (address.tileX + 1) * span))
    const bottom = Math.round(worldToScreenZ(this.view, (address.tileZ + 1) * span))
    this.context.drawImage(picture, sourceX, sourceZ, sourceSize, sourceSize, left, top, right - left, bottom - top)
  }

  /** Draws the part of a coarser tile that covers {@code address}; false when no coarser tile has arrived either. */
  private drawFallback(address: TileAddress, style: TileStyle): boolean {
    for (let levels = 1; levels <= COARSER_FALLBACK_LEVELS; levels++) {
      const coarser = coarserTile(address, levels)
      const picture = this.store.picture(coarser, this.layer, style)
      if (picture === null) {
        continue
      }
      const factor = 2 ** levels
      const sourceSize = picture.width / factor
      const sourceX = (address.tileX - coarser.tileX * factor) * sourceSize
      const sourceZ = (address.tileZ - coarser.tileZ * factor) * sourceSize
      this.drawTile(picture, address, sourceX, sourceZ, sourceSize)
      return true
    }
    return false
  }

  private drawGrid(): void {
    this.drawLines(CHUNK_BLOCKS, CHUNK_LINE, 1)
    this.drawLines(REGION_BLOCKS, REGION_LINE, 1.5)
  }

  private drawLines(gapBlocks: number, color: string, width: number): void {
    if (gapBlocks / this.view.blocksPerPixel < SMALLEST_GRID_GAP_PIXELS) {
      return
    }
    const context = this.context
    context.strokeStyle = color
    context.lineWidth = width
    context.beginPath()
    const firstX = Math.ceil(screenToWorldX(this.view, 0) / gapBlocks) * gapBlocks
    for (let worldX = firstX; worldX <= screenToWorldX(this.view, this.view.width); worldX += gapBlocks) {
      const screenX = Math.round(worldToScreenX(this.view, worldX)) + 0.5
      context.moveTo(screenX, 0)
      context.lineTo(screenX, this.view.height)
    }
    const firstZ = Math.ceil(screenToWorldZ(this.view, 0) / gapBlocks) * gapBlocks
    for (let worldZ = firstZ; worldZ <= screenToWorldZ(this.view, this.view.height); worldZ += gapBlocks) {
      const screenZ = Math.round(worldToScreenZ(this.view, worldZ)) + 0.5
      context.moveTo(0, screenZ)
      context.lineTo(this.view.width, screenZ)
    }
    context.stroke()
  }

  private drawMarkers(): void {
    for (const entity of this.entities) {
      this.drawMarker(entity, ENTITY_FILL, ENTITY_RADIUS_PIXELS, false)
    }
    for (const player of this.players) {
      this.drawMarker(player, PLAYER_FILL, PLAYER_RADIUS_PIXELS, true)
    }
  }

  private drawMarker(marker: Marker, fill: string, radius: number, named: boolean): void {
    const context = this.context
    const x = worldToScreenX(this.view, marker.worldX)
    const z = worldToScreenZ(this.view, marker.worldZ)
    context.beginPath()
    context.arc(x, z, radius, 0, Math.PI * 2)
    context.fillStyle = fill
    context.fill()
    context.lineWidth = 2
    context.strokeStyle = MARKER_OUTLINE
    context.stroke()
    if (!named) {
      return
    }
    context.font = '600 12px Inter, "Segoe UI", system-ui, sans-serif'
    context.textAlign = 'center'
    context.lineWidth = 3
    context.strokeStyle = 'rgba(0, 0, 0, 0.7)'
    context.strokeText(marker.label, x, z - radius - 6)
    context.fillStyle = MARKER_OUTLINE
    context.fillText(marker.label, x, z - radius - 6)
  }

  private lastReady = 0
  private lastTotal = 0

  private reportStatus(ready = this.lastReady, total = this.lastTotal): void {
    this.lastReady = ready
    this.lastTotal = total
    const cursor = this.cursor === null ? { x: this.view.width / 2, y: this.view.height / 2 } : this.cursor
    this.listener.onStatus({
      blocksPerPixel: this.view.blocksPerPixel,
      widthBlocks: Math.round(this.view.width * this.view.blocksPerPixel),
      heightBlocks: Math.round(this.view.height * this.view.blocksPerPixel),
      tilesReady: ready,
      tilesTotal: total,
      cursorX: Math.floor(screenToWorldX(this.view, cursor.x)),
      cursorZ: Math.floor(screenToWorldZ(this.view, cursor.y))
    })
  }

  // Counting what is on screen is cheap but not free, so it runs at most a few times a second and once more at rest.
  private scheduleCensus(tiles: TileAddress[]): void {
    window.clearTimeout(this.censusTimer)
    const wait = Math.max(0, this.lastCensusMillis + CENSUS_INTERVAL_MILLIS - performance.now())
    this.censusTimer = window.setTimeout(() => {
      this.lastCensusMillis = performance.now()
      this.listener.onShares(this.census(tiles))
      this.listener.onArea(this.screenArea())
    }, wait)
  }

  private screenArea(): WorldRect {
    return {
      minX: screenToWorldX(this.view, 0),
      minZ: screenToWorldZ(this.view, 0),
      maxX: screenToWorldX(this.view, this.view.width),
      maxZ: screenToWorldZ(this.view, this.view.height)
    }
  }

  private census(tiles: TileAddress[]): Share[] {
    const placed: PlacedTile[] = []
    for (const address of tiles) {
      const tile = this.store.get(address, this.layer)
      if (tile !== null) {
        const span = this.world.tileSize * address.blocksPerPixel
        placed.push({ tile, worldX: address.tileX * span, worldZ: address.tileZ * span })
      }
    }
    const screen = this.screenArea()
    const area = { minimumX: screen.minX, minimumZ: screen.minZ, maximumX: screen.maxX, maximumZ: screen.maxZ }
    return countShares(placed, area, this.isHeightView(), this.world.fluidHeight)
  }
}

function labelAt(labels: MapLabel[], index: number): Highlight | null {
  if (index === NO_LABEL) {
    return null
  }
  const label = labels[index]
  return { kind: label.kind, key: label.key, name: label.name }
}
