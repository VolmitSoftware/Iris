import { Box3, Group, Vector3, type BufferGeometry } from 'three'
import { TileStore, type TileSource } from '../map/TileStore'
import type { TileAddress } from '../map/viewport'
import type { CameraLink, ViewportContent } from '../studio/SceneViewport'
import { CameraSight } from './cameraSight'
import type { ColumnField } from './columnField'
import { groundInView, type GroundInView } from './groundInView'
import { marchOntoSurface } from './surfaceMarch'
import { planCover, type Cover, type DrawnTile, type TileState } from './terrainCover'
import { buildTerrainMesh, type TerrainMesh, type TerrainSurface } from './terrainMesh'
import { addressOf, ancestorAt, childrenOf, isInside, tileAt, tileContaining, tileKey, type ColumnArea, type TerrainTile } from './terrainTiles'

/** How the tiles of a source look as ground, and how low and high that ground can reach. */
export interface TerrainStyle<Tile> {
  surfaceOf(tile: Tile, place: TerrainTile): TerrainSurface
  floorY: number
  topY: number
  /**
   * Builds a tile's mesh away from the frames, as {@link buildTerrainMesh} does from {@link surfaceOf}; where not given,
   * tiles are built on the spot.
   */
  buildMesh?(tile: Tile, place: TerrainTile, base: number, holes: ColumnArea[]): Promise<TerrainMesh>
}

export interface TerrainStreamOptions<Tile, Layer> {
  source: TileSource<Tile, Layer, never>
  /** The coarsest level the source samples: tiles of level n have columns 2^n blocks wide. */
  coarsestLevel: number
  /** The box the camera frames when the terrain is first shown; ground is looked for between its heights until tiles show where it really stands. */
  focus: Box3
  /**
   * A layer whose tiles arrive quickly, drawn where the layer's own have not arrived yet, or null for none; a layer
   * whose tiles are slow to sample, such as one with generated blocks, so never leaves ground missing for long.
   */
  quickLayerOf?(layer: Layer): Layer | null
  /** Told how many tiles are still on their way, whenever that changes. */
  onPending(pending: number): void
  onError(message: string): void
}

/** A layer's tiles that have arrived, and every coarser tile that holds one, as of a version of the store. */
interface StoredIndex {
  version: number
  layerKey: string
  tiles: Set<string>
  holding: Set<string>
}

/** A tile's mesh on its way from a worker, for the data and holes it was asked for; null until it is built. */
interface BuildingMesh<Tile> {
  tile: TerrainTile
  data: Tile
  signature: string
  mesh: TerrainMesh | null
}

interface DrawnMesh<Tile> {
  tile: TerrainTile
  data: Tile
  signature: string
  mesh: TerrainMesh
}

const MESH_BUILD_BUDGET_MILLISECONDS = 12
// What a workstation GPU takes in a few milliseconds.
const UPLOAD_BYTES_PER_FRAME = 16 * 1024 * 1024

/** The bytes a mesh's geometry hands the GPU when it is first drawn. */
function bytesOf(mesh: TerrainMesh): number {
  let bytes = 0
  mesh.object.traverse((object) => {
    const geometry = (object as { geometry?: BufferGeometry }).geometry
    if (geometry === undefined) {
      return
    }
    for (const attribute of Object.values(geometry.attributes)) {
      bytes += (attribute.array as ArrayLike<number> & { byteLength: number }).byteLength
    }
    bytes += (geometry.getIndex()?.array as { byteLength: number } | undefined)?.byteLength ?? 0
  })
  return bytes
}
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
  private readonly sightListeners = new Set<() => void>()
  private standIns = new Map<string, Tile>()
  private readonly building = new Map<string, BuildingMesh<Tile>>()
  private storedIndex: StoredIndex | null = null
  private uploadBytesLeft = UPLOAD_BYTES_PER_FRAME
  private nextFrame: number | null = null
  private layer: Layer | null = null
  private style: TerrainStyle<Tile> | null = null
  private styleVersion = 0
  private camera: CameraLink | null = null
  private reportedPending = -1
  private continuation: ReturnType<typeof setTimeout> | null = null
  private disposed = false
  // Tiles are looked for where ground stands, not across the world's whole height: under a tall sky most of a tile's
  // box is air, which lands in the view where its ground does not.
  private lowestGround: number
  private highestGround: number
  private groundRangeGrew = false

  constructor(private readonly options: TerrainStreamOptions<Tile, Layer>) {
    this.lowestGround = options.focus.min.y
    this.highestGround = options.focus.max.y
    this.store = new TileStore(options.source, () => this.replan(), options.onError)
    this.content = {
      object: this.group,
      bounds: options.focus,
      isGround: true,
      surfaceAlong: (ray, reach) => marchOntoSurface(ray, reach, (this.style?.topY ?? 0) + ABOVE_TOP_BLOCKS, (x, z) => this.finestTopOver(x, z, (field) => field.surfaceTopOver(x, z))),
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

  /** Tells the listener whenever what the camera sees may have changed: it moved, or ground arrived; returns how to stop. */
  onSightChanged(listener: () => void): () => void {
    this.sightListeners.add(listener)
    return () => this.sightListeners.delete(listener)
  }

  /** The ground the camera looks at now, or null while none is drawn there. */
  groundInView(): GroundInView | null {
    if (this.camera === null || this.style === null) {
      return null
    }
    return groundInView(this.camera.view(), this.style.topY + ABOVE_TOP_BLOCKS, {
      surfaceTopOver: (x, z) => this.finestTopOver(x, z, (field) => field.surfaceTopOver(x, z)),
      groundTopOver: (x, z) => this.finestTopOver(x, z, (field) => field.groundTopOver(x, z))
    })
  }

  /** Draws the tiles another way, without sampling them again. */
  restyle(style: TerrainStyle<Tile>): void {
    this.style = style
    this.styleVersion++
    this.replan()
  }

  dispose(): void {
    this.disposed = true
    this.sightListeners.clear()
    if (this.continuation !== null) {
      clearTimeout(this.continuation)
    }
    if (this.nextFrame !== null) {
      cancelAnimationFrame(this.nextFrame)
    }
    this.drawn.forEach((drawn) => drawn.mesh.dispose())
    this.drawn.clear()
    this.building.forEach((building) => building.mesh?.dispose())
    this.building.clear()
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
    const sight = new CameraSight(this.camera.view(), this.lowestGround, this.highestGround)
    const layer = this.layer
    const quick = this.options.quickLayerOf?.(layer) ?? null
    const stateOf = (tile: TerrainTile): TileState => this.stateOf(tile, layer, quick)
    const knownInside = (tile: TerrainTile): TerrainTile[] => [...this.coarsestStoredInside(tile, layer), ...this.drawnInside(tile)]
    const cover = planCover(sight, stateOf, this.options.coarsestLevel, knownInside)
    this.store.wantEach(this.wantedOf(cover.wanted, layer, quick))
    this.draw(cover, layer, quick, this.style)
    this.reportPending()
    this.sightListeners.forEach((listener) => listener())
  }

  // Quick tiles come first, so the view fills at once. The slow ones go finest first: the ground near the camera, whose
  // blocks show the most, is not kept waiting behind the horizon's tiles, which each take far longer.
  private wantedOf(tiles: TerrainTile[], layer: Layer, quick: Layer | null): { address: TileAddress; layer: Layer }[] {
    if (quick === null) {
      return tiles.map((tile) => ({ address: addressOf(tile), layer }))
    }
    const quickOnes = tiles.filter((tile) => !this.store.has(addressOf(tile), quick)).map((tile) => ({ address: addressOf(tile), layer: quick }))
    const finestFirst = [...tiles].sort((first, second) => first.level - second.level)
    return [...quickOnes, ...finestFirst.map((tile) => ({ address: addressOf(tile), layer }))]
  }

  private stateOf(tile: TerrainTile, layer: Layer, quick: Layer | null): TileState {
    if (this.store.has(addressOf(tile), layer)) {
      return 'fresh'
    }
    if (this.standIns.has(tileKey(tile))) {
      return 'standIn'
    }
    return quick !== null && this.store.has(addressOf(tile), quick) ? 'quick' : 'none'
  }

  private dataOf(tile: TerrainTile, layer: Layer, quick: Layer | null): Tile | null {
    const key = tileKey(tile)
    const fresh = this.store.get(addressOf(tile), layer)
    if (fresh !== null) {
      this.standIns.delete(key)
      return fresh
    }
    const quickTile = quick === null ? null : this.store.get(addressOf(tile), quick)
    return this.standIns.get(key) ?? quickTile ?? null
  }

  // Ground drawn now, such as a stand-in left from the layer before, stays until a tile as true replaces it.
  private drawnInside(tile: TerrainTile): TerrainTile[] {
    return [...this.drawn.values()].map((drawn) => drawn.tile).filter((finer) => isInside(finer, tile))
  }

  // The layer's tiles that have arrived inside the tile, however much finer, but only the coarsest over each spot: they
  // show the ground as well from where it is too coarse for its own tile to have arrived, with the fewest faces.
  private coarsestStoredInside(tile: TerrainTile, layer: Layer): TerrainTile[] {
    const stored = this.storedIndexOf(layer)
    const found: TerrainTile[] = []
    const lookInside = (parent: TerrainTile): void => {
      for (const child of childrenOf(parent)) {
        const key = tileKey(child)
        if (stored.tiles.has(key)) {
          found.push(child)
        } else if (stored.holding.has(key)) {
          lookInside(child)
        }
      }
    }
    if (stored.holding.has(tileKey(tile))) {
      lookInside(tile)
    }
    return found
  }

  private storedIndexOf(layer: Layer): StoredIndex {
    const layerKey = this.options.source.layerKey(layer)
    if (this.storedIndex?.version === this.store.version && this.storedIndex.layerKey === layerKey) {
      return this.storedIndex
    }
    const index: StoredIndex = { version: this.store.version, layerKey, tiles: new Set(), holding: new Set() }
    for (const address of this.store.addressesOf(layer)) {
      const tile = tileAt(address)
      index.tiles.add(tileKey(tile))
      for (let level = tile.level + 1; level <= this.options.coarsestLevel; level++) {
        index.holding.add(tileKey(ancestorAt(tile, level)))
      }
    }
    this.storedIndex = index
    return index
  }

  private draw(cover: Cover, layer: Layer, quick: Layer | null, style: TerrainStyle<Tile>): void {
    const deadline = performance.now() + MESH_BUILD_BUDGET_MILLISECONDS
    const stillDrawn = new Set<string>()
    let behind = false
    // Finer tiles are built before the coarser ones get their holes, so running out of time leaves ground drawn twice, never a gap.
    for (const draw of [...cover.draws].sort((first, second) => first.tile.level - second.tile.level)) {
      const key = tileKey(draw.tile)
      stillDrawn.add(key)
      const data = this.dataOf(draw.tile, layer, quick)
      if (data === null || this.isCurrent(this.drawn.get(key), data, draw)) {
        continue
      }
      if (performance.now() > deadline) {
        behind = true
        continue
      }
      if (style.buildMesh === undefined) {
        this.show(key, draw, this.builtNow(draw, data, style), data)
        continue
      }
      this.showOnceBuilt(key, draw, data, cover, style.buildMesh, style.floorY)
    }
    for (const [key, building] of [...this.building]) {
      if (!stillDrawn.has(key)) {
        building.mesh?.dispose()
        this.building.delete(key)
      }
    }
    this.camera?.redraw()
    if (this.groundRangeGrew) {
      this.groundRangeGrew = false
      this.continueLater()
    }
    if (behind) {
      this.continueLater()
      return
    }
    for (const [key, drawn] of [...this.drawn]) {
      if (!stillDrawn.has(key) && !this.isReplacementBuilding(drawn.tile)) {
        this.hide(key, drawn)
      }
    }
  }

  private isCurrent(drawn: DrawnMesh<Tile> | undefined, data: Tile, draw: DrawnTile): boolean {
    return drawn !== undefined && drawn.data === data && drawn.signature === this.signatureOf(draw)
  }

  private signatureOf(draw: DrawnTile): string {
    const holes = draw.holes.map((hole) => `${hole.fromColumn},${hole.fromRow},${hole.toColumn},${hole.toRow}`).sort()
    return `${this.styleVersion}/${holes.join(';')}`
  }

  // Ground leaving the plan stays until what takes its place, a coarser tile over it or finer ones in it, is built.
  private isReplacementBuilding(tile: TerrainTile): boolean {
    for (const building of this.building.values()) {
      if (building.mesh === null && (isInside(tile, building.tile) || isInside(building.tile, tile))) {
        return true
      }
    }
    return false
  }

  private builtNow(draw: DrawnTile, data: Tile, style: TerrainStyle<Tile>): TerrainMesh {
    return buildTerrainMesh(style.surfaceOf(data, draw.tile), style.floorY, draw.holes)
  }

  // A tile is built away from the frames, and shown once built. One with holes is shown only once the finer tiles that
  // fill them are, so what it drew before stays meanwhile and the ground never shows a gap.
  private showOnceBuilt(key: string, draw: DrawnTile, data: Tile, cover: Cover, buildMesh: NonNullable<TerrainStyle<Tile>['buildMesh']>, base: number): void {
    const signature = this.signatureOf(draw)
    const building = this.building.get(key)
    if (building !== undefined && building.data === data && building.signature === signature) {
      const finer = cover.draws.filter((other) => isInside(other.tile, draw.tile)).map((other) => tileKey(other.tile))
      if (building.mesh === null || !finer.every((finerKey) => this.drawn.has(finerKey))) {
        return
      }
      const bytes = bytesOf(building.mesh)
      if (bytes > this.uploadBytesLeft && this.uploadBytesLeft < UPLOAD_BYTES_PER_FRAME) {
        this.continueNextFrame()
        return
      }
      this.uploadBytesLeft -= bytes
      this.building.delete(key)
      this.show(key, draw, building.mesh, data)
      return
    }
    building?.mesh?.dispose()
    const started: BuildingMesh<Tile> = { tile: draw.tile, data, signature, mesh: null }
    this.building.set(key, started)
    buildMesh(data, draw.tile, base, draw.holes).then(
      (mesh) => {
        if (this.disposed || this.building.get(key) !== started) {
          mesh.dispose()
          return
        }
        started.mesh = mesh
        this.continueLater()
      },
      (error: Error) => {
        if (!this.disposed && this.building.get(key) === started) {
          this.building.delete(key)
          this.options.onError(error.message)
        }
      }
    )
  }

  private show(key: string, draw: DrawnTile, mesh: TerrainMesh, data: Tile): void {
    const previous = this.drawn.get(key)
    if (previous !== undefined) {
      this.hide(key, previous)
    }
    this.group.add(mesh.object)
    this.drawn.set(key, { tile: draw.tile, data, signature: this.signatureOf(draw), mesh })
    this.growGroundRangeOver(mesh.field)
  }

  private growGroundRangeOver(field: ColumnField): void {
    const tops = field.topRange
    if (tops === null || (tops.lowest >= this.lowestGround && tops.highest <= this.highestGround)) {
      return
    }
    this.lowestGround = Math.min(this.lowestGround, tops.lowest)
    this.highestGround = Math.max(this.highestGround, tops.highest)
    this.groundRangeGrew = true
  }

  private hide(key: string, drawn: DrawnMesh<Tile>): void {
    this.group.remove(drawn.mesh.object)
    drawn.mesh.dispose()
    this.drawn.delete(key)
  }

  // A frame hands the GPU only so many bytes of new meshes; the rest wait for the next, so a burst of tiles never stalls one.
  private continueNextFrame(): void {
    if (this.nextFrame !== null) {
      return
    }
    this.nextFrame = requestAnimationFrame(() => {
      this.nextFrame = null
      this.uploadBytesLeft = UPLOAD_BYTES_PER_FRAME
      this.replan()
    })
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

  // The finest drawn tile that knows the block answers; coarser ones have holes where finer ones are drawn.
  private finestTopOver(worldX: number, worldZ: number, topOf: (field: ColumnField) => number | null): number | null {
    for (let level = 0; level <= this.options.coarsestLevel; level++) {
      const field = this.drawn.get(tileKey(tileContaining(worldX, worldZ, level)))?.mesh.field
      const top = field === undefined ? null : topOf(field)
      if (top !== null) {
        return top
      }
    }
    return null
  }
}
