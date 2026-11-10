import type { ColumnField } from './columnField'
import { QuadBatch, type Vec3 } from './quadBatch'

// Light on each face, as Minecraft shades blocks: tops full, north and south sides lighter than east and west.
const TOP_LIGHT = 1
const NORTH_SOUTH_LIGHT = 0.8
const EAST_WEST_LIGHT = 0.6
const WATER_COLOR = 0

interface Span {
  bottom: number
  top: number
  color: number
}

interface Side {
  /** Which way the side looks, as a step to the neighbouring column. */
  columnStep: number
  rowStep: number
  light: number
}

const SIDES: readonly Side[] = [
  { columnStep: 0, rowStep: -1, light: NORTH_SOUTH_LIGHT },
  { columnStep: 0, rowStep: 1, light: NORTH_SOUTH_LIGHT },
  { columnStep: -1, rowStep: 0, light: EAST_WEST_LIGHT },
  { columnStep: 1, rowStep: 0, light: EAST_WEST_LIGHT }
]

/**
 * The ground as block faces: the top of every column, and the sides where a column stands above its neighbour, or
 * above {@code base} at the field's edge and next to columns where nothing is known. Neighbouring columns showing the
 * same face in the same colour are one face.
 */
export function groundFaces(field: ColumnField, base: number): QuadBatch {
  const batch = new QuadBatch(true)
  addTops(field, batch, (column, row) => {
    const top = field.hasGround(column, row) ? field.groundTop[field.indexOf(column, row)] : null
    return top === null ? null : { bottom: top, top, color: field.color[field.indexOf(column, row)] }
  })
  addSides(field, batch, (column, row, side) => {
    if (!field.hasGround(column, row)) {
      return null
    }
    const top = field.groundTop[field.indexOf(column, row)]
    const [nextColumn, nextRow] = [column + side.columnStep, row + side.rowStep]
    const bottom = field.hasGround(nextColumn, nextRow) ? field.groundTop[field.indexOf(nextColumn, nextRow)] : base
    return bottom < top ? { bottom, top, color: field.color[field.indexOf(column, row)] } : null
  })
  return batch
}

/**
 * The water over the ground: its top where a column holds some, and the sides where it stands above what lies next
 * to it, down to its own ground. Water at the edge of the field or beside columns where nothing is known has no side,
 * as it goes on in the field next to it. Drawn without colour; the water has one.
 */
export function waterFaces(field: ColumnField): QuadBatch {
  const batch = new QuadBatch(false)
  const waterAt = (column: number, row: number): number | null => {
    if (!field.contains(column, row)) {
      return null
    }
    const top = field.waterTop[field.indexOf(column, row)]
    return Number.isNaN(top) ? null : top
  }
  addTops(field, batch, (column, row) => {
    const top = waterAt(column, row)
    return top === null ? null : { bottom: top, top, color: WATER_COLOR }
  })
  addSides(field, batch, (column, row, side) => {
    const top = waterAt(column, row)
    if (top === null) {
      return null
    }
    const next = field.surfaceTop(column + side.columnStep, row + side.rowStep)
    if (next === null) {
      return null
    }
    const bottom = Math.max(next, field.groundTop[field.indexOf(column, row)])
    return bottom < top ? { bottom, top, color: WATER_COLOR } : null
  })
  return batch
}

function addTops(field: ColumnField, batch: QuadBatch, spanAt: (column: number, row: number) => Span | null): void {
  for (let row = 0; row < field.rows; row++) {
    mergeRuns(field.columns, (column) => spanAt(column, row), (first, count, span) => {
      const step = field.blocksPerColumn
      batch.add([field.originX + first * step, span.top, field.originZ + row * step], [count * step, 0, 0], [0, 0, step], shaded(span.color, TOP_LIGHT))
    })
  }
}

function addSides(field: ColumnField, batch: QuadBatch, spanAt: (column: number, row: number, side: Side) => Span | null): void {
  for (const side of SIDES) {
    const runsAlongRow = side.rowStep !== 0
    const lanes = runsAlongRow ? field.rows : field.columns
    const length = runsAlongRow ? field.columns : field.rows
    for (let lane = 0; lane < lanes; lane++) {
      const columnOf = (step: number): number => (runsAlongRow ? step : lane)
      const rowOf = (step: number): number => (runsAlongRow ? lane : step)
      mergeRuns(length, (step) => spanAt(columnOf(step), rowOf(step), side), (first, count, span) => {
        batch.add(...sideQuad(field, side, columnOf(first), rowOf(first), count, span), shaded(span.color, side.light))
      })
    }
  }
}

// The corner a side starts from is the one on its left as seen from outside, so it runs and rises into view.
function sideQuad(field: ColumnField, side: Side, column: number, row: number, count: number, span: Span): [Vec3, Vec3, Vec3] {
  const step = field.blocksPerColumn
  const x = field.originX + column * step
  const z = field.originZ + row * step
  const length = count * step
  const up: Vec3 = [0, span.top - span.bottom, 0]
  if (side.rowStep < 0) {
    return [[x, span.bottom, z], [length, 0, 0], up]
  }
  if (side.rowStep > 0) {
    return [[x + length, span.bottom, z + step], [-length, 0, 0], up]
  }
  if (side.columnStep < 0) {
    return [[x, span.bottom, z + length], [0, 0, -length], up]
  }
  return [[x + step, span.bottom, z], [0, 0, length], up]
}

function mergeRuns(length: number, spanAt: (index: number) => Span | null, emit: (first: number, count: number, span: Span) => void): void {
  let first = 0
  while (first < length) {
    const span = spanAt(first)
    if (span === null) {
      first++
      continue
    }
    let end = first + 1
    while (end < length && sameSpan(span, spanAt(end))) {
      end++
    }
    emit(first, end - first, span)
    first = end
  }
}

function sameSpan(span: Span, other: Span | null): boolean {
  return other !== null && other.top === span.top && other.bottom === span.bottom && other.color === span.color
}

function shaded(color: number, light: number): Vec3 {
  return [(((color >> 16) & 0xff) / 255) * light, (((color >> 8) & 0xff) / 255) * light, ((color & 0xff) / 255) * light]
}
