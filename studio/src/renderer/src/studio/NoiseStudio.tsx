import { Dices, RotateCcw, Search } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ScrollArea } from '@/components/ui/scroll-area'
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group'
import { cn } from '@/lib/utils'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { NoiseSource, NoiseTile } from '../bridge/types'
import type { TiledFraming as TerrainFraming } from '../map/TiledView'
import { NOISE_PALETTES, noiseScale, type NoisePaletteId, type NoiseScale } from '../noise/noiseScale'
import { NoiseTerrain } from '../noise/noiseTerrain'
import { createNoiseView, noiseTileSource, type NoiseLayer, type NoiseStatus, type NoiseView } from '../noise/noiseTiles'
import { focusAround, type TerrainStyle } from '../scene/terrainStream'
import { hexColor } from './colors'
import { SceneViewport } from './SceneViewport'
import { TiledCanvas } from './TiledCanvas'
import { Panel, SidebarPanel } from './Sidebar'
import { useTerrainStream } from './useTerrainStream'

type NoiseDisplay = 'flat' | 'terrain'

const NUMBER = new Intl.NumberFormat('en-US', { maximumFractionDigits: 3 })
const DEFAULT_SEED = '12345'
// Noise tiles are asked for down to 4096 blocks a value, which is level 12.
const COARSEST_NOISE_LEVEL = 12
const LOWEST_FOCUS_BLOCKS = -32
const LOWEST_RELIEF_BLOCKS = 1
const TALLEST_RELIEF_BLOCKS = 4064
const DEFAULT_RELIEF_BLOCKS = 64
const RELIEF_STEPS = 1000
const SWATCH_STEPS = 24
const TOGGLE_ITEM = 'h-7 px-3 text-[12px] data-[state=on]:bg-selection data-[state=on]:text-foreground'
const PILL = 'pointer-events-none absolute rounded-md border border-overlay-border bg-card/90 px-3 py-1.5 text-[12px] text-secondary-text'

interface NoiseStudioProps {
  client: BridgeClient
  packRevision: number
  onSummary(summary: string | null): void
  tell(message: string): void
}

/** The pack's noise and the built-in styles, from above or as terrain (v3e's noise explorer). */
export function NoiseStudio({ client, packRevision, onSummary, tell }: NoiseStudioProps): React.JSX.Element {
  const sources = useNoiseSources(client, packRevision, tell)
  const [chosenId, setChosenId] = useState<string | null>(null)
  const source = sources.find((candidate) => candidate.id === chosenId) ?? sources[0] ?? null
  const [seed, setSeed] = useState(DEFAULT_SEED)
  const [paletteId, setPaletteId] = useState<NoisePaletteId>('terrain')
  const [display, setDisplay] = useState<NoiseDisplay>('flat')
  const [reliefBlocks, setReliefBlocks] = useState(DEFAULT_RELIEF_BLOCKS)
  const [status, setStatus] = useState<NoiseStatus | null>(null)
  const [terrainFraming, setTerrainFraming] = useState<TerrainFraming | null>(null)
  const controllerRef = useRef<NoiseView | null>(null)
  const scale = useMemo(() => noiseScale(paletteId, source), [paletteId, source])
  const latestTell = useRef(tell)
  latestTell.current = tell
  const createView = useCallback(
    (canvas: HTMLCanvasElement) => createNoiseView(canvas, client, { onStatus: setStatus, onError: (message) => latestTell.current(message) }),
    [client]
  )

  useEffect(() => {
    const layer = source === null ? null : { sourceId: source.id, seed, revision: packRevision }
    controllerRef.current?.show(layer, { key: `${paletteId}/${source?.blockRange?.lowest}/${source?.blockRange?.highest}`, scale })
  }, [source, seed, packRevision, paletteId, scale])

  const showAs = useCallback((next: NoiseDisplay) => {
    setDisplay(next)
    if (next === 'terrain') {
      setTerrainFraming(controllerRef.current?.framing() ?? null)
    }
  }, [])

  useEffect(() => {
    if (source === null) {
      onSummary(null)
      return
    }
    const seedText = source.usesSeed ? `seed ${seed}` : "the world's seed"
    onSummary(`${source.name} · ${seedText}`)
  }, [source, seed, onSummary])
  useEffect(() => () => onSummary(null), [onSummary])

  return (
    <>
      <div className="relative min-w-0 flex-1 overflow-hidden rounded-lg">
        <TiledCanvas
          create={createView}
          viewRef={controllerRef}
          className={cn('absolute inset-0 size-full cursor-grab active:cursor-grabbing', display !== 'flat' && 'invisible')}
        />
        {display === 'terrain' && source !== null && terrainFraming !== null && (
          <NoiseTerrainView client={client} source={source} seed={seed} revision={packRevision} framing={terrainFraming} scale={scale} reliefBlocks={reliefBlocks} tell={tell} />
        )}
        <div className="absolute top-3 left-3 flex items-center gap-2">
          <ToggleGroup type="single" variant="outline" size="sm" className="bg-card/90" value={display} onValueChange={(value) => value !== '' && showAs(value as NoiseDisplay)}>
            <ToggleGroupItem value="flat" className={TOGGLE_ITEM} title="The noise from above, coloured by value">
              2D
            </ToggleGroupItem>
            <ToggleGroupItem value="terrain" className={TOGGLE_ITEM} title="The noise as terrain">
              3D
            </ToggleGroupItem>
          </ToggleGroup>
          {display === 'flat' && (
            <Button variant="outline" size="icon-sm" className="bg-card/90" title="Back to 0, 0" onClick={() => controllerRef.current?.resetView()}>
              <RotateCcw />
            </Button>
          )}
          {display === 'terrain' && source?.blockRange === null && <ReliefControl blocks={reliefBlocks} onChange={setReliefBlocks} />}
        </div>
        {display === 'flat' && status !== null && source !== null && <div className={cn(PILL, 'right-3 bottom-3 tabular-nums')}>{readout(status, source)}</div>}
      </div>
      <SidebarPanel className="flex-1">
        <header className="px-4 pt-2.5 pb-2.5">
          <h2 className="text-[13px] font-semibold">Noise</h2>
          <p className="text-[11.5px] text-secondary-text">{NUMBER.format(sources.length)} sources</p>
        </header>
        <SourceList sources={sources} current={source?.id ?? null} onSelect={setChosenId} />
      </SidebarPanel>
      <Panel className="w-[250px]">
        <SeedField seed={seed} usesSeed={source?.usesSeed ?? true} onChange={setSeed} />
        <PaletteList current={paletteId} source={source} scale={scale} onSelect={setPaletteId} />
      </Panel>
    </>
  )
}

function readout(status: NoiseStatus, source: NoiseSource): string {
  const spot = `${Math.floor(status.cursorX)}, ${Math.floor(status.cursorZ)}`
  if (status.value === null) {
    return spot
  }
  const value = NUMBER.format(status.value)
  return `${spot} · ${source.blockRange === null ? value : `${value} blocks`}`
}

function SeedField({ seed, usesSeed, onChange }: { seed: string; usesSeed: boolean; onChange(seed: string): void }): React.JSX.Element {
  const [draft, setDraft] = useState(seed)
  useEffect(() => setDraft(seed), [seed])
  const commit = (): void => {
    const trimmed = draft.trim()
    if (/^-?\d{1,19}$/.test(trimmed) && isLong(trimmed)) {
      onChange(trimmed)
    } else {
      setDraft(seed)
    }
  }
  return (
    <section className="border-t px-4 pt-2.5 pb-3">
      <h3 className="pb-1.5 text-[11px] font-semibold text-muted-foreground">Seed</h3>
      <div className="flex items-center gap-1.5">
        <Input
          value={draft}
          disabled={!usesSeed}
          inputMode="numeric"
          className="h-7 px-2 text-[12px] tabular-nums md:text-[12px]"
          onChange={(event) => setDraft(event.target.value)}
          onBlur={commit}
          onKeyDown={(event) => event.key === 'Enter' && commit()}
        />
        <Button variant="outline" size="icon-sm" title="Random seed" disabled={!usesSeed} onClick={() => onChange(randomSeed())}>
          <Dices />
        </Button>
      </div>
      {!usesSeed && <p className="pt-1.5 text-[11.5px] text-secondary-text">Keeps the world&apos;s seed</p>}
    </section>
  )
}

const LONG_LIMIT = 2n ** 63n

function isLong(text: string): boolean {
  const value = BigInt(text)
  return value >= -LONG_LIMIT && value < LONG_LIMIT
}

function randomSeed(): string {
  const [high, low] = crypto.getRandomValues(new Uint32Array(2))
  return BigInt.asIntN(64, (BigInt(high) << 32n) | BigInt(low)).toString()
}

function PaletteList({
  current,
  source,
  scale,
  onSelect
}: {
  current: NoisePaletteId
  source: NoiseSource | null
  scale: NoiseScale
  onSelect(palette: NoisePaletteId): void
}): React.JSX.Element {
  return (
    <section className="border-t px-2 pt-2.5 pb-2">
      <h3 className="px-2 pb-1 text-[11px] font-semibold text-muted-foreground">Palette</h3>
      {NOISE_PALETTES.map((palette) => (
        <button
          key={palette.id}
          onClick={() => onSelect(palette.id)}
          className={cn('flex h-[26px] w-full items-center gap-2 rounded-md px-2 text-left', palette.id === current ? 'bg-selection' : 'hover:bg-accent')}
        >
          <span className="w-[68px] shrink-0 truncate">{palette.label}</span>
          <span className="h-2.5 min-w-0 flex-1 rounded-[3px]" style={{ background: gradientOf(noiseScale(palette.id, source)) }} />
        </button>
      ))}
      <p className="flex justify-between px-2 pt-1 text-[11px] text-secondary-text tabular-nums">
        <span>{NUMBER.format(scale.minimum)}</span>
        <span>{source?.blockRange === null ? '' : 'blocks above the fluid'}</span>
        <span>{NUMBER.format(scale.maximum)}</span>
      </p>
    </section>
  )
}

function gradientOf(scale: NoiseScale): string {
  const stops = Array.from({ length: SWATCH_STEPS + 1 }, (_, step) => hexColor(scale.colorAlong(step / SWATCH_STEPS)))
  return `linear-gradient(to right, ${stops.join(', ')})`
}

function SourceList({ sources, current, onSelect }: { sources: NoiseSource[]; current: string | null; onSelect(id: string): void }): React.JSX.Element {
  const [filter, setFilter] = useState('')
  const shown = sources.filter((source) => source.name.toLowerCase().includes(filter.trim().toLowerCase()))
  const categories = [...new Set(shown.map((source) => source.category))]
  return (
    <section className="flex min-h-0 flex-1 flex-col border-t">
      <div className="relative px-3 pt-2.5 pb-1.5">
        <Search className="pointer-events-none absolute top-[15px] left-5 size-3.5 text-muted-foreground" />
        <Input value={filter} placeholder="Search" className="h-7 pl-7 text-[12px] md:text-[12px]" onChange={(event) => setFilter(event.target.value)} />
      </div>
      <ScrollArea className="min-h-0 flex-1">
        <div className="px-2 pb-1.5">
          {categories.map((category) => (
            <section key={category}>
              <h3 className="truncate px-2 pt-2 pb-1 text-[11px] font-semibold text-muted-foreground">{category}</h3>
              {shown
                .filter((source) => source.category === category)
                .map((source) => (
                  <button
                    key={source.id}
                    title={source.id}
                    onClick={() => onSelect(source.id)}
                    className={cn('flex h-[26px] w-full items-center rounded-md px-2 text-left', source.id === current ? 'bg-selection' : 'hover:bg-accent')}
                  >
                    <span className="truncate">{source.name}</span>
                  </button>
                ))}
            </section>
          ))}
          {shown.length === 0 && <p className="px-2 py-1 text-[12px] text-muted-foreground">Nothing to show</p>}
        </div>
      </ScrollArea>
    </section>
  )
}

/** How many blocks the highest noise rises above the lowest, on a scale that gives low reliefs room. */
function ReliefControl({ blocks, onChange }: { blocks: number; onChange(blocks: number): void }): React.JSX.Element {
  const span = Math.log(TALLEST_RELIEF_BLOCKS) - Math.log(LOWEST_RELIEF_BLOCKS)
  const step = Math.round(((Math.log(blocks) - Math.log(LOWEST_RELIEF_BLOCKS)) / span) * RELIEF_STEPS)
  return (
    <label className="flex h-8 items-center gap-2 rounded-md border bg-card/90 px-3 text-[12px] text-secondary-text" title="How many blocks the highest noise rises above the lowest">
      Relief
      <input
        type="range"
        min={0}
        max={RELIEF_STEPS}
        value={step}
        className="w-28 accent-primary"
        onChange={(event) => onChange(Math.round(Math.exp(Math.log(LOWEST_RELIEF_BLOCKS) + (Number(event.target.value) / RELIEF_STEPS) * span)))}
      />
      <span className="w-[72px] text-foreground tabular-nums">{NUMBER.format(blocks)} blocks</span>
    </label>
  )
}

function NoiseTerrainView({
  client,
  source,
  seed,
  revision,
  framing,
  scale,
  reliefBlocks,
  tell
}: {
  client: BridgeClient
  source: NoiseSource
  seed: string
  revision: number
  framing: TerrainFraming
  scale: NoiseScale
  reliefBlocks: number
  tell(message: string): void
}): React.JSX.Element {
  const tileSource = useMemo(() => noiseTileSource(client), [client])
  const focus = useMemo(() => focusAround(framing.centerX, framing.centerZ, LOWEST_FOCUS_BLOCKS, DEFAULT_RELIEF_BLOCKS), [framing])
  const layer = useMemo<NoiseLayer>(() => ({ sourceId: source.id, seed, revision }), [source.id, seed, revision])
  const style = useMemo<TerrainStyle<NoiseTile>>(
    () => ({
      surfaceOf: (tile, place) => new NoiseTerrain(tile, place, source, scale, reliefBlocks),
      floorY: source.blockRange?.lowest ?? 0,
      topY: source.blockRange?.highest ?? reliefBlocks
    }),
    [source, scale, reliefBlocks]
  )
  const { content, pending } = useTerrainStream(tileSource, COARSEST_NOISE_LEVEL, focus, layer, style, tell)
  return (
    <div className="absolute inset-0">
      <SceneViewport content={content} framing={content} />
      {pending > 0 && <div className={cn(PILL, 'top-3 left-1/2 -translate-x-1/2')}>Sampling {source.name}</div>}
    </div>
  )
}

function useNoiseSources(client: BridgeClient, packRevision: number, tell: (message: string) => void): NoiseSource[] {
  const [sources, setSources] = useState<NoiseSource[]>([])
  useEffect(() => {
    let active = true
    client
      .noiseSources()
      .then((found) => active && setSources(found))
      .catch((failure: Error) => active && tell(failure.message))
    return () => {
      active = false
    }
  }, [client, packRevision, tell])
  return sources
}
