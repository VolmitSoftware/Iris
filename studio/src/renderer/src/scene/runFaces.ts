import type { TileRuns } from '../bridge/types'
import type { Direction } from './blockModels'
import type { BlockLook } from './blockLooks'
import type { ColumnField } from './columnField'
import { shaded } from './columnFaces'
import { FACE_LIGHT } from './coverLooks'
import { QuadBatch, type Vec3 } from './quadBatch'
import { sunk, tintAt } from './waterTint'

/** A coarser tile's runs of solid blocks, and how to colour the blocks they name. */
export interface SurfaceRuns {
  runs: TileRuns
  /** Where a column's runs lie in {@link runs}, by its column and row. */
  cellAt(column: number, row: number): number
  looks: readonly (BlockLook | null)[]
  colors: readonly number[]
}

interface Side {
  direction: Direction
  columnStep: number
  rowStep: number
}

const SIDES: readonly Side[] = [
  { direction: 'north', columnStep: 0, rowStep: -1 },
  { direction: 'south', columnStep: 0, rowStep: 1 },
  { direction: 'west', columnStep: -1, rowStep: 0 },
  { direction: 'east', columnStep: 1, rowStep: 0 }
]

/** A span of heights, both ends included. */
interface Span {
  bottom: number
  top: number
}

/**
 * A coarser tile's ground in 3D: each run of solid blocks of each column as a box as wide as the column, with its top,
 * its underside where it floats, and its sides where the columns beside it leave them in the open. The ground reaches
 * down to the run under its neighbours' tops, or to {@code base} at the field's edge and beside holes, so tiles next to
 * each other leave no cracks. Each face takes the average colour of its block's picture on that side, as the textures of
 * finer ground fade to it with distance, so the change from one to the other cannot be seen.
 */
export function runFaces(field: ColumnField, surface: SurfaceRuns, base: number): QuadBatch {
  const batch = new QuadBatch(true)
  for (let row = 0; row < field.rows; row++) {
    for (let column = 0; column < field.columns; column++) {
      if (field.hasGround(column, row)) {
        addColumn(batch, field, surface, column, row, base)
      }
    }
  }
  return batch
}

function addColumn(batch: QuadBatch, field: ColumnField, surface: SurfaceRuns, column: number, row: number, base: number): void {
  const { runs } = surface
  const cell = surface.cellAt(column, row)
  const first = runs.starts[cell]
  const last = runs.starts[cell + 1] - 1
  const water = field.waterTopAt(column, row)
  for (let run = first; run <= last; run++) {
    const isGround = run === last
    const top = runs.tops[run]
    const bottom = runs.bottoms[run]
    const block = runs.blocks[run]
    if (run === first || runs.bottoms[run - 1] !== top + 1) {
      addTop(batch, field, column, row, top + 1, colorOf(surface, block, 'top', FACE_LIGHT.up), water)
    }
    if (!isGround && (run === last || runs.tops[run + 1] !== bottom - 1)) {
      addBottom(batch, field, column, row, bottom, colorOf(surface, block, 'top', FACE_LIGHT.down), water)
    }
    for (const side of SIDES) {
      const neighbour = [column + side.columnStep, row + side.rowStep] as const
      const known = field.hasGround(...neighbour) && hasRuns(surface, surface.cellAt(...neighbour))
      const lowest = isGround ? (known ? -Infinity : base) : bottom
      const covered = known ? spansOf(surface, surface.cellAt(...neighbour)) : []
      for (const open of uncovered({ bottom: lowest, top }, covered)) {
        addSide(batch, field, surface, column, row, side, open, top, block, water)
      }
    }
  }
}

function hasRuns(surface: SurfaceRuns, cell: number): boolean {
  return surface.runs.starts[cell + 1] > surface.runs.starts[cell]
}

// The heights of a column its runs fill, the ground's reaching all the way down.
function spansOf(surface: SurfaceRuns, cell: number): Span[] {
  const { runs } = surface
  const spans: Span[] = []
  const last = runs.starts[cell + 1] - 1
  for (let run = runs.starts[cell]; run <= last; run++) {
    spans.push({ bottom: run === last ? -Infinity : runs.bottoms[run], top: runs.tops[run] })
  }
  return spans
}

/** The parts of {@code span} none of {@code covered} fills, lowest first. */
export function uncovered(span: Span, covered: readonly Span[]): Span[] {
  let open: Span[] = [span]
  for (const cover of covered) {
    open = open.flatMap((part) => {
      if (cover.top < part.bottom || cover.bottom > part.top) {
        return [part]
      }
      const left: Span[] = []
      if (cover.bottom > part.bottom) {
        left.push({ bottom: part.bottom, top: cover.bottom - 1 })
      }
      if (cover.top < part.top) {
        left.push({ bottom: cover.top + 1, top: part.top })
      }
      return left
    })
  }
  return open.filter((part) => part.top >= part.bottom).sort((first, second) => first.bottom - second.bottom)
}

// The top block of a run shows its side picture; the blocks under it, the picture of the blocks under a column's top.
function addSide(batch: QuadBatch, field: ColumnField, surface: SurfaceRuns, column: number, row: number, side: Side, open: Span, runTop: number, block: number, water: number | null): void {
  const light = side.direction === 'north' || side.direction === 'south' ? FACE_LIGHT.north : FACE_LIGHT.west
  if (open.top === runTop) {
    addSideQuad(batch, field, column, row, side, runTop, runTop + 1, colorOf(surface, block, 'side', light), water)
  }
  const belowTop = open.top === runTop ? runTop - 1 : open.top
  if (belowTop >= open.bottom) {
    addSideQuad(batch, field, column, row, side, open.bottom, belowTop + 1, colorOf(surface, block, 'below', light), water)
  }
}

function addSideQuad(batch: QuadBatch, field: ColumnField, column: number, row: number, side: Side, from: number, to: number, color: Vec3, water: number | null): void {
  const step = field.blocksPerColumn
  const x = field.originX + column * step
  const z = field.originZ + row * step
  const up: Vec3 = [0, to - from, 0]
  const [origin, along]: [Vec3, Vec3] =
    side.rowStep < 0
      ? [[x, from, z], [step, 0, 0]]
      : side.rowStep > 0
        ? [[x + step, from, z + step], [-step, 0, 0]]
        : side.columnStep < 0
          ? [[x, from, z + step], [0, 0, -step]]
          : [[x + step, from, z], [0, 0, step]]
  batch.add(origin, along, up, underWater(color, from, water), underWater(color, to, water))
}

function addTop(batch: QuadBatch, field: ColumnField, column: number, row: number, y: number, color: Vec3, water: number | null): void {
  const step = field.blocksPerColumn
  const shown = underWater(color, y, water)
  batch.add([field.originX + column * step, y, field.originZ + row * step], [step, 0, 0], [0, 0, step], shown, shown)
}

function addBottom(batch: QuadBatch, field: ColumnField, column: number, row: number, y: number, color: Vec3, water: number | null): void {
  const step = field.blocksPerColumn
  const shown = underWater(color, y, water)
  batch.add([field.originX + column * step, y, field.originZ + row * step], [0, 0, step], [step, 0, 0], shown, shown)
}

function colorOf(surface: SurfaceRuns, block: number, face: 'top' | 'side' | 'below', light: number): Vec3 {
  const look = surface.looks[block]
  return shaded(look === null || look === undefined ? surface.colors[block] : look[face].color, light)
}

function underWater(color: Vec3, y: number, water: number | null): Vec3 {
  return water === null ? color : sunk(color, tintAt(water - y))
}
