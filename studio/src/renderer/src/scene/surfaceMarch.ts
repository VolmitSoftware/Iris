import { Vector3, type Ray } from 'three'

const FINEST_STEP_BLOCKS = 0.5
// Further off, a step is longer: what lies there is drawn in coarser columns.
const STEP_PER_DISTANCE = 0.01
const REFINEMENTS = 16

/**
 * Where the ray first comes down onto the surface within {@code reach}, found by marching along it, so a pick does not
 * have to test every face. {@code surfaceTopOver} says where the surface stands over a block, or null where nothing is
 * known. A ray that starts below the surface, or leaves above {@code ceilingY}, finds nothing.
 */
export function marchOntoSurface(ray: Ray, reach: number, ceilingY: number, surfaceTopOver: (worldX: number, worldZ: number) => number | null): Vector3 | null {
  const pointAt = (distance: number): Vector3 => ray.direction.clone().multiplyScalar(distance).add(ray.origin)
  const isBelow = (point: Vector3): boolean => {
    const top = surfaceTopOver(point.x, point.z)
    return top !== null && point.y <= top
  }
  if (isBelow(ray.origin)) {
    return null
  }
  let above = 0
  while (above < reach) {
    const below = Math.min(reach, above + Math.max(FINEST_STEP_BLOCKS, above * STEP_PER_DISTANCE))
    const point = pointAt(below)
    if (isBelow(point)) {
      return refined(above, below, pointAt, isBelow)
    }
    if (point.y > ceilingY && ray.direction.y >= 0) {
      return null
    }
    above = below
  }
  return null
}

function refined(above: number, below: number, pointAt: (distance: number) => Vector3, isBelow: (point: Vector3) => boolean): Vector3 {
  for (let refinement = 0; refinement < REFINEMENTS; refinement++) {
    const middle = (above + below) / 2
    if (isBelow(pointAt(middle))) {
      below = middle
    } else {
      above = middle
    }
  }
  return pointAt(below)
}
