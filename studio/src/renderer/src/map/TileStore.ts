import { BridgeError } from '../bridge/BridgeClient'
import type { TileAddress } from './viewport'

/** What a store's tiles are, where they come from, and how one is drawn in a style. */
export interface TileSource<Tile, Layer, Style> {
  /** Tells layers apart; a layer that changes on hotload carries its revision here, so older tiles are not used again. */
  layerKey(layer: Layer): string
  fetch(address: TileAddress, layer: Layer, signal: AbortSignal): Promise<Tile>
  bytesOf(tile: Tile): number
  /** Only for sources whose tiles are drawn as pictures. */
  styleKey?(style: Style): string
  pixels?(tile: Tile, style: Style): ImageData
}

interface StoredTile<Tile> {
  tile: Tile
  address: TileAddress
  layerKey: string
  bytes: number
  lastUsed: number
  picture: HTMLCanvasElement | null
  pictureStyle: string
}

// Drawing a few hundred tiles at once only queues them on the server; a handful in flight keeps it busy.
const MAXIMUM_IN_FLIGHT = 6
const CACHE_BYTES = 256 * 1024 * 1024

/**
 * Tiles fetched from the bridge as a view needs them, kept within a memory budget, and drawn as the current style
 * asks. A tile that leaves the view before it arrives is not fetched.
 */
export class TileStore<Tile, Layer, Style> {
  private readonly tiles = new Map<string, StoredTile<Tile>>()
  private readonly inFlight = new Map<string, { controller: AbortController; layerKey: string }>()
  /** Asked for again only after a refresh, which changes every key, so a broken tile is not fetched on every frame. */
  private readonly failed = new Set<string>()
  private wanted: { key: string; address: TileAddress; layer: Layer }[] = []
  private storedBytes = 0
  private useClock = 0
  private changes = 0

  constructor(
    private readonly source: TileSource<Tile, Layer, Style>,
    private readonly onChange: () => void,
    private readonly onError: (message: string) => void
  ) {}

  key(address: TileAddress, layer: Layer): string {
    return `${this.source.layerKey(layer)}/${address.blocksPerPixel}/${address.tileX}/${address.tileZ}`
  }

  /** The tile if it has arrived, marked as just used. */
  get(address: TileAddress, layer: Layer): Tile | null {
    const stored = this.tiles.get(this.key(address, layer))
    if (stored === undefined) {
      return null
    }
    stored.lastUsed = ++this.useClock
    return stored.tile
  }

  /** Whether the tile has arrived; unlike {@link get}, asking does not count as using it. */
  has(address: TileAddress, layer: Layer): boolean {
    return this.tiles.has(this.key(address, layer))
  }

  /** Where the layer's tiles that have arrived lie; asking does not count as using them. */
  addressesOf(layer: Layer): TileAddress[] {
    const layerKey = this.source.layerKey(layer)
    return [...this.tiles.values()].filter((stored) => stored.layerKey === layerKey).map((stored) => stored.address)
  }

  /** Changes whenever a tile arrives or is let go, so what is worked out from the stored tiles is worked out again. */
  get version(): number {
    return this.changes
  }

  /** The tile drawn in {@code style}; restyled only when the style changed since it was last drawn. */
  picture(address: TileAddress, layer: Layer, style: Style): HTMLCanvasElement | null {
    const stored = this.tiles.get(this.key(address, layer))
    if (stored === undefined || this.source.styleKey === undefined || this.source.pixels === undefined) {
      return null
    }
    stored.lastUsed = ++this.useClock
    const wantedStyle = this.source.styleKey(style)
    if (stored.picture === null || stored.pictureStyle !== wantedStyle) {
      stored.picture = stored.picture ?? document.createElement('canvas')
      paint(stored.picture, this.source.pixels(stored.tile, style))
      stored.pictureStyle = wantedStyle
    }
    return stored.picture
  }

  /** Fetches the tiles the view shows, in the order given, and gives up on the ones it no longer shows. */
  want(addresses: TileAddress[], layer: Layer): void {
    this.wantEach(addresses.map((address) => ({ address, layer })))
  }

  /** As {@link want}, each tile of its own layer. */
  wantEach(tiles: { address: TileAddress; layer: Layer }[]): void {
    this.wanted = tiles
      .map(({ address, layer }) => ({ key: this.key(address, layer), address, layer }))
      .filter((entry) => !this.tiles.has(entry.key) && !this.failed.has(entry.key))
    const stillWanted = new Set(this.wanted.map((entry) => entry.key))
    for (const [key, request] of this.inFlight) {
      if (!stillWanted.has(key)) {
        request.controller.abort()
        this.inFlight.delete(key)
      }
    }
    this.startRequests()
  }

  /** How many of the wanted tiles are still on their way. */
  pending(): number {
    return this.wanted.length
  }

  clear(): void {
    for (const request of this.inFlight.values()) {
      request.controller.abort()
    }
    this.inFlight.clear()
    this.failed.clear()
    this.tiles.clear()
    this.wanted = []
    this.storedBytes = 0
    this.changes++
  }

  // Each layer has its own requests in flight, so tiles of a slow layer, which take a minute, never hold up a quick one's.
  private startRequests(): void {
    const inFlightByLayer = new Map<string, number>()
    for (const { layerKey } of this.inFlight.values()) {
      inFlightByLayer.set(layerKey, (inFlightByLayer.get(layerKey) ?? 0) + 1)
    }
    for (const entry of this.wanted) {
      const layerKey = this.source.layerKey(entry.layer)
      const inFlight = inFlightByLayer.get(layerKey) ?? 0
      if (this.inFlight.has(entry.key) || inFlight >= MAXIMUM_IN_FLIGHT) {
        continue
      }
      inFlightByLayer.set(layerKey, inFlight + 1)
      this.fetchTile(entry.key, layerKey, entry.address, entry.layer)
    }
  }

  private fetchTile(key: string, layerKey: string, address: TileAddress, layer: Layer): void {
    const controller = new AbortController()
    this.inFlight.set(key, { controller, layerKey })
    this.source
      .fetch(address, layer, controller.signal)
      .then((tile) => this.store(key, address, layer, tile))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          this.failed.add(key)
          this.onError(error instanceof BridgeError ? error.message : 'The server did not answer')
        }
      })
      .finally(() => {
        if (this.inFlight.get(key)?.controller === controller) {
          this.inFlight.delete(key)
        }
        this.wanted = this.wanted.filter((entry) => entry.key !== key)
        this.startRequests()
        this.onChange()
      })
  }

  private store(key: string, address: TileAddress, layer: Layer, tile: Tile): void {
    const bytes = this.source.bytesOf(tile)
    this.tiles.set(key, { tile, address, layerKey: this.source.layerKey(layer), bytes, lastUsed: ++this.useClock, picture: null, pictureStyle: '' })
    this.storedBytes += bytes
    this.changes++
    this.evictOldest()
  }

  private evictOldest(): void {
    if (this.storedBytes <= CACHE_BYTES) {
      return
    }
    const byAge = [...this.tiles.entries()].sort((first, second) => first[1].lastUsed - second[1].lastUsed)
    for (const [key, stored] of byAge) {
      if (this.storedBytes <= CACHE_BYTES * 0.8) {
        return
      }
      this.tiles.delete(key)
      this.storedBytes -= stored.bytes
      this.changes++
    }
  }
}

function paint(canvas: HTMLCanvasElement, pixels: ImageData): void {
  canvas.width = pixels.width
  canvas.height = pixels.height
  canvas.getContext('2d')!.putImageData(pixels, 0, 0)
}
