import { BridgeError, type BridgeClient } from '../bridge/BridgeClient'
import type { MapTile } from '../bridge/types'
import { stylePixels, styleKey, type TileStyle } from './tileStyle'
import type { TileAddress } from './viewport'

/** What one tile request needs besides where the tile is. */
export interface TileLayer {
  view: string
  heights: boolean
  /** Bumped when the pack is hotloaded, so older tiles are not used again. */
  revision: number
}

interface StoredTile {
  tile: MapTile
  bytes: number
  lastUsed: number
  picture: HTMLCanvasElement | null
  pictureStyle: string
}

// Drawing a few hundred tiles at once only queues them on the server; a handful in flight keeps it busy.
const MAXIMUM_IN_FLIGHT = 6
const CACHE_BYTES = 256 * 1024 * 1024

/**
 * The map's tiles: fetched from the bridge as the view needs them, kept within a memory budget, and drawn as the
 * current style asks. A tile that leaves the view before it arrives is not fetched.
 */
export class TileStore {
  private readonly tiles = new Map<string, StoredTile>()
  private readonly inFlight = new Map<string, AbortController>()
  /** Asked for again only after a refresh, which changes every key, so a broken tile is not fetched on every frame. */
  private readonly failed = new Set<string>()
  private wanted: { key: string; address: TileAddress; layer: TileLayer }[] = []
  private storedBytes = 0
  private useClock = 0

  constructor(
    private readonly client: BridgeClient,
    private readonly tileSize: number,
    private readonly onChange: () => void,
    private readonly onError: (message: string) => void
  ) {}

  key(address: TileAddress, layer: TileLayer): string {
    return `${layer.revision}/${layer.view}/${layer.heights}/${address.blocksPerPixel}/${address.tileX}/${address.tileZ}`
  }

  /** The tile if it has arrived, marked as just used. */
  get(address: TileAddress, layer: TileLayer): MapTile | null {
    const stored = this.tiles.get(this.key(address, layer))
    if (stored === undefined) {
      return null
    }
    stored.lastUsed = ++this.useClock
    return stored.tile
  }

  /** The tile drawn in {@code style}; restyled only when the style changed since it was last drawn. */
  picture(address: TileAddress, layer: TileLayer, style: TileStyle): HTMLCanvasElement | null {
    const stored = this.tiles.get(this.key(address, layer))
    if (stored === undefined) {
      return null
    }
    stored.lastUsed = ++this.useClock
    const wantedStyle = styleKey(style)
    if (stored.picture === null || stored.pictureStyle !== wantedStyle) {
      stored.picture = stored.picture ?? document.createElement('canvas')
      paint(stored.picture, stored.tile, style)
      stored.pictureStyle = wantedStyle
    }
    return stored.picture
  }

  /** Fetches the tiles the view shows, in the order given, and gives up on the ones it no longer shows. */
  want(addresses: TileAddress[], layer: TileLayer): void {
    this.wanted = addresses
      .map((address) => ({ key: this.key(address, layer), address, layer }))
      .filter((entry) => !this.tiles.has(entry.key) && !this.failed.has(entry.key))
    const stillWanted = new Set(this.wanted.map((entry) => entry.key))
    for (const [key, controller] of this.inFlight) {
      if (!stillWanted.has(key)) {
        controller.abort()
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
    for (const controller of this.inFlight.values()) {
      controller.abort()
    }
    this.inFlight.clear()
    this.failed.clear()
    this.tiles.clear()
    this.wanted = []
    this.storedBytes = 0
  }

  private startRequests(): void {
    for (const entry of this.wanted) {
      if (this.inFlight.size >= MAXIMUM_IN_FLIGHT) {
        return
      }
      if (!this.inFlight.has(entry.key)) {
        this.fetchTile(entry.key, entry.address, entry.layer)
      }
    }
  }

  private fetchTile(key: string, address: TileAddress, layer: TileLayer): void {
    const controller = new AbortController()
    this.inFlight.set(key, controller)
    const request = { view: layer.view, ...address, size: this.tileSize, heights: layer.heights }
    this.client
      .tile(request, controller.signal)
      .then((tile) => this.store(key, tile))
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          this.failed.add(key)
          this.onError(error instanceof BridgeError ? error.message : 'The server did not answer')
        }
      })
      .finally(() => {
        if (this.inFlight.get(key) === controller) {
          this.inFlight.delete(key)
        }
        this.wanted = this.wanted.filter((entry) => entry.key !== key)
        this.startRequests()
        this.onChange()
      })
  }

  private store(key: string, tile: MapTile): void {
    const bytes = tile.subjects.byteLength * 2 + (tile.heights?.byteLength ?? 0) + tile.size * tile.size * 4
    this.tiles.set(key, { tile, bytes, lastUsed: ++this.useClock, picture: null, pictureStyle: '' })
    this.storedBytes += bytes
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
    }
  }
}

function paint(canvas: HTMLCanvasElement, tile: MapTile, style: TileStyle): void {
  canvas.width = tile.size
  canvas.height = tile.size
  canvas.getContext('2d')!.putImageData(new ImageData(stylePixels(tile, style), tile.size, tile.size), 0, 0)
}
