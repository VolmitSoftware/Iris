import { useCallback, useEffect, useRef, useState } from 'react'
import type { ThemeChoice } from '../../../shared/bridge'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { MapHover, Marker, WorldInfo } from '../bridge/types'
import type { Share } from '../map/census'
import type { MapController, MapStatus, MapToggles } from '../map/MapController'
import type { Highlight } from '../map/tileStyle'
import { HelpDialog } from './HelpDialog'
import { HoverCard } from './HoverCard'
import { Legend } from './Legend'
import { MapCanvas, type CanvasPoint } from './MapCanvas'
import { actionForKey, viewForKey, type ShortcutAction } from './shortcuts'
import { StatusBar } from './StatusBar'
import { StudioToolbar } from './StudioToolbar'
import { ViewList } from './ViewList'

const KEYBOARD_ZOOM_FACTOR = 2 ** 0.25
const ENTITY_POLL_MILLIS = 2000
const NOTICE_MILLIS = 4000

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
  const view = world.views.find((candidate) => candidate.id === viewId) ?? world.views[0]

  const tell = useCallback((message: string) => setNotice(message), [])

  useEffect(() => {
    if (notice === null) {
      return
    }
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MILLIS)
    return () => window.clearTimeout(timer)
  }, [notice])

  useEffect(() => controller.current?.setView(viewId), [viewId])
  useEffect(() => controller.current?.setToggles(toggles), [toggles])
  useEffect(() => controller.current?.setHighlight(highlight), [highlight])
  useEffect(() => controller.current?.setMarkers(players, toggles.entities ? entities : []), [players, entities, toggles.entities])

  useEffect(() => {
    if (toggles.follow && players.length > 0) {
      controller.current?.centerOn(players[0].worldX, players[0].worldZ)
    }
  }, [players, toggles.follow])

  useServerEvents(client, controller, setPlayers, tell, setConnected)
  useEntities(client, toggles.entities, setEntities)
  useHover(client, controller, viewId, pointer, setHover)

  const selectView = useCallback((nextViewId: string) => {
    setViewId(nextViewId)
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
          selectView(world.views[(world.views.findIndex((candidate) => candidate.id === viewId) + 1) % world.views.length].id)
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
    [world.views, viewId, selectView, toggle, highlightUnderCursor, tell]
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
        theme={theme}
        onToggle={toggle}
        onRefresh={() => runShortcut('refresh')}
        onResetZoom={() => runShortcut('resetZoom')}
        onHelp={() => setHelpOpen(true)}
        onDisconnect={onDisconnect}
      />
      <div className="flex min-h-0 flex-1">
        <ViewList views={world.views} current={viewId} onSelect={selectView} />
        <div className="relative min-w-0 flex-1">
          <MapCanvas
            client={client}
            world={world}
            viewId={viewId}
            controllerRef={controller}
            listener={{ onStatus: setStatus, onShares: setShares, onError: tell }}
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
            <HoverCard hover={hover} point={pointer} viewId={viewId} detailed={detailed} minY={world.minY} />
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
        <Legend
          title={legendTitle(viewId)}
          shares={shares}
          highlight={highlight}
          onSelect={(subject) => setHighlight((current) => (sameSubject(current, subject) ? null : subject))}
        />
      </div>
      <StatusBar viewLabel={view.label} status={status} notice={notice} />
      <HelpDialog open={helpOpen} onOpenChange={setHelpOpen} views={world.views} />
    </div>
  )
}

function sameSubject(first: Highlight | null, second: Highlight | null): boolean {
  return first !== null && second !== null && first.kind === second.kind && first.key === second.key
}

function legendTitle(viewId: string): string {
  if (viewId === 'regions') {
    return 'Regions on screen'
  }
  return viewId === 'height' ? 'Heights on screen' : 'Biomes on screen'
}

/** Players and hotloads from the server; the first world event only says the world runs, which is known. */
function useServerEvents(
  client: BridgeClient,
  controller: React.RefObject<MapController | null>,
  setPlayers: (players: Marker[]) => void,
  tell: (message: string) => void,
  setConnected: (connected: boolean) => void
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
  }, [client, controller, setPlayers, tell, setConnected])
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
  viewId: string,
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
      .hover(viewId, Math.floor(spot.x), Math.floor(spot.z), stop.signal)
      .then(setHover)
      .catch(() => undefined)
    return () => stop.abort()
  }, [client, controller, viewId, pointer, setHover])
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
