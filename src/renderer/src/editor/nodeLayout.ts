import type { GraphNode, JsonPath } from '../bridge/types'

export const NODE_WIDTH = 168
export const NODE_HEIGHT = 46
const COLUMN_GAP = 64
const ROW_GAP = 10
const TREE_GAP = 26

/** A node where the canvas draws it, by its top left corner; `id` tells it from every other node of the file. */
export interface PlacedNode {
  id: string
  node: GraphNode
  x: number
  y: number
}

export interface PlacedWire {
  from: PlacedNode
  to: PlacedNode
  label: string
}

export interface NodeLayout {
  nodes: PlacedNode[]
  wires: PlacedWire[]
  width: number
  height: number
}

export function idOf(path: JsonPath): string {
  return JSON.stringify(path)
}

/** The node at {@code path} among the trees of {@code roots}, or null when there is none, such as after it was deleted. */
export function nodeWithPath(roots: GraphNode[], path: JsonPath): GraphNode | null {
  const id = idOf(path)
  for (const root of roots) {
    if (idOf(root.path) === id) {
      return root
    }
    const found = nodeWithPath(
      root.children.map((link) => link.node),
      path
    )
    if (found !== null) {
      return found
    }
  }
  return null
}

/** Whether {@code path} is {@code ancestor} or sits inside it. */
export function isWithin(path: JsonPath, ancestor: JsonPath): boolean {
  return ancestor.length <= path.length && ancestor.every((step, index) => path[index] === step)
}

/**
 * Lays each root's tree out from left to right, a column per generation, one tree under the other. Leaves take a row
 * each and a parent sits level with the middle of its children, so wires never cross.
 */
export function layoutNodes(roots: GraphNode[]): NodeLayout {
  const nodes: PlacedNode[] = []
  const wires: PlacedWire[] = []
  let nextTop = 0
  let width = 0

  const place = (node: GraphNode, depth: number): PlacedNode => {
    const x = depth * (NODE_WIDTH + COLUMN_GAP)
    width = Math.max(width, x + NODE_WIDTH)
    const children = node.children.map((link) => ({ label: link.wire, placed: place(link.node, depth + 1) }))
    const y = children.length === 0 ? takeRow() : (children[0].placed.y + children[children.length - 1].placed.y) / 2
    const placed: PlacedNode = { id: idOf(node.path), node, x, y }
    nodes.push(placed)
    for (const child of children) {
      wires.push({ from: placed, to: child.placed, label: child.label })
    }
    return placed
  }

  const takeRow = (): number => {
    const top = nextTop
    nextTop += NODE_HEIGHT + ROW_GAP
    return top
  }

  for (const root of roots) {
    place(root, 0)
    nextTop += TREE_GAP
  }
  return { nodes, wires, width, height: Math.max(0, nextTop - ROW_GAP - TREE_GAP) }
}
