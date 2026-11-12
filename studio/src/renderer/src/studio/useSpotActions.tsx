import { useMemo } from 'react'
import type { Vector3 } from 'three'
import { ContextMenuItem, ContextMenuSeparator } from '@/components/ui/context-menu'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { SpotActions } from './SceneViewport'

/**
 * What a spot of terrain in 3D offers, as a spot on the map does: teleporting there and copying where it is.
 *
 * @param inStudioWorld whether the terrain is the studio's own world, the only one the server teleports into
 */
export function useSpotActions(client: BridgeClient, tell: (message: string) => void, inStudioWorld: boolean): SpotActions {
  return useMemo(() => {
    const teleportTo = (spot: Vector3): void => {
      const [blockX, blockZ] = [Math.floor(spot.x), Math.floor(spot.z)]
      client
        .teleport(blockX, blockZ)
        .then(() => tell(`Teleporting to ${blockX}, ${blockZ}`))
        .catch((failure: Error) => tell(failure.message))
    }
    const copy = (spot: Vector3): void => {
      const coordinates = coordinatesOf(spot)
      window.studio.copyText(coordinates)
      tell(`Copied ${coordinates}`)
    }
    return {
      onCtrlClick: (spot) => inStudioWorld && teleportTo(spot),
      menuItems: (spot) => (
        <>
          {inStudioWorld && (
            <>
              <ContextMenuItem onSelect={() => teleportTo(spot)}>Teleport here</ContextMenuItem>
              <ContextMenuSeparator />
            </>
          )}
          <ContextMenuItem onSelect={() => copy(spot)}>Copy coordinates ({coordinatesOf(spot)})</ContextMenuItem>
        </>
      )
    }
  }, [client, tell, inStudioWorld])
}

// A top face stands where a player's feet do, so its height is the Y to stand at.
function coordinatesOf(spot: Vector3): string {
  return `${Math.floor(spot.x)}, ${Math.round(spot.y)}, ${Math.floor(spot.z)}`
}
