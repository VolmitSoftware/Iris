import { PerspectiveCamera, Ray, Vector3, type Mesh } from 'three'
import { describe, expect, it, vi } from 'vitest'
import type { TileSource } from '../map/TileStore'
import type { CameraLink } from '../studio/SceneViewport'
import { focusAround, TerrainStream, type TerrainStyle } from './terrainStream'

interface FlatTile {
  height: number
}

interface FlatLayer {
  name: string
  height: number
  /** Resolves the tiles of the layer once called; they stay on their way until then. */
  gate: Promise<void>
  /** Where given, the gate of the tiles this many blocks a column, in place of {@link gate}. */
  gateAt?: (blocksPerPixel: number) => Promise<void>
}

const COARSEST_LEVEL = 8
// A source that cannot sample coarser has more tiles in sight than it may draw, so it has to pick the right ones.
const COARSEST_SAVED_LEVEL = 4
const LOOKED_AT = new Vector3(0, 64, 0)
const open = Promise.resolve()

const flatStyle: TerrainStyle<FlatTile> = {
  surfaceOf: (tile, place) => ({
    columns: 128,
    rows: 128,
    originX: place.tileX * 128 * 2 ** place.level,
    originZ: place.tileZ * 128 * 2 ** place.level,
    blocksPerColumn: 2 ** place.level,
    groundAt: () => tile.height,
    waterAt: () => null,
    colorAt: () => 0x808080
  }),
  floorY: 0,
  topY: 128
}

function setUp(
  coarsestLevel = COARSEST_LEVEL,
  quickLayerOf?: (layer: FlatLayer) => FlatLayer | null
): {
  stream: TerrainStream<FlatTile, FlatLayer>
  camera: PerspectiveCamera
  moved(): void
  requested: number[]
  requestedLayers: string[]
  pendingCounts: number[]
  stopFollowing: () => void
} {
  const requested: number[] = []
  const requestedLayers: string[] = []
  const pendingCounts: number[] = []
  const source: TileSource<FlatTile, FlatLayer, never> = {
    layerKey: (layer) => layer.name,
    fetch: async (address, layer) => {
      requested.push(address.blocksPerPixel)
      requestedLayers.push(layer.name)
      await (layer.gateAt?.(address.blocksPerPixel) ?? layer.gate)
      return { height: layer.height }
    },
    bytesOf: () => 1
  }
  const stream = new TerrainStream({
    source,
    coarsestLevel,
    quickLayerOf,
    focus: focusAround(0, 0, 0, 128),
    onPending: (pending) => pendingCounts.push(pending),
    onError: (message) => {
      throw new Error(message)
    }
  })
  const camera = new PerspectiveCamera(45, 1.6, 4, 40_000)
  camera.position.set(0, 664, 360)
  camera.lookAt(LOOKED_AT)
  const listeners = new Set<() => void>()
  const link: CameraLink = {
    view: () => ({ camera, width: 1280, height: 800, orbitDistance: camera.position.distanceTo(LOOKED_AT) }),
    onMoved: (listener) => {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
    redraw: vi.fn()
  }
  const stopFollowing = stream.content.follow!(link)
  return { stream, camera, moved: () => listeners.forEach((listener) => listener()), requested, requestedLayers, pendingCounts, stopFollowing }
}

async function settle(): Promise<void> {
  for (let turn = 0; turn < 30; turn++) {
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

const meshesOf = (stream: TerrainStream<FlatTile, FlatLayer>): Mesh[] => stream.content.object.children.flatMap((tile) => tile.children as Mesh[])

describe('terrain stream', () => {
  it('draws nothing until it has a layer and a style, then the tiles in sight as they arrive', async () => {
    const { stream, requested } = setUp()
    await settle()
    expect(requested).toEqual([])
    stream.restyle(flatStyle)
    stream.showLayer({ name: 'one', height: 70, gate: open })
    await settle()
    expect(requested.length).toBeGreaterThan(0)
    expect(stream.content.object.children.length).toBeGreaterThan(0)
  })

  it('asks for the coarsest tiles first and reports what is still on its way', async () => {
    const { stream, requested, pendingCounts } = setUp()
    stream.restyle(flatStyle)
    stream.showLayer({ name: 'one', height: 70, gate: open })
    await settle()
    expect(requested[0]).toBe(Math.max(...requested))
    expect(pendingCounts[0]).toBeGreaterThan(0)
    expect(pendingCounts.at(-1)).toBe(0)
  })

  it('keeps the ground it drew while the tiles of another layer are on their way', async () => {
    const { stream } = setUp()
    stream.restyle(flatStyle)
    stream.showLayer({ name: 'one', height: 70, gate: open })
    await settle()
    const drawn = stream.content.object.children.length
    let release = (): void => {}
    stream.showLayer({ name: 'two', height: 90, gate: new Promise<void>((resolve) => (release = resolve)) })
    await settle()
    expect(stream.content.object.children.length).toBe(drawn)
    const ray = new Ray(new Vector3(10, 300, 10), new Vector3(0, -1, 0))
    expect(stream.content.surfaceAlong!(ray, 1000)!.y).toBeCloseTo(71, 3)
    release()
    await settle()
    expect(stream.content.surfaceAlong!(ray, 1000)!.y).toBeCloseTo(91, 3)
  })

  it('draws the quick stand-in of a slow layer at once, and asks for the slow tiles finest first', async () => {
    let release = (): void => {}
    const slow: FlatLayer = { name: 'slow', height: 90, gate: new Promise<void>((resolve) => (release = resolve)) }
    const quick: FlatLayer = { name: 'quick', height: 70, gate: open }
    const { stream, requested, requestedLayers } = setUp(COARSEST_LEVEL, (layer) => (layer === slow ? quick : null))
    stream.restyle(flatStyle)
    stream.showLayer(slow)
    await settle()
    const ray = new Ray(new Vector3(10, 300, 10), new Vector3(0, -1, 0))
    expect(requestedLayers[0]).toBe('quick')
    expect(stream.content.surfaceAlong!(ray, 1000)!.y).toBeCloseTo(71, 3)
    const slowScales = requested.filter((_, at) => requestedLayers[at] === 'slow')
    expect(slowScales.length).toBeGreaterThan(0)
    expect(slowScales).toEqual([...slowScales].sort((first, second) => first - second))
    release()
    await settle()
    expect(stream.content.surfaceAlong!(ray, 1000)!.y).toBeCloseTo(91, 3)
  })

  it('draws the tiles again in another style without asking for them again', async () => {
    const { stream, requested } = setUp()
    stream.restyle(flatStyle)
    stream.showLayer({ name: 'one', height: 70, gate: open })
    await settle()
    const asked = requested.length
    stream.restyle({ ...flatStyle, surfaceOf: (tile, place) => ({ ...flatStyle.surfaceOf(tile, place), groundAt: () => tile.height + 10 }) })
    await settle()
    expect(requested.length).toBe(asked)
    const ray = new Ray(new Vector3(10, 300, 10), new Vector3(0, -1, 0))
    expect(stream.content.surfaceAlong!(ray, 1000)!.y).toBeCloseTo(81, 3)
  })

  it('finds the ground a ray meets, and nothing where it passes by', async () => {
    const { stream } = setUp()
    stream.restyle(flatStyle)
    stream.showLayer({ name: 'one', height: 70, gate: open })
    await settle()
    const down = new Ray(new Vector3(10, 300, 10), new Vector3(0, -1, 0))
    expect(stream.content.surfaceAlong!(down, 1000)!.toArray().map(Math.round)).toEqual([10, 71, 10])
    const up = new Ray(new Vector3(10, 300, 10), new Vector3(0, 1, 0))
    expect(stream.content.surfaceAlong!(up, 1000)).toBeNull()
  })

  it('draws more tiles when the camera moves to ground it has not drawn', async () => {
    const { stream, camera, moved } = setUp()
    stream.restyle(flatStyle)
    stream.showLayer({ name: 'one', height: 70, gate: open })
    await settle()
    const ray = new Ray(new Vector3(40_000, 300, 0), new Vector3(0, -1, 0))
    expect(stream.content.surfaceAlong!(ray, 1000)).toBeNull()
    camera.position.set(40_000, 664, 360)
    camera.lookAt(new Vector3(40_000, 64, 0))
    moved()
    await settle()
    expect(stream.content.surfaceAlong!(ray, 1000)!.y).toBeCloseTo(71, 3)
  })

  it('reads the height of the ground the camera looks at once it is drawn, and tells when the view may have changed', async () => {
    const { stream, camera, moved } = setUp()
    const sightChanges = vi.fn()
    stream.onSightChanged(sightChanges)
    stream.restyle(flatStyle)
    expect(stream.groundInView()).toBeNull()
    stream.showLayer({ name: 'one', height: 70, gate: open })
    await settle()
    expect(sightChanges).toHaveBeenCalled()
    const seen = stream.groundInView()!
    expect(new Set(seen.heights)).toEqual(new Set([70]))
    expect(seen.area.maxX).toBeGreaterThan(seen.area.minX)

    camera.position.set(40_000, 664, 360)
    camera.lookAt(new Vector3(40_000, 64, 0))
    moved()
    await settle()
    expect(stream.groundInView()!.area.minX).toBeGreaterThan(seen.area.maxX)
  })

  it('draws the ground under a camera that is close to it in a world with a tall sky', async () => {
    const { stream, camera, moved } = setUp(COARSEST_SAVED_LEVEL)
    stream.restyle({ ...flatStyle, floorY: -64, topY: 1024 })
    stream.showLayer({ name: 'one', height: 70, gate: open })
    camera.position.set(250, 260, 300)
    camera.lookAt(new Vector3(36, 71, 47))
    moved()
    await settle()
    const sight = new Ray(camera.position.clone(), camera.getWorldDirection(new Vector3()))
    expect(stream.content.surfaceAlong!(sight, 2000)!.y).toBeCloseTo(71, 1)
  })

  it('shows the true ground it has from closer up again, rather than a quick stand-in, while coarser tiles are on their way', async () => {
    const neverArrives = new Promise<void>(() => {})
    const slow: FlatLayer = { name: 'slow', height: 90, gate: open, gateAt: (blocksPerPixel) => (blocksPerPixel <= 8 ? open : neverArrives) }
    const quick: FlatLayer = { name: 'quick', height: 70, gate: open }
    const { stream, camera, moved } = setUp(COARSEST_LEVEL, (layer) => (layer === slow ? quick : null))
    stream.restyle(flatStyle)
    stream.showLayer(slow)
    await settle()
    // Looking far away takes the ground near the origin out of sight, and so out of the drawn tiles.
    camera.position.set(500_000, 664, 360)
    camera.lookAt(500_000, 64, 0)
    moved()
    await settle()
    camera.position.set(0, 20_000, 12_000)
    camera.lookAt(LOOKED_AT)
    moved()
    await settle()

    const ray = new Ray(new Vector3(10, 30_000, 10), new Vector3(0, -1, 0))
    expect(stream.content.surfaceAlong!(ray, 40_000)!.y).toBeCloseTo(91, 3)
  })

  it('stops drawing when it is let go', async () => {
    const { stream, stopFollowing, requested } = setUp()
    stream.restyle(flatStyle)
    stream.showLayer({ name: 'one', height: 70, gate: open })
    await settle()
    stopFollowing()
    stream.dispose()
    const asked = requested.length
    stream.showLayer({ name: 'two', height: 90, gate: open })
    await settle()
    expect(requested.length).toBe(asked)
    expect(meshesOf(stream)).toHaveLength(0)
  })
})
