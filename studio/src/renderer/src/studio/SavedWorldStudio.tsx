import { IdeIcon } from '@/components/IdeIcon'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { cn } from '@/lib/utils'
import { BridgeError, STATUS_NOT_FOUND, type BridgeClient } from '../bridge/BridgeClient'
import type { SavedStart, SavedWorldInfo, WorldInfo } from '../bridge/types'
import { BlockColors } from '../scene/blockColors'
import { BlockLooks } from '../scene/blockLooks'
import { BlockModels } from '../scene/blockModels'
import { BridgeAssets } from '../scene/BridgeAssets'
import { PendingWork } from '../scene/pendingWork'
import { SavedTerrain, type ReadSavedTile, type SavedTerrainColoring } from '../scene/savedTerrain'
import { savedTileSource, type SavedLayer } from '../scene/savedTileSource'
import { TerrainAtlas } from '../scene/terrainAtlas'
import { focusAround, type TerrainStyle } from '../scene/terrainStream'
import { SceneViewport } from './SceneViewport'
import { SidebarPanel } from './Sidebar'
import { useTerrainStream } from './useTerrainStream'

const NUMBER = new Intl.NumberFormat('en-US')
// Saved tiles are read at up to 16 blocks a pixel, which is level 4.
const COARSEST_SAVED_LEVEL = 4
const COLORING_ITEM = 'h-7 px-3 text-[12px] data-[state=on]:bg-selection data-[state=on]:text-foreground'
const PILL = 'pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-md border border-overlay-border bg-card/90 px-3 py-1.5 text-[12px] text-secondary-text'

interface SavedWorldStudioProps {
  client: BridgeClient
  /** The studio's own world, whose saved copy is chosen first. */
  world: WorldInfo
  onSummary(summary: string | null): void
  /** How far the tiles being read have come, 0 to 1, or null when none are. */
  onProgress(progress: number | null): void
  tell(message: string): void
}

/** A world as its region files hold it, in 3D, around the region nearest its origin (v3e's saved world explorer). */
export function SavedWorldStudio({ client, world, onSummary, onProgress, tell }: SavedWorldStudioProps): React.JSX.Element {
  const worlds = useSavedWorlds(client, tell)
  const [chosenId, setChosenId] = useState<string | null>(null)
  const chosen = worlds.find((candidate) => candidate.id === chosenId) ?? ownWorldAmong(worlds, world) ?? worlds[0] ?? null
  const [coloring, setColoring] = useState<SavedTerrainColoring>('biomes')
  const [readings, setReadings] = useState(0)
  const { start, looking } = useSavedStart(client, chosen?.id ?? null, readings, tell)
  const { tileSource, atlas } = useMemo(() => {
    const images = new BridgeAssets(client, () => {})
    const models = new BlockModels(images)
    const pictures = new TerrainAtlas(images)
    return { tileSource: savedTileSource(client, new BlockColors(models, images), new BlockLooks(models, pictures)), atlas: pictures }
  }, [client])
  useEffect(() => () => atlas.dispose(), [atlas])
  const centerX = start?.centerX ?? 0
  const centerZ = start?.centerZ ?? 0
  const lowestGround = start?.lowestGround ?? 0
  const highestGround = start?.highestGround ?? 0
  const focus = useMemo(() => focusAround(centerX, centerZ, lowestGround, highestGround), [centerX, centerZ, lowestGround, highestGround])
  const layer = useMemo<SavedLayer | null>(() => (start === null || chosen === null ? null : { worldId: chosen.id, readings }), [start, chosen, readings])
  const seaLevel = start?.seaLevel ?? null
  const minY = start?.minY ?? null
  const style = useMemo<TerrainStyle<ReadSavedTile> | null>(
    () =>
      minY === null
        ? null
        : { surfaceOf: (read, place) => new SavedTerrain(read, place, coloring, seaLevel, atlas), floorY: minY, topY: minY + world.height },
    [minY, seaLevel, coloring, world.height, atlas]
  )
  const { content, pending } = useTerrainStream(tileSource, COARSEST_SAVED_LEVEL, focus, layer, style, tell)
  const reading = looking || pending > 0
  const tileWork = useRef(new PendingWork())

  useEffect(() => {
    const completion = tileWork.current.completion(pending)
    onProgress(pending > 0 ? completion : null)
  }, [pending, onProgress])
  useEffect(() => () => onProgress(null), [onProgress])

  useEffect(() => {
    if (chosen === null || start === null) {
      onSummary(null)
      return
    }
    onSummary(`${chosen.name} · around ${NUMBER.format(start.centerX)}, ${NUMBER.format(start.centerZ)}`)
  }, [chosen, start, onSummary])
  useEffect(() => () => onSummary(null), [onSummary])

  return (
    <>
      <div className="relative min-w-0 flex-1 overflow-hidden rounded-lg">
        <SceneViewport content={content} framing={content} />
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
            <IdeIcon name="refresh" />
          </Button>
        </div>
        {reading && chosen !== null && <div className={PILL}>Reading {chosen.name}</div>}
        {!reading && chosen !== null && start === null && <div className={PILL}>Nothing finished is saved in {chosen.name}</div>}
      </div>
      <SidebarPanel className="flex-1">
        <header className="px-4 pt-2.5 pb-2.5">
          <h2 className="text-[13px] font-semibold">Saved worlds</h2>
          <p className="text-[11.5px] text-secondary-text">{NUMBER.format(worlds.length)} on the server</p>
        </header>
        <ScrollArea className="min-h-0 flex-1 border-t">
          <WorldList worlds={worlds} current={chosen?.id ?? null} onSelect={setChosenId} />
        </ScrollArea>
      </SidebarPanel>
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

/**
 * Where the chosen world's view opens, null while it is looked up and where nothing finished is saved. Reading again
 * keeps the old start until the new one arrives, so the ground drawn stays.
 */
function useSavedStart(client: BridgeClient, worldId: string | null, readings: number, tell: (message: string) => void): { start: SavedStart | null; looking: boolean } {
  const [start, setStart] = useState<SavedStart | null>(null)
  const [looking, setLooking] = useState(false)
  useEffect(() => setStart(null), [worldId])
  useEffect(() => {
    if (worldId === null) {
      return
    }
    const stop = new AbortController()
    setLooking(true)
    client
      .savedStart(worldId, stop.signal)
      .then(setStart)
      .catch((failure: unknown) => {
        if (stop.signal.aborted) {
          return
        }
        if (failure instanceof BridgeError && failure.status === STATUS_NOT_FOUND) {
          setStart(null)
          return
        }
        tell(failure instanceof Error ? failure.message : 'The server did not answer')
      })
      .finally(() => !stop.signal.aborted && setLooking(false))
    return () => stop.abort()
  }, [client, worldId, readings, tell])
  return { start, looking }
}
