import { Box, Circle, Flower2, GitCommitVertical, GitFork, Group, Link2, Paintbrush, Sparkles, type LucideIcon } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuSeparator, ContextMenuSub, ContextMenuSubContent, ContextMenuSubTrigger, ContextMenuTrigger } from '@/components/ui/context-menu'
import { cn } from '@/lib/utils'
import type { GraphEdit, GraphNode, JsonPath } from '../bridge/types'
import { idOf, isWithin, layoutNodes, NODE_HEIGHT, NODE_WIDTH, type NodeLayout, type PlacedNode } from '../editor/nodeLayout'

const KIND_ICONS: Record<string, LucideIcon> = {
  Object: Box,
  Stem: GitCommitVertical,
  Blob: Circle,
  Branches: GitFork,
  Cluster: Sparkles,
  Group: Group,
  'Shared part': Link2,
  Material: Paintbrush,
  Decoration: Flower2
}
const FIT_MARGIN = 24
const LARGEST_FIT_SCALE = 1
// Below this the node text can't be read; a larger tree is panned instead of shrunk further.
const SMALLEST_FIT_SCALE = 0.8
const SMALLEST_SCALE = 0.25
const LARGEST_SCALE = 2
const WHEEL_ZOOM_PER_PIXEL = 0.0015
const DRAG_START_PIXELS = 4
const GRID_SPACING = 16

interface Camera {
  x: number
  y: number
  scale: number
}

interface NodeDrag {
  placed: PlacedNode
  startX: number
  startY: number
  pointerX: number
  pointerY: number
  moved: boolean
  target: PlacedNode | null
}

interface NodeCanvasProps {
  roots: GraphNode[]
  /** Tells files apart, so a new one is fitted into view. */
  fileKey: string
  selectedId: string | null
  locked: boolean
  onSelect(node: GraphNode | null): void
  onEdit(edit: GraphEdit): void
}

/** The file as a tree of nodes: drag the background to look around, drag a node onto another to grow it from there. */
export function NodeCanvas({ roots, fileKey, selectedId, locked, onSelect, onEdit }: NodeCanvasProps): React.JSX.Element {
  const layout = useMemo(() => layoutNodes(roots), [roots])
  const frame = useRef<HTMLDivElement>(null)
  const [camera, setCamera] = useCamera(frame, layout, fileKey)
  const [drag, setDrag] = useState<NodeDrag | null>(null)
  const [menuNode, setMenuNode] = useState<GraphNode | null>(null)
  const pan = useRef<{ x: number; y: number } | null>(null)

  const canvasPoint = (clientX: number, clientY: number): { x: number; y: number } => {
    const bounds = frame.current!.getBoundingClientRect()
    return { x: (clientX - bounds.left - camera.x) / camera.scale, y: (clientY - bounds.top - camera.y) / camera.scale }
  }

  const pointerDown = (event: React.PointerEvent): void => {
    if (event.button !== 0) {
      return
    }
    event.currentTarget.setPointerCapture(event.pointerId)
    const placed = nodeAt(layout, canvasPoint(event.clientX, event.clientY))
    if (placed === null) {
      pan.current = { x: event.clientX, y: event.clientY }
      return
    }
    setDrag({ placed, startX: event.clientX, startY: event.clientY, pointerX: event.clientX, pointerY: event.clientY, moved: false, target: null })
  }

  const pointerMove = (event: React.PointerEvent): void => {
    if (pan.current !== null) {
      const [dx, dy] = [event.clientX - pan.current.x, event.clientY - pan.current.y]
      pan.current = { x: event.clientX, y: event.clientY }
      setCamera((current) => ({ ...current, x: current.x + dx, y: current.y + dy }))
      return
    }
    if (drag === null) {
      return
    }
    const moved = drag.moved || Math.hypot(event.clientX - drag.startX, event.clientY - drag.startY) > DRAG_START_PIXELS
    const canMove = moved && !locked && drag.placed.node.movable
    const target = canMove ? moveTarget(layout, drag.placed, canvasPoint(event.clientX, event.clientY)) : null
    setDrag({ ...drag, pointerX: event.clientX, pointerY: event.clientY, moved, target })
  }

  const pointerUp = (): void => {
    pan.current = null
    if (drag === null) {
      return
    }
    if (!drag.moved) {
      onSelect(drag.placed.node)
    } else if (drag.target !== null) {
      onEdit({ op: 'move', path: drag.placed.node.path, toList: drag.target.node.childList! })
    }
    setDrag(null)
  }

  const dragging = drag !== null && drag.moved && drag.placed.node.movable && !locked
  const dragOffset = dragging ? { x: (drag.pointerX - drag.startX) / camera.scale, y: (drag.pointerY - drag.startY) / camera.scale } : null

  return (
    <ContextMenu onOpenChange={(open) => !open && setMenuNode(null)}>
      <ContextMenuTrigger asChild>
        <div
          ref={frame}
          className="relative h-full w-full touch-none overflow-hidden bg-background select-none"
          style={{
            backgroundImage: 'radial-gradient(var(--overlay-border) 1px, transparent 1px)',
            backgroundSize: `${GRID_SPACING * camera.scale}px ${GRID_SPACING * camera.scale}px`,
            backgroundPosition: `${camera.x}px ${camera.y}px`
          }}
          onPointerDown={pointerDown}
          onPointerMove={pointerMove}
          onPointerUp={pointerUp}
          onPointerCancel={() => {
            pan.current = null
            setDrag(null)
          }}
          onContextMenu={(event) => {
            const placed = nodeAt(layout, canvasPoint(event.clientX, event.clientY))
            setMenuNode(placed?.node ?? null)
            if (placed === null) {
              event.preventDefault()
            } else {
              onSelect(placed.node)
            }
          }}
          onDoubleClick={(event) => nodeAt(layout, canvasPoint(event.clientX, event.clientY)) === null && setCamera(fitted(frame.current!, layout))}
        >
          <div className="absolute top-0 left-0 origin-top-left" style={{ transform: `translate(${camera.x}px, ${camera.y}px) scale(${camera.scale})` }}>
            <Wires layout={layout} />
            {layout.nodes.map((placed) => (
              <NodeBox
                key={placed.id}
                placed={placed}
                selected={placed.id === selectedId}
                target={drag?.target?.id === placed.id}
                offset={dragging && drag.placed.id === placed.id ? dragOffset : null}
              />
            ))}
          </div>
        </div>
      </ContextMenuTrigger>
      {menuNode !== null && <NodeMenu node={menuNode} locked={locked} onEdit={onEdit} />}
    </ContextMenu>
  )
}

function NodeBox({ placed, selected, target, offset }: { placed: PlacedNode; selected: boolean; target: boolean; offset: { x: number; y: number } | null }): React.JSX.Element {
  const { node } = placed
  const Icon = KIND_ICONS[node.kind] ?? Box
  const detail = node.roles.length > 0 ? node.roles.join(', ') : node.title === node.kind ? null : node.kind
  return (
    <div
      className={cn(
        'absolute flex items-center gap-2 rounded-md border bg-card px-2.5 shadow-xs',
        selected ? 'border-primary ring-1 ring-primary' : 'border-overlay-border',
        target && 'border-primary bg-selection',
        offset !== null && 'z-10 opacity-80 shadow-md'
      )}
      style={{
        left: placed.x + (offset?.x ?? 0),
        top: placed.y + (offset?.y ?? 0),
        width: NODE_WIDTH,
        height: NODE_HEIGHT,
        cursor: node.movable ? 'grab' : 'default'
      }}
    >
      <Icon className="size-4 shrink-0 text-secondary-text" />
      <div className="min-w-0 leading-tight">
        <div className="truncate text-[12.5px] font-medium">{node.title}</div>
        {detail !== null && <div className="truncate text-[11px] text-secondary-text">{detail}</div>}
      </div>
    </div>
  )
}

function Wires({ layout }: { layout: NodeLayout }): React.JSX.Element {
  return (
    <svg className="pointer-events-none absolute top-0 left-0 overflow-visible" width={layout.width} height={layout.height}>
      {layout.wires.map(({ from, to, label }) => {
        const [startX, startY] = [from.x + NODE_WIDTH, from.y + NODE_HEIGHT / 2]
        const [endX, endY] = [to.x, to.y + NODE_HEIGHT / 2]
        const bend = (endX - startX) / 2
        return (
          <g key={`${from.id}>${to.id}`}>
            <path d={`M${startX},${startY} C${startX + bend},${startY} ${endX - bend},${endY} ${endX},${endY}`} className="fill-none stroke-input" strokeWidth={1.5} />
            {label !== '' && (
              <text x={endX - 6} y={endY - 6} textAnchor="end" className="fill-secondary-text text-[10.5px]">
                {label}
              </text>
            )}
          </g>
        )
      })}
    </svg>
  )
}

function NodeMenu({ node, locked, onEdit }: { node: GraphNode; locked: boolean; onEdit(edit: GraphEdit): void }): React.JSX.Element {
  const removable = node.duplicable
  return (
    <ContextMenuContent className="min-w-[180px]">
      {node.additions.length > 0 && (
        <ContextMenuSub>
          <ContextMenuSubTrigger disabled={locked}>Add</ContextMenuSubTrigger>
          <ContextMenuSubContent>
            {node.additions.map((addition) => (
              <ContextMenuItem key={`${idOf(addition.list)}:${addition.label}`} onSelect={() => onEdit({ op: 'append', list: addition.list, literal: addition.literal })}>
                {addition.label}
              </ContextMenuItem>
            ))}
          </ContextMenuSubContent>
        </ContextMenuSub>
      )}
      <ContextMenuItem disabled={locked || !node.duplicable} onSelect={() => onEdit({ op: 'duplicate', path: node.path })}>
        Duplicate
      </ContextMenuItem>
      <ContextMenuSeparator />
      <ContextMenuItem variant="destructive" disabled={locked || !removable} onSelect={() => onEdit({ op: 'delete', path: node.path })}>
        Delete
      </ContextMenuItem>
    </ContextMenuContent>
  )
}

function nodeAt(layout: NodeLayout, point: { x: number; y: number }): PlacedNode | null {
  return layout.nodes.find((placed) => point.x >= placed.x && point.x <= placed.x + NODE_WIDTH && point.y >= placed.y && point.y <= placed.y + NODE_HEIGHT) ?? null
}

// A node can grow from any other that takes children, except from inside itself or from where it already grows.
function moveTarget(layout: NodeLayout, dragged: PlacedNode, point: { x: number; y: number }): PlacedNode | null {
  const over = nodeAt(layout, point)
  const toList = over?.node.childList
  if (over === null || toList === null || toList === undefined || isWithin(toList, dragged.node.path) || isParentList(dragged.node.path, toList)) {
    return null
  }
  return over
}

function isParentList(path: JsonPath, list: JsonPath): boolean {
  return path.length === list.length + 1 && isWithin(path, list)
}

function useCamera(frame: React.RefObject<HTMLDivElement | null>, layout: NodeLayout, fileKey: string): [Camera, React.Dispatch<React.SetStateAction<Camera>>] {
  const [camera, setCamera] = useState<Camera>({ x: FIT_MARGIN, y: FIT_MARGIN, scale: 1 })
  const fittedFile = useRef<string | null>(null)

  useEffect(() => {
    if (frame.current === null || layout.nodes.length === 0 || fittedFile.current === fileKey) {
      return
    }
    fittedFile.current = fileKey
    setCamera(fitted(frame.current, layout))
  }, [frame, layout, fileKey])

  useEffect(() => {
    const element = frame.current
    if (element === null) {
      return
    }
    const wheel = (event: WheelEvent): void => {
      event.preventDefault()
      const bounds = element.getBoundingClientRect()
      const [pointerX, pointerY] = [event.clientX - bounds.left, event.clientY - bounds.top]
      setCamera((current) => {
        const scale = clamp(current.scale * Math.exp(-event.deltaY * WHEEL_ZOOM_PER_PIXEL), SMALLEST_SCALE, LARGEST_SCALE)
        const ratio = scale / current.scale
        return { scale, x: pointerX - (pointerX - current.x) * ratio, y: pointerY - (pointerY - current.y) * ratio }
      })
    }
    element.addEventListener('wheel', wheel, { passive: false })
    return () => element.removeEventListener('wheel', wheel)
  }, [frame])

  return [camera, setCamera]
}

function fitted(frame: HTMLDivElement, layout: NodeLayout): Camera {
  const [width, height] = [frame.clientWidth - 2 * FIT_MARGIN, frame.clientHeight - 2 * FIT_MARGIN]
  const scale = clamp(Math.min(width / Math.max(layout.width, 1), height / Math.max(layout.height, 1)), SMALLEST_FIT_SCALE, LARGEST_FIT_SCALE)
  return { scale, x: FIT_MARGIN, y: FIT_MARGIN + Math.max(0, (height - layout.height * scale) / 2) }
}

function clamp(value: number, lowest: number, highest: number): number {
  return Math.min(highest, Math.max(lowest, value))
}
