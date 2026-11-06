import { RotateCw } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { cn } from '@/lib/utils'
import { BridgeError, STATUS_NOT_FOUND, type BridgeClient } from '../bridge/BridgeClient'
import type { SavedStart, SavedTile, SavedWorldInfo, WorldInfo } from '../bridge/types'
import { BlockColors } from '../scene/blockColors'
import { BlockModels } from '../scene/blockModels'
import { BridgeAssets } from '../scene/BridgeAssets'
import { SavedTerrain, type SavedTerrainArea, type SavedTerrainColoring } from '../scene/savedTerrain'
import { buildTerrainMesh } from '../scene/terrainMesh'
import { SceneViewport } from './SceneViewport'

const NUMBER = new Intl.NumberFormat('en-US')
const TILES_PER_SIDE = 4
const TILE_PIXELS = 128
const BLOCKS_PER_PIXEL = 2
const TILE_BLOCKS = TILE_PIXELS * BLOCKS_PER_PIXEL
const NO_COLORS = new Map<string, number>()
const COLORING_ITEM = 'h-7 px-3 text-[12px] data-[state=on]:bg-selection data-[state=on]:text-foreground'
const PILL = 'pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-md border border-overlay-border bg-card/90 px-3 py-1.5 text-[12px] text-secondary-text'

interface SavedWorldStudioProps {
  client: BridgeClient
  /** The studio's own world, whose saved copy is chosen first. */
  world: WorldInfo
  onSummary(summary: string | null): void
  tell(message: string): void
}

/** A world as its region files hold it, in 3D, around the region nearest its origin (v3e's saved world explorer). */
export function SavedWorldStudio({ client, world, onSummary, tell }: SavedWorldStudioProps): React.JSX.Element {
  const worlds = useSavedWorlds(client, tell)
  const [chosenId, setChosenId] = useState<string | null>(null)
  const chosen = worlds.find((candidate) => candidate.id === chosenId) ?? ownWorldAmong(worlds, world) ?? worlds[0] ?? null
  const [coloring, setColoring] = useState<SavedTerrainColoring>('biomes')
  const [readings, setReadings] = useState(0)
  const { start, area, reading } = useSavedArea(client, chosen?.id ?? null, readings, tell)
  const blockColors = useBlockColors(client, coloring === 'blocks' ? area : null)
  const mesh = useMemo(() => {
    if (area === null || start === null || (coloring === 'blocks' && blockColors === null)) {
      return null
    }
    return buildTerrainMesh(new SavedTerrain(area, coloring, start.seaLevel, blockColors ?? NO_COLORS))
  }, [area, start, coloring, blockColors])

  useEffect(() => () => mesh?.dispose(), [mesh])
  useEffect(() => {
    if (chosen === null || start === null) {
      onSummary(null)
      return
    }
    const side = NUMBER.format(TILES_PER_SIDE * TILE_BLOCKS)
    onSummary(`${chosen.name} · ${side} × ${side} blocks around ${NUMBER.format(start.centerX)}, ${NUMBER.format(start.centerZ)}`)
  }, [chosen, start, onSummary])
  useEffect(() => () => onSummary(null), [onSummary])

  return (
    <>
      <div className="relative min-w-0 flex-1">
        <SceneViewport content={mesh?.content ?? null} framing={area} />
        <div className="absolute top-3 left-3 flex items-center gap-2">
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            className="bg-card/90"
            value={coloring}
            onValueChange={(value) => value !== '' && setColoring(value as SavedTerrainColoring)}
          >
            <ToggleGroupItem value="biomes" className={COLORING_ITEM}>
              Biomes
            </ToggleGroupItem>
            <ToggleGroupItem value="blocks" className={COLORING_ITEM}>
              Blocks
            </ToggleGroupItem>
            <ToggleGroupItem value="height" className={COLORING_ITEM}>
              Height
            </ToggleGroupItem>
          </ToggleGroup>
          <Button variant="outline" size="icon-sm" className="bg-card/90" title="Read again" disabled={reading} onClick={() => setReadings((count) => count + 1)}>
            <RotateCw />
          </Button>
        </div>
        {reading && chosen !== null && <div className={PILL}>Reading {chosen.name}</div>}
        {!reading && chosen !== null && start === null && <div className={PILL}>Nothing finished is saved in {chosen.name}</div>}
      </div>
      <aside className="flex w-[250px] shrink-0 flex-col border-l bg-card">
        <header className="px-4 pt-2.5 pb-2.5">
          <h2 className="text-[13px] font-semibold">Saved worlds</h2>
          <p className="text-[11.5px] text-secondary-text">{NUMBER.format(worlds.length)} on the server</p>
        </header>
        <ScrollArea className="min-h-0 flex-1 border-t">
          <WorldList worlds={worlds} current={chosen?.id ?? null} onSelect={setChosenId} />
        </ScrollArea>
      </aside>
    </>
  )
}

function WorldList({ worlds, current, onSelect }: { worlds: SavedWorldInfo[]; current: string | null; onSelect(id: string): void }): React.JSX.Element {
  const namespaces = [...new Set(worlds.map((world) => world.namespace))]
  return (
    <div className="px-2 py-1.5">
      {namespaces.map((namespace) => (
        <section key={namespace}>
          <h3 className="truncate px-2 pt-2 pb-1 text-[11px] font-semibold text-muted-foreground">{namespace}</h3>
          {worlds
            .filter((world) => world.namespace === namespace)
            .map((world) => (
              <button
                key={world.id}
                title={world.id}
                onClick={() => onSelect(world.id)}
                className={cn('flex h-[26px] w-full items-center rounded-md px-2 text-left', world.id === current ? 'bg-selection' : 'hover:bg-accent')}
              >
                <span className="truncate">{world.name}</span>
              </button>
            ))}
        </section>
      ))}
      {worlds.length === 0 && <p className="px-2 py-1 text-[12px] text-muted-foreground">Nothing to show</p>}
    </div>
  )
}

// The studio's world folder is named after its dimension, so world_iris_procfix is saved as iris/procfix.
function ownWorldAmong(worlds: SavedWorldInfo[], world: WorldInfo): SavedWorldInfo | undefined {
  return worlds.find((candidate) => world.world.endsWith(`_${candidate.name}`) || world.world === candidate.name)
}

function useSavedWorlds(client: BridgeClient, tell: (message: string) => void): SavedWorldInfo[] {
  const [worlds, setWorlds] = useState<SavedWorldInfo[]>([])
  useEffect(() => {
    let active = true
    client
      .savedWorlds()
      .then((found) => active && setWorlds(found))
      .catch((failure: Error) => active && tell(failure.message))
    return () => {
      active = false
    }
  }, [client, tell])
  return worlds
}

/** Where the chosen world's view opens and the square of tiles around it; every tile arrives before it is shown. */
function useSavedArea(
  client: BridgeClient,
  worldId: string | null,
  readings: number,
  tell: (message: string) => void
): { start: SavedStart | null; area: SavedTerrainArea | null; reading: boolean } {
  const [start, setStart] = useState<SavedStart | null>(null)
  const [area, setArea] = useState<SavedTerrainArea | null>(null)
  const [reading, setReading] = useState(false)
  useEffect(() => {
    if (worldId === null) {
      return
    }
    const stop = new AbortController()
    setReading(true)
    setStart(null)
    setArea(null)
    readSavedArea(client, worldId, stop.signal)
      .then((found) => {
        setStart(found?.start ?? null)
        setArea(found?.area ?? null)
      })
      .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
      .finally(() => !stop.signal.aborted && setReading(false))
    return () => stop.abort()
  }, [client, worldId, readings, tell])
  return { start, area, reading }
}

async function readSavedArea(client: BridgeClient, worldId: string, signal: AbortSignal): Promise<{ start: SavedStart; area: SavedTerrainArea } | null> {
  let start: SavedStart
  try {
    start = await client.savedStart(worldId, signal)
  } catch (failure) {
    if (failure instanceof BridgeError && failure.status === STATUS_NOT_FOUND) {
      return null
    }
    throw failure
  }
  const firstTileX = Math.floor(start.centerX / TILE_BLOCKS - TILES_PER_SIDE / 2)
  const firstTileZ = Math.floor(start.centerZ / TILE_BLOCKS - TILES_PER_SIDE / 2)
  const tiles: SavedTile[][] = await Promise.all(
    Array.from({ length: TILES_PER_SIDE }, (_, row) =>
      Promise.all(
        Array.from({ length: TILES_PER_SIDE }, (_, column) =>
          client.savedTile(worldId, firstTileX + column, firstTileZ + row, BLOCKS_PER_PIXEL, TILE_PIXELS, signal)
        )
      )
    )
  )
  return { start, area: { tiles, originX: firstTileX * TILE_BLOCKS, originZ: firstTileZ * TILE_BLOCKS } }
}

/** The colour of every block in the area, read from the client's textures, or null until they are read. */
function useBlockColors(client: BridgeClient, area: SavedTerrainArea | null): Map<string, number> | null {
  const colors = useMemo(() => {
    const images = new BridgeAssets(client, () => {})
    return new BlockColors(new BlockModels(images), images)
  }, [client])
  const [read, setRead] = useState<Map<string, number> | null>(null)
  useEffect(() => {
    setRead(null)
    if (area === null) {
      return
    }
    let active = true
    const blocks = area.tiles.flat().flatMap((tile) => tile.blockNames)
    colors.colorsOf(blocks).then((found) => active && setRead(found))
    return () => {
      active = false
    }
  }, [colors, area])
  return read
}
