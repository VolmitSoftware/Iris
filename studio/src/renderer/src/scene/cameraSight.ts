import { Box3, Frustum, Vector3, type PerspectiveCamera } from 'three'
import { blocksPerColumn, originXOf, originZOf, tileBlocks, tileContaining, type TerrainTile } from './terrainTiles'

/** What a camera looks through: its size in CSS pixels and how far it orbits from what is in the middle. */
export interface SightView {
  camera: PerspectiveCamera
  width: number
  height: number
  orbitDistance: number
}

const NDC_MIDDLE_DEPTH = 0.5
const NOT_BEHIND_THE_CAMERA = 0
const BEHIND_THE_CAMERA = 1e9

/**
 * What the camera sees of ground that lies between {@code floorY} and {@code topY}: which terrain tiles land in the
 * view, or within a margin around it, and how wide their columns look from where the camera stands.
 */
export class CameraSight {
  private readonly frustums = new Map<number, Frustum>()
  private readonly position = new Vector3()

  constructor(
    private readonly view: SightView,
    readonly floorY: number,
    readonly topY: number
  ) {
    view.camera.updateMatrixWorld()
    view.camera.getWorldPosition(this.position)
  }

  get orbitDistance(): number {
    return this.view.orbitDistance
  }

  /** How many pixels the view has, with {@code margin} of its size added on every side. */
  pixelsWithin(margin: number): number {
    const widening = 1 + 2 * margin
    return this.view.width * this.view.height * widening * widening
  }

  /** Whether any of the tile, from the floor to the top, lands in the view widened by {@code margin} of its size on every side. */
  sees(tile: TerrainTile, margin: number): boolean {
    return this.frustumWith(margin).intersectsBox(this.boxOf(tile))
  }

  /** How many pixels wide a column of the tile looks at the nearest ground of it. */
  columnPixels(tile: TerrainTile): number {
    const distance = Math.max(this.boxOf(tile).distanceToPoint(this.position), this.view.camera.near)
    const pixelsPerBlock = this.view.height / (2 * distance * Math.tan((this.view.camera.fov * Math.PI) / 360))
    return blocksPerColumn(tile.level) * pixelsPerBlock
  }

  /** How far the tile's middle lies from the middle of the view on the screen; tiles behind the camera lie furthest. */
  distanceFromMiddle(tile: TerrainTile): number {
    const middle = new Vector3(originXOf(tile) + tileBlocks(tile.level) / 2, (this.floorY + this.topY) / 2, originZOf(tile) + tileBlocks(tile.level) / 2)
    const behind = middle.clone().applyMatrix4(this.view.camera.matrixWorldInverse).z > NOT_BEHIND_THE_CAMERA
    if (behind) {
      return BEHIND_THE_CAMERA + middle.distanceTo(this.position)
    }
    const onScreen = middle.project(this.view.camera)
    return Math.hypot(onScreen.x, onScreen.y)
  }

  /** The tiles of {@code level} in sight, widened by {@code margin}. */
  tilesAt(level: number, margin: number): TerrainTile[] {
    const { first, last } = this.footprint(level, margin)
    const inSight: TerrainTile[] = []
    for (let tileZ = first.tileZ; tileZ <= last.tileZ; tileZ++) {
      for (let tileX = first.tileX; tileX <= last.tileX; tileX++) {
        const tile = { level, tileX, tileZ }
        if (this.sees(tile, margin)) {
          inSight.push(tile)
        }
      }
    }
    return inSight
  }

  // The corners of the widened view, cast onto the floor and the top of the ground, bound the tiles that can be in it.
  private footprint(level: number, margin: number): { first: TerrainTile; last: TerrainTile } {
    const { camera } = this.view
    const reach = camera.far
    const widening = 1 + 2 * margin
    let [minX, minZ, maxX, maxZ] = [Infinity, Infinity, -Infinity, -Infinity]
    for (const [cornerX, cornerY] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
      const direction = new Vector3(cornerX * widening, cornerY * widening, NDC_MIDDLE_DEPTH).unproject(camera).sub(this.position).normalize()
      for (const groundY of [this.floorY, this.topY]) {
        const distance = direction.y < 0 ? Math.min(reach, (groundY - this.position.y) / direction.y) : reach
        const point = direction.clone().multiplyScalar(Math.max(0, distance)).add(this.position)
        minX = Math.min(minX, point.x)
        maxX = Math.max(maxX, point.x)
        minZ = Math.min(minZ, point.z)
        maxZ = Math.max(maxZ, point.z)
      }
    }
    return { first: tileContaining(minX, minZ, level), last: tileContaining(maxX, maxZ, level) }
  }

  private frustumWith(margin: number): Frustum {
    let frustum = this.frustums.get(margin)
    if (frustum === undefined) {
      const { camera } = this.view
      const widening = 1 + 2 * margin
      const projection = camera.projectionMatrix.clone()
      projection.elements[0] /= widening
      projection.elements[5] /= widening
      frustum = new Frustum().setFromProjectionMatrix(projection.multiply(camera.matrixWorldInverse))
      this.frustums.set(margin, frustum)
    }
    return frustum
  }

  private boxOf(tile: TerrainTile): Box3 {
    const size = tileBlocks(tile.level)
    const x = originXOf(tile)
    const z = originZOf(tile)
    return new Box3(new Vector3(x, this.floorY, z), new Vector3(x + size, this.topY, z + size))
  }
}
