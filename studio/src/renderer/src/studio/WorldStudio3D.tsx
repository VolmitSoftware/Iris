import { useEffect, useMemo, useState } from 'react'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import type { BridgeClient, WorldRect } from '../bridge/BridgeClient'
import type { MapTile, WorldInfo } from '../bridge/types'
import { MapTerrain, type MapTerrainArea, type MapTerrainColoring } from '../scene/mapTerrain'
import { buildTerrainMesh } from '../scene/terrainMesh'
import { SceneViewport } from './SceneViewport'

const NUMBER = new Intl.NumberFormat('en-US')
const TILES_PER_SIDE = 4
const TILE_PIXELS = 128
const BLOCKS_PER_PIXEL = 1
const TILE_BLOCKS = TILE_PIXELS * BLOCKS_PER_PIXEL
const COLORING_ITEM = 'h-7 px-3 text-[12px] data-[state=on]:bg-selection data-[state=on]:text-foreground'

interface WorldStudio3DProps {
  client: BridgeClient
  world: WorldInfo
  /** Where the map last looked; the terrain is the square around its middle, or around the world's centre. */
  mapArea: WorldRect | null
  packRevision: number
  onSummary(summary: string | null): void
  tell(message: string): void
}

/** The terrain around where the map looked, in 3D, coloured by biome, region or height (v3e's 3D world view). */
export function WorldStudio3D({ client, world, mapArea, packRevision, onSummary, tell }: WorldStudio3DProps): React.JSX.Element {
  const [coloring, setColoring] = useState<MapTerrainColoring>('biomes')
  const centre = useMemo(() => centreOf(mapArea), [mapArea])
  const area = useTerrainArea(client, centre, packRevision, tell)
  const mesh = useMemo(() => (area === null ? null : buildTerrainMesh(new MapTerrain(area, coloring, world.fluidHeight))), [area, coloring, world.fluidHeight])

  useEffect(() => () => mesh?.dispose(), [mesh])
  useEffect(() => {
    const side = NUMBER.format(TILES_PER_SIDE * TILE_BLOCKS)
    onSummary(`${side} × ${side} blocks around ${NUMBER.format(centre.x)}, ${NUMBER.format(centre.z)}`)
    return () => onSummary(null)
  }, [centre, onSummary])

  return (
    <div className="relative min-w-0 flex-1">
      <SceneViewport content={mesh?.content ?? null} framing={area} />
      <ToggleGroup
        type="single"
        variant="outline"
        size="sm"
        className="absolute top-3 left-3 bg-card/90"
        value={coloring}
        onValueChange={(value) => value !== '' && setColoring(value as MapTerrainColoring)}
      >
        <ToggleGroupItem value="biomes" className={COLORING_ITEM}>
          Biomes
        </ToggleGroupItem>
        <ToggleGroupItem value="regions" className={COLORING_ITEM}>
          Regions
        </ToggleGroupItem>
        <ToggleGroupItem value="height" className={COLORING_ITEM}>
          Height
        </ToggleGroupItem>
      </ToggleGroup>
      {area === null && (
        <div className="pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-md border border-overlay-border bg-card/90 px-3 py-1.5 text-[12px] text-secondary-text">
          Sampling the terrain
        </div>
      )}
    </div>
  )
}

function centreOf(area: WorldRect | null): { x: number; z: number } {
  if (area === null) {
    return { x: 0, z: 0 }
  }
  return { x: Math.round((area.minX + area.maxX) / 2), z: Math.round((area.minZ + area.maxZ) / 2) }
}

/** The tiles of the square around {@code centre}, with heights; every tile arrives before the square is shown. */
function useTerrainArea(client: BridgeClient, centre: { x: number; z: number }, packRevision: number, tell: (message: string) => void): MapTerrainArea | null {
  const [area, setArea] = useState<MapTerrainArea | null>(null)
  useEffect(() => {
    const stop = new AbortController()
    const firstTileX = Math.floor(centre.x / TILE_BLOCKS - TILES_PER_SIDE / 2)
    const firstTileZ = Math.floor(centre.z / TILE_BLOCKS - TILES_PER_SIDE / 2)
    const rows = Array.from({ length: TILES_PER_SIDE }, (_, row) =>
      Promise.all(
        Array.from({ length: TILES_PER_SIDE }, (_, column) =>
          client.tile(
            {
              view: 'biomes',
              preview: null,
              tileX: firstTileX + column,
              tileZ: firstTileZ + row,
              blocksPerPixel: BLOCKS_PER_PIXEL,
              size: TILE_PIXELS,
              heights: true,
              pickNoise: false
            },
            stop.signal
          )
        )
      )
    )
    setArea(null)
    Promise.all(rows)
      .then((tiles: MapTile[][]) => setArea({ tiles, originX: firstTileX * TILE_BLOCKS, originZ: firstTileZ * TILE_BLOCKS }))
      .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
    return () => stop.abort()
  }, [client, centre, packRevision, tell])
  return area
}
