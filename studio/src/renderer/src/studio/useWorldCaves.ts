import { useEffect, useMemo, useState } from 'react'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { Share } from '../map/census'
import { BlockModels } from '../scene/blockModels'
import { BridgeAssets } from '../scene/BridgeAssets'
import { INTS_PER_BLOCK, type BlockScene } from '../scene/sceneDecoding'
import { buildSceneMesh, type SceneMesh } from '../scene/sceneMesh'

/** Where the caves of the world are shown: a square of blocks around a spot. */
export interface CaveArea {
  centerX: number
  centerZ: number
  size: number
}

export interface WorldCaves {
  scene: BlockScene | null
  mesh: SceneMesh | null
  /** What the server is doing while the caves are on their way, or null once they are in. */
  status: string | null
}

/**
 * The colour of each band of heights the caves are drawn in, from the highest down, as the server colours them
 * (Iris core CaveSpace).
 */
const CAVE_BANDS: readonly { block: string; name: string; color: number }[] = [
  { block: 'minecraft:yellow_concrete', name: 'Y 64 and up', color: 0xf1af15 },
  { block: 'minecraft:orange_concrete', name: 'Y 32 to 63', color: 0xe06101 },
  { block: 'minecraft:red_concrete', name: 'Y 0 to 31', color: 0x8e2121 },
  { block: 'minecraft:magenta_concrete', name: 'Y −32 to −1', color: 0xa9309f },
  { block: 'minecraft:purple_concrete', name: 'Y −64 to −33', color: 0x64209c },
  { block: 'minecraft:blue_concrete', name: 'Below Y −64', color: 0x2c2e8f }
]

/** How much of the caves' outside lies in each band of heights, highest first; bands without caves are left out. */
export function caveBandShares(scene: BlockScene): Share[] {
  const counts = new Map<number, number>()
  for (let offset = 3; offset < scene.blocks.length; offset += INTS_PER_BLOCK) {
    const paletteIndex = scene.blocks[offset]
    counts.set(paletteIndex, (counts.get(paletteIndex) ?? 0) + 1)
  }
  const blocks = scene.blocks.length / INTS_PER_BLOCK
  return CAVE_BANDS.flatMap((band) => {
    const count = counts.get(scene.palette.indexOf(band.block)) ?? 0
    return count === 0 ? [] : [{ subject: { kind: 'HEIGHT' as const, key: band.block, name: band.name }, color: band.color, fraction: count / blocks }]
  })
}

/**
 * The caves of the world's generated chunks in {@code area}, asked for again when it or the pack changes; null asks
 * for none. A newer area abandons an older one, on the server too, and a replaced mesh is freed.
 */
export function useWorldCaves(client: BridgeClient, area: CaveArea | null, packRevision: number, tell: (message: string) => void): WorldCaves {
  const [waiting, setWaiting] = useState<string | null>(null)
  const images = useMemo(() => new BridgeAssets(client, setWaiting), [client])
  const models = useMemo(() => new BlockModels(images), [images])
  const [shown, setShown] = useState<{ scene: BlockScene; mesh: SceneMesh } | null>(null)
  const [building, setBuilding] = useState(false)
  const centerX = area?.centerX
  const centerZ = area?.centerZ
  const size = area?.size

  useEffect(() => {
    if (centerX === undefined || centerZ === undefined || size === undefined) {
      return
    }
    const stop = new AbortController()
    setBuilding(true)
    client
      .caves(centerX, centerZ, size, stop.signal)
      .then((scene) => buildSceneMesh(scene, models, images).then((mesh) => ({ scene, mesh })))
      .then((built) => {
        if (stop.signal.aborted) {
          built.mesh.dispose()
          return
        }
        setShown(built)
      })
      .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
      .finally(() => !stop.signal.aborted && setBuilding(false))
    return () => stop.abort()
  }, [client, centerX, centerZ, size, packRevision, models, images, tell])

  useEffect(() => () => shown?.mesh.dispose(), [shown])

  return {
    scene: shown?.scene ?? null,
    mesh: shown?.mesh ?? null,
    status: building ? (waiting ?? `Generating the caves of ${size} × ${size} blocks`) : null
  }
}
