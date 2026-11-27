import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { BridgeClient, WorldRect } from '../bridge/BridgeClient'
import type { MapLabel, Marker, ModuleViewInfo, SceneViewInfo, ServerUsage, StudioPack, ViewInfo, WorldInfo } from '../bridge/types'
import type { MapStatus, MapToggles } from '../map/MapController'
import { MapLinks } from '../map/mapLinks'
import { cn } from '@/lib/utils'
import { Glyph } from './Glyph'
import { HelpDialog } from './HelpDialog'
import { ImageMapStudio } from './ImageMapStudio'
import { MapView } from './MapView'
import { NoiseStudio } from './NoiseStudio'
import { PaneLayout, type WorkbenchChange } from './PaneLayout'
import { PatternStudio } from './PatternStudio'
import { PregenStudio } from './PregenStudio'
import { SavedWorldStudio } from './SavedWorldStudio'
import { SceneStudio } from './SceneStudio'
import { SettingsDialog } from './SettingsDialog'
import { isTypingInto, shortcutRows, viewOfShortcutId, windowShortcutFor, type ShortcutAction } from './shortcuts'
import { forgetTab } from './rememberedState'
import { DetailSlot, SidebarSlot } from './Sidebar'
import { StatusBar } from './StatusBar'
import { StudioToolbar } from './StudioToolbar'
import { TabCommands, TabContext, type TabHandle } from './tabContext'
import type { Theme } from './theme'
import { useBindings } from './useBindings'
import { ViewList } from './ViewList'
import { WorldStudio3D } from './WorldStudio3D'
import {
  activateTab,
  closeTab,
  focusedPane,
  focusedTab,
  keepTab,
  openTab,
  panesOf,
  peekTab,
  settleEntry,
  showView,
  stepTab,
  titleTab,
  workbenchWith,
  type Pane,
  type Tab,
  type Workbench
} from './workbench'

/**
 * The island beside the stripe meets it flush with square corners, as IntelliJ's tool windows meet theirs, and so do
 * the panes on the left edge while there is no island there; the row's gap is 1.5.
 */
const ISLAND_FLUSH_WITH_STRIPE = '[&>nav]:-mr-1.5 [&>nav+div:not(:empty)]:rounded-l-none [&:has(>nav+div:empty)_[data-left-edge]]:rounded-l-none'
const NOTICE_MILLIS = 4000

interface MapStudioProps {
  client: BridgeClient
  world: WorldInfo
  theme: Theme
  /** Switches the studio to another pack; settles once the studio is on it or the server refused. */
  onOpenPack(packKey: string): Promise<void>
  onDisconnect(): void
}

export interface StudioToggles extends MapToggles {
  follow: boolean
  entities: boolean
}

/** What a tab's view says for the status bar. */
interface TabReport {
  summary?: string | null
  progress?: number | null
  status?: MapStatus | null
}

/** What every tab's view draws from: the server, the world, and what the studio knows about both. */
interface Shared {
  client: BridgeClient
  world: WorldInfo
  views: ViewInfo[]
  sceneViews: SceneViewInfo[]
  patternViews: ModuleViewInfo[]
  packRevision: number
  regions: MapLabel[]
  players: Marker[]
  detailed: boolean
  links: MapLinks
  lastMapArea: React.RefObject<WorldRect | null>
  report(tabId: string, report: TabReport): void
  tell(message: string): void
}

/** The studio of one world: the views on the left, their tabs in panes in the middle, what the focused one shows on the right. */
export function MapStudio({ client, world, theme, onOpenPack, onDisconnect }: MapStudioProps): React.JSX.Element {
  const [workbench, setWorkbench] = useState<Workbench>(() => workbenchWith({ viewId: world.views[0]?.id ?? 'biomes', entry: null }))
  const change = useCallback((next: WorkbenchChange) => setWorkbench(next), [])
  const [players, setPlayers] = useState<Marker[]>([])
  const [usage, setUsage] = useState<ServerUsage | null>(null)
  const [detailed, setDetailed] = useState(false)
  const [helpOpen, setHelpOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [connected, setConnected] = useState(true)
  const [packRevision, setPackRevision] = useState(0)
  const [reports, setReports] = useState<Record<string, TabReport>>({})
  const [sidebarSlot, setSidebarSlot] = useState<HTMLElement | null>(null)
  const [detailSlot, setDetailSlot] = useState<HTMLElement | null>(null)
  const [openingPack, setOpeningPack] = useState<StudioPack | null>(null)
  const links = useMemo(() => new MapLinks(), [])
  const lastMapArea = useRef<WorldRect | null>(null)
  const loadSceneViews = useCallback(() => client.sceneViews(), [client])
  const loadPatternViews = useCallback(() => client.patternViews(), [client])
  const sceneViews = useModuleViews(loadSceneViews, packRevision)
  const patternViews = useModuleViews(loadPatternViews, packRevision)
  const views = useMemo(
    () => [...withAppViews(world.views), ...patternViews.map(asViewInfo), ...sceneViews.map(asViewInfo)],
    [world.views, patternViews, sceneViews]
  )
  const entryViews = useMemo(() => new Set([...ENTRY_VIEW_IDS, ...sceneViews.map((view) => view.id), ...patternViews.map((view) => view.id)]), [sceneViews, patternViews])
  const regions = usePreviewableRegions(client, packRevision)
  const focused = focusedTab(workbench)
  const viewOf = useCallback((viewId: string) => views.find((candidate) => candidate.id === viewId) ?? null, [views])
  const viewLabel = useCallback((viewId: string) => viewOf(viewId)?.label ?? viewId, [viewOf])
  const showsMapOf = useCallback((viewId: string) => !APP_VIEW_IDS.has(viewId) && !entryViews.has(viewId), [entryViews])

  const tell = useCallback((message: string) => setNotice(message), [])
  const report = useCallback(
    (tabId: string, next: TabReport) => setReports((current) => ({ ...current, [tabId]: { ...current[tabId], ...next } })),
    []
  )

  const loadPacks = useCallback(() => client.packs(), [client])
  const openPack = useCallback(
    (pack: StudioPack) => {
      setOpeningPack(pack)
      onOpenPack(pack.key).catch((failure: Error) => {
        setOpeningPack(null)
        tell(failure.message)
      })
    },
    [onOpenPack, tell]
  )

  useEffect(() => {
    if (notice === null) {
      return
    }
    const timer = window.setTimeout(() => setNotice(null), NOTICE_MILLIS)
    return () => window.clearTimeout(timer)
  }, [notice])

  const packReloaded = useCallback(() => setPackRevision((revision) => revision + 1), [])
  useServerEvents(client, setPlayers, setUsage, tell, setConnected, packReloaded)

  const openView = useCallback((viewId: string) => setWorkbench((current) => showView(current, viewId, entryViews.has(viewId))), [entryViews])
  const keepView = useCallback(
    (viewId: string) =>
      setWorkbench((current) => {
        const shown = showView(current, viewId, false)
        return keepTab(shown, focusedTab(shown).id)
      }),
    []
  )
  const commands = useCommandRegistry()
  const recent = useRecentTabs(setWorkbench)

  const runShortcut = useCallback(
    (action: ShortcutAction) => {
      switch (action) {
        case 'nextView': {
          const at = views.findIndex((candidate) => candidate.id === focused.viewId)
          openView(views[(at + 1) % views.length].id)
          return
        }
        case 'help':
          setHelpOpen((open) => !open)
          return
        case 'closeTab':
          setWorkbench((current) => closeTab(current, focusedTab(current).id))
          return
        case 'previousTab':
          setWorkbench((current) => stepTab(current, -1))
          return
        case 'nextTab':
          setWorkbench((current) => stepTab(current, 1))
          return
        case 'recentTab':
          recent.step(workbench)
          return
        default:
          commands.run(focused.id, action)
      }
    },
    [views, focused, openView, recent, workbench, commands]
  )

  useKeyboard(runShortcut, openView, setDetailed, recent.settle, views)

  const shared = useMemo<Shared>(
    () => ({ client, world, views, sceneViews, patternViews, packRevision, regions, players, detailed, links, lastMapArea, report, tell }),
    [client, world, views, sceneViews, patternViews, packRevision, regions, players, detailed, links, report, tell]
  )
  const renderTab = useCallback(
    (tab: Tab, pane: Pane, shown: boolean) => {
      const inFocus = shown && workbench.focusedPaneId === pane.id
      return (
        <SidebarSlot.Provider value={inFocus ? sidebarSlot : null}>
          <DetailSlot.Provider value={inFocus ? detailSlot : null}>
            <TabHost tab={tab} shown={shown} focused={inFocus} change={change} shared={shared} />
          </DetailSlot.Provider>
        </SidebarSlot.Provider>
      )
    },
    [workbench.focusedPaneId, sidebarSlot, detailSlot, change, shared]
  )

  useForgetClosedTabs(workbench, setReports)

  const focusedReport = reports[focused.id] ?? {}
  const showsMap = showsMapOf(focused.viewId)
  const label = focused.title ?? viewLabel(focused.viewId)
  return (
    <div className="flex h-full flex-col">
      <StudioToolbar world={world} openingPack={openingPack} loadPacks={loadPacks} onOpenPack={openPack} onHelp={() => setHelpOpen(true)} onSettings={() => setSettingsOpen(true)} />
      <div className={cn('relative flex min-h-0 flex-1 gap-1.5 pr-1.5', ISLAND_FLUSH_WITH_STRIPE)}>
        {openingPack !== null && (
          <div className="absolute inset-0 z-40 flex items-center justify-center bg-background/70 backdrop-motion">
            <div className="popup flex items-center gap-2 px-4 py-3 dialog-motion">
              <Glyph glyph="refresh" className="animate-spin" />
              Opening {openingPack.name}…
            </div>
          </div>
        )}
        {!connected && (
          <div className="absolute inset-0 z-40 flex items-center justify-center bg-background/70 backdrop-motion">
            <div className="popup p-5 text-center dialog-motion">
              <p className="font-medium">Lost the connection to the server</p>
              <button className="mt-3 text-link hover:underline" onClick={onDisconnect}>
                Back to the world list
              </button>
            </div>
          </div>
        )}
        <ViewList views={views} current={focused.viewId} onOpen={openView} onKeep={keepView} slotRef={setSidebarSlot} />
        <TabCommands.Provider value={commands.register}>
          <PaneLayout workbench={workbench} change={change} viewLabel={viewLabel} renderTab={renderTab} keepsRunning={(tab) => showsMapOf(tab.viewId)} />
        </TabCommands.Provider>
        <div ref={setDetailSlot} className="flex shrink-0 flex-col gap-1.5 empty:hidden" />
      </div>
      <StatusBar
        viewLabel={showsMap ? label : statusLabel(viewLabel(focused.viewId), focusedReport.summary ?? null)}
        status={showsMap ? (focusedReport.status ?? null) : null}
        notice={notice}
        usage={usage}
        progress={showsMap ? null : (focusedReport.progress ?? null)}
      />
      <HelpDialog open={helpOpen} onOpenChange={setHelpOpen} views={views} />
      <SettingsDialog open={settingsOpen} onOpenChange={setSettingsOpen} views={views} theme={theme} />
    </div>
  )
}

interface TabHostProps {
  tab: Tab
  shown: boolean
  focused: boolean
  change(change: WorkbenchChange): void
  shared: Shared
}

/** Gives a tab's view its tab: what it shows, and how it opens other entries. */
function TabHost({ tab, shown, focused, change, shared }: TabHostProps): React.JSX.Element {
  const { id, viewId } = tab
  const choose = useCallback(
    (entry: string, keep?: boolean) =>
      change((current) => {
        const opened = openTab(current, { viewId, entry }, keep !== true)
        return keep === true ? keepTab(opened, focusedTab(opened).id) : opened
      }),
    [change, viewId]
  )
  const keep = useCallback(() => change((current) => keepTab(current, id)), [change, id])
  const settle = useCallback(
    (entry: string | null, title: string | null) => change((current) => titleTab(entry === null ? current : settleEntry(current, id, entry), id, title)),
    [change, id]
  )
  const handle = useMemo<TabHandle>(
    () => ({ id, viewId, entry: tab.entry, visible: shown, focused, choose, keep, settle }),
    [id, viewId, tab.entry, shown, focused, choose, keep, settle]
  )
  return (
    <TabContext.Provider value={handle}>
      <TabView tabId={id} viewId={viewId} shared={shared} />
    </TabContext.Provider>
  )
}

/** The view a tab shows. */
function TabView({ tabId, viewId, shared }: { tabId: string; viewId: string; shared: Shared }): React.JSX.Element | null {
  const { client, world, packRevision, report, tell } = shared
  const onSummary = useCallback((summary: string | null) => report(tabId, { summary }), [report, tabId])
  const onProgress = useCallback((progress: number | null) => report(tabId, { progress }), [report, tabId])
  const onStatus = useCallback((status: MapStatus | null) => report(tabId, { status }), [report, tabId])
  const onArea = useCallback((area: WorldRect) => (shared.lastMapArea.current = area), [shared.lastMapArea])
  // The 3D world opens around where a map looked last, and stays there as maps move on.
  const [openingArea] = useState(() => shared.lastMapArea.current)
  const view = shared.views.find((candidate) => candidate.id === viewId)
  const sceneView = shared.sceneViews.find((candidate) => candidate.id === viewId)
  if (sceneView !== undefined) {
    return <SceneStudio client={client} view={sceneView} packRevision={packRevision} onSummary={onSummary} tell={tell} />
  }
  const patternView = shared.patternViews.find((candidate) => candidate.id === viewId)
  if (patternView !== undefined) {
    return <PatternStudio client={client} view={patternView} packRevision={packRevision} onSummary={onSummary} tell={tell} />
  }
  if (view === undefined) {
    return null
  }
  switch (viewId) {
    case WORLD_3D.id:
      return <WorldStudio3D title={view.label} client={client} world={world} mapArea={openingArea} packRevision={packRevision} onSummary={onSummary} tell={tell} />
    case SAVED_WORLD.id:
      return <SavedWorldStudio title={view.label} client={client} world={world} onSummary={onSummary} onProgress={onProgress} tell={tell} />
    case PREGEN_VIEW_ID:
      return <PregenStudio title={view.label} client={client} onSummary={onSummary} tell={tell} />
    case IMAGE_MAPS_VIEW_ID:
      return <ImageMapStudio title={view.label} client={client} packRevision={packRevision} onSummary={onSummary} tell={tell} />
    case NOISE.id:
      return <NoiseStudio title={view.label} client={client} packRevision={packRevision} onSummary={onSummary} tell={tell} />
    default:
      return (
        <MapView
          client={client}
          world={world}
          view={view}
          packRevision={packRevision}
          regions={shared.regions}
          players={shared.players}
          detailed={shared.detailed}
          links={shared.links}
          onStatus={onStatus}
          onArea={onArea}
          tell={tell}
        />
      )
  }
}

/** Drops what closed tabs left behind, so a closed tab opened again starts afresh. */
function useForgetClosedTabs(workbench: Workbench, setReports: React.Dispatch<React.SetStateAction<Record<string, TabReport>>>): void {
  const open = useRef(new Set<string>())
  useEffect(() => {
    const now = new Set(panesOf(workbench.root).flatMap((pane) => pane.tabs.map((tab) => tab.id)))
    const closed = [...open.current].filter((id) => !now.has(id))
    open.current = now
    if (closed.length === 0) {
      return
    }
    closed.forEach(forgetTab)
    setReports((current) => Object.fromEntries(Object.entries(current).filter(([id]) => now.has(id))))
  }, [workbench.root, setReports])
}

/** The keys each tab's view takes while it is the focused one, such as the map's. */
function useCommandRegistry(): { register(tabId: string, run: (action: string) => void): () => void; run(tabId: string, action: string): void } {
  const handlers = useRef(new Map<string, (action: string) => void>())
  const register = useCallback((tabId: string, run: (action: string) => void) => {
    handlers.current.set(tabId, run)
    return () => {
      if (handlers.current.get(tabId) === run) {
        handlers.current.delete(tabId)
      }
    }
  }, [])
  const run = useCallback((tabId: string, action: string) => handlers.current.get(tabId)?.(action), [])
  return useMemo(() => ({ register, run }), [register, run])
}

/**
 * Ctrl+Tab steps through the tabs from the most recently used while Ctrl is held, as IntelliJ's switcher does, and
 * the tab shown when Ctrl comes up counts as used.
 */
function useRecentTabs(setWorkbench: React.Dispatch<React.SetStateAction<Workbench>>): { step(workbench: Workbench): void; settle(): void } {
  const stepping = useRef<{ order: string[]; at: number } | null>(null)
  const step = useCallback(
    (workbench: Workbench) => {
      const order = stepping.current?.order ?? workbench.used
      if (order.length < 2) {
        return
      }
      const at = ((stepping.current?.at ?? 0) + 1) % order.length
      stepping.current = { order, at }
      setWorkbench((current) => peekTab(current, order[at]))
    },
    [setWorkbench]
  )
  const settle = useCallback(() => {
    if (stepping.current === null) {
      return
    }
    stepping.current = null
    setWorkbench((current) => activateTab(current, focusedPane(current).activeId))
  }, [setWorkbench])
  return useMemo(() => ({ step, settle }), [step, settle])
}

/** The app's views with entries to list; every module view has them too. */
const ENTRY_VIEW_IDS = ['noise', 'imageMaps', 'savedWorld']

const SCENE_GROUP = { group: 'BLOCKS', groupLabel: 'Blocks' }
/** The app draws the 3D world from the server's biome tiles, so the server does not list it among its views. */
const WORLD_3D: ViewInfo = {
  id: 'world3d',
  label: '3D world',
  description: 'The terrain in 3D, coloured by biome, region or height, or as the blocks the world generates',
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
const IMAGE_MAPS_VIEW_ID = 'imageMaps'
const PREGEN_VIEW_ID = 'pregen'
const APP_VIEW_IDS = new Set([WORLD_3D.id, SAVED_WORLD.id, NOISE.id, IMAGE_MAPS_VIEW_ID, PREGEN_VIEW_ID])

function withAppViews(serverViews: ViewInfo[]): ViewInfo[] {
  const afterRegions = serverViews.findIndex((candidate) => candidate.id === 'regions') + 1
  return [...serverViews.slice(0, afterRegions), WORLD_3D, ...serverViews.slice(afterRegions)]
}

function asViewInfo(view: ModuleViewInfo): ViewInfo {
  return { ...view, ...SCENE_GROUP }
}

/** Views the pack's modules show, read again after the pack reloads; keep {@code load} stable. */
function useModuleViews<View extends ModuleViewInfo>(load: () => Promise<View[]>, packRevision: number): View[] {
  const [views, setViews] = useState<View[]>([])
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

function statusLabel(viewLabel: string, summary: string | null): string {
  return summary === null ? viewLabel : `${viewLabel} · ${summary}`
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

/** Players, usage and hotloads from the server; the first world event only says the world runs, which is known. */
function useServerEvents(
  client: BridgeClient,
  setPlayers: (players: Marker[]) => void,
  setUsage: (usage: ServerUsage) => void,
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
            onPackReloaded()
            tell('Pack reloaded, redrawing')
          } else {
            tell('The world stopped')
          }
        },
        onPlayers: setPlayers,
        onUsage: setUsage,
        onClosed: () => setConnected(false)
      },
      stop.signal
    )
    return () => stop.abort()
  }, [client, setPlayers, setUsage, tell, setConnected, onPackReloaded])
}

function useKeyboard(
  runShortcut: (action: ShortcutAction) => void,
  openView: (view: string) => void,
  setDetailed: (detailed: boolean) => void,
  settleRecent: () => void,
  views: ViewInfo[]
): void {
  const bindings = useBindings()
  useEffect(() => {
    const rows = shortcutRows(views)
    const down = (event: KeyboardEvent): void => {
      if (event.key === 'Shift') {
        setDetailed(true)
      }
      if (isTypingInto(event.target)) {
        return
      }
      const id = windowShortcutFor(event, rows, bindings)
      if (id === null) {
        return
      }
      event.preventDefault()
      const viewId = viewOfShortcutId(id)
      if (viewId === null) {
        runShortcut(id as ShortcutAction)
      } else {
        openView(viewId)
      }
    }
    const up = (event: KeyboardEvent): void => {
      if (event.key === 'Shift') {
        setDetailed(false)
      }
      if (event.key === 'Control' || event.key === 'Meta') {
        settleRecent()
      }
    }
    const blur = (): void => {
      setDetailed(false)
      settleRecent()
    }
    window.addEventListener('keydown', down)
    window.addEventListener('keyup', up)
    window.addEventListener('blur', blur)
    return () => {
      window.removeEventListener('keydown', down)
      window.removeEventListener('keyup', up)
      window.removeEventListener('blur', blur)
    }
  }, [runShortcut, openView, setDetailed, settleRecent, views, bindings])
}
