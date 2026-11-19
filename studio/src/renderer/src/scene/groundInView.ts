import { Ray, Vector3 } from 'three'
import type { WorldRect } from '../bridge/BridgeClient'
import type { SightView } from './cameraSight'
import { marchOntoSurface } from './surfaceMarch'

/** Where the ground drawn so far stands: over a block, what the sky sees there, and the ground under any water. Null where nothing is known. */
export interface DrawnGround {
  surfaceTopOver(worldX: number, worldZ: number): number | null
  groundTopOver(worldX: number, worldZ: number): number | null
}

/** The ground the camera looks at: the box around it, and the height of the ground at each spot looked at. */
export interface GroundInView {
  area: WorldRect
  heights: number[]
}

// The spots are laid across the longer side of the view, as many along the shorter side as keeps them evenly apart.
const SPOTS_ALONG_LONGER_SIDE = 32
const NDC_MIDDLE_DEPTH = 0.5
const NDC_SPAN = 2
const SMALLEST_AREA_SIDE_BLOCKS = 1

/**
 * What the camera sees of {@code ground}: rays through an even grid of spots on the screen, each met where it comes
 * down onto the ground, and the box around the places they meet. Null when no ray meets any ground.
 *
 * @param ceilingY the height above which no ground stands
 */
export function groundInView(view: SightView, ceilingY: number, ground: DrawnGround): GroundInView | null {
  const { camera } = view
  camera.updateMatrixWorld()
  const eye = camera.getWorldPosition(new Vector3())
  const longer = Math.max(view.width, view.height)
  const spotsAcross = Math.max(1, Math.round((SPOTS_ALONG_LONGER_SIDE * view.width) / longer))
  const spotsDown = Math.max(1, Math.round((SPOTS_ALONG_LONGER_SIDE * view.height) / longer))
  const heights: number[] = []
  let [minX, minZ, maxX, maxZ] = [Infinity, Infinity, -Infinity, -Infinity]
  for (let spotZ = 0; spotZ < spotsDown; spotZ++) {
    for (let spotX = 0; spotX < spotsAcross; spotX++) {
      const through = new Vector3(centreOf(spotX, spotsAcross), -centreOf(spotZ, spotsDown), NDC_MIDDLE_DEPTH).unproject(camera)
      const ray = new Ray(eye, through.sub(eye).normalize())
      const hit = marchOntoSurface(ray, camera.far, ceilingY, (x, z) => ground.surfaceTopOver(x, z))
      const groundTop = hit === null ? null : ground.groundTopOver(hit.x, hit.z)
      if (hit === null || groundTop === null) {
        continue
      }
      heights.push(groundTop - 1)
      minX = Math.min(minX, hit.x)
      maxX = Math.max(maxX, hit.x)
      minZ = Math.min(minZ, hit.z)
      maxZ = Math.max(maxZ, hit.z)
    }
  }
  if (heights.length === 0) {
    return null
  }
  return {
    area: { minX, minZ, maxX: Math.max(maxX, minX + SMALLEST_AREA_SIDE_BLOCKS), maxZ: Math.max(maxZ, minZ + SMALLEST_AREA_SIDE_BLOCKS) },
    heights
  }
}

function centreOf(spot: number, spots: number): number {
  return -1 + ((spot + 0.5) * NDC_SPAN) / spots
}
