import type { BridgeConnection } from '../../../shared/bridge'
import { decodeTile } from './tileDecoding'
import type { MapHover, MapTile, Marker, WorldInfo } from './types'

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
  tileX: number
  tileZ: number
  blocksPerPixel: number
  size: number
  heights: boolean
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

  hover(view: string, worldX: number, worldZ: number, signal?: AbortSignal): Promise<MapHover> {
    return this.json(`hover?view=${encodeURIComponent(view)}&x=${worldX}&z=${worldZ}`, { signal })
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
    const query = new URLSearchParams({
      view: request.view,
      x: String(request.tileX),
      z: String(request.tileZ),
      scale: String(request.blocksPerPixel),
      size: String(request.size),
      heights: request.heights ? '1' : '0'
    })
    const response = await this.fetch(`tile?${query}`, { signal })
    return decodeTile(await response.arrayBuffer())
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
