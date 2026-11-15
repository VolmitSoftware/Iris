import { memo } from 'react'
import { cn } from '@/lib/utils'
import type { GraphNode } from '../bridge/types'
import { NODE_WIDTH, type PlacedNode, type Point } from '../editor/nodeLayout'
import { NODE_HEADER, NODE_STRIPE, type NodeRows } from '../editor/nodeRows'
import { colorOfKind, colorOfRole } from './nodeColors'
import { NodeRowView } from './NodeRows'

/** Where a node's sockets sit below its top: level with the middle of its header. */
export const SOCKET_TOP = NODE_STRIPE + NODE_HEADER / 2
const SOCKET_SIZE = 9
const CHIP_FILL_ALPHA = '47'

interface NodeBoxProps {
  placed: PlacedNode
  rows: NodeRows
  selected: boolean
  /** Whether the part being dragged would grow from it if dropped now. */
  dropTarget: boolean
  /** How far it is being dragged from where it sits, or null. */
  offset: Point | null
  /** Whether the file can be written. */
  locked: boolean
}

/**
 * One node as the editor draws it: a stripe in the colour of its kind, a header with its title and roles, and a row
 * per setting, edited in place. A part has an input socket on its left, a ring to drag when it can grow elsewhere; a
 * node others grow from has an output socket on its right.
 */
export const NodeBox = memo(function NodeBox({ placed, rows, selected, dropTarget, offset, locked }: NodeBoxProps): React.JSX.Element {
  const { node } = placed
  const editable = !locked && node.settings.length > 0
  return (
    <div
      data-node-id={placed.id}
      className={cn(
        'absolute cursor-move rounded-md bg-popover text-[11.5px] outline -outline-offset-1 dark:bg-secondary',
        dropTarget ? 'outline-2 outline-success' : selected ? 'outline-2 outline-primary' : 'outline-1 outline-overlay-border',
        selected && 'z-[5]',
        offset !== null && 'z-10 shadow-lg'
      )}
      style={{ left: placed.x + (offset?.x ?? 0), top: placed.y + (offset?.y ?? 0), width: NODE_WIDTH, height: placed.height }}
    >
      <div className="rounded-t-md" style={{ height: NODE_STRIPE, background: colorOfKind(node.kind) }} />
      <NodeHeader node={node} />
      {rows.rows.map((row) => (
        <NodeRowView key={row.id} row={row} addable={rows.addable} editable={editable} />
      ))}
      {placed.grown && <InputSocket handle={node.movable && !locked} />}
      {placed.growing && <span className="absolute rounded-full bg-secondary-text" style={socketStyle('right')} />}
    </div>
  )
})

function NodeHeader({ node }: { node: GraphNode }): React.JSX.Element {
  return (
    <div className="flex items-center gap-1 px-2" style={{ height: NODE_HEADER }}>
      <span className="min-w-0 flex-1 truncate text-[12.5px] font-semibold" title={node.title}>
        {node.title}
      </span>
      {node.roles.map((role) => (
        <RoleChip key={role} role={role} />
      ))}
    </div>
  )
}

function RoleChip({ role }: { role: string }): React.JSX.Element {
  const color = colorOfRole(role)
  return (
    <span className="max-w-[96px] shrink-0 truncate rounded-full border px-[7px] text-[11px] leading-4" style={{ borderColor: color, background: color + CHIP_FILL_ALPHA }}>
      {role}
    </span>
  )
}

// A part that can grow elsewhere has a ring for an input, a handle to drag onto the node it should grow from.
function InputSocket({ handle }: { handle: boolean }): React.JSX.Element {
  return (
    <span
      data-socket={handle ? 'regrow' : 'input'}
      title={handle ? 'Drag onto another node to grow from it' : undefined}
      className={cn(
        'absolute rounded-full border-[1.5px] border-secondary-text transition-transform',
        handle ? 'cursor-pointer bg-popover hover:scale-[1.35] hover:border-primary dark:bg-secondary' : 'bg-secondary-text'
      )}
      style={socketStyle('left')}
    />
  )
}

function socketStyle(side: 'left' | 'right'): React.CSSProperties {
  return { [side]: -SOCKET_SIZE / 2, top: SOCKET_TOP - SOCKET_SIZE / 2, width: SOCKET_SIZE, height: SOCKET_SIZE }
}
