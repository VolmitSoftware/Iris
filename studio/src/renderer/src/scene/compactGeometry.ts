import { BufferAttribute, BufferGeometry, type Vector3Tuple } from 'three'

// Positions are kept in sixteenths of a block, the finest step a block model uses, from the geometry's lowest corner.
const STEPS_PER_BLOCK = 16
const MOST_STEPS = 32_767

/** Where a compact geometry's corner lies in the world and how far one of its steps goes along each axis. */
export interface CompactPlacement {
  origin: Vector3Tuple
  scale: Vector3Tuple
}

/** A geometry's arrays packed small: positions in whole steps, colours a byte a channel, picture places in 16 bits. */
export interface CompactArrays {
  position: Int16Array
  color: Uint8Array | null
  uv: Uint16Array | null
  index: Uint32Array
  placement: CompactPlacement
}

/**
 * The geometry's arrays in about half the bytes, which the GPU takes in half the time: a tile's corners are whole steps
 * of a sixteenth of a block, or of a column across wider columns, from the tile's lowest corner; colours and picture
 * places are fractions packed into integers. A geometry too tall for that many steps takes longer steps up and down.
 */
export function compacted(geometry: BufferGeometry, blocksPerColumn: number): CompactArrays {
  const positions = geometry.getAttribute('position').array as Float32Array
  const min: Vector3Tuple = [Infinity, Infinity, Infinity]
  const max: Vector3Tuple = [-Infinity, -Infinity, -Infinity]
  for (let at = 0; at < positions.length; at += 3) {
    for (let axis = 0; axis < 3; axis++) {
      min[axis] = Math.min(min[axis], positions[at + axis])
      max[axis] = Math.max(max[axis], positions[at + axis])
    }
  }
  const horizontal = blocksPerColumn / STEPS_PER_BLOCK
  const stepFor = (axis: number, finest: number): number => Math.max(finest, (max[axis] - min[axis]) / MOST_STEPS)
  const scale: Vector3Tuple = [stepFor(0, horizontal), stepFor(1, 1 / STEPS_PER_BLOCK), stepFor(2, horizontal)]
  const position = new Int16Array(positions.length)
  for (let at = 0; at < positions.length; at++) {
    const axis = at % 3
    position[at] = Math.round((positions[at] - min[axis]) / scale[axis])
  }
  const color = geometry.getAttribute('color')?.array as Float32Array | undefined
  const uv = geometry.getAttribute('uv')?.array as Float32Array | undefined
  return {
    position,
    color: color === undefined ? null : Uint8Array.from(color, (channel) => Math.round(Math.min(1, Math.max(0, channel)) * 255)),
    uv: uv === undefined ? null : Uint16Array.from(uv, (place) => Math.round(Math.min(1, Math.max(0, place)) * 65_535)),
    index: geometry.getIndex()!.array as Uint32Array,
    placement: { origin: min, scale }
  }
}

/** The geometry the compact arrays hold, in the steps they were packed in; draw it placed by their placement. */
export function geometryOfCompact(arrays: CompactArrays): BufferGeometry {
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(arrays.position, 3))
  if (arrays.color !== null) {
    geometry.setAttribute('color', new BufferAttribute(arrays.color, 3, true))
  }
  if (arrays.uv !== null) {
    geometry.setAttribute('uv', new BufferAttribute(arrays.uv, 2, true))
  }
  geometry.setIndex(new BufferAttribute(arrays.index, 1))
  return geometry
}
