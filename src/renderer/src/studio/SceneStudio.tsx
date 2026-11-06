import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { SceneEntry, SceneViewInfo } from '../bridge/types'
import { BlockModels } from '../scene/blockModels'
import { BridgeAssets } from '../scene/BridgeAssets'
import { INTS_PER_BLOCK, type BlockScene } from '../scene/sceneDecoding'
import { buildSceneMesh, type SceneMesh } from '../scene/sceneMesh'
import { SceneViewport } from './SceneViewport'

const NUMBER = new Intl.NumberFormat('en-US')
const DEFAULT_NAMESPACE_PREFIX = 'minecraft:'

interface SceneStudioProps {
  client: BridgeClient
  view: SceneViewInfo
  /** Changes when the pack reloads, so the entries and the scene are read again. */
  packRevision: number
  /** What the status bar says about the scene, or null while none is shown. */
  onSummary(summary: string | null): void
  tell(message: string): void
}

/** A scene view: its entries on the right, the chosen one in 3D in the middle, with the blocks it is made of. */
export function SceneStudio({ client, view, packRevision, onSummary, tell }: SceneStudioProps): React.JSX.Element {
  const [waiting, setWaiting] = useState<string | null>(null)
  const images = useMemo(() => new BridgeAssets(client, setWaiting), [client])
  const models = useMemo(() => new BlockModels(images), [images])
  const entries = useSceneEntries(client, view.id, packRevision, tell)
  const [chosen, setChosen] = useState<{ key: string; variant: number } | null>(null)
  const entry = entries.find((candidate) => candidate.key === chosen?.key) ?? entries[0] ?? null
  const variant = entry !== null && chosen?.key === entry.key ? Math.min(chosen.variant, entry.variants - 1) : 0
  const { scene, mesh, building } = useSceneMesh(client, view.id, entry, variant, packRevision, models, images, tell)
  const chooseVariant = (next: number): void => {
    if (entry !== null) {
      setChosen({ key: entry.key, variant: (next + entry.variants) % entry.variants })
    }
  }

  useEffect(() => onSummary(entry === null || scene === null ? null : summaryOf(entry, scene)), [entry, scene, onSummary])
  useEffect(() => () => onSummary(null), [onSummary])

  return (
    <>
      <div className="relative min-w-0 flex-1">
        <SceneViewport mesh={mesh} />
        {entry !== null && entry.variants > 1 && (
          <div className="absolute top-3 left-3 flex items-center gap-1 rounded-md border border-overlay-border bg-card/90 p-1 text-[12px] tabular-nums">
            <Button variant="ghost" size="icon-sm" title="Previous variant" onClick={() => chooseVariant(variant - 1)}>
              <ChevronLeft />
            </Button>
            <span className="px-1">
              Variant {variant + 1} of {entry.variants}
            </span>
            <Button variant="ghost" size="icon-sm" title="Next variant" onClick={() => chooseVariant(variant + 1)}>
              <ChevronRight />
            </Button>
          </div>
        )}
        {building && entry !== null && (
          <div className="pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-md border border-overlay-border bg-card/90 px-3 py-1.5 text-[12px] text-secondary-text">
            {waiting ?? `Building ${entry.name}`}
          </div>
        )}
      </div>
      <aside className="flex w-[250px] shrink-0 flex-col border-l bg-card">
        <EntryBrowser title={view.label} entries={entries} current={entry?.key ?? null} onSelect={(key) => setChosen({ key, variant: 0 })} />
        <BlockList scene={scene} />
      </aside>
    </>
  )
}

function summaryOf(entry: SceneEntry, scene: BlockScene): string {
  const [width, height, depth] = [0, 1, 2].map((axis) => scene.max[axis] - scene.min[axis])
  const blocks = scene.blocks.length / INTS_PER_BLOCK
  return `${entry.key} · ${NUMBER.format(blocks)} blocks · ${width} × ${height} × ${depth}`
}

function useSceneEntries(client: BridgeClient, viewId: string, packRevision: number, tell: (message: string) => void): SceneEntry[] {
  const [entries, setEntries] = useState<SceneEntry[]>([])
  useEffect(() => {
    let active = true
    client
      .sceneEntries(viewId)
      .then((found) => active && setEntries(found))
      .catch((failure: Error) => active && tell(failure.message))
    return () => {
      active = false
    }
  }, [client, viewId, packRevision, tell])
  return entries
}

/** The chosen entry's scene and its mesh; a newer choice abandons an older one, and a replaced mesh is freed. */
function useSceneMesh(
  client: BridgeClient,
  viewId: string,
  entry: SceneEntry | null,
  variant: number,
  packRevision: number,
  models: BlockModels,
  images: BridgeAssets,
  tell: (message: string) => void
): { scene: BlockScene | null; mesh: SceneMesh | null; building: boolean } {
  const [scene, setScene] = useState<BlockScene | null>(null)
  const [mesh, setMesh] = useState<SceneMesh | null>(null)
  const [building, setBuilding] = useState(false)
  const key = entry?.key ?? null

  useEffect(() => {
    if (key === null) {
      return
    }
    const stop = new AbortController()
    setBuilding(true)
    client
      .scene(viewId, key, variant, stop.signal)
      .then(async (found) => {
        const built = await buildSceneMesh(found, models, images)
        if (stop.signal.aborted) {
          disposeMesh(built)
          return
        }
        setScene(found)
        setMesh(built)
        if (built.unknownStates.length > 0) {
          tell(`No model for ${built.unknownStates.length === 1 ? built.unknownStates[0] : `${built.unknownStates.length} block states`}`)
        }
      })
      .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
      .finally(() => !stop.signal.aborted && setBuilding(false))
    return () => stop.abort()
  }, [client, viewId, key, variant, packRevision, models, images, tell])

  useEffect(() => () => disposeMesh(mesh), [mesh])

  return { scene, mesh, building }
}

function disposeMesh(mesh: SceneMesh | null): void {
  mesh?.geometry.dispose()
  mesh?.atlas.dispose()
}

interface EntryBrowserProps {
  title: string
  entries: SceneEntry[]
  current: string | null
  onSelect(key: string): void
}

/** The view's entries under their folders, with a search over names and folders (v3e ObjectBrowser). */
function EntryBrowser({ title, entries, current, onSelect }: EntryBrowserProps): React.JSX.Element {
  const [search, setSearch] = useState('')
  const wanted = search.trim().toLowerCase()
  const groups = useMemo(() => groupedEntries(entries, wanted), [entries, wanted])
  return (
    <section className="flex min-h-0 flex-[7] flex-col border-b">
      <header className="px-4 pt-2.5 pb-2.5">
        <h2 className="truncate text-[13px] font-semibold">{title}</h2>
        <p className="truncate text-[11.5px] text-secondary-text">{NUMBER.format(entries.length)} in the pack</p>
        <Input
          className="mt-2 h-7 text-[12px]"
          placeholder="Find by name or folder"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          onKeyDown={(event) => event.key === 'Escape' && setSearch('')}
        />
      </header>
      <ScrollArea className="min-h-0 flex-1 border-t">
        <div className="px-2 py-1.5">
          {groups.map(([group, members]) => (
            <section key={group}>
              {group !== '' && <h3 className="truncate px-2 pt-2 pb-1 text-[11px] font-semibold text-muted-foreground">{group}</h3>}
              {members.map((entry) => (
                <button
                  key={entry.key}
                  title={entry.key}
                  onClick={() => onSelect(entry.key)}
                  className={cn('flex h-[26px] w-full items-center rounded-md px-2 text-left', entry.key === current ? 'bg-selection' : 'hover:bg-accent')}
                >
                  <span className="truncate">{entry.name}</span>
                </button>
              ))}
            </section>
          ))}
          {groups.length === 0 && <p className="px-2 py-1 text-[12px] text-muted-foreground">{entries.length === 0 ? 'Nothing to show' : 'Nothing matches'}</p>}
        </div>
      </ScrollArea>
    </section>
  )
}

function groupedEntries(entries: SceneEntry[], wanted: string): [string, SceneEntry[]][] {
  const groups = new Map<string, SceneEntry[]>()
  for (const entry of entries) {
    if (wanted !== '' && !entry.key.toLowerCase().includes(wanted)) {
      continue
    }
    const members = groups.get(entry.group) ?? []
    members.push(entry)
    groups.set(entry.group, members)
  }
  return [...groups.entries()]
}

/** The blocks of the scene, most used first, each with its count (v3e ObjectOverviewPanel). */
function BlockList({ scene }: { scene: BlockScene | null }): React.JSX.Element {
  const counts = useMemo(() => (scene === null ? [] : blockCounts(scene)), [scene])
  return (
    <section className="flex min-h-0 flex-[3] flex-col">
      <h2 className="px-4 pt-2.5 pb-1.5 text-[11px] font-semibold text-muted-foreground">Blocks</h2>
      <ScrollArea className="min-h-0 flex-1">
        <div className="px-4 pb-2">
          {counts.map(([state, count]) => (
            <div key={state} title={state} className="flex h-[26px] items-center gap-2">
              <span className="min-w-0 flex-1 truncate">{state.replace(DEFAULT_NAMESPACE_PREFIX, '')}</span>
              <span className="shrink-0 text-[11.5px] text-secondary-text tabular-nums">{NUMBER.format(count)}</span>
            </div>
          ))}
        </div>
      </ScrollArea>
    </section>
  )
}

// Block states that differ only in how they face count as one block, as a builder reads the list.
function blockCounts(scene: BlockScene): [string, number][] {
  const counts = new Map<string, number>()
  for (let offset = 3; offset < scene.blocks.length; offset += INTS_PER_BLOCK) {
    const state = scene.palette[scene.blocks[offset]]
    const material = state.includes('[') ? state.slice(0, state.indexOf('[')) : state
    counts.set(material, (counts.get(material) ?? 0) + 1)
  }
  return [...counts.entries()].sort((first, second) => second[1] - first[1])
}
