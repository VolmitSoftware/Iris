import { useEffect, useMemo, useState } from 'react'
import { Toggle } from '@/components/ui/toggle'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import type { BridgeClient, MapLayerQuery, WorldRect } from '../bridge/BridgeClient'
import type { MapTile, WorldInfo } from '../bridge/types'
import { heightBandShares } from '../map/census'
import { mapTileSource, type TileLayer } from '../map/mapTileSource'
import type { GroundInView } from '../scene/groundInView'
import { blockArt } from '../scene/blockArt'
import { MapTerrain, type MapTerrainColoring } from '../scene/mapTerrain'
import { meshOfGeometry } from '../scene/terrainMesh'
import { TerrainMeshWorkers } from '../scene/terrainMeshWorkers'
import { focusAround, type TerrainStyle } from '../scene/terrainStream'
import { TILE_COLUMNS } from '../scene/terrainTiles'
import { Legend } from './Legend'
import { PickInspector } from './PickInspector'
import { SceneViewport } from './SceneViewport'
import { usePickInspection } from './usePickInspection'
import { useSpotActions } from './useSpotActions'
import { HEADER_CHOICE, Panel, SizeChoice, ViewIsland } from './Sidebar'
import { useGroundInView, useLongPending, useTerrainStream } from './useTerrainStream'
import { caveBandShares, useWorldCaves, type CaveArea } from './useWorldCaves'

const NUMBER = new Intl.NumberFormat('en-US')
// The map's coarsest tiles are 4096 blocks a pixel, which is level 12.
const COARSEST_LEVEL = 12
const BLOCKS_ABOVE_FLUID_IN_FOCUS = 96
const BLOCKS_BELOW_FLUID_IN_FOCUS = 32
// Every level carries blocks, out to the horizon: a coarse tile generates a chunk for each of its columns, a minute or
// so of the server's time, and the ground already sampled stays in sight meanwhile.
const MOST_BLOCKS_PER_COLUMN = 2 ** COARSEST_LEVEL
// Every chunk of the square is generated: 256 blocks across is 256 chunks, seconds of the server's time.
const CAVE_SIZES = [128, 256, 512]
const OPENING_CAVE_SIZE = 256

// Tiles with generated blocks take seconds to minutes each. The same tiles without them stand in, but only on ground
// where no tile with blocks of any level is known yet.
function withoutBlocks(layer: TileLayer): TileLayer | null {
  return layer.blocks === null ? null : { ...layer, blocks: null, shape: false }
}

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
  const source = useMemo(() => mapTileSource(client, TILE_COLUMNS, art, MOST_BLOCKS_PER_COLUMN), [client, art])
  const focus = useMemo(
    () => focusAround(centre.x, centre.z, world.fluidHeight - BLOCKS_BELOW_FLUID_IN_FOCUS, world.fluidHeight + BLOCKS_ABOVE_FLUID_IN_FOCUS),
    [centre, world.fluidHeight]
  )
  const showsBlocks = coloring === 'blocks'
  const layer = useMemo<TileLayer>(
    () => ({ view: 'biomes', preview: null, heights: true, pickNoise: false, blocks: showsBlocks ? 'generated' : null, shape: showsBlocks, revision: packRevision }),
    [packRevision, showsBlocks]
  )
  const meshWorkers = useMemo(() => new TerrainMeshWorkers(), [])
  useEffect(() => () => meshWorkers.dispose(), [meshWorkers])
  const style = useMemo<TerrainStyle<MapTile>>(
    () => ({
      surfaceOf: (tile, place) => new MapTerrain(tile, place, coloring, world.fluidHeight, art.atlas ?? undefined),
      floorY: world.minY,
      topY: world.minY + world.height,
      buildMesh: (tile, place, base, holes) =>
        meshWorkers
          .build({ tile, place, coloring, fluidHeight: world.fluidHeight, plain: art.atlas?.plain ?? null, base, holes })
          .then((geometry) => meshOfGeometry(geometry, art.atlas))
    }),
    [coloring, world.fluidHeight, world.minY, world.height, art, meshWorkers]
  )
  const { content, pending, stream } = useTerrainStream(source, COARSEST_LEVEL, focus, layer, style, tell, withoutBlocks)
  const sampling = useLongPending(pending)
  const ground = useGroundInView(stream)
  const heightShares = useMemo(() => heightBandShares(ground?.heights ?? [], world.fluidHeight), [ground, world.fluidHeight])
  const inspectsPicks = coloring !== 'height'
  const pickView = coloring === 'regions' ? 'regions' : 'biomes'
  const pickLayer = useMemo<MapLayerQuery>(() => ({ view: pickView, preview: null }), [pickView])
  const area = useAreaOf(ground)
  const inspection = usePickInspection(client, pickLayer, area, inspectsPicks, packRevision)
  const spotActions = useSpotActions(client, tell, true)
  const [showsCaves, setShowsCaves] = useState(false)
  const [caveArea, setCaveArea] = useState<CaveArea | null>(null)
  const caves = useWorldCaves(client, caveArea, packRevision, tell)
  const caveShares = useMemo(() => (caves.scene === null ? [] : caveBandShares(caves.scene)), [caves.scene])
  // The caves are generated around the middle of the ground in view when they are shown, or at another size there.
  const showCavesAt = (size: number): void => {
    const middle = area === null ? centre : { x: Math.round((area.minX + area.maxX) / 2), z: Math.round((area.minZ + area.maxZ) / 2) }
    setCaveArea((known) => (known !== null && known.centerX === middle.x && known.centerZ === middle.z && known.size === size ? known : { centerX: middle.x, centerZ: middle.z, size }))
    setShowsCaves(true)
  }

  const notice = showsCaves ? caves.status : sampling ? 'Sampling the terrain' : null

  useEffect(() => {
    const around = showsCaves && caveArea !== null ? { x: caveArea.centerX, z: caveArea.centerZ } : centre
    onSummary(`${showsCaves ? 'Caves around' : 'Around'} ${NUMBER.format(around.x)}, ${NUMBER.format(around.z)}`)
    return () => onSummary(null)
  }, [centre, showsCaves, caveArea, onSummary])

  return (
    <>
      <ViewIsland
        title={title}
        tools={
          <>
            {showsCaves ? (
              <SizeChoice sizes={CAVE_SIZES} size={caveArea?.size ?? OPENING_CAVE_SIZE} onSize={showCavesAt} />
            ) : (
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
                <ToggleGroupItem value="blocks" className={HEADER_CHOICE} title="The blocks the world generates">
                  Blocks
                </ToggleGroupItem>
              </ToggleGroup>
            )}
            <Toggle
              size="sm"
              className={HEADER_CHOICE}
              pressed={showsCaves}
              title="The caves of the generated chunks around the middle of the view, coloured by height"
              onPressedChange={(pressed) => (pressed ? showCavesAt(caveArea?.size ?? OPENING_CAVE_SIZE) : setShowsCaves(false))}
            >
              Caves
            </Toggle>
          </>
        }
      >
        {showsCaves ? (
          <SceneViewport content={caves.mesh?.content ?? null} framing={caves.scene} />
        ) : (
          <SceneViewport content={content} framing={content} spotActions={spotActions} />
        )}
        {notice !== null && (
          <div className="pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-md border border-overlay-border bg-card px-3 py-1.5 text-[12px] text-secondary-text">
            {notice}
          </div>
        )}
      </ViewIsland>
      <Panel className="w-[250px]">
        {showsCaves ? (
          <Legend title="Caves by height" shares={caveShares} emptyText={caves.status === null ? 'No caves here' : 'Generating the caves…'} className="flex-1" />
        ) : inspectsPicks ? (
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
