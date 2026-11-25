import { Activity, createContext, useContext, useRef, useState } from 'react'
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuSeparator, ContextMenuShortcut, ContextMenuTrigger } from '@/components/ui/context-menu'
import { cn } from '@/lib/utils'
import { Glyph } from './Glyph'
import { PaneChromeContext } from './Sidebar'
import { useKeyText } from './useBindings'
import { ViewIcon } from './ViewList'
import {
  activateTab,
  closeOtherTabs,
  closeTab,
  focusPane,
  keepTab,
  moveTab,
  resizeSplit,
  splitTab,
  type Edge,
  type LayoutNode,
  type Pane,
  type Split,
  type Tab,
  type Workbench
} from './workbench'

/** A change to the panes and tabs, as the workbench functions make them. */
export type WorkbenchChange = (workbench: Workbench) => Workbench

interface PaneLayoutProps {
  workbench: Workbench
  change(change: WorkbenchChange): void
  /** What a tab's view is called, for a tab its view did not name yet. */
  viewLabel(viewId: string): string
  /** Draws a tab's view; called for every open tab, as a hidden tab keeps its state. */
  renderTab(tab: Tab, pane: Pane, shown: boolean): React.ReactNode
  /** Whether a hidden tab's view keeps running, for a view cheap to keep that would be slow to draw again. */
  keepsRunning(tab: Tab): boolean
}

type DropTarget = { paneId: string; edge: Edge | 'center' }

interface Dragging {
  tabId: string | null
  start(tabId: string): void
  end(): void
}

const DraggingContext = createContext<Dragging>({ tabId: null, start: () => undefined, end: () => undefined })
const TAB_DRAG_TYPE = 'application/x-iris-tab'
// Within this share of a pane's width or height from its edge, a dropped tab splits the pane there.
const EDGE_DROP_SHARE = 0.25

/** The panes and their tabs, split side by side or above each other, as IntelliJ's editor splits are. */
export function PaneLayout({ workbench, change, viewLabel, renderTab, keepsRunning }: PaneLayoutProps): React.JSX.Element {
  const [dragged, setDragged] = useState<string | null>(null)
  const dragging: Dragging = { tabId: dragged, start: setDragged, end: () => setDragged(null) }
  const single = workbench.root.kind === 'pane' && workbench.root.tabs.length === 1
  return (
    <DraggingContext.Provider value={dragging}>
      <div className="flex min-h-0 min-w-0 flex-1">
        <LayoutBranch
          node={workbench.root}
          atLeftEdge
          workbench={workbench}
          single={single}
          change={change}
          viewLabel={viewLabel}
          renderTab={renderTab}
          keepsRunning={keepsRunning}
        />
      </div>
    </DraggingContext.Provider>
  )
}

interface BranchProps {
  node: LayoutNode
  atLeftEdge: boolean
  workbench: Workbench
  /** Whether only one tab is open in all, which then has no close button. */
  single: boolean
  change(change: WorkbenchChange): void
  viewLabel(viewId: string): string
  renderTab(tab: Tab, pane: Pane, shown: boolean): React.ReactNode
  keepsRunning(tab: Tab): boolean
}

function LayoutBranch(props: BranchProps): React.JSX.Element {
  const { node } = props
  if (node.kind === 'pane') {
    return <PaneView {...props} pane={node} />
  }
  return <SplitView {...props} split={node} />
}

function SplitView({ split, ...props }: BranchProps & { split: Split }): React.JSX.Element {
  const box = useRef<HTMLDivElement>(null)
  const row = split.direction === 'row'
  const resize = (index: number, delta: number): void => {
    const length = row ? box.current!.clientWidth : box.current!.clientHeight
    const share = delta / Math.max(1, length)
    const sizes = [...split.sizes]
    sizes[index] += share
    sizes[index + 1] -= share
    props.change((workbench) => resizeSplit(workbench, split.id, sizes))
  }
  return (
    <div ref={box} className={cn('flex min-h-0 min-w-0 flex-1', row ? 'flex-row' : 'flex-col')}>
      {split.children.map((child, index) => (
        <div key={child.id} className="contents">
          {index > 0 && <Divider row={row} onDrag={(delta) => resize(index - 1, delta)} />}
          <div className="flex min-h-0 min-w-0" style={{ flex: `${split.sizes[index]} 1 0px` }}>
            <LayoutBranch {...props} node={child} atLeftEdge={props.atLeftEdge && (!row || index === 0)} />
          </div>
        </div>
      ))}
    </div>
  )
}

/** The gap between two panes, dragged to share the room between them differently. */
function Divider({ row, onDrag }: { row: boolean; onDrag(delta: number): void }): React.JSX.Element {
  const last = useRef<number | null>(null)
  return (
    <div
      role="separator"
      aria-orientation={row ? 'vertical' : 'horizontal'}
      className={cn('group relative shrink-0', row ? 'w-1.5 cursor-col-resize' : 'h-1.5 cursor-row-resize')}
      onPointerDown={(event) => {
        event.currentTarget.setPointerCapture(event.pointerId)
        last.current = row ? event.clientX : event.clientY
      }}
      onPointerMove={(event) => {
        if (last.current === null) {
          return
        }
        const at = row ? event.clientX : event.clientY
        onDrag(at - last.current)
        last.current = at
      }}
      onPointerUp={() => {
        last.current = null
      }}
    >
      <span className={cn('absolute rounded-full bg-primary opacity-0 transition-opacity group-hover:opacity-60', row ? 'inset-y-2 left-0.5 w-0.5' : 'inset-x-2 top-0.5 h-0.5')} />
    </div>
  )
}

function PaneView({ pane, atLeftEdge, workbench, single, change, viewLabel, renderTab, keepsRunning }: BranchProps & { pane: Pane }): React.JSX.Element {
  const dragging = useContext(DraggingContext)
  const focused = workbench.focusedPaneId === pane.id
  const tabStrip = <TabStrip pane={pane} focused={focused} single={single} change={change} viewLabel={viewLabel} />
  return (
    <div
      data-pane={pane.id}
      data-focused={focused || undefined}
      className="relative flex min-h-0 min-w-0 flex-1"
      onPointerDownCapture={() => !focused && change((current) => focusPane(current, pane.id))}
    >
      {pane.tabs.map((tab) => {
        const shown = tab.id === pane.activeId
        // A hidden tab lets go of what it drew with, its renderer and its tiles, unless its view is cheap to keep.
        return (
          <div key={`${tab.id}:${tab.viewId}`} className={cn('flex min-h-0 min-w-0 flex-1', !shown && 'hidden')}>
            <Activity mode={shown || keepsRunning(tab) ? 'visible' : 'hidden'}>
              <PaneChromeContext.Provider value={shown ? { tabStrip, atLeftEdge } : null}>{renderTab(tab, pane, shown)}</PaneChromeContext.Provider>
            </Activity>
          </div>
        )
      })}
      {dragging.tabId !== null && <DropZones pane={pane} tabId={dragging.tabId} change={change} onDone={dragging.end} />}
    </div>
  )
}

/** Where a dragged tab lands on a pane: at an edge it splits the pane there, in the middle it joins the pane's tabs. */
function DropZones({ pane, tabId, change, onDone }: { pane: Pane; tabId: string; change(change: WorkbenchChange): void; onDone(): void }): React.JSX.Element {
  const [target, setTarget] = useState<DropTarget | null>(null)
  return (
    <div
      className="absolute inset-x-0 top-10 bottom-0 z-30"
      onDragOver={(event) => {
        event.preventDefault()
        event.dataTransfer.dropEffect = 'move'
        const edge = edgeAt(event.currentTarget.getBoundingClientRect(), event.clientX, event.clientY)
        setTarget((current) => (current?.edge === edge ? current : { paneId: pane.id, edge }))
      }}
      onDragLeave={() => setTarget(null)}
      onDrop={(event) => {
        event.preventDefault()
        const edge = edgeAt(event.currentTarget.getBoundingClientRect(), event.clientX, event.clientY)
        change((workbench) => (edge === 'center' ? moveTab(workbench, tabId, pane.id) : splitTab(workbench, tabId, pane.id, edge, false)))
        setTarget(null)
        onDone()
      }}
    >
      {target !== null && <span className={cn('pointer-events-none absolute rounded-[10px] border-2 border-primary bg-primary/15', DROP_AREAS[target.edge])} />}
    </div>
  )
}

const DROP_AREAS: Record<Edge | 'center', string> = {
  left: 'inset-y-0 left-0 w-1/2',
  right: 'inset-y-0 right-0 w-1/2',
  top: 'inset-x-0 top-0 h-1/2',
  bottom: 'inset-x-0 bottom-0 h-1/2',
  center: 'inset-0'
}

function edgeAt(bounds: DOMRect, clientX: number, clientY: number): Edge | 'center' {
  const x = (clientX - bounds.left) / bounds.width
  const y = (clientY - bounds.top) / bounds.height
  const nearest = Math.min(x, 1 - x, y, 1 - y)
  if (nearest > EDGE_DROP_SHARE) {
    return 'center'
  }
  if (nearest === x) {
    return 'left'
  }
  if (nearest === 1 - x) {
    return 'right'
  }
  return nearest === y ? 'top' : 'bottom'
}

interface TabStripProps {
  pane: Pane
  focused: boolean
  single: boolean
  change(change: WorkbenchChange): void
  viewLabel(viewId: string): string
}

/** A pane's tabs, in the header of the island of the one it shows. */
function TabStrip({ pane, focused, single, change, viewLabel }: TabStripProps): React.JSX.Element {
  const dragging = useContext(DraggingContext)
  const [insertAt, setInsertAt] = useState<number | null>(null)
  const indexAt = (event: React.DragEvent): number => {
    const tabs = [...event.currentTarget.querySelectorAll<HTMLElement>('[role=tab]')]
    const after = tabs.findIndex((tab) => {
      const bounds = tab.getBoundingClientRect()
      return event.clientX < bounds.left + bounds.width / 2
    })
    return after < 0 ? tabs.length : after
  }
  return (
    <div
      role="tablist"
      aria-label="Tabs"
      className="relative flex min-w-0 flex-1 items-stretch overflow-x-auto [scrollbar-width:none]"
      onDragOver={(event) => {
        if (dragging.tabId === null) {
          return
        }
        event.preventDefault()
        setInsertAt(indexAt(event))
      }}
      onDragLeave={() => setInsertAt(null)}
      onDrop={(event) => {
        const tabId = event.dataTransfer.getData(TAB_DRAG_TYPE) || dragging.tabId
        if (tabId === null) {
          return
        }
        event.preventDefault()
        const index = indexAt(event)
        const from = pane.tabs.findIndex((tab) => tab.id === tabId)
        change((workbench) => moveTab(workbench, tabId, pane.id, from >= 0 && from < index ? index - 1 : index))
        setInsertAt(null)
        dragging.end()
      }}
    >
      {pane.tabs.map((tab, index) => (
        <TabButton
          key={tab.id}
          tab={tab}
          active={tab.id === pane.activeId}
          focused={focused}
          closable={!single}
          label={tab.title ?? viewLabel(tab.viewId)}
          insertBefore={insertAt === index}
          change={change}
          paneId={pane.id}
        />
      ))}
      {insertAt === pane.tabs.length && <span className="w-0.5 shrink-0 self-stretch bg-primary" />}
    </div>
  )
}

interface TabButtonProps {
  tab: Tab
  paneId: string
  active: boolean
  focused: boolean
  closable: boolean
  label: string
  insertBefore: boolean
  change(change: WorkbenchChange): void
}

function TabButton({ tab, paneId, active, focused, closable, label, insertBefore, change }: TabButtonProps): React.JSX.Element {
  const dragging = useContext(DraggingContext)
  const keyText = useKeyText()
  return (
    <ContextMenu>
      <ContextMenuTrigger asChild>
        <div
          role="tab"
          aria-selected={active}
          data-tab={tab.id}
          data-preview={tab.preview || undefined}
          title={label}
          draggable
          onDragStart={(event) => {
            event.dataTransfer.setData(TAB_DRAG_TYPE, tab.id)
            event.dataTransfer.effectAllowed = 'move'
            dragging.start(tab.id)
          }}
          onDragEnd={dragging.end}
          onMouseDown={(event) => {
            if (event.button === 1) {
              event.preventDefault()
              change((workbench) => closeTab(workbench, tab.id))
            }
          }}
          onClick={() => change((workbench) => activateTab(workbench, tab.id))}
          onDoubleClick={() => change((workbench) => keepTab(workbench, tab.id))}
          className={cn(
            'group relative flex max-w-56 shrink-0 cursor-default items-center gap-1.5 pr-1 pl-3 text-[13px] select-none',
            insertBefore && 'shadow-[inset_2px_0_0_var(--primary)]',
            active ? 'text-foreground' : 'text-secondary-text hover:bg-accent/60 hover:text-foreground'
          )}
        >
          <ViewIcon viewId={tab.viewId} className="size-3.5 shrink-0" />
          <span className={cn('truncate', tab.preview && 'italic')}>{label}</span>
          <button
            aria-label={`Close ${label}`}
            tabIndex={-1}
            onClick={(event) => {
              event.stopPropagation()
              change((workbench) => closeTab(workbench, tab.id))
            }}
            className={cn(
              'flex size-5 shrink-0 items-center justify-center rounded-sm hover:bg-accent',
              !closable && 'invisible',
              closable && !active && 'opacity-0 group-hover:opacity-100'
            )}
          >
            <Glyph glyph="close" small />
          </button>
          {active && <span className={cn('absolute inset-x-2 bottom-0 h-0.5 rounded-full', focused ? 'bg-primary' : 'bg-muted-foreground/50')} />}
        </div>
      </ContextMenuTrigger>
      <ContextMenuContent className="min-w-56">
        {tab.preview && <ContextMenuItem onSelect={() => change((workbench) => keepTab(workbench, tab.id))}>Keep Tab Open</ContextMenuItem>}
        <ContextMenuItem disabled={!closable} onSelect={() => change((workbench) => closeTab(workbench, tab.id))}>
          Close
          <ContextMenuShortcut>{keyText('closeTab')}</ContextMenuShortcut>
        </ContextMenuItem>
        <ContextMenuItem onSelect={() => change((workbench) => closeOtherTabs(workbench, tab.id))}>Close Other Tabs</ContextMenuItem>
        <ContextMenuSeparator />
        <ContextMenuItem onSelect={() => change((workbench) => splitTab(workbench, tab.id, paneId, 'right', true))}>Split Right</ContextMenuItem>
        <ContextMenuItem onSelect={() => change((workbench) => splitTab(workbench, tab.id, paneId, 'bottom', true))}>Split Down</ContextMenuItem>
        <ContextMenuItem onSelect={() => change((workbench) => splitTab(workbench, tab.id, paneId, 'right', false))}>Split and Move Right</ContextMenuItem>
        <ContextMenuItem onSelect={() => change((workbench) => splitTab(workbench, tab.id, paneId, 'bottom', false))}>Split and Move Down</ContextMenuItem>
      </ContextMenuContent>
    </ContextMenu>
  )
}
