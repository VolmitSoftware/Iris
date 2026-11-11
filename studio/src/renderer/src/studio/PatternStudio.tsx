import { IdeIcon } from '@/components/IdeIcon'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { ModuleViewInfo, PatternEntry } from '../bridge/types'
import { createPatternView, type PatternBlocks, type PatternLayer, type PatternStatus, type PatternView } from '../pattern/patternTiles'
import { BlockColors } from '../scene/blockColors'
import { BlockModels } from '../scene/blockModels'
import { BridgeAssets } from '../scene/BridgeAssets'
import { BlockIcon } from './BlockIcon'
import { EntryBrowser } from './EntryBrowser'
import { Panel, SidebarPanel, Stepper, ViewIsland } from './Sidebar'
import { TiledCanvas } from './TiledCanvas'

const DEFAULT_NAMESPACE_PREFIX = 'minecraft:'
// A layer thinner than the depth shown, or a palette with no block for the spot.
const NONE_LABEL = 'nothing at this depth'

/** The blocks the shown layer's tiles placed so far by name, each with one state to draw its icon as, and whether some spots got none. */
interface LegendBlocks {
  examples: Map<string, string>
  someNone: boolean
}

const EMPTY_LEGEND: LegendBlocks = { examples: new Map(), someNone: false }
const PILL = 'pointer-events-none absolute rounded-md border border-overlay-border bg-card px-3 py-1.5 text-[12px] text-secondary-text'

interface PatternStudioProps {
  client: BridgeClient
  view: ModuleViewInfo
  /** Changes when the pack reloads, so the entries and the tiles are read again. */
  packRevision: number
  onSummary(summary: string | null): void
  tell(message: string): void
}

/** A pattern view: its entries on the right, what the chosen one places from above in the middle (v3e palette explorer). */
export function PatternStudio({ client, view, packRevision, onSummary, tell }: PatternStudioProps): React.JSX.Element {
  const entries = usePatternEntries(client, view.id, packRevision, tell)
  const [chosen, setChosen] = useState<{ key: string; depth: number } | null>(null)
  const entry = entries.find((candidate) => candidate.key === chosen?.key) ?? entries[0] ?? null
  const depth = entry !== null && chosen?.key === entry.key ? Math.min(chosen.depth, entry.depths - 1) : 0
  const layer = useMemo<PatternLayer | null>(
    () => (entry === null ? null : { viewId: view.id, key: entry.key, depth, revision: packRevision }),
    [view.id, entry, depth, packRevision]
  )
  const [status, setStatus] = useState<PatternStatus | null>(null)
  const { legend, blockColors, createView } = usePatternView(client, layer, setStatus, tell)
  const viewRef = useRef<PatternView | null>(null)

  useEffect(() => viewRef.current?.show(layer, 'blocks'), [layer])
  useEffect(() => onSummary(entry === null ? null : `${entry.group} · ${entry.name}`), [entry, onSummary])
  useEffect(() => () => onSummary(null), [onSummary])

  const chooseDepth = (next: number): void => {
    if (entry !== null) {
      setChosen({ key: entry.key, depth: (next + entry.depths) % entry.depths })
    }
  }

  return (
    <>
      <ViewIsland
        title={entry === null ? view.label : `${view.label} · ${entry.name}`}
        tools={
          <>
            {entry !== null && entry.depths > 1 && (
              <Stepper label={`Depth ${depth + 1} of ${entry.depths}`} backHint="Higher" onHint="Deeper" onBack={() => chooseDepth(depth - 1)} onOn={() => chooseDepth(depth + 1)} />
            )}
            <Button variant="ghost" size="icon-sm" className="size-7" title="Back to 0, 0" onClick={() => viewRef.current?.resetView()}>
              <IdeIcon name="reset" />
            </Button>
          </>
        }
      >
        <TiledCanvas create={createView} viewRef={viewRef} className="absolute inset-0 size-full cursor-grab active:cursor-grabbing" />
        {status !== null && entry !== null && <div className={cn(PILL, 'right-3 bottom-3 tabular-nums')}>{readout(status)}</div>}
      </ViewIsland>
      <SidebarPanel className="flex-1">
        <EntryBrowser className="flex-1" title={view.label} entries={entries} current={entry?.key ?? null} onSelect={(key) => setChosen({ key, depth: 0 })} />
      </SidebarPanel>
      <Panel className="w-[250px]">
        <EntryFacts entry={entry} />
        <Legend blocks={legend} looks={blockColors} />
      </Panel>
    </>
  )
}

function readout(status: PatternStatus): string {
  const spot = `${Math.floor(status.cursorX)}, ${Math.floor(status.cursorZ)}`
  switch (status.spot.kind) {
    case 'block':
      return `${spot} · ${shortName(status.spot.block)}`
    case 'none':
      return `${spot} · ${NONE_LABEL}`
    case 'unknown':
      return spot
  }
}

/** The block without its namespace when that is Minecraft's, and without its state, as a builder names it. */
function shortName(block: string): string {
  const material = block.includes('[') ? block.slice(0, block.indexOf('[')) : block
  return material.startsWith(DEFAULT_NAMESPACE_PREFIX) ? material.slice(DEFAULT_NAMESPACE_PREFIX.length) : material
}

function usePatternEntries(client: BridgeClient, viewId: string, packRevision: number, tell: (message: string) => void): PatternEntry[] {
  const [entries, setEntries] = useState<PatternEntry[]>([])
  useEffect(() => {
    let active = true
    client
      .patternEntries(viewId)
      .then((found) => active && setEntries(found))
      .catch((failure: Error) => active && tell(failure.message))
    return () => {
      active = false
    }
  }, [client, viewId, packRevision, tell])
  return entries
}

/** The view's maker for the canvas, and the blocks the shown layer's tiles placed so far with their colours. */
function usePatternView(
  client: BridgeClient,
  layer: PatternLayer | null,
  onStatus: (status: PatternStatus) => void,
  tell: (message: string) => void
): { legend: LegendBlocks; blockColors: BlockColors; createView(canvas: HTMLCanvasElement): PatternView } {
  const blockColors = useMemo(() => {
    const images = new BridgeAssets(client, () => {})
    return new BlockColors(new BlockModels(images), images)
  }, [client])
  const [legend, setLegend] = useState<LegendBlocks>(EMPTY_LEGEND)
  const latest = useRef({ layer, onStatus, tell })
  latest.current = { layer, onStatus, tell }

  useEffect(() => setLegend(EMPTY_LEGEND), [layer])

  const createView = useCallback(
    (canvas: HTMLCanvasElement) =>
      createPatternView(canvas, client, blockColors, {
        onStatus: (status) => latest.current.onStatus(status),
        onBlocks: (tileLayer, blocks) => tileLayer === latest.current.layer && setLegend((shown) => withBlocks(shown, blocks)),
        onError: (message) => latest.current.tell(message)
      }),
    [client, blockColors]
  )
  return { legend, blockColors, createView }
}

/** The legend with the tile's blocks added, one row per block whatever its state; unchanged when it had them all. */
function withBlocks(shown: LegendBlocks, tile: PatternBlocks): LegendBlocks {
  const added = [...tile.drawnAs].map(([block, drawnAs]) => [shortName(block), drawnAs] as const).filter(([name]) => !shown.examples.has(name))
  const someNone = shown.someNone || tile.someNone
  if (added.length === 0 && someNone === shown.someNone) {
    return shown
  }
  return { examples: new Map([...added, ...shown.examples]), someNone }
}

function EntryFacts({ entry }: { entry: PatternEntry | null }): React.JSX.Element | null {
  if (entry === null) {
    return null
  }
  return (
    <section className="border-b px-4 pt-2.5 pb-2.5">
      <h2 className="truncate text-[11px] font-semibold text-muted-foreground" title={entry.key}>
        {entry.group} · {entry.name}
      </h2>
      {entry.facts.map((fact) => (
        <p key={fact} className="pt-1 text-[12px] leading-snug text-secondary-text">
          {fact}
        </p>
      ))}
    </section>
  )
}

/** The blocks the shown tiles place, each with its texture. */
function Legend({ blocks, looks }: { blocks: LegendBlocks; looks: BlockColors }): React.JSX.Element {
  const sorted = [...blocks.examples].sort(([first], [second]) => first.localeCompare(second))
  return (
    <section className="flex min-h-0 flex-[3] flex-col">
      <h2 className="px-4 pt-2.5 pb-1.5 text-[11px] font-semibold text-muted-foreground">Blocks</h2>
      <ScrollArea className="min-h-0 flex-1">
        <div className="px-4 pb-2">
          {sorted.map(([block, example]) => (
            <div key={block} className="flex h-[26px] items-center gap-2">
              <BlockIcon block={example} blocks={looks} />
              <span className="min-w-0 flex-1 truncate">{block}</span>
            </div>
          ))}
          {blocks.someNone && (
            <div className="flex h-[26px] items-center gap-2 text-secondary-text">
              <span className="size-4 shrink-0 rounded-[2px] border border-foreground/25 bg-map-background" />
              <span className="min-w-0 flex-1 truncate">Nothing at this depth</span>
            </div>
          )}
        </div>
      </ScrollArea>
    </section>
  )
}
