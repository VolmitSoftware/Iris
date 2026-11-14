import { useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import type { BridgeClient } from '../bridge/BridgeClient'
import { NO_PART, type JsonPath, type SceneEntry, type SceneViewInfo } from '../bridge/types'
import { parsedText } from '../editor/jsonValues'
import { nodeDrawingPart, nodeWithPath } from '../editor/nodeLayout'
import { SuggestionStore } from '../editor/suggestionStore'
import { useNodeEditor } from '../editor/useNodeEditor'
import { BlockColors } from '../scene/blockColors'
import { BlockIndex, blockAtHit, NO_BLOCK } from '../scene/blockPick'
import { BlockModels } from '../scene/blockModels'
import { BridgeAssets } from '../scene/BridgeAssets'
import { countFarLeaves } from '../scene/farLeaves'
import { blocksInPart, blocksOutsidePart } from '../scene/partFocus'
import { INTS_PER_BLOCK, type BlockScene } from '../scene/sceneDecoding'
import { buildSceneMesh, type SceneMesh } from '../scene/sceneMesh'
import { BakeMeter, useBakeTiming } from './BakeMeter'
import { BlockIcon } from './BlockIcon'
import { EntryBrowser } from './EntryBrowser'
import { useEntryFiles } from './EntryFileDialogs'
import { NodeInspector } from './NodeInspector'
import { NodePane } from './NodePane'
import { Panel, SidebarPanel, SizeChoice, Stepper, ViewIsland } from './Sidebar'
import { SceneViewport, type PickedSurface } from './SceneViewport'

const NUMBER = new Intl.NumberFormat('en-US')
const DEFAULT_NAMESPACE_PREFIX = 'minecraft:'
const FAR_LEAVES_HINT = 'Leaves more than 6 blocks from wood. Vanilla would let them decay, so they are made persistent.'

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
  const blockLooks = useMemo(() => new BlockColors(models, images), [models, images])
  const entries = useSceneEntries(client, view.id, packRevision, tell)
  const [chosen, setChosen] = useState<{ key: string; variant: number } | null>(null)
  const entry = entries.find((candidate) => candidate.key === chosen?.key) ?? entries[0] ?? null
  const variant = entry !== null && chosen?.key === entry.key ? Math.min(chosen.variant, entry.variants - 1) : 0
  const entryKeys = useMemo(() => entries.map((candidate) => candidate.key), [entries])
  const files = useEntryFiles(client, view.id, entryKeys, (key) => setChosen({ key, variant: 0 }), tell)
  const [farLeavesOnly, setFarLeavesOnly] = useState(false)
  const [chosenSize, setChosenSize] = useState<{ viewId: string; size: number } | null>(null)
  const size = chosenSize?.viewId === view.id ? chosenSize.size : (view.sizes?.opening ?? null)
  const chooseVariant = (next: number): void => {
    if (entry !== null) {
      setChosen({ key: entry.key, variant: (next + entry.variants) % entry.variants })
    }
  }

  const bakeTiming = useBakeTiming(client, view.id, entry?.key ?? null, view.baked, packRevision, tell)

  const editor = useNodeEditor(client, view.id, view.editable ? (entry?.key ?? null) : null, packRevision, tell)
  const [selectedPath, setSelectedPath] = useState<JsonPath | null>(null)
  const selected = editor.state?.roots != null && selectedPath !== null ? nodeWithPath(editor.state.roots, selectedPath) : null
  const values = useMemo(() => (editor.state === null ? undefined : parsedText(editor.state.text)), [editor.state])
  const suggestions = useMemo(() => new SuggestionStore((of) => client.suggestions(of)), [client, packRevision])

  const [pickedPart, setPickedPart] = useState(NO_PART)
  const highlightedPart = view.parts ? (selected?.partId ?? pickedPart) : NO_PART
  const { scene, shown, sceneParts, building } = useSceneMesh(client, view, entry, variant, size, packRevision, models, images, farLeavesOnly, highlightedPart, tell)
  const shownScene = shown?.scene ?? null
  const blockIndex = useMemo(() => (shownScene === null ? null : new BlockIndex(shownScene)), [shownScene])
  const shownParts = partsOf(sceneParts, shownScene)
  const pickPart = (picked: PickedSurface): void => {
    const choose = (part: number): void => {
      setPickedPart(part)
      setSelectedPath(part === NO_PART || editor.state?.roots == null ? null : (nodeDrawingPart(editor.state.roots, part)?.path ?? null))
    }
    const block = picked === null || blockIndex === null ? NO_BLOCK : blockAtHit(blockIndex, picked.point, picked.direction)
    if (block === NO_BLOCK) {
      choose(NO_PART)
    } else if (shownParts === null) {
      tell('Still finding which part placed each block')
    } else if (shownParts[block] === NO_PART) {
      choose(NO_PART)
      tell('That block belongs to no part')
    } else {
      choose(shownParts[block])
    }
  }

  const summaryParts = partsOf(sceneParts, scene)
  useEffect(
    () => onSummary(entry === null || scene === null ? null : summaryOf(entry, scene, summaryParts === null ? '' : partNote(summaryParts, highlightedPart))),
    [entry, scene, summaryParts, highlightedPart, onSummary]
  )
  useEffect(() => () => onSummary(null), [onSummary])
  useEffect(() => {
    setSelectedPath(null)
    setPickedPart(NO_PART)
  }, [entry?.key])

  return (
    <>
      <SidebarPanel className="flex-1">
        <EntryBrowser
          className="flex-1"
          title={view.label}
          entries={entries}
          current={entry?.key ?? null}
          onSelect={(key) => setChosen({ key, variant: 0 })}
          fileActions={view.creatable ? files.actions : undefined}
        />
        {files.dialogs}
      </SidebarPanel>
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <SceneArea
          title={view.label}
          entry={entry}
          variant={variant}
          sizes={view.sizes?.blocksAcross ?? null}
          size={size}
          shown={shown}
          status={building && entry !== null ? (waiting ?? `Building ${entry.name}`) : null}
          onVariant={chooseVariant}
          onSize={(next) => setChosenSize({ viewId: view.id, size: next })}
          onPick={view.parts ? pickPart : undefined}
        />
        {view.editable && entry !== null && (
          <NodePane
            editor={editor}
            entryKey={entry.key}
            selected={selected}
            onSelect={(node) => {
              setSelectedPath(node?.path ?? null)
              setPickedPart(NO_PART)
            }}
          />
        )}
      </div>
      <Panel className={view.editable ? 'w-[300px]' : 'w-[250px]'}>
        {view.baked && entry !== null && <BakeMeter timing={bakeTiming} />}
        {selected !== null && editor.state !== null ? (
          <NodeInspector key={JSON.stringify(selected.path)} node={selected} state={editor.state} values={values} suggestions={suggestions} onEdit={editor.edit} />
        ) : (
          <BlockList scene={scene} blocks={blockLooks} farLeavesOnly={farLeavesOnly} onFarLeavesOnly={setFarLeavesOnly} />
        )}
      </Panel>
    </>
  )
}

interface SceneAreaProps {
  /** What the island is called while no entry is chosen. */
  title: string
  entry: SceneEntry | null
  variant: number
  /** The sizes the scenes can be built at, or null when each entry has its own. */
  sizes: readonly number[] | null
  size: number | null
  shown: ShownScene | null
  /** What is being built, or null when nothing is. */
  status: string | null
  onVariant(variant: number): void
  onSize(size: number): void
  /** Called when a block is clicked; absent when the scene has no parts to pick. */
  onPick?(picked: PickedSurface): void
}

function SceneArea({ title, entry, variant, sizes, size, shown, status, onVariant, onSize, onPick }: SceneAreaProps): React.JSX.Element {
  return (
    <ViewIsland
      className="flex-[11]"
      title={entry === null ? title : `${title} · ${entry.name}`}
      tools={
        <>
          {sizes !== null && size !== null && <SizeChoice sizes={sizes} size={size} onSize={onSize} />}
          {entry !== null && entry.variants > 1 && (
            <Stepper
              label={`Variant ${variant + 1} of ${entry.variants}`}
              backHint="Previous variant"
              onHint="Next variant"
              onBack={() => onVariant(variant - 1)}
              onOn={() => onVariant(variant + 1)}
            />
          )}
        </>
      }
    >
      <SceneViewport content={shown?.mesh.content ?? null} framing={shown?.scene} onPick={onPick} />
        {status !== null && (
          <div className="pointer-events-none absolute top-3 left-1/2 -translate-x-1/2 rounded-md border border-overlay-border bg-card px-3 py-1.5 text-[12px] text-secondary-text">
            {status}
          </div>
        )}
    </ViewIsland>
  )
}

function summaryOf(entry: SceneEntry, scene: BlockScene, partNote: string): string {
  const [width, height, depth] = [0, 1, 2].map((axis) => scene.max[axis] - scene.min[axis])
  const blocks = scene.blocks.length / INTS_PER_BLOCK
  return `${entry.key} · ${NUMBER.format(blocks)} blocks${partNote} · ${width} × ${height} × ${depth}`
}

// A part that only carves has no blocks of its own, which is why nothing lights up.
function partNote(parts: Int32Array, part: number): string {
  if (part === NO_PART) {
    return ''
  }
  const blocks = blocksInPart(parts, part)
  return blocks === 0 ? ' · part has no blocks here' : ` · part has ${NUMBER.format(blocks)}`
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

interface ShownScene {
  scene: BlockScene
  mesh: SceneMesh
}

/** The part of each block of {@code scene}, when the parts were read for that scene. */
interface SceneParts {
  scene: BlockScene
  parts: Int32Array
}

function partsOf(sceneParts: SceneParts | null, scene: BlockScene | null): Int32Array | null {
  return sceneParts !== null && sceneParts.scene === scene ? sceneParts.parts : null
}

/**
 * The chosen entry's scene, and its mesh, with only the far leaves drawn when asked and every block outside the
 * highlighted part drawn faintly; a newer choice abandons an older one, and a replaced mesh is freed. Asking for the
 * far leaves or another part builds the mesh again from the scene already read. A view with parts also reads which
 * part placed each block, once the scene is in.
 */
function useSceneMesh(
  client: BridgeClient,
  view: SceneViewInfo,
  entry: SceneEntry | null,
  variant: number,
  size: number | null,
  packRevision: number,
  models: BlockModels,
  images: BridgeAssets,
  farLeavesOnly: boolean,
  highlightedPart: number,
  tell: (message: string) => void
): { scene: BlockScene | null; shown: ShownScene | null; sceneParts: SceneParts | null; building: boolean } {
  const [scene, setScene] = useState<BlockScene | null>(null)
  const [sceneParts, setSceneParts] = useState<SceneParts | null>(null)
  const [shown, setShown] = useState<ShownScene | null>(null)
  const [building, setBuilding] = useState(false)
  const unknownStatesTold = useRef<BlockScene | null>(null)
  const key = entry?.key ?? null
  const viewId = view.id
  const hasParts = view.parts

  useEffect(() => {
    if (key === null) {
      return
    }
    const stop = new AbortController()
    const place = { key, variant, size }
    setBuilding(true)
    client
      .scene(viewId, place, stop.signal)
      .then((found) => {
        if (stop.signal.aborted) {
          return
        }
        setScene(found)
        if (hasParts) {
          client
            .sceneParts(viewId, place, stop.signal)
            .then((parts) => !stop.signal.aborted && setSceneParts({ scene: found, parts }))
            .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
        }
      })
      .catch((failure: Error) => {
        if (stop.signal.aborted) {
          return
        }
        tell(failure.message)
        setBuilding(false)
      })
    return () => stop.abort()
  }, [client, viewId, hasParts, key, variant, size, packRevision, tell])

  const faint = useMemo(() => {
    const parts = partsOf(sceneParts, scene)
    return parts === null ? null : blocksOutsidePart(parts, highlightedPart)
  }, [sceneParts, scene, highlightedPart])

  useEffect(() => {
    if (scene === null) {
      return
    }
    const stop = new AbortController()
    setBuilding(true)
    buildSceneMesh(scene, models, images, farLeavesOnly, faint)
      .then((built) => {
        if (stop.signal.aborted) {
          disposeMesh(built)
          return
        }
        setShown({ scene, mesh: built })
        if (!farLeavesOnly && built.unknownStates.length > 0 && unknownStatesTold.current !== scene) {
          unknownStatesTold.current = scene
          tell(`No model for ${built.unknownStates.length === 1 ? built.unknownStates[0] : `${built.unknownStates.length} block states`}`)
        }
      })
      .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
      .finally(() => !stop.signal.aborted && setBuilding(false))
    return () => stop.abort()
  }, [scene, farLeavesOnly, faint, models, images, tell])

  useEffect(() => () => disposeMesh(shown?.mesh ?? null), [shown])

  return { scene, shown, sceneParts, building }
}

function disposeMesh(mesh: SceneMesh | null): void {
  mesh?.dispose()
}

interface BlockListProps {
  scene: BlockScene | null
  blocks: BlockColors
  farLeavesOnly: boolean
  onFarLeavesOnly(only: boolean): void
}

/** The blocks of the scene, most used first, each with its count (v3e ObjectOverviewPanel), and how many leaves are too far from wood. */
function BlockList({ scene, blocks, farLeavesOnly, onFarLeavesOnly }: BlockListProps): React.JSX.Element {
  const counts = useMemo(() => (scene === null ? [] : blockCounts(scene)), [scene])
  const farLeaves = useMemo(() => (scene === null ? 0 : countFarLeaves(scene)), [scene])
  return (
    <section className="flex min-h-0 flex-[3] flex-col">
      <h2 className="px-4 pt-2.5 pb-1.5 text-[11px] font-semibold text-muted-foreground">Blocks</h2>
      {farLeaves > 0 && (
        <div className="flex h-[26px] shrink-0 items-center gap-2 px-4 text-[11.5px]" title={FAR_LEAVES_HINT}>
          <span className="min-w-0 flex-1 truncate">
            {NUMBER.format(farLeaves)} {farLeaves === 1 ? 'leaf' : 'leaves'} too far from wood
          </span>
          <Button variant="link" size="xs" className="h-auto p-0 text-[11.5px]" title="Draw only the far leaves" onClick={() => onFarLeavesOnly(!farLeavesOnly)}>
            {farLeavesOnly ? 'Show all' : 'Show only'}
          </Button>
        </div>
      )}
      <ScrollArea className="min-h-0 flex-1">
        <div className="px-4 pb-2">
          {counts.map(({ material, example, count }) => (
            <div key={material} title={material} className="flex h-[26px] items-center gap-2">
              <BlockIcon block={example} blocks={blocks} />
              <span className="min-w-0 flex-1 truncate">{material.replace(DEFAULT_NAMESPACE_PREFIX, '')}</span>
              <span className="shrink-0 text-[11.5px] text-secondary-text tabular-nums">{NUMBER.format(count)}</span>
            </div>
          ))}
        </div>
      </ScrollArea>
    </section>
  )
}

interface BlockUse {
  material: string
  /** One full state of the block, so its icon has a model to come from. */
  example: string
  count: number
}

// Block states that differ only in how they face count as one block, as a builder reads the list.
function blockCounts(scene: BlockScene): BlockUse[] {
  const uses = new Map<string, BlockUse>()
  for (let offset = 3; offset < scene.blocks.length; offset += INTS_PER_BLOCK) {
    const state = scene.palette[scene.blocks[offset]]
    const material = state.includes('[') ? state.slice(0, state.indexOf('[')) : state
    const use = uses.get(material)
    if (use === undefined) {
      uses.set(material, { material, example: state, count: 1 })
    } else {
      use.count++
    }
  }
  return [...uses.values()].sort((first, second) => second.count - first.count)
}
