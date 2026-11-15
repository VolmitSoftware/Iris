import { describe, expect, it } from 'vitest'
import type { GraphNode, JsonPath } from '../bridge/types'
import { NO_PART } from '../bridge/types'
import { canRegrow, GRID, isWithin, layoutNodes, nodeAt, nodeDrawingPart, NODE_WIDTH, type PlacedNode } from './nodeLayout'

function node(path: JsonPath, children: GraphNode[] = []): GraphNode {
  return {
    kind: 'Stem',
    title: 'Stem',
    path,
    partId: NO_PART,
    roles: [],
    settings: [],
    children: children.map((child) => ({ wire: '', node: child })),
    childList: null,
    movable: true,
    duplicable: true,
    additions: []
  }
}

describe('layoutNodes', () => {
  const height = (): number => 50
  const placedAt = (layout: ReturnType<typeof layoutNodes>, path: JsonPath): PlacedNode => layout.nodes.find((placed) => placed.id === JSON.stringify(path))!

  it('sets a node level with its first child, a column to its left, and each next child on the rows below', () => {
    const layout = layoutNodes([node([], [node(['parts', 0]), node(['parts', 1])])], height)
    const [parent, first, second] = [placedAt(layout, []), placedAt(layout, ['parts', 0]), placedAt(layout, ['parts', 1])]

    expect(first.y).toBe(parent.y)
    expect(first.x - parent.x).toBe(NODE_WIDTH + 2 * GRID)
    expect(second.y).toBeGreaterThanOrEqual(first.y + 50 + 10)
    expect(layout.wires).toHaveLength(2)
  })

  it('stacks the other roots under the first, in its column, and starts every node on a grid point', () => {
    const layout = layoutNodes([node([], [node(['parts', 0])]), node(['materials', 0])], height)
    const [object, material] = [placedAt(layout, []), placedAt(layout, ['materials', 0])]

    expect(material.x).toBe(object.x)
    expect(material.y).toBeGreaterThan(object.y + 50)
    expect(layout.nodes.every((placed) => placed.x % GRID === 0 && placed.y % GRID === 0)).toBe(true)
  })

  it('makes room below a tall node before its next sibling', () => {
    const tall = (graphNode: GraphNode): number => (graphNode.path.length === 2 && graphNode.path[1] === 0 ? 300 : 50)
    const layout = layoutNodes([node([], [node(['parts', 0]), node(['parts', 1])])], tall)

    expect(placedAt(layout, ['parts', 1]).y).toBeGreaterThan(placedAt(layout, ['parts', 0]).y + 300)
  })

  it('puts a node where it was dragged, and marks which nodes have ports', () => {
    const layout = layoutNodes([node([], [node(['parts', 0])])], height, new Map([[JSON.stringify(['parts', 0]), { x: 500, y: 44 }]]))
    const part = placedAt(layout, ['parts', 0])

    expect([part.x, part.y]).toEqual([500, 44])
    expect([part.grown, part.growing]).toEqual([true, false])
    expect([placedAt(layout, []).grown, placedAt(layout, []).growing]).toEqual([false, true])
  })
})

describe('nodeAt', () => {
  it('finds the node under a point by its own height', () => {
    const layout = layoutNodes([node([], [node(['parts', 0])])], () => 50)
    const part = layout.nodes[1]

    expect(nodeAt(layout.nodes, { x: part.x + 5, y: part.y + 49 })?.id).toBe(part.id)
    expect(nodeAt(layout.nodes, { x: part.x + 5, y: part.y + 51 })).toBeNull()
  })
})

describe('canRegrow', () => {
  const withList = (path: JsonPath, children: GraphNode[] = []): GraphNode => ({ ...node(path, children), childList: [...path, 'children'] })

  it('lets a part grow from another that takes children, but not from inside itself or where it already grows', () => {
    const part = withList(['parts', 0])

    expect(canRegrow(part, withList(['parts', 1]))).toBe(true)
    expect(canRegrow(part, withList(['parts', 0, 'children', 0]))).toBe(false)
    expect(canRegrow(withList(['parts', 1, 'children', 0]), withList(['parts', 1]))).toBe(false)
    expect(canRegrow({ ...part, movable: false }, withList(['parts', 1]))).toBe(false)
  })
})

describe('nodeDrawingPart', () => {
  const drawing = (path: JsonPath, partId: number, children: GraphNode[] = []): GraphNode => ({ ...node(path, children), partId })

  it('finds the node that draws the part, whichever tree and depth it is in', () => {
    const roots = [drawing([], 0, [drawing(['parts', 0], 1)]), drawing(['materials', 0], NO_PART, [drawing(['materials', 0, 'parts', 0], 2)])]

    expect(nodeDrawingPart(roots, 1)?.path).toEqual(['parts', 0])
    expect(nodeDrawingPart(roots, 2)?.path).toEqual(['materials', 0, 'parts', 0])
  })

  it('takes the outer node when a node and the one it copies draw the same part', () => {
    const roots = [drawing([], 3, [drawing(['branches', 'part'], 3)])]

    expect(nodeDrawingPart(roots, 3)?.path).toEqual([])
  })

  it('finds none for a part no node draws', () => {
    expect(nodeDrawingPart([drawing([], 0)], 5)).toBeNull()
  })
})

describe('isWithin', () => {
  it('holds for the path itself and what sits inside it, not for a sibling', () => {
    expect(isWithin(['parts', 0, 'children', 1], ['parts', 0])).toBe(true)
    expect(isWithin(['parts', 0], ['parts', 0])).toBe(true)
    expect(isWithin(['parts', 1], ['parts', 0])).toBe(false)
  })
})
