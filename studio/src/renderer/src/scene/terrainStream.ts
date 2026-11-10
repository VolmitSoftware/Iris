import { Box3, Group, Vector3 } from 'three'
import { TileStore, type TileSource } from '../map/TileStore'
import type { CameraLink, ViewportContent } from '../studio/SceneViewport'
import { CameraSight } from './cameraSight'
import { marchOntoSurface } from './surfaceMarch'
import { planCover, type Cover, type DrawnTile, type TileState } from './terrainCover'
import { buildTerrainMesh, type TerrainMesh, type TerrainSurface } from './terrainMesh'
import { addressOf, tileContaining, tileKey, type TerrainTile } from './terrainTiles'

/** How the tiles of a source look as ground, and how low and high that ground can reach. */
export interface TerrainStyle<Tile> {
  surfaceOf(tile: Tile, place: TerrainTile): TerrainSurface
  floorY: number
  topY: number
}

export interface TerrainStreamOptions<Tile, Layer> {
  source: TileSource<Tile, Layer, never>
  /** The coarsest level the source samples: tiles of level n have columns 2^n blocks wide. */
  coarsestLevel: number
  /** The box the camera frames when the terrain is first shown. */
  focus: Box3
  /** Told how many tiles are still on their way, whenever that changes. */
  onPending(pending: number): void
  onError(message: string): void
}

interface DrawnMesh<Tile> {
  tile: TerrainTile
  data: Tile
  signature: string
  mesh: TerrainMesh
}

const MESH_BUILD_BUDGET_MILLISECONDS = 12
const ABOVE_TOP_BLOCKS = 1
const FOCUS_HALF_SIDE_BLOCKS = 256

/** The box the camera first frames: a square of blocks around the spot, from {@code lowY} to {@code highY}. */
export function focusAround(centerX: number, centerZ: number, lowY: number, highY: number): Box3 {
  return new Box3(
    new Vector3(centerX - FOCUS_HALF_SIDE_BLOCKS, lowY, centerZ - FOCUS_HALF_SIDE_BLOCKS),
    new Vector3(centerX + FOCUS_HALF_SIDE_BLOCKS, highY, centerZ + FOCUS_HALF_SIDE_BLOCKS)
  )
}

/**
 * Endless terrain drawn where the camera looks: tiles of one layer of a source, coarse ones first and then finer ones
 * down to a column per pixel, fetched as the camera moves and kept within a memory budget, so going back is instant.
 * Ground that was drawn before the layer changed stands in until its tile is sampled anew, and coarser tiles stand in
 * for finer ones that are not there yet, so the view never empties while it catches up.
 */
export class TerrainStream<Tile, Layer> {
  readonly content: ViewportContent
  private readonly group = new Group()
  private readonly store: TileStore<Tile, Layer, never>
  private readonly drawn = new Map<string, DrawnMesh<Tile>>()
  private standIns = new Map<string, Tile>()
  private layer: Layer | null = null
  private style: TerrainStyle<Tile> | null = null
  private styleVersion = 0
  private camera: CameraLink | null = null
  private reportedPending = -1
  private continuation: ReturnType<typeof setTimeout> | null = null
  private disposed = false

  constructor(private readonly options: TerrainStreamOptions<Tile, Layer>) {
    this.store = new TileStore(options.source, () => this.replan(), options.onError)
    this.content = {
      object: this.group,
      bounds: options.focus,
      surfaceAlong: (ray, reach) => marchOntoSurface(ray, reach, (this.style?.topY ?? 0) + ABOVE_TOP_BLOCKS, (x, z) => this.surfaceTopOver(x, z)),
      follow: (camera) => this.follow(camera)
    }
  }

  /** Draws another layer of the source; what is drawn now stays until the new layer's tiles replace it. */
  showLayer(layer: Layer): void {
    if (this.layer !== null && this.options.source.layerKey(this.layer) === this.options.source.layerKey(layer)) {
      this.layer = layer
      return
    }
    this.standIns = new Map([...this.drawn].map(([key, drawn]) => [key, drawn.data]))
    this.layer = layer
    this.replan()
  }

  /** Draws the tiles another way, without sampling them again. */
  restyle(style: TerrainStyle<Tile>): void {
    this.style = style
    this.styleVersion++
    this.replan()
  }

  dispose(): void {
    this.disposed = true
    if (this.continuation !== null) {
      clearTimeout(this.continuation)
    }
    this.drawn.forEach((drawn) => drawn.mesh.dispose())
    this.drawn.clear()
    this.group.clear()
    this.store.clear()
  }

  private follow(camera: CameraLink): () => void {
    this.camera = camera
    const stop = camera.onMoved(() => this.replan())
    this.replan()
    return () => {
      stop()
      this.camera = null
    }
  }

  private replan(): void {
    if (this.disposed || this.layer === null || this.style === null || this.camera === null) {
      return
    }
    const sight = new CameraSight(this.camera.view(), this.style.floorY, this.style.topY)
    const layer = this.layer
    const cover = planCover(sight, (tile) => this.stateOf(tile, layer), this.options.coarsestLevel)
    this.store.want(cover.wanted.map(addressOf), layer)
    this.draw(cover, layer, this.style)
    this.reportPending()
  }

  private stateOf(tile: TerrainTile, layer: Layer): TileState {
    if (this.store.has(addressOf(tile), layer)) {
      return 'fresh'
    }
    return this.standIns.has(tileKey(tile)) ? 'standIn' : 'none'
  }

  private dataOf(tile: TerrainTile, layer: Layer): Tile | null {
    const key = tileKey(tile)
    const fresh = this.store.get(addressOf(tile), layer)
    if (fresh !== null) {
      this.standIns.delete(key)
      return fresh
    }
    return this.standIns.get(key) ?? null
  }

  private draw(cover: Cover, layer: Layer, style: TerrainStyle<Tile>): void {
    const deadline = performance.now() + MESH_BUILD_BUDGET_MILLISECONDS
    const stillDrawn = new Set<string>()
    let behind = false
    for (const draw of cover.draws) {
      const key = tileKey(draw.tile)
      stillDrawn.add(key)
      const data = this.dataOf(draw.tile, layer)
      if (data === null || this.isCurrent(this.drawn.get(key), data, draw)) {
        continue
      }
      if (performance.now() > deadline) {
        behind = true
        continue
      }
      this.show(key, draw, data, style)
    }
    for (const [key, drawn] of [...this.drawn]) {
      if (!stillDrawn.has(key)) {
        this.hide(key, drawn)
      }
    }
    this.camera?.redraw()
    if (behind) {
      this.continueLater()
    }
  }

  private isCurrent(drawn: DrawnMesh<Tile> | undefined, data: Tile, draw: DrawnTile): boolean {
    return drawn !== undefined && drawn.data === data && drawn.signature === this.signatureOf(draw)
  }

  private signatureOf(draw: DrawnTile): string {
    const holes = draw.holes.map((hole) => `${hole.fromColumn},${hole.fromRow},${hole.toColumn},${hole.toRow}`).sort()
    return `${this.styleVersion}/${holes.join(';')}`
  }

  private show(key: string, draw: DrawnTile, data: Tile, style: TerrainStyle<Tile>): void {
    const previous = this.drawn.get(key)
    if (previous !== undefined) {
      this.hide(key, previous)
    }
    const mesh = buildTerrainMesh(style.surfaceOf(data, draw.tile), style.floorY, draw.holes)
    this.group.add(mesh.object)
    this.drawn.set(key, { tile: draw.tile, data, signature: this.signatureOf(draw), mesh })
  }

  private hide(key: string, drawn: DrawnMesh<Tile>): void {
    this.group.remove(drawn.mesh.object)
    drawn.mesh.dispose()
    this.drawn.delete(key)
  }

  private continueLater(): void {
    if (this.continuation !== null) {
      return
    }
    this.continuation = setTimeout(() => {
      this.continuation = null
      this.replan()
    }, 0)
  }

  private reportPending(): void {
    const pending = this.store.pending()
    if (pending !== this.reportedPending) {
      this.reportedPending = pending
      this.options.onPending(pending)
    }
  }

  private surfaceTopOver(worldX: number, worldZ: number): number | null {
    for (let level = 0; level <= this.options.coarsestLevel; level++) {
      const top = this.drawn.get(tileKey(tileContaining(worldX, worldZ, level)))?.mesh.field.surfaceTopOver(worldX, worldZ) ?? null
      if (top !== null) {
        return top
      }
    }
    return null
  }
}
