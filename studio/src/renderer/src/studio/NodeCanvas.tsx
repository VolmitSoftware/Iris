import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ContextMenu, ContextMenuTrigger } from '@/components/ui/context-menu'
import type { GraphEdit, GraphNode, SettingsType } from '../bridge/types'
import { boundsOf, canRegrow, GRID, idOf, layoutNodes, NODE_WIDTH, nodeAt, snappedToGrid, type PlacedNode, type Point } from '../editor/nodeLayout'
import { positionsAfterEdit } from '../editor/nodePositions'
import { nodeRows, type NodeRows } from '../editor/nodeRows'
import type { SuggestionStore } from '../editor/suggestionStore'
import { rolesOf, type SuggestionSource } from '../editor/suggestions'
import { NodeBox } from './NodeBox'
import { NodeEditingContext, type NodeEditing, type SettingRow } from './nodeEditing'
import { NodeMenu, type MenuTarget } from './NodeMenus'
import { NodeWires } from './NodeWires'
import { useNodeCamera, type Camera } from './useNodeCamera'
import { useNodePositions } from './useNodePositions'

const DRAG_START_PIXELS = 3
const MIDDLE_BUTTON = 1
const SMALLEST_DOT_SPACING = 6
const DOT_RADIUS = 1

/** A node being dragged to another place, or a part's ring being dragged onto the node it should grow from. */
type NodeGesture =
  | { kind: 'moving'; placed: PlacedNode; start: Point; moved: boolean; offset: Point }
  | { kind: 'regrowing'; placed: PlacedNode; pointer: Point; target: PlacedNode | null }

interface NodeCanvasProps {
  roots: GraphNode[]
  types: Record<string, SettingsType>
  /** The file parsed, to read what each setting is set to. */
  values: unknown
  /** The file, by its place in the pack, which keeps where its nodes were dragged. */
  file: string
  selectedId: string | null
  locked: boolean
  suggestions: SuggestionStore
  /** Filled with the way to fit every node into view, for the keys that live outside the canvas. */
  showAllRef: React.RefObject<(() => void) | null>
  onSelect(node: GraphNode | null): void
  onEdit(edit: GraphEdit): void
}

/**
 * The file's nodes on a grid, each listing its settings to edit in place, wired to the nodes they grow from. Drag the
 * background to look around, a node to move it, and a part's ring onto another node to grow it from there.
 */
export function NodeCanvas({ roots, types, values, file, selectedId, locked, suggestions, showAllRef, onSelect, onEdit }: NodeCanvasProps): React.JSX.Element {
  const frame = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState<ReadonlySet<string>>(new Set())
  const [openCurve, setOpenCurve] = useState<string | null>(null)
  const [positions, setPositions] = useNodePositions(file)
  const rowsById = useMemo(() => rowsOfNodes(roots, types, values, open, !locked), [roots, types, values, open, locked])
  const layout = useMemo(() => layoutNodes(roots, (node) => rowsById.get(idOf(node.path))!.height, new Map(Object.entries(positions))), [roots, rowsById, positions])
  const view = useNodeCamera(frame, boundsOf(layout.nodes))
  const [gesture, setGesture] = useState<NodeGesture | null>(null)
  const [menu, setMenu] = useState<MenuTarget | null>(null)
  const panFrom = useRef<Point | null>(null)
  const settingUnderMenu = useRef<SettingRow | null>(null)
  const chosenHere = useRef<string | null>(null)

  const edit = useCallback(
    (change: GraphEdit) => {
      setPositions((current) => positionsAfterEdit(current, change, values))
      onEdit(change)
    },
    [setPositions, onEdit, values]
  )
  const select = useCallback(
    (node: GraphNode) => {
      chosenHere.current = idOf(node.path)
      onSelect(node)
    },
    [onSelect]
  )
  const { showAll } = view
  const showEveryNode = useCallback(() => showAll(boundsOf(layout.nodes)), [showAll, layout])
  useEffect(() => {
    showAllRef.current = showEveryNode
    return () => {
      showAllRef.current = null
    }
  }, [showAllRef, showEveryNode])
  useRevealChosen(layout.nodes, selectedId, chosenHere, view.reveal)
  const editing = useEditing({ roots, locked, openCurve, setOpenCurve, setOpen, edit, suggestions, settingUnderMenu })

  const pointerDown = (event: React.PointerEvent<HTMLDivElement>): void => {
    const target = event.target as Element
    if (!event.currentTarget.contains(target) || (event.button !== 0 && event.button !== MIDDLE_BUTTON)) {
      return
    }
    const placed = event.button === 0 ? placedUnder(target, layout.nodes) : null
    if (placed === null) {
      event.currentTarget.setPointerCapture(event.pointerId)
      panFrom.current = { x: event.clientX, y: event.clientY }
      return
    }
    select(placed.node)
    if (target.closest('[data-node-control]') !== null) {
      return
    }
    event.currentTarget.setPointerCapture(event.pointerId)
    if (target.closest('[data-port="regrow"]') !== null) {
      setGesture({ kind: 'regrowing', placed, pointer: view.graphPoint(event.clientX, event.clientY), target: null })
    } else {
      setGesture({ kind: 'moving', placed, start: { x: event.clientX, y: event.clientY }, moved: false, offset: { x: 0, y: 0 } })
    }
  }

  // The gesture as it stands at {@code event}, worked out from the event itself: a release can come before the last
  // move was drawn.
  const gestureAt = (event: React.PointerEvent): NodeGesture | null => {
    if (gesture?.kind === 'moving') {
      const [dx, dy] = [event.clientX - gesture.start.x, event.clientY - gesture.start.y]
      const moved = gesture.moved || Math.hypot(dx, dy) > DRAG_START_PIXELS
      return { ...gesture, moved, offset: moved ? { x: dx / view.camera.scale, y: dy / view.camera.scale } : gesture.offset }
    }
    if (gesture?.kind === 'regrowing') {
      const pointer = view.graphPoint(event.clientX, event.clientY)
      const over = nodeAt(layout.nodes, pointer)
      const takesIt = over !== null && over !== gesture.placed && canRegrow(gesture.placed.node, over.node)
      return { ...gesture, pointer, target: takesIt ? over : null }
    }
    return null
  }

  const pointerMove = (event: React.PointerEvent): void => {
    if (panFrom.current !== null) {
      view.panBy(event.clientX - panFrom.current.x, event.clientY - panFrom.current.y)
      panFrom.current = { x: event.clientX, y: event.clientY }
      return
    }
    if (gesture !== null) {
      setGesture(gestureAt(event))
    }
  }

  const pointerUp = (event: React.PointerEvent): void => {
    panFrom.current = null
    const finished = gestureAt(event)
    setGesture(null)
    if (finished?.kind === 'moving' && finished.moved) {
      const { placed, offset } = finished
      const dropped = { x: snappedToGrid(placed.x + offset.x), y: snappedToGrid(placed.y + offset.y) }
      if (dropped.x !== placed.x || dropped.y !== placed.y) {
        setPositions((current) => ({ ...current, [placed.id]: dropped }))
      }
    } else if (finished?.kind === 'regrowing' && finished.target !== null) {
      edit({ op: 'move', path: finished.placed.node.path, toList: finished.target.node.childList! })
    }
  }

  const contextMenu = (event: React.MouseEvent<HTMLDivElement>): void => {
    const target = event.target as Element
    const setting = settingUnderMenu.current
    settingUnderMenu.current = null
    if (!event.currentTarget.contains(target)) {
      event.preventDefault()
      return
    }
    const placed = placedUnder(target, layout.nodes)
    if (placed === null) {
      setMenu({ on: 'background', root: roots[0] ?? null })
      return
    }
    select(placed.node)
    setMenu({ on: 'node', node: placed.node, setting })
  }

  const moved = gesture?.kind === 'moving' && gesture.moved ? { id: gesture.placed.id, offset: gesture.offset } : null
  const regrow = gesture?.kind === 'regrowing' ? { part: gesture.placed, pointer: gesture.pointer, onTarget: gesture.target !== null } : null
  const dropTargetId = gesture?.kind === 'regrowing' ? (gesture.target?.id ?? null) : null
  const { camera } = view

  return (
    <ContextMenu onOpenChange={(isOpen) => !isOpen && setMenu(null)}>
      <ContextMenuTrigger asChild>
        <div
          ref={frame}
          className="relative h-full w-full touch-none overflow-hidden bg-card select-none"
          style={gridStyle(camera)}
          onPointerDown={pointerDown}
          onPointerMove={pointerMove}
          onPointerUp={pointerUp}
          onPointerCancel={() => {
            panFrom.current = null
            setGesture(null)
          }}
          onContextMenu={contextMenu}
          onDoubleClick={(event) => placedUnder(event.target as Element, layout.nodes) === null && event.currentTarget.contains(event.target as Element) && showEveryNode()}
        >
          <div className="absolute top-0 left-0 origin-top-left" style={{ transform: `translate(${camera.x}px, ${camera.y}px) scale(${camera.scale})` }}>
            <NodeEditingContext.Provider value={editing}>
              <NodeWires wires={layout.wires} selectedId={selectedId} moved={moved} regrow={regrow} />
              {layout.nodes.map((placed) => (
                <NodeBox
                  key={placed.id}
                  placed={placed}
                  rows={rowsById.get(placed.id)!}
                  selected={placed.id === selectedId}
                  dropTarget={placed.id === dropTargetId}
                  offset={moved?.id === placed.id ? moved.offset : null}
                  locked={locked}
                />
              ))}
            </NodeEditingContext.Provider>
          </div>
          {Math.abs(camera.scale - 1) > 0.001 && (
            <span className="pointer-events-none absolute right-2 bottom-1.5 text-[11px] text-muted-foreground tabular-nums">{Math.round(camera.scale * 100)}%</span>
          )}
        </div>
      </ContextMenuTrigger>
      {menu !== null && (
        <NodeMenu
          target={menu}
          locked={locked}
          onEdit={edit}
          onOpenCurve={setOpenCurve}
          onArrange={() => setPositions({})}
          onShowAll={showEveryNode}
          onResetZoom={view.resetZoom}
        />
      )}
    </ContextMenu>
  )
}

function rowsOfNodes(roots: GraphNode[], types: Record<string, SettingsType>, values: unknown, open: ReadonlySet<string>, editable: boolean): Map<string, NodeRows> {
  const rows = new Map<string, NodeRows>()
  const visit = (node: GraphNode): void => {
    rows.set(idOf(node.path), nodeRows(node, types, values, open, editable))
    node.children.forEach((link) => visit(link.node))
  }
  roots.forEach(visit)
  return rows
}

function placedUnder(target: Element, nodes: PlacedNode[]): PlacedNode | null {
  const id = target.closest('[data-node-id]')?.getAttribute('data-node-id')
  return nodes.find((placed) => placed.id === id) ?? null
}

// Each dot sits on a point nodes snap to, so they stay on the dots at any zoom.
function gridStyle(camera: Camera): React.CSSProperties {
  const spacing = GRID * camera.scale
  if (spacing < SMALLEST_DOT_SPACING) {
    return {}
  }
  return {
    backgroundImage: `radial-gradient(circle at ${DOT_RADIUS}px ${DOT_RADIUS}px, var(--overlay-border) ${DOT_RADIUS}px, transparent ${DOT_RADIUS + 0.5}px)`,
    backgroundSize: `${spacing}px ${spacing}px`,
    backgroundPosition: `${camera.x - DOT_RADIUS}px ${camera.y - DOT_RADIUS}px`
  }
}

// A node chosen elsewhere, such as by picking its part in the scene, is brought into sight; one chosen here already is.
function useRevealChosen(nodes: PlacedNode[], selectedId: string | null, chosenHere: React.RefObject<string | null>, reveal: (area: { left: number; top: number; right: number; bottom: number }) => void): void {
  const handled = useRef<string | null>(null)
  useEffect(() => {
    if (selectedId === handled.current) {
      return
    }
    handled.current = selectedId
    const placed = nodes.find((candidate) => candidate.id === selectedId)
    if (placed === undefined || selectedId === chosenHere.current) {
      return
    }
    reveal({ left: placed.x, top: placed.y, right: placed.x + NODE_WIDTH, bottom: placed.y + placed.height })
  }, [nodes, selectedId, chosenHere, reveal])
}

interface EditingInputs {
  roots: GraphNode[]
  locked: boolean
  openCurve: string | null
  setOpenCurve(rowId: string | null): void
  setOpen: React.Dispatch<React.SetStateAction<ReadonlySet<string>>>
  edit(change: GraphEdit): void
  suggestions: SuggestionStore
  settingUnderMenu: React.RefObject<SettingRow | null>
}

function useEditing({ roots, locked, openCurve, setOpenCurve, setOpen, edit, suggestions, settingUnderMenu }: EditingInputs): NodeEditing {
  const roles = useMemo(() => rolesOf(roots), [roots])
  return useMemo(
    () => ({
      locked,
      openCurve,
      onOpenCurve: setOpenCurve,
      onToggleFold: (foldId: string) =>
        setOpen((current) => {
          const next = new Set(current)
          if (!next.delete(foldId)) {
            next.add(foldId)
          }
          return next
        }),
      onEdit: edit,
      loadSuggestions: (source: SuggestionSource) => (source.from === 'roles' ? Promise.resolve(roles) : suggestions.load(source)),
      onSettingMenu: (row: SettingRow) => {
        settingUnderMenu.current = row
      }
    }),
    [locked, openCurve, setOpenCurve, setOpen, edit, roles, suggestions, settingUnderMenu]
  )
}
