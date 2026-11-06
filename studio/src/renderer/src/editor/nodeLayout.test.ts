import { describe, expect, it } from 'vitest'
import type { GraphNode, JsonPath } from '../bridge/types'
import { NO_PART } from '../bridge/types'
import { isWithin, layoutNodes, NODE_WIDTH } from './nodeLayout'

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
  it('sets a parent level with the middle of its children, a column to their left', () => {
    const layout = layoutNodes([node([], [node(['parts', 0]), node(['parts', 1]), node(['parts', 2])])])
    const [first, , last, parent] = layout.nodes

    expect(parent.y).toBe((first.y + last.y) / 2)
    expect(first.x - parent.x).toBeGreaterThan(NODE_WIDTH)
    expect(layout.wires).toHaveLength(3)
  })

  it('lays the next root out under the tree before it', () => {
    const layout = layoutNodes([node([], [node(['parts', 0])]), node(['materials', 0])])
    const material = layout.nodes.find((placed) => placed.id === JSON.stringify(['materials', 0]))!

    expect(material.x).toBe(0)
    expect(material.y).toBeGreaterThan(Math.max(...layout.nodes.filter((placed) => placed !== material).map((placed) => placed.y)))
  })
})

describe('isWithin', () => {
  it('holds for the path itself and what sits inside it, not for a sibling', () => {
    expect(isWithin(['parts', 0, 'children', 1], ['parts', 0])).toBe(true)
    expect(isWithin(['parts', 0], ['parts', 0])).toBe(true)
    expect(isWithin(['parts', 1], ['parts', 0])).toBe(false)
  })
})
