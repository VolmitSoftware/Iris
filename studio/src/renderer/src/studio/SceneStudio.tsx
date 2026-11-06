import { ChevronLeft, ChevronRight } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { JsonPath, SceneEntry, SceneViewInfo } from '../bridge/types'
import { parsedText } from '../editor/jsonValues'
import { nodeWithPath } from '../editor/nodeLayout'
import { useNodeEditor } from '../editor/useNodeEditor'
import { BlockModels } from '../scene/blockModels'
import { BridgeAssets } from '../scene/BridgeAssets'
import { INTS_PER_BLOCK, type BlockScene } from '../scene/sceneDecoding'
import { buildSceneMesh, type SceneMesh } from '../scene/sceneMesh'
import { EntryBrowser } from './EntryBrowser'
import { NodeInspector } from './NodeInspector'
import { NodePane } from './NodePane'
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

/**
 * A scene view: its entries on the right, the chosen one in 3D in the middle, with the blocks it is made of. An
 * editable view shows the entry's file as nodes under the scene, and the chosen node's settings on the right.
 */
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

  const editor = useNodeEditor(client, view.id, view.editable ? (entry?.key ?? null) : null, packRevision, tell)
  const [selectedPath, setSelectedPath] = useState<JsonPath | null>(null)
  const selected = editor.state?.roots != null && selectedPath !== null ? nodeWithPath(editor.state.roots, selectedPath) : null
  const values = useMemo(() => (editor.state === null ? undefined : parsedText(editor.state.text)), [editor.state])

  useEffect(() => onSummary(entry === null || scene === null ? null : summaryOf(entry, scene)), [entry, scene, onSummary])
  useEffect(() => () => onSummary(null), [onSummary])
  useEffect(() => setSelectedPath(null), [entry?.key])

  return (
    <>
      <div className="flex min-w-0 flex-1 flex-col">
        <SceneArea
          entry={entry}
          variant={variant}
          mesh={mesh}
          status={building && entry !== null ? (waiting ?? `Building ${entry.name}`) : null}
          onVariant={chooseVariant}
        />
        {view.editable && entry !== null && (
          <NodePane editor={editor} entryKey={entry.key} selected={selected} onSelect={(node) => setSelectedPath(node?.path ?? null)} />
        )}
      </div>
      <aside className={cn('flex shrink-0 flex-col border-l bg-card', view.editable ? 'w-[300px]' : 'w-[250px]')}>
        <EntryBrowser className={selected === null ? 'flex-[7]' : 'flex-[3]'} title={view.label} entries={entries} current={entry?.key ?? null} onSelect={(key) => setChosen({ key, variant: 0 })} />
        {selected !== null && editor.state !== null ? (
          <NodeInspector key={JSON.stringify(selected.path)} node={selected} state={editor.state} values={values} onEdit={editor.edit} />
        ) : (
          <BlockList scene={scene} />
        )}
      </aside>
    </>
  )
}

interface SceneAreaProps {
  entry: SceneEntry | null
  variant: number
  mesh: SceneMesh | null
  /** What is being built, or null when nothing is. */
  status: string | null
  onVariant(variant: number): void
}

function SceneArea({ entry, variant, mesh, status, onVariant }: SceneAreaProps): React.JSX.Element {
  return (
    <div className="relative min-h-0 flex-[11]">
      <SceneViewport content={mesh?.content ?? null} framing={mesh} />
        {entry !== null && entry.variants > 1 && (
          <div className="absolute top-3 left-3 flex items-center gap-1 rounded-md border border-overlay-border bg-card/90 p-1 text-[12px] tabular-nums">
            <Button variant="ghost" size="icon-sm" title="Previous variant" onClick={() => onVariant(variant - 1)}>
              <ChevronLeft />
            </Button>
            <span className="px-1">
              Variant {variant + 1} of {entry.variants}
            </span>
            <Button variant="ghost" size="icon-sm" title="Next variant" onClick={() => onVariant(variant + 1)}>
              <ChevronRight />
            </Button>
          </div>
        )}
        {status !== null && (
          <div className="pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-md border border-overlay-border bg-card/90 px-3 py-1.5 text-[12px] text-secondary-text">
            {status}
          </div>
        )}
    </div>
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
  mesh?.dispose()
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
