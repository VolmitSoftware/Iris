import type { BridgeConnection } from '../../../shared/bridge'
import { decodeScene, type BlockScene } from '../scene/sceneDecoding'
import { decodePattern } from './patternDecoding'
import { decodeSavedTile } from './savedTileDecoding'
import { decodeTile } from './tileDecoding'
import type {
  MapHover,
  MapLabel,
  MapTile,
  Marker,
  ModuleViewInfo,
  NoiseSource,
  NoiseTile,
  PatternEntry,
  PatternTile,
  PickInspection,
  SavedStart,
  SavedTile,
  SavedWorldInfo,
  SceneEntry,
  WorldInfo
} from './types'

export const STATUS_NOT_FOUND = 404
export const STATUS_UNAVAILABLE = 503

export class BridgeError extends Error {
  constructor(
    readonly status: number,
    message: string
  ) {
    super(message)
  }
}

export interface TileRequest {
  view: string
  /** The region the region view previews everywhere, or null for the world as it is. */
  preview: string | null
  tileX: number
  tileZ: number
  blocksPerPixel: number
  size: number
  heights: boolean
  pickNoise: boolean
}

/** One tile of a pattern: tile (x, z) covers columns × blocksPerColumn blocks from x × that, z × that. */
export interface PatternRequest {
  viewId: string
  key: string
  depth: number
  tileX: number
  tileZ: number
  blocksPerColumn: number
  columns: number
}

/** What a map spot or area is asked about: a view, and the region it previews. */
export interface MapLayerQuery {
  view: string
  preview: string | null
}

export interface WorldRect {
  minX: number
  minZ: number
  maxX: number
  maxZ: number
}

export interface BridgeEvents {
  onWorld(running: boolean): void
  onPlayers(players: Marker[]): void
  onClosed(): void
}

const EVENT_SEPARATOR = '\n\n'

/** One connection to a world's bridge, over HTTP on the loopback interface. */
export class BridgeClient {
  private readonly base: string
  private readonly authorization: string

  constructor(readonly connection: BridgeConnection) {
    this.base = `http://127.0.0.1:${connection.port}/v1/`
    this.authorization = `Bearer ${connection.token}`
  }

  world(): Promise<WorldInfo> {
    return this.json('world')
  }

  hover(layer: MapLayerQuery, worldX: number, worldZ: number, signal?: AbortSignal): Promise<MapHover> {
    return this.json(`hover?${layerQuery(layer, { x: String(worldX), z: String(worldZ) })}`, { signal })
  }

  /** How the noise picks what the layer shows across {@code area}. */
  picks(layer: MapLayerQuery, area: WorldRect, signal?: AbortSignal): Promise<PickInspection> {
    const corners = { minX: String(area.minX), minZ: String(area.minZ), maxX: String(area.maxX), maxZ: String(area.maxZ) }
    return this.json(`picks?${layerQuery(layer, corners)}`, { signal })
  }

  /** The regions the world picks biomes for, which the region view can preview. */
  regions(): Promise<MapLabel[]> {
    return this.json('regions')
  }

  entities(): Promise<Marker[]> {
    return this.json('entities')
  }

  async teleport(worldX: number, worldZ: number): Promise<void> {
    await this.json(`teleport?x=${worldX}&z=${worldZ}`, { method: 'POST' })
  }

  /** Opens the file of the biome or region at a spot on the server's machine; the server's answer, if any. */
  async openInEditor(worldX: number, worldZ: number, view: string): Promise<string | null> {
    const answer = await this.json<{ message: string | null }>(`open?x=${worldX}&z=${worldZ}&view=${view}`, { method: 'POST' })
    return answer.message
  }

  async tile(request: TileRequest, signal: AbortSignal): Promise<MapTile> {
    const query = layerQuery(request, {
      x: String(request.tileX),
      z: String(request.tileZ),
      scale: String(request.blocksPerPixel),
      size: String(request.size),
      heights: request.heights ? '1' : '0',
      noise: request.pickNoise ? '1' : '0'
    })
    const response = await this.fetch(`tile?${query}`, { signal })
    return decodeTile(await response.arrayBuffer())
  }

  /** The scene views the pack's modules show, such as its objects. */
  sceneViews(): Promise<ModuleViewInfo[]> {
    return this.json('scenes')
  }

  sceneEntries(viewId: string): Promise<SceneEntry[]> {
    return this.json(`scene-entries?${new URLSearchParams({ view: viewId })}`)
  }

  async scene(viewId: string, key: string, variant: number, signal?: AbortSignal): Promise<BlockScene> {
    const response = await this.fetch(`scene?${new URLSearchParams({ view: viewId, key, variant: String(variant) })}`, { signal })
    return decodeScene(await response.arrayBuffer())
  }

  /** The pattern views the pack's modules show, such as its palettes. */
  patternViews(): Promise<ModuleViewInfo[]> {
    return this.json('patterns')
  }

  patternEntries(viewId: string): Promise<PatternEntry[]> {
    return this.json(`pattern-entries?${new URLSearchParams({ view: viewId })}`)
  }

  /** What an entry places at {@code depth} below the top across one tile, a column every {@code blocksPerColumn} blocks. */
  async pattern(request: PatternRequest, signal: AbortSignal): Promise<PatternTile> {
    const query = new URLSearchParams({
      view: request.viewId,
      key: request.key,
      depth: String(request.depth),
      x: String(request.tileX),
      z: String(request.tileZ),
      scale: String(request.blocksPerColumn),
      size: String(request.columns)
    })
    const response = await this.fetch(`pattern?${query}`, { signal })
    return decodePattern(await response.arrayBuffer())
  }

  /** The worlds saved on the server's disk. */
  savedWorlds(): Promise<SavedWorldInfo[]> {
    return this.json('saved-worlds')
  }

  /** Where a saved world's view opens; reads the region nearest the origin whole, so it can take a second or two. */
  savedStart(worldId: string, signal?: AbortSignal): Promise<SavedStart> {
    return this.json(`saved-start?${new URLSearchParams({ world: worldId })}`, { signal })
  }

  async savedTile(worldId: string, tileX: number, tileZ: number, blocksPerPixel: number, size: number, signal: AbortSignal): Promise<SavedTile> {
    const query = new URLSearchParams({ world: worldId, x: String(tileX), z: String(tileZ), scale: String(blocksPerPixel), size: String(size) })
    const response = await this.fetch(`saved-tile?${query}`, { signal })
    return decodeSavedTile(await response.arrayBuffer())
  }

  /** The pack's generators and expressions, the dimension's overlay noise, and the built-in noise styles. */
  noiseSources(): Promise<NoiseSource[]> {
    return this.json('noise-sources')
  }

  async noiseTile(sourceId: string, seed: string, tileX: number, tileZ: number, blocksPerPixel: number, size: number, signal: AbortSignal): Promise<NoiseTile> {
    const query = new URLSearchParams({ source: sourceId, seed, x: String(tileX), z: String(tileZ), scale: String(blocksPerPixel), size: String(size) })
    const response = await this.fetch(`noise-tile?${query}`, { signal })
    return { size, values: new Float32Array(await response.arrayBuffer()) }
  }

  /** A file of the Minecraft client's assets, such as {@code minecraft/blockstates/stone.json}; null when there is none. */
  async asset(path: string): Promise<Response | null> {
    try {
      return await this.fetch(`assets/${path}`, {})
    } catch (error) {
      if (error instanceof BridgeError && error.status === STATUS_NOT_FOUND) {
        return null
      }
      throw error
    }
  }

  /** Streams the bridge's events until {@code signal} aborts or the bridge goes away. */
  async listen(events: BridgeEvents, signal: AbortSignal): Promise<void> {
    try {
      const response = await this.fetch('events', { signal })
      const reader = response.body!.pipeThrough(new TextDecoderStream()).getReader()
      let pending = ''
      for (;;) {
        const { value, done } = await reader.read()
        if (done) {
          break
        }
        pending += value
        let end: number
        while ((end = pending.indexOf(EVENT_SEPARATOR)) >= 0) {
          dispatch(pending.slice(0, end), events)
          pending = pending.slice(end + EVENT_SEPARATOR.length)
        }
      }
    } catch (error) {
      if (signal.aborted) {
        return
      }
      console.warn('Studio events stopped', error)
    }
    if (!signal.aborted) {
      events.onClosed()
    }
  }

  private async json<T>(endpoint: string, init: RequestInit = {}): Promise<T> {
    const response = await this.fetch(endpoint, init)
    return (await response.json()) as T
  }

  private async fetch(endpoint: string, init: RequestInit): Promise<Response> {
    const response = await fetch(this.base + endpoint, {
      ...init,
      headers: { Authorization: this.authorization }
    })
    if (!response.ok) {
      const body = await response.json().catch(() => ({ error: response.statusText }))
      throw new BridgeError(response.status, String(body.error ?? response.statusText))
    }
    return response
  }
}

function layerQuery(layer: MapLayerQuery, rest: Record<string, string>): URLSearchParams {
  const query = new URLSearchParams({ view: layer.view, ...rest })
  if (layer.preview !== null) {
    query.set('preview', layer.preview)
  }
  return query
}

function dispatch(block: string, events: BridgeEvents): void {
  let name = 'message'
  let data = ''
  for (const line of block.split('\n')) {
    if (line.startsWith('event: ')) {
      name = line.slice('event: '.length)
    } else if (line.startsWith('data: ')) {
      data += line.slice('data: '.length)
    }
  }
  if (data === '') {
    return
  }
  if (name === 'world') {
    events.onWorld((JSON.parse(data) as { running: boolean }).running)
  } else if (name === 'players') {
    events.onPlayers(JSON.parse(data) as Marker[])
  }
}
