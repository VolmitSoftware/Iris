import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Waves } from 'lucide-react'
import type { BridgeClient, MapLayerQuery, WorldRect } from '../bridge/BridgeClient'
import type { MapHover, MapLabel, Marker, ViewInfo, WorldInfo } from '../bridge/types'
import type { Share } from '../map/census'
import type { MapController, MapStatus } from '../map/MapController'
import type { MapLinks } from '../map/mapLinks'
import type { Highlight } from '../map/tileStyle'
import { HoverCard } from './HoverCard'
import { Legend } from './Legend'
import { MapCanvas, type CanvasPoint } from './MapCanvas'
import { PickInspector, hasPickSections } from './PickInspector'
import { RegionPreviewList } from './RegionPreviewList'
import type { StudioToggles } from './MapStudio'
import type { ShortcutAction } from './shortcuts'
import { DetailPanel, SidebarPanel, ViewIsland } from './Sidebar'
import { MapTools } from './StudioToolbar'
import { useRememberedState } from './rememberedState'
import { useSettledEntry, useTab, useTabCommands } from './tabContext'
import { usePickInspection } from './usePickInspection'

const KEYBOARD_ZOOM_FACTOR = 2 ** 0.25
const ENTITY_POLL_MILLIS = 2000
/** The views whose spots a noise picks, which the pick inspector explains. */
const PICK_VIEWS = new Set(['biomes', 'regions'])
const OPENING_TOGGLES: StudioToggles = { shading: true, regionBorders: false, grid: false, blocks: false, follow: false, entities: false }

interface MapViewProps {
  client: BridgeClient
  world: WorldInfo
  view: ViewInfo
  packRevision: number
  /** The regions the region view can preview everywhere. */
  regions: MapLabel[]
  players: Marker[]
  /** Whether Shift is held, for more about the spot under the cursor. */
  detailed: boolean
  links: MapLinks
  onStatus(status: MapStatus | null): void
  /** Where the map looks, which the 3D world opens around. */
  onArea(area: WorldRect): void
  tell(message: string): void
}

/** One map tab: the map of a view in the middle, the regions it previews on the left, what is on screen on the right. */
export function MapView({ client, world, view, packRevision, regions, players, detailed, links, onStatus, onArea, tell }: MapViewProps): React.JSX.Element {
  const tab = useTab()
  const controller = useRef<MapController | null>(null)
  const [toggles, setToggles] = useRememberedState<StudioToggles>(`${tab.id}:toggles`, OPENING_TOGGLES)
  const [highlight, setHighlight] = useState<Highlight | null>(null)
  const [shares, setShares] = useState<Share[]>([])
  const [entities, setEntities] = useState<Marker[]>([])
  const [pointer, setPointer] = useState<CanvasPoint | null>(null)
  const [hover, setHover] = useState<MapHover | null>(null)
  const [preview, setPreview] = useRememberedState<string | null>(`${tab.id}:preview`, null)
  const [area, setArea] = useState<WorldRect | null>(null)
  const [linked, setLinked] = useRememberedState(`${tab.id}:linked`, false)
  const layer = useMemo<MapLayerQuery>(() => ({ view: view.id, preview: view.id === 'regions' ? preview : null }), [view.id, preview])
  const inspectsPicks = PICK_VIEWS.has(view.id)
  const inspection = usePickInspection(client, layer, area, inspectsPicks, packRevision)
  const previewName = layer.preview === null ? null : (regions.find((region) => region.key === layer.preview)?.name ?? layer.preview)
  useSettledEntry(tab, null, previewName === null ? null : `${view.label} · ${previewName}`)

  useEffect(() => controller.current?.setView(layer.view, layer.preview), [layer])
  useEffect(() => controller.current?.setToggles(toggles), [toggles])
  useEffect(() => controller.current?.setHighlight(highlight), [highlight])
  useEffect(() => controller.current?.setMarkers(players, toggles.entities ? entities : []), [players, entities, toggles.entities])
  useEffect(() => () => onStatus(null), [onStatus])
  usePackReload(controller, packRevision)
  useEntities(client, toggles.entities, setEntities)
  useHover(client, controller, layer, pointer, setHover)

  useEffect(() => {
    if (toggles.follow && players.length > 0) {
      controller.current?.centerOn(players[0].worldX, players[0].worldZ)
    }
  }, [players, toggles.follow])

  useEffect(() => {
    if (!linked) {
      return
    }
    const joined = links.join(tab.id, (placement) => controller.current?.place(placement))
    if (joined !== null) {
      controller.current?.place(joined)
    } else if (controller.current !== null) {
      links.moved(tab.id, controller.current.placement())
    }
    return () => links.leave(tab.id)
  }, [linked, links, tab.id])

  const moved = useCallback(() => {
    if (controller.current !== null) {
      links.moved(tab.id, controller.current.placement())
    }
  }, [links, tab.id])

  const toggle = useCallback((name: keyof StudioToggles) => setToggles((current) => ({ ...current, [name]: !current[name] })), [setToggles])
  const selectPreview = (regionKey: string | null): void => {
    setPreview(regionKey)
    setHighlight(null)
  }

  const highlightUnderCursor = useCallback(() => {
    if (pointer === null || controller.current === null) {
      return
    }
    const { subject } = controller.current.subjectsAt(pointer.x, pointer.y)
    setHighlight((current) => (subject === null || sameSubject(current, subject) ? null : subject))
  }, [pointer])

  const run = useCallback(
    (action: string) => {
      const map = controller.current
      switch (action as ShortcutAction) {
        case 'refresh':
          map?.refresh()
          tell('Redrawing with the latest pack files')
          return
        case 'zoomIn':
          map?.zoomAt(KEYBOARD_ZOOM_FACTOR)
          moved()
          return
        case 'zoomOut':
          map?.zoomAt(1 / KEYBOARD_ZOOM_FACTOR)
          moved()
          return
        case 'resetZoom':
          map?.resetZoom()
          moved()
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
        case 'blocks':
          toggle('blocks')
          return
        case 'borders':
          toggle('regionBorders')
      }
    },
    [toggle, highlightUnderCursor, moved, tell]
  )
  useTabCommands(tab, run)

  const spotAt = (point: CanvasPoint): { x: number; z: number } | null => {
    const spot = controller.current?.worldAt(point.x, point.y)
    return spot === undefined ? null : { x: Math.floor(spot.x), z: Math.floor(spot.z) }
  }
  const teleport = (point: CanvasPoint): void => {
    const spot = spotAt(point)
    if (spot === null) {
      return
    }
    client
      .teleport(spot.x, spot.z)
      .then(() => tell(`Teleporting to ${spot.x}, ${spot.z}`))
      .catch((failure: Error) => tell(failure.message))
  }
  const openFile = (point: CanvasPoint): void => {
    const spot = controller.current?.worldAt(point.x, point.y)
    if (spot === undefined) {
      return
    }
    client
      .openInEditor(spot.x, spot.z, view.id)
      .then((message) => tell(message ?? 'Opened'))
      .catch((failure: Error) => tell(failure.message))
  }
  const choose = (subject: Highlight): void => setHighlight((current) => (sameSubject(current, subject) ? null : subject))

  return (
    <>
      {view.id === 'regions' && regions.length > 1 && (
        <SidebarPanel className="flex-1 overflow-y-auto p-1.5">
          <RegionPreviewList regions={regions} current={layer.preview} onSelect={selectPreview} />
        </SidebarPanel>
      )}
      <ViewIsland
        title={view.label}
        subtitle={previewName ?? undefined}
        tools={
          <MapTools
            toggles={toggles}
            hasPlayers={world.hasPlayers}
            linked={linked}
            onLink={() => setLinked((current) => !current)}
            onToggle={toggle}
            onRefresh={() => run('refresh')}
            onResetZoom={() => run('resetZoom')}
          />
        }
      >
        <MapCanvas
          client={client}
          world={world}
          viewId={view.id}
          controllerRef={controller}
          listener={{
            onStatus,
            onShares: setShares,
            onArea: (next) => {
              setArea((current) => (sameRect(current, next) ? current : next))
              onArea(next)
            },
            onError: tell
          }}
          onPointer={setPointer}
          onDragged={() => setToggles((current) => (current.follow ? { ...current, follow: false } : current))}
          onMoved={moved}
          onTeleport={teleport}
          onOpenFile={openFile}
          onHighlight={choose}
          highlight={highlight}
          onCopy={(text) => {
            window.studio.copyText(text)
            tell(`Copied ${text}`)
          }}
        />
        {pointer !== null && hover !== null && (
          <HoverCard hover={hover} point={pointer} viewId={view.id} previewing={layer.preview !== null} detailed={detailed} minY={world.minY} />
        )}
        {view.id === 'rivers' && !world.hydrology && (
          <div className="absolute inset-0 flex items-center justify-center bg-card">
            <div className="text-center">
              <Waves className="mx-auto mb-3 size-10" />
              <p className="font-medium">No rivers planned</p>
              <p className="mt-1 text-secondary-text">Enable hydrology in</p>
              <p className="font-mono text-[11.5px] text-secondary-text">dimensions/{world.dimension}.json</p>
              <p className="mt-1 text-muted-foreground">They appear here once the pack reloads.</p>
            </div>
          </div>
        )}
      </ViewIsland>
      {inspectsPicks && hasPickSections(inspection) && (
        <DetailPanel className="w-[250px] flex-[7]">
          <PickInspector title={previewName ?? view.label} inspection={inspection} />
        </DetailPanel>
      )}
      <DetailPanel className="w-[250px] flex-[3]">
        <Legend className="flex-1" title={legendTitle(layer)} shares={shares} selection={{ highlight, onSelect: choose }} />
      </DetailPanel>
    </>
  )
}

/** Draws the map again from the server when the pack reloaded, but not when a hidden tab comes back. */
function usePackReload(controller: React.RefObject<MapController | null>, packRevision: number): void {
  const drawnFor = useRef(packRevision)
  useEffect(() => {
    if (drawnFor.current === packRevision) {
      return
    }
    drawnFor.current = packRevision
    controller.current?.refresh()
  }, [controller, packRevision])
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
  if (layer.view === 'rivers') {
    return 'Water on screen'
  }
  return 'Biomes on screen'
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
