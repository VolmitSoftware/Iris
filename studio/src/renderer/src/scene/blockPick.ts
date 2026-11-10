import type { Vector3 } from 'three'
import { INTS_PER_BLOCK, type BlockScene } from './sceneDecoding'

/** How far past a hit surface along the ray a point is taken to tell which block the surface belongs to, in blocks. */
const SURFACE_NUDGE = 1e-3
export const NO_BLOCK = -1

/** The blocks of a scene by position. */
export class BlockIndex {
  private readonly indexes = new Map<number, number>()
  private readonly sizeY: number
  private readonly sizeZ: number

  constructor(private readonly scene: BlockScene) {
    ;[this.sizeY, this.sizeZ] = [scene.max[1] - scene.min[1], scene.max[2] - scene.min[2]]
    const blocks = scene.blocks
    for (let offset = 0; offset < blocks.length; offset += INTS_PER_BLOCK) {
      this.indexes.set(this.keyOf(blocks[offset], blocks[offset + 1], blocks[offset + 2]), offset / INTS_PER_BLOCK)
    }
  }

  /** The place of the block at x, y and z among the scene's blocks, or NO_BLOCK. */
  at(x: number, y: number, z: number): number {
    const inside = [x, y, z].every((value, axis) => value >= this.scene.min[axis] && value < this.scene.max[axis])
    return inside ? (this.indexes.get(this.keyOf(x, y, z)) ?? NO_BLOCK) : NO_BLOCK
  }

  private keyOf(x: number, y: number, z: number): number {
    const [localX, localY, localZ] = [x - this.scene.min[0], y - this.scene.min[1], z - this.scene.min[2]]
    return (localX * this.sizeY + localY) * this.sizeZ + localZ
  }
}

/** The block whose surface a ray met at {@code point}: the one the ray goes into. NO_BLOCK when that spot holds none. */
export function blockAtHit(index: BlockIndex, point: Vector3, direction: Vector3): number {
  const inside = point.clone().addScaledVector(direction, SURFACE_NUDGE)
  return index.at(Math.floor(inside.x), Math.floor(inside.y), Math.floor(inside.z))
}
