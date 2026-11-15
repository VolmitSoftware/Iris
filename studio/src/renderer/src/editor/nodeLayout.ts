import type { GraphNode, JsonPath } from '../bridge/types'

/** The spacing nodes snap to, and the dots of the canvas mark. */
export const GRID = 22
export const NODE_WIDTH = 12 * GRID
const MARGIN = GRID
const COLUMN_GAP = 2 * GRID
const MIN_ROW_GAP = 10

export interface Point {
  x: number
  y: number
}

/** A node where the canvas draws it, by its top left corner; `id` tells it from every other node of the file. */
export interface PlacedNode {
  id: string
  node: GraphNode
  x: number
  y: number
  height: number
  /** Whether it grows from another node, so it has an input port. */
  grown: boolean
  /** Whether other nodes grow from it, so it has an output port. */
  growing: boolean
}

export interface PlacedWire {
  from: PlacedNode
  to: PlacedNode
  label: string
}

export interface NodeLayout {
  nodes: PlacedNode[]
  wires: PlacedWire[]
}

export interface Bounds {
  left: number
  top: number
  right: number
  bottom: number
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

/** The first node, going down each tree in turn, that draws {@code part}, or null when none does. */
export function nodeDrawingPart(roots: GraphNode[], part: number): GraphNode | null {
  for (const root of roots) {
    if (root.partId === part) {
      return root
    }
    const found = nodeDrawingPart(
      root.children.map((link) => link.node),
      part
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

export function snappedToGrid(coordinate: number): number {
  return Math.round(coordinate / GRID) * GRID
}

function nextRowBelow(bottom: number): number {
  return Math.ceil((bottom + MIN_ROW_GAP) / GRID) * GRID
}

function columnLeft(column: number): number {
  return MARGIN + column * (NODE_WIDTH + COLUMN_GAP)
}

/**
 * Lays the nodes out on the grid: the roots, such as the object, its materials and its decorations, stacked in the
 * first column, and what grows from them to the right, a column per generation. Each subtree takes the rows below the
 * last, and a node sits level with its first child, so it is read beside what grows from it. A node in
 * {@code placed}, by its id, sits where it was dragged instead.
 */
export function layoutNodes(roots: GraphNode[], heightOf: (node: GraphNode) => number, placed: ReadonlyMap<string, Point> = new Map()): NodeLayout {
  const nodes: PlacedNode[] = []
  const wires: PlacedWire[] = []

  const add = (node: GraphNode, x: number, y: number, grown: boolean): PlacedNode => {
    const id = idOf(node.path)
    const at = placed.get(id) ?? { x, y }
    const box: PlacedNode = { id, node, x: at.x, y: at.y, height: heightOf(node), grown, growing: node.children.length > 0 }
    nodes.push(box)
    return box
  }

  // Returns the bottom of the subtree as laid out, which a node dragged elsewhere does not change.
  const placeTree = (link: GraphNode['children'][number], parent: PlacedNode, column: number, top: number): number => {
    const box = add(link.node, columnLeft(column), top, true)
    wires.push({ from: parent, to: box, label: link.wire })
    let bottom = top + box.height
    let childTop = top
    for (const child of link.node.children) {
      const childBottom = placeTree(child, box, column + 1, childTop)
      bottom = Math.max(bottom, childBottom)
      childTop = nextRowBelow(childBottom)
    }
    return bottom
  }

  let firstColumnTop = MARGIN
  const rootBoxes = roots.map((root) => {
    const box = add(root, MARGIN, firstColumnTop, false)
    firstColumnTop = nextRowBelow(firstColumnTop + box.height)
    return box
  })
  let treeTop = MARGIN
  roots.forEach((root, index) => {
    for (const link of root.children) {
      treeTop = nextRowBelow(placeTree(link, rootBoxes[index], 1, treeTop))
    }
  })
  return { nodes, wires }
}

/** The smallest rectangle around every node; null when there are none. */
export function boundsOf(nodes: PlacedNode[]): Bounds | null {
  if (nodes.length === 0) {
    return null
  }
  return {
    left: Math.min(...nodes.map((node) => node.x)),
    top: Math.min(...nodes.map((node) => node.y)),
    right: Math.max(...nodes.map((node) => node.x + NODE_WIDTH)),
    bottom: Math.max(...nodes.map((node) => node.y + node.height))
  }
}

/** The node under {@code point}, the one drawn last when they overlap; null over the background. */
export function nodeAt(nodes: PlacedNode[], point: Point): PlacedNode | null {
  for (let index = nodes.length - 1; index >= 0; index--) {
    const box = nodes[index]
    if (point.x >= box.x && point.x <= box.x + NODE_WIDTH && point.y >= box.y && point.y <= box.y + box.height) {
      return box
    }
  }
  return null
}

/** Whether {@code dragged} may grow from {@code over}: it takes children, and is neither inside it nor already its parent. */
export function canRegrow(dragged: GraphNode, over: GraphNode): boolean {
  const toList = over.childList
  if (!dragged.movable || toList === null || isWithin(toList, dragged.path)) {
    return false
  }
  const alreadyThere = dragged.path.length === toList.length + 1 && isWithin(dragged.path, toList)
  return !alreadyThere
}
