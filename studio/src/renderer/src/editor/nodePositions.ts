import type { GraphEdit, JsonPath } from '../bridge/types'
import { valueAt } from './jsonValues'
import { idOf, isWithin, type Point } from './nodeLayout'

/** Where nodes were dragged to, by the id of their path in the file. */
export type NodePositions = Readonly<Record<string, Point>>

/**
 * The positions after {@code edit} changed the file {@code values} holds. A deleted node's position goes; the
 * elements after a removed one move up an index, and so do the positions named by them. A moved node keeps its
 * position under its new path, at the end of the list it joins.
 */
export function positionsAfterEdit(positions: NodePositions, edit: GraphEdit, values: unknown): NodePositions {
  if (edit.op === 'delete') {
    return remapped(positions, (path) => (isWithin(path, edit.path) ? null : pathAfterRemoval(path, edit.path)))
  }
  if (edit.op !== 'move') {
    return positions
  }
  const joined = valueAt(values, edit.toList)
  const newPath = [...pathAfterRemoval(edit.toList, edit.path), Array.isArray(joined) ? joined.length : 0]
  return remapped(positions, (path) => (isWithin(path, edit.path) ? [...newPath, ...path.slice(edit.path.length)] : pathAfterRemoval(path, edit.path)))
}

/** {@code path} once the array element at {@code removed} is gone: one index lower when it is after it in that array. */
export function pathAfterRemoval(path: JsonPath, removed: JsonPath): JsonPath {
  const index = removed.at(-1)
  const array = removed.slice(0, -1)
  const step = path[array.length]
  if (typeof index !== 'number' || path.length <= array.length || !isWithin(path, array) || typeof step !== 'number' || step <= index) {
    return path
  }
  return [...array, step - 1, ...path.slice(array.length + 1)]
}

function remapped(positions: NodePositions, newPathOf: (path: JsonPath) => JsonPath | null): NodePositions {
  const next: Record<string, Point> = {}
  for (const [id, position] of Object.entries(positions)) {
    const newPath = newPathOf(JSON.parse(id) as JsonPath)
    if (newPath !== null) {
      next[idOf(newPath)] = position
    }
  }
  return next
}
