import { Raycaster, type Ray, type Vector3 } from 'three'
import type { ViewportContent } from '../studio/SceneViewport'

/** Where the ray first meets what the content draws within {@code reach}, or null when it passes by. */
export function surfaceAlong(content: ViewportContent, ray: Ray, reach: number): Vector3 | null {
  if (content.surfaceAlong !== undefined) {
    return content.surfaceAlong(ray)
  }
  const caster = new Raycaster(ray.origin, ray.direction, 0, reach)
  const [nearest] = caster.intersectObject(content.object, true)
  return nearest === undefined ? null : nearest.point
}
