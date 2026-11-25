import { memo } from 'react'
import { cn } from '@/lib/utils'
import { NODE_WIDTH, type PlacedNode, type PlacedWire, type Point } from '../editor/nodeLayout'
import { colorOfKind } from './nodeColors'
import { SOCKET_TOP } from './NodeBox'

/** The wire to a part placed once per copy, such as a branch of branches, rather than once. */
export const EACH_COPY_WIRE = 'each copy'
const SMALLEST_BEND = 30
const COPY_DASH = '5 5'

/** A node being dragged: how far it is from where it sits. */
export interface MovedNode {
  id: string
  offset: Point
}

/** A part's ring being dragged to grow it elsewhere: the pointer, and whether it is over a node that takes it. */
export interface RegrowLine {
  part: PlacedNode
  pointer: Point
  onTarget: boolean
}

interface NodeWiresProps {
  wires: PlacedWire[]
  selectedId: string | null
  moved: MovedNode | null
  regrow: RegrowLine | null
}

/** The wires from each node to the nodes that grow from it; a copy's is dashed in its parent's colour. */
export const NodeWires = memo(function NodeWires({ wires, selectedId, moved, regrow }: NodeWiresProps): React.JSX.Element {
  const at = (placed: PlacedNode): Point => (moved?.id === placed.id ? { x: placed.x + moved.offset.x, y: placed.y + moved.offset.y } : placed)
  return (
    <svg className="pointer-events-none absolute top-0 left-0 overflow-visible" width={1} height={1}>
      {wires.map(({ from, to, label }) => {
        const lit = selectedId === from.id || selectedId === to.id
        const copies = label === EACH_COPY_WIRE
        const [start, end] = [at(from), at(to)]
        return (
          <path
            key={`${from.id}>${to.id}`}
            d={curve({ x: start.x + NODE_WIDTH, y: start.y + SOCKET_TOP }, { x: end.x, y: end.y + SOCKET_TOP })}
            className={cn('fill-none', lit ? 'stroke-primary' : !copies && 'stroke-input')}
            style={copies && !lit ? { stroke: colorOfKind(from.node.kind) } : undefined}
            strokeWidth={lit ? 2 : 1.5}
            strokeDasharray={copies ? COPY_DASH : undefined}
            strokeLinecap="round"
          />
        )
      })}
      {regrow !== null && (
        <path
          d={curve(regrow.pointer, { x: regrow.part.x, y: regrow.part.y + SOCKET_TOP })}
          className={cn('fill-none', regrow.onTarget ? 'stroke-success' : 'stroke-primary')}
          strokeWidth={2}
          strokeDasharray={COPY_DASH}
          strokeLinecap="round"
        />
      )}
    </svg>
  )
})

function curve(from: Point, to: Point): string {
  const bend = Math.max(SMALLEST_BEND, Math.abs(to.x - from.x) / 2)
  return `M${from.x},${from.y} C${from.x + bend},${from.y} ${to.x - bend},${to.y} ${to.x},${to.y}`
}
