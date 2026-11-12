import { NO_BLOCK, type TileRuns } from '../bridge/types'
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
 * finer ground fade to it with distance, so the change from one to the other cannot be seen. Faces of neighbouring
 * columns that lie in one plane over the same heights in the same colour are one face.
 */
export function runFaces(field: ColumnField, surface: SurfaceRuns, base: number): QuadBatch {
  const faces = new MergedFaces(field)
  for (let row = 0; row < field.rows; row++) {
    for (let column = 0; column < field.columns; column++) {
      if (field.hasGround(column, row)) {
        addColumn(faces, field, surface, column, row, base)
      }
    }
  }
  return faces.batch()
}

function addColumn(faces: MergedFaces, field: ColumnField, surface: SurfaceRuns, column: number, row: number, base: number): void {
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
      const color = underWater(withPlants(surface, run, colorOf(surface, block, 'top', FACE_LIGHT.up)), top + 1, water)
      faces.add('up', row, column, top + 1, top + 1, color, color)
    }
    if (!isGround && (run === last || runs.tops[run + 1] !== bottom - 1)) {
      const color = underWater(colorOf(surface, block, 'top', FACE_LIGHT.down), bottom, water)
      faces.add('down', row, column, bottom, bottom, color, color)
    }
    for (const side of SIDES) {
      const neighbour = [column + side.columnStep, row + side.rowStep] as const
      const known = field.hasGround(...neighbour) && hasRuns(surface, surface.cellAt(...neighbour))
      const lowest = isGround ? (known ? -Infinity : base) : bottom
      const covered = known ? spansOf(surface, surface.cellAt(...neighbour)) : []
      for (const open of uncovered({ bottom: lowest, top }, covered)) {
        addSide(faces, surface, column, row, side, open, top, block, water)
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
function addSide(faces: MergedFaces, surface: SurfaceRuns, column: number, row: number, side: Side, open: Span, runTop: number, block: number, water: number | null): void {
  const light = side.direction === 'north' || side.direction === 'south' ? FACE_LIGHT.north : FACE_LIGHT.west
  if (open.top === runTop) {
    const color = colorOf(surface, block, 'side', light)
    faces.add(side.direction, row, column, runTop, runTop + 1, underWater(color, runTop, water), underWater(color, runTop + 1, water))
  }
  const belowTop = open.top === runTop ? runTop - 1 : open.top
  if (belowTop >= open.bottom) {
    const color = colorOf(surface, block, 'below', light)
    faces.add(side.direction, row, column, open.bottom, belowTop + 1, underWater(color, open.bottom, water), underWater(color, belowTop + 1, water))
  }
}

function colorOf(surface: SurfaceRuns, block: number, face: 'top' | 'side' | 'below', light: number): Vec3 {
  const look = surface.looks[block]
  return shaded(look === null || look === undefined ? surface.colors[block] : look[face].color, light)
}

// Seen from afar, the plants on a block hide this much of its top where they stand.
const PLANT_COVER = 0.5
const WHOLE_SHARE = 255

// Near ground draws its grass and flowers, which colour it seen from afar; the run's top takes their colour as they would.
function withPlants(surface: SurfaceRuns, run: number, color: Vec3): Vec3 {
  const plant = surface.runs.covers[run]
  if (plant === NO_BLOCK) {
    return color
  }
  const share = (surface.runs.coverShares[run] / WHOLE_SHARE) * PLANT_COVER
  const plantColor = shaded(surface.colors[plant], FACE_LIGHT.up)
  return [color[0] + (plantColor[0] - color[0]) * share, color[1] + (plantColor[1] - color[1]) * share, color[2] + (plantColor[2] - color[2]) * share]
}

function underWater(color: Vec3, y: number, water: number | null): Vec3 {
  return water === null ? color : sunk(color, tintAt(water - y))
}

/** Faces that lie along one line of columns in one plane, over the same heights in the same colours, and where along it they stand. */
interface FaceLine {
  direction: Direction
  /** The row of the line, or its column for faces looking west or east. */
  lane: number
  from: number
  to: number
  bottomColor: Vec3
  topColor: Vec3
  /** The columns along the line, or rows for faces looking west or east, in the order they were added. */
  places: number[]
}

/**
 * Column faces gathered first and drawn as few quads as they allow: faces of columns next to each other along a row, or
 * along a column for faces looking west or east, that lie at the same heights in the same colours become one.
 */
class MergedFaces {
  private readonly lines = new Map<string, FaceLine>()

  constructor(private readonly field: ColumnField) {}

  /** A face of the column; tops and undersides lie at {@code from}, sides reach from {@code from} to {@code to}. */
  add(direction: Direction, row: number, column: number, from: number, to: number, bottomColor: Vec3, topColor: Vec3): void {
    const alongColumns = direction !== 'west' && direction !== 'east'
    const lane = alongColumns ? row : column
    const key = `${direction}|${lane}|${from}|${to}|${bottomColor.join(',')}|${topColor.join(',')}`
    let line = this.lines.get(key)
    if (line === undefined) {
      line = { direction, lane, from, to, bottomColor, topColor, places: [] }
      this.lines.set(key, line)
    }
    line.places.push(alongColumns ? column : row)
  }

  batch(): QuadBatch {
    const batch = new QuadBatch(true)
    for (const line of this.lines.values()) {
      let start = 0
      for (let at = 1; at <= line.places.length; at++) {
        if (at === line.places.length || line.places[at] !== line.places[at - 1] + 1) {
          this.addQuad(batch, line, line.places[start], at - start)
          start = at
        }
      }
    }
    return batch
  }

  private addQuad(batch: QuadBatch, line: FaceLine, first: number, count: number): void {
    const step = this.field.blocksPerColumn
    const { originX, originZ } = this.field
    const length = count * step
    const lane = line.lane * step
    const along = first * step
    const height: Vec3 = [0, line.to - line.from, 0]
    switch (line.direction) {
      case 'up':
        batch.add([originX + along, line.from, originZ + lane], [length, 0, 0], [0, 0, step], line.bottomColor, line.topColor)
        return
      case 'down':
        batch.add([originX + along, line.from, originZ + lane], [0, 0, step], [length, 0, 0], line.bottomColor, line.topColor)
        return
      case 'north':
        batch.add([originX + along, line.from, originZ + lane], [length, 0, 0], height, line.bottomColor, line.topColor)
        return
      case 'south':
        batch.add([originX + along + length, line.from, originZ + lane + step], [-length, 0, 0], height, line.bottomColor, line.topColor)
        return
      case 'west':
        batch.add([originX + lane, line.from, originZ + along + length], [0, 0, -length], height, line.bottomColor, line.topColor)
        return
      case 'east':
        batch.add([originX + lane + step, line.from, originZ + along], [0, 0, length], height, line.bottomColor, line.topColor)
    }
  }
}
