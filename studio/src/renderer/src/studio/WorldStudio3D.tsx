import { useEffect, useMemo, useState } from 'react'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import type { BridgeClient, MapLayerQuery, WorldRect } from '../bridge/BridgeClient'
import type { MapTile, WorldInfo } from '../bridge/types'
import { heightBandShares } from '../map/census'
import { mapTileSource, type TileLayer } from '../map/mapTileSource'
import type { GroundInView } from '../scene/groundInView'
import { blockArt } from '../scene/blockArt'
import { MapTerrain, type MapTerrainColoring } from '../scene/mapTerrain'
import { focusAround, type TerrainStyle } from '../scene/terrainStream'
import { TILE_COLUMNS } from '../scene/terrainTiles'
import { Legend } from './Legend'
import { PickInspector } from './PickInspector'
import { SceneViewport } from './SceneViewport'
import { usePickInspection } from './usePickInspection'
import { HEADER_CHOICE, Panel, ViewIsland } from './Sidebar'
import { useGroundInView, useLongPending, useTerrainStream } from './useTerrainStream'

const NUMBER = new Intl.NumberFormat('en-US')
// The map's coarsest tiles are 4096 blocks a pixel, which is level 12.
const COARSEST_LEVEL = 12
const BLOCKS_ABOVE_FLUID_IN_FOCUS = 96
const BLOCKS_BELOW_FLUID_IN_FOCUS = 32

interface WorldStudio3DProps {
  title: string
  client: BridgeClient
  world: WorldInfo
  /** Where the map last looked; the terrain opens around its middle, or around the world's centre. */
  mapArea: WorldRect | null
  packRevision: number
  onSummary(summary: string | null): void
  tell(message: string): void
}

/**
 * The terrain around where the map looked, in 3D and without an edge, coloured by biome, region or height, beside how
 * the noise picks the biomes or regions the camera sees, or how much of that ground lies at each height (v3e's 3D world view).
 */
export function WorldStudio3D({ title, client, world, mapArea, packRevision, onSummary, tell }: WorldStudio3DProps): React.JSX.Element {
  const [coloring, setColoring] = useState<MapTerrainColoring>('biomes')
  const centre = useMemo(() => centreOf(mapArea), [mapArea])
  const art = useMemo(() => blockArt(client, true), [client])
  useEffect(() => () => art.dispose(), [art])
  const source = useMemo(() => mapTileSource(client, TILE_COLUMNS, art), [client, art])
  const focus = useMemo(
    () => focusAround(centre.x, centre.z, world.fluidHeight - BLOCKS_BELOW_FLUID_IN_FOCUS, world.fluidHeight + BLOCKS_ABOVE_FLUID_IN_FOCUS),
    [centre, world.fluidHeight]
  )
  const showsBlocks = coloring === 'blocks'
  const layer = useMemo<TileLayer>(
    () => ({ view: 'biomes', preview: null, heights: true, pickNoise: false, blocks: showsBlocks ? 'generated' : null, revision: packRevision }),
    [packRevision, showsBlocks]
  )
  const style = useMemo<TerrainStyle<MapTile>>(
    () => ({
      surfaceOf: (tile, place) => new MapTerrain(tile, place, coloring, world.fluidHeight, art.atlas ?? undefined),
      floorY: world.minY,
      topY: world.minY + world.height
    }),
    [coloring, world.fluidHeight, world.minY, world.height, art]
  )
  const { content, pending, stream } = useTerrainStream(source, COARSEST_LEVEL, focus, layer, style, tell)
  const sampling = useLongPending(pending)
  const ground = useGroundInView(stream)
  const heightShares = useMemo(() => heightBandShares(ground?.heights ?? [], world.fluidHeight), [ground, world.fluidHeight])
  const inspectsPicks = coloring !== 'height'
  const pickView = coloring === 'regions' ? 'regions' : 'biomes'
  const pickLayer = useMemo<MapLayerQuery>(() => ({ view: pickView, preview: null }), [pickView])
  const area = useAreaOf(ground)
  const inspection = usePickInspection(client, pickLayer, area, inspectsPicks, packRevision)

  useEffect(() => {
    onSummary(`Around ${NUMBER.format(centre.x)}, ${NUMBER.format(centre.z)}`)
    return () => onSummary(null)
  }, [centre, onSummary])

  return (
    <>
      <ViewIsland
        title={title}
        tools={
          <ToggleGroup type="single" size="sm" className="gap-0.5" value={coloring} onValueChange={(value) => value !== '' && setColoring(value as MapTerrainColoring)}>
            <ToggleGroupItem value="biomes" className={HEADER_CHOICE}>
              Biomes
            </ToggleGroupItem>
            <ToggleGroupItem value="regions" className={HEADER_CHOICE}>
              Regions
            </ToggleGroupItem>
            <ToggleGroupItem value="height" className={HEADER_CHOICE}>
              Height
            </ToggleGroupItem>
            <ToggleGroupItem value="blocks" className={HEADER_CHOICE} title="The blocks the world generates, shown close up">
              Blocks
            </ToggleGroupItem>
          </ToggleGroup>
        }
      >
        <SceneViewport content={content} framing={content} />
        {sampling && (
          <div className="pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-md border border-overlay-border bg-card px-3 py-1.5 text-[12px] text-secondary-text">
            Sampling the terrain
          </div>
        )}
      </ViewIsland>
      <Panel className="w-[250px]">
        {inspectsPicks ? (
          <PickInspector title={coloring === 'regions' ? 'Regions' : 'Biomes'} inspection={inspection} />
        ) : (
          <Legend title="Heights in view" shares={heightShares} emptyText="Looking at the ground…" className="flex-1" />
        )}
      </Panel>
    </>
  )
}

// The same box is the same area, so the measurement is not asked for again while only the heights in it change.
function useAreaOf(ground: GroundInView | null): WorldRect | null {
  const minX = ground?.area.minX
  const minZ = ground?.area.minZ
  const maxX = ground?.area.maxX
  const maxZ = ground?.area.maxZ
  return useMemo(() => (minX === undefined || minZ === undefined || maxX === undefined || maxZ === undefined ? null : { minX, minZ, maxX, maxZ }), [minX, minZ, maxX, maxZ])
}

function centreOf(area: WorldRect | null): { x: number; z: number } {
  if (area === null) {
    return { x: 0, z: 0 }
  }
  return { x: Math.round((area.minX + area.maxX) / 2), z: Math.round((area.minZ + area.maxZ) / 2) }
}
