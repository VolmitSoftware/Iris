import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { ThemeChoice } from '../../../shared/bridge'
import type { BridgeClient, MapLayerQuery, WorldRect } from '../bridge/BridgeClient'
import type { MapHover, MapLabel, Marker, ModuleViewInfo, PickDisplay, PickInspection, ViewInfo, WorldInfo } from '../bridge/types'
import type { Share } from '../map/census'
import type { MapController, MapStatus, MapToggles } from '../map/MapController'
import type { Highlight } from '../map/tileStyle'
import { HelpDialog } from './HelpDialog'
import { HoverCard } from './HoverCard'
import { Legend } from './Legend'
import { MapCanvas, type CanvasPoint } from './MapCanvas'
import { PickInspector } from './PickInspector'
import { RegionPreviewList } from './RegionPreviewList'
import { NoiseStudio } from './NoiseStudio'
import { PatternStudio } from './PatternStudio'
import { SavedWorldStudio } from './SavedWorldStudio'
import { SceneStudio } from './SceneStudio'
import { WorldStudio3D } from './WorldStudio3D'
import { actionForKey, viewForKey, type ShortcutAction } from './shortcuts'
import { StatusBar } from './StatusBar'
import { StudioToolbar } from './StudioToolbar'
import { ViewList } from './ViewList'

const KEYBOARD_ZOOM_FACTOR = 2 ** 0.25
const ENTITY_POLL_MILLIS = 2000
const NOTICE_MILLIS = 4000
// Measuring the picks in view samples thousands of spots, so it waits for the map to rest.
const INSPECTION_REST_MILLIS = 350
/** The views whose spots a noise picks, which the pick inspector explains. */
const PICK_VIEWS = new Set(['biomes', 'regions'])

interface MapStudioProps {
  client: BridgeClient
  world: WorldInfo
  theme: { choice: ThemeChoice; next: () => void }
  onDisconnect(): void
}

export interface StudioToggles extends MapToggles {
  follow: boolean
  entities: boolean
}

/** The map of one world: views on the left, the map in the middle, what is on screen on the right. */
export function MapStudio({ client, world, theme, onDisconnect }: MapStudioProps): React.JSX.Element {
  const controller = useRef<MapController | null>(null)
  const [viewId, setViewId] = useState(world.views[0]?.id ?? 'biomes')
  const [toggles, setToggles] = useState<StudioToggles>({ shading: true, regionBorders: false, grid: false, follow: false, entities: false })
  const [highlight, setHighlight] = useState<Highlight | null>(null)
  const [status, setStatus] = useState<MapStatus | null>(null)
  const [shares, setShares] = useState<Share[]>([])
  const [players, setPlayers] = useState<Marker[]>([])
  const [entities, setEntities] = useState<Marker[]>([])
  const [pointer, setPointer] = useState<CanvasPoint | null>(null)
  const [hover, setHover] = useState<MapHover | null>(null)
  const [detailed, setDetailed] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [connected, setConnected] = useState(true)
  const [preview, setPreview] = useState<string | null>(null)
  const [pickDisplay, setPickDisplay] = useState<PickDisplay>('picks')
  const [area, setArea] = useState<WorldRect | null>(null)
  const [packRevision, setPackRevision] = useState(0)
  const [sceneSummary, setSceneSummary] = useState<string | null>(null)
  const loadSceneViews = useCallback(() => client.sceneViews(), [client])
  const loadPatternViews = useCallback(() => client.patternViews(), [client])
  const sceneViews = useModuleViews(loadSceneViews, packRevision)
  const patternViews = useModuleViews(loadPatternViews, packRevision)
  const views = useMemo(
    () => [...withAppViews(world.views), ...patternViews.map(asViewInfo), ...sceneViews.map(asViewInfo), NOISE],
    [world.views, patternViews, sceneViews]
  )
  const sceneView = sceneViews.find((candidate) => candidate.id === viewId) ?? null
  const patternView = patternViews.find((candidate) => candidate.id === viewId) ?? null
  const showsMap = sceneView === null && patternView === null && !APP_VIEW_IDS.has(viewId)
  const view = views.find((candidate) => candidate.id === viewId) ?? views[0]
  const layer = useMemo<MapLayerQuery>(() => ({ view: viewId, preview: viewId === 'regions' ? preview : null }), [viewId, preview])
  const regions = usePreviewableRegions(client, packRevision)
  const inspectsPicks = PICK_VIEWS.has(viewId)
  const inspection = usePickInspection(client, layer, area, inspectsPicks, packRevision)
  const previewName = layer.preview === null ? null : (regions.find((region) => region.key === layer.preview)?.name ?? layer.preview)

  const tell = useCallback((message: string) => setNotice(message), [])

  useEffect(() => {
    if (notice === null) {
      return
    }
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MILLIS)
    return () => window.clearTimeout(timer)
  }, [notice])

  useEffect(() => controller.current?.setView(layer.view, layer.preview), [layer])
  useEffect(() => controller.current?.setPickDisplay(pickDisplay), [pickDisplay])
  useEffect(() => controller.current?.setToggles(toggles), [toggles])
  useEffect(() => controller.current?.setHighlight(highlight), [highlight])
  useEffect(() => controller.current?.setMarkers(players, toggles.entities ? entities : []), [players, entities, toggles.entities])

  useEffect(() => {
    if (toggles.follow && players.length > 0) {
      controller.current?.centerOn(players[0].worldX, players[0].worldZ)
    }
  }, [players, toggles.follow])

  const packReloaded = useCallback(() => setPackRevision((revision) => revision + 1), [])
  useServerEvents(client, controller, setPlayers, tell, setConnected, packReloaded)
  useEntities(client, toggles.entities, setEntities)
  useHover(client, controller, layer, pointer, setHover)

  const selectView = useCallback((nextViewId: string) => {
    setViewId(nextViewId)
    setHighlight(null)
  }, [])

  const selectPreview = useCallback((regionKey: string | null) => {
    setPreview(regionKey)
    setHighlight(null)
  }, [])

  const toggle = useCallback((name: keyof StudioToggles) => setToggles((current) => ({ ...current, [name]: !current[name] })), [])

  const highlightUnderCursor = useCallback(() => {
    if (pointer === null || controller.current === null) {
      return
    }
    const { subject } = controller.current.subjectsAt(pointer.x, pointer.y)
    setHighlight((current) => (subject === null || sameSubject(current, subject) ? null : subject))
  }, [pointer])

  const runShortcut = useCallback(
    (action: ShortcutAction) => {
      const map = controller.current
      switch (action) {
        case 'nextView':
          selectView(views[(views.findIndex((candidate) => candidate.id === viewId) + 1) % views.length].id)
          return
        case 'refresh':
          map?.refresh()
          tell('Redrawing with the latest pack files')
          return
        case 'zoomIn':
          map?.zoomAt(KEYBOARD_ZOOM_FACTOR)
          return
        case 'zoomOut':
          map?.zoomAt(1 / KEYBOARD_ZOOM_FACTOR)
          return
        case 'resetZoom':
          map?.resetZoom()
          return
        case 'dismiss':
          setHighlight(null)
          return
        case 'follow':
          toggle('follow')
          return
        case 'grid':
          toggle('grid')
          return
        case 'entities':
          toggle('entities')
          return
        case 'highlight':
          highlightUnderCursor()
          return
        case 'shading':
          toggle('shading')
          return
        case 'borders':
          toggle('regionBorders')
          return
        case 'help':
          setHelpOpen((open) => !open)
      }
    },
    [views, viewId, selectView, toggle, highlightUnderCursor, tell]
  )

  useKeyboard(runShortcut, selectView, setDetailed, world)

  const teleport = useCallback(
    (point: CanvasPoint) => {
      const spot = controller.current?.worldAt(point.x, point.y)
      if (spot === undefined) {
        return
      }
      client
        .teleport(Math.floor(spot.x), Math.floor(spot.z))
        .then(() => tell(`Teleporting to ${Math.floor(spot.x)}, ${Math.floor(spot.z)}`))
        .catch((failure: Error) => tell(failure.message))
    },
    [client, tell]
  )

  const openFile = useCallback(
    (point: CanvasPoint) => {
      const spot = controller.current?.worldAt(point.x, point.y)
      if (spot === undefined) {
        return
      }
      client
        .openInEditor(spot.x, spot.z, viewId)
        .then((message) => tell(message ?? 'Opened on the server'))
        .catch((failure: Error) => tell(failure.message))
    },
    [client, viewId, tell]
  )

  return (
    <div className="flex h-full flex-col bg-background">
      <StudioToolbar
        title={view.label}
        world={world}
        toggles={toggles}
        hasPlayers={world.hasPlayers}
        mapTools={showsMap}
        theme={theme}
        onToggle={toggle}
        onRefresh={() => runShortcut('refresh')}
        onResetZoom={() => runShortcut('resetZoom')}
        onHelp={() => setHelpOpen(true)}
        onDisconnect={onDisconnect}
      />
      <div className="flex min-h-0 flex-1">
        <ViewList views={views} current={viewId} onSelect={selectView}>
          {viewId === 'regions' && regions.length > 1 && <RegionPreviewList regions={regions} current={layer.preview} onSelect={selectPreview} />}
        </ViewList>
        {sceneView !== null ? (
          <SceneStudio client={client} view={sceneView} packRevision={packRevision} onSummary={setSceneSummary} tell={tell} />
        ) : patternView !== null ? (
          <PatternStudio client={client} view={patternView} packRevision={packRevision} onSummary={setSceneSummary} tell={tell} />
        ) : viewId === WORLD_3D.id ? (
          <WorldStudio3D client={client} world={world} mapArea={area} packRevision={packRevision} onSummary={setSceneSummary} tell={tell} />
        ) : viewId === SAVED_WORLD.id ? (
          <SavedWorldStudio client={client} world={world} onSummary={setSceneSummary} tell={tell} />
        ) : viewId === NOISE.id ? (
          <NoiseStudio client={client} packRevision={packRevision} onSummary={setSceneSummary} tell={tell} />
        ) : (
          <>
        <div className="relative min-w-0 flex-1">
          <MapCanvas
            client={client}
            world={world}
            viewId={viewId}
            controllerRef={controller}
            listener={{ onStatus: setStatus, onShares: setShares, onArea: (next) => setArea((current) => (sameRect(current, next) ? current : next)), onError: tell }}
            onPointer={setPointer}
            onDragged={() => setToggles((current) => (current.follow ? { ...current, follow: false } : current))}
            onTeleport={teleport}
            onOpenFile={openFile}
            onHighlight={(subject) => setHighlight((current) => (sameSubject(current, subject) ? null : subject))}
            highlight={highlight}
            onCopy={(text) => {
              window.studio.copyText(text)
              tell(`Copied ${text}`)
            }}
          />
          {pointer !== null && hover !== null && (
            <HoverCard hover={hover} point={pointer} viewId={viewId} previewing={layer.preview !== null} detailed={detailed} minY={world.minY} />
          )}
          {!connected && (
            <div className="absolute inset-0 flex items-center justify-center bg-background/70">
              <div className="rounded-xl border border-overlay-border bg-card p-5 text-center shadow-lg">
                <p className="font-medium">Lost the connection to the server</p>
                <button className="mt-3 text-link hover:underline" onClick={onDisconnect}>
                  Back to the world list
                </button>
              </div>
            </div>
          )}
        </div>
        <aside className="flex w-[250px] shrink-0 flex-col border-l bg-card">
          {inspectsPicks && (
            <PickInspector
              title={previewName ?? view.label}
              picksLabel={viewId === 'regions' && layer.preview === null ? 'Regions' : 'Biomes'}
              inspection={inspection}
              display={pickDisplay}
              onDisplay={setPickDisplay}
            />
          )}
          <Legend
            className={inspectsPicks ? 'flex-[3]' : 'flex-1'}
            title={legendTitle(layer)}
            shares={shares}
            highlight={highlight}
            onSelect={(subject) => setHighlight((current) => (sameSubject(current, subject) ? null : subject))}
          />
        </aside>
          </>
        )}
      </div>
      <StatusBar viewLabel={statusLabel(view.label, previewName, showsMap ? null : sceneSummary)} status={showsMap ? status : null} notice={notice} />
      <HelpDialog open={helpOpen} onOpenChange={setHelpOpen} views={views} />
    </div>
  )
}

const SCENE_GROUP = { group: 'BLOCKS', groupLabel: 'Blocks' }
/** The app draws the 3D world from the server's biome tiles, so the server does not list it among its views. */
const WORLD_3D: ViewInfo = {
  id: 'world3d',
  label: '3D world',
  description: 'The terrain in 3D, coloured by biome, region or height',
  group: 'WORLD',
  groupLabel: 'World'
}
const SAVED_WORLD: ViewInfo = {
  id: 'savedWorld',
  label: 'Saved world',
  description: 'A world as the server saved it to disk, in 3D',
  group: 'WORLD',
  groupLabel: 'World'
}
const NOISE: ViewInfo = {
  id: 'noise',
  label: 'Noise',
  description: "The pack's generators and expressions and the built-in noise styles, from above or as terrain",
  group: 'PACK',
  groupLabel: 'Pack'
}
const APP_VIEW_IDS = new Set([WORLD_3D.id, SAVED_WORLD.id, NOISE.id])

function withAppViews(serverViews: ViewInfo[]): ViewInfo[] {
  const afterHeight = serverViews.findIndex((candidate) => candidate.id === 'height') + 1
  return [...serverViews.slice(0, afterHeight), WORLD_3D, SAVED_WORLD, ...serverViews.slice(afterHeight)]
}

function asViewInfo(view: ModuleViewInfo): ViewInfo {
  return { ...view, ...SCENE_GROUP }
}

function statusLabel(viewLabel: string, previewName: string | null, sceneSummary: string | null): string {
  const detail = sceneSummary ?? previewName
  return detail === null ? viewLabel : `${viewLabel} · ${detail}`
}

/** Views the pack's modules show, read again after the pack reloads; keep {@code load} stable. */
function useModuleViews(load: () => Promise<ModuleViewInfo[]>, packRevision: number): ModuleViewInfo[] {
  const [views, setViews] = useState<ModuleViewInfo[]>([])
  useEffect(() => {
    let active = true
    load()
      .then((found) => active && setViews(found))
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [load, packRevision])
  return views
}

function sameSubject(first: Highlight | null, second: Highlight | null): boolean {
  return first !== null && second !== null && first.kind === second.kind && first.key === second.key
}

function sameRect(first: WorldRect | null, second: WorldRect): boolean {
  return first !== null && first.minX === second.minX && first.minZ === second.minZ && first.maxX === second.maxX && first.maxZ === second.maxZ
}

function legendTitle(layer: MapLayerQuery): string {
  if (layer.view === 'regions' && layer.preview === null) {
    return 'Regions on screen'
  }
  return layer.view === 'height' ? 'Heights on screen' : 'Biomes on screen'
}

/** The regions the region view can preview, read again after the pack reloads. */
function usePreviewableRegions(client: BridgeClient, packRevision: number): MapLabel[] {
  const [regions, setRegions] = useState<MapLabel[]>([])
  useEffect(() => {
    let active = true
    client
      .regions()
      .then((found) => active && setRegions(found))
      .catch(() => undefined)
    return () => {
      active = false
    }
  }, [client, packRevision])
  return regions
}

/**
 * Measures how the noise picks what the view shows, once the map has rested on an area for a moment; the last
 * measurement stays up until the next one arrives, so panning does not blank the panel.
 */
function usePickInspection(
  client: BridgeClient,
  layer: MapLayerQuery,
  area: WorldRect | null,
  enabled: boolean,
  packRevision: number
): PickInspection | null {
  const [inspection, setInspection] = useState<PickInspection | null>(null)
  useEffect(() => setInspection(null), [layer, packRevision])
  useEffect(() => {
    if (!enabled || area === null) {
      return
    }
    const stop = new AbortController()
    const timer = window.setTimeout(() => {
      client
        .picks(layer, area, stop.signal)
        .then(setInspection)
        .catch(() => undefined)
    }, INSPECTION_REST_MILLIS)
    return () => {
      window.clearTimeout(timer)
      stop.abort()
    }
  }, [client, layer, area, enabled, packRevision])
  return inspection
}

/** Players and hotloads from the server; the first world event only says the world runs, which is known. */
function useServerEvents(
  client: BridgeClient,
  controller: React.RefObject<MapController | null>,
  setPlayers: (players: Marker[]) => void,
  tell: (message: string) => void,
  setConnected: (connected: boolean) => void,
  onPackReloaded: () => void
): void {
  useEffect(() => {
    const stop = new AbortController()
    let firstWorldEvent = true
    void client.listen(
      {
        onWorld: (running) => {
          if (firstWorldEvent) {
            firstWorldEvent = false
            return
          }
          if (running) {
            controller.current?.refresh()
            onPackReloaded()
            tell('Pack reloaded, redrawing')
          } else {
            tell('The world stopped')
          }
        },
        onPlayers: setPlayers,
        onClosed: () => setConnected(false)
      },
      stop.signal
    )
    return () => stop.abort()
  }, [client, controller, setPlayers, tell, setConnected, onPackReloaded])
}

function useEntities(client: BridgeClient, visible: boolean, setEntities: (entities: Marker[]) => void): void {
  useEffect(() => {
    if (!visible) {
      return
    }
    let active = true
    const refresh = (): void => {
      client
        .entities()
        .then((found) => active && setEntities(found))
        .catch(() => undefined)
    }
    refresh()
    const timer = window.setInterval(refresh, ENTITY_POLL_MILLIS)
    return () => {
      active = false
      window.clearInterval(timer)
    }
  }, [client, visible, setEntities])
}

/** Asks the server about the spot under the cursor; a newer spot aborts the question about an older one. */
function useHover(
  client: BridgeClient,
  controller: React.RefObject<MapController | null>,
  layer: MapLayerQuery,
  pointer: CanvasPoint | null,
  setHover: (hover: MapHover | null) => void
): void {
  useEffect(() => {
    if (pointer === null || controller.current === null) {
      setHover(null)
      return
    }
    const spot = controller.current.worldAt(pointer.x, pointer.y)
    const stop = new AbortController()
    client
      .hover(layer, Math.floor(spot.x), Math.floor(spot.z), stop.signal)
      .then(setHover)
      .catch(() => undefined)
    return () => stop.abort()
  }, [client, controller, layer, pointer, setHover])
}

function useKeyboard(
  runShortcut: (action: ShortcutAction) => void,
  selectView: (view: string) => void,
  setDetailed: (detailed: boolean) => void,
  world: WorldInfo
): void {
  useEffect(() => {
    const down = (event: KeyboardEvent): void => {
      if (event.key === 'Shift') {
        setDetailed(true)
      }
      if (event.ctrlKey || event.metaKey || event.altKey || event.target instanceof HTMLInputElement) {
        return
      }
      const viewId = viewForKey(event.key)
      if (viewId !== null && world.views.some((view) => view.id === viewId)) {
        selectView(viewId)
        return
      }
      const action = actionForKey(event.key)
      if (action !== null) {
        event.preventDefault()
        runShortcut(action)
      }
    }
    const up = (event: KeyboardEvent): void => {
      if (event.key === 'Shift') {
        setDetailed(false)
      }
    }
    const blur = (): void => setDetailed(false)
    window.addEventListener('keydown', down)
    window.addEventListener('keyup', up)
    window.addEventListener('blur', blur)
    return () => {
      window.removeEventListener('keydown', down)
      window.removeEventListener('keyup', up)
      window.removeEventListener('blur', blur)
    }
  }, [runShortcut, selectView, setDetailed, world])
}
