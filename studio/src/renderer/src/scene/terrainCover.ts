import type { CameraSight } from './cameraSight'
import {
  ancestorAt,
  childrenOf,
  columnsUnder,
  isInside,
  MOST_LEVELS_FINER,
  TILE_COLUMNS,
  tileKey,
  type ColumnArea,
  type TerrainTile
} from './terrainTiles'

/**
 * What is known of a tile: its ground; ground left over from before the layer changed that stands in until it is
 * sampled anew; only a quick, rougher stand-in, drawn where nothing truer of any level is known; or nothing.
 */
export type TileState = 'none' | 'quick' | 'standIn' | 'fresh'

export interface DrawnTile {
  tile: TerrainTile
  /** The columns finer tiles draw instead. */
  holes: ColumnArea[]
}

export interface Cover {
  draws: DrawnTile[]
  /** What to sample, most needed first: the backdrop, then a level finer than the detail, then the detail. */
  wanted: TerrainTile[]
}

// A column is drawn at most this many pixels wide, as wider ones show their blockiness; coarser ones only where there
// are too many. Finer columns than a pixel cost the GPU faces no one can see: at half a pixel, a forest view held
// twelve million triangles.
const MOST_COLUMN_PIXELS = 1
// A little above one, as tiles in sight stick out past the view's edges; beyond it, columns only add faces no one can tell apart.
const MOST_COLUMNS_PER_PIXEL = 1.25
const MOST_TILES_IN_SIGHT = 64
// The backdrop reaches to the horizon, where a few coarse tiles do; the rest of the tiles go to ground that is near.
const MOST_BACKDROP_TILES = 16
const DETAIL_MARGIN = 0.15
const BACKDROP_MARGIN = 0.5
const PREVIEW_LEVELS_ABOVE_DETAIL = 1
const MOST_LEVELS_FINER_LEFT_OVER = 2
const BACKDROP_TILE_BLOCKS_PER_ORBIT_BLOCK = 1
const COLUMNS_PER_TILE = TILE_COLUMNS * TILE_COLUMNS

type StateOf = (tile: TerrainTile) => TileState

/**
 * What to draw for one camera, and which tiles it still needs. Detail tiles in sight have columns about a pixel wide,
 * nearer ground finer than far ground; backdrop tiles cover the view and more with coarser columns. Until detail is
 * sampled the nearest sampled tile of a coarser level stands in, with holes where finer tiles are ready, and detail
 * left over from before zooming out stands in for the coarser tile that is not sampled yet.
 *
 * Quick stand-ins are drawn only where no truer tile of any level is known, so ground once sampled stays in sight until
 * what replaces it is as true.
 *
 * @param coarsestLevel the coarsest level the source can sample
 * @param knownInside   the tiles finer than a tile, inside it, whose ground is known, however much finer; where not
 *                      given, the ones up to a couple of levels finer that {@code stateOf} knows
 */
export function planCover(sight: CameraSight, stateOf: StateOf, coarsestLevel: number, knownInside?: (tile: TerrainTile) => TerrainTile[]): Cover {
  const backdropLevel = backdropLevelFor(sight, coarsestLevel)
  const finestLevel = Math.max(0, backdropLevel - MOST_LEVELS_FINER)
  const backdrop = sight.tilesAt(backdropLevel, BACKDROP_MARGIN).sort(nearestFirst(sight)).slice(0, MOST_BACKDROP_TILES)
  const detail = detailTiles(sight, backdrop, finestLevel)
  const isTrue = (tile: TerrainTile): boolean => stateOf(tile) === 'fresh' || stateOf(tile) === 'standIn'
  const leftOverInside = knownInside ?? ((tile: TerrainTile) => finerLeftOver(tile, finestLevel, stateOf, MOST_LEVELS_FINER_LEFT_OVER))
  const candidates = candidatesOf(backdrop, detail, backdropLevel, isTrue, (tile) => leftOverInside(tile).filter((finer) => finer.level >= finestLevel && isTrue(finer)))
  const ready = candidates.filter(isTrue)
  const quickFill = candidates.filter((tile) => stateOf(tile) === 'quick' && !ready.some((truer) => isInside(tile, truer)))
  const drawn = [...ready, ...quickFill]
  return {
    draws: drawn.map((tile) => ({ tile, holes: drawn.filter((finer) => isInside(finer, tile)).map((finer) => columnsUnder(tile, finer)) })),
    wanted: wantedInOrder(sight, backdrop, detail, backdropLevel, stateOf)
  }
}

// The backdrop tiles are about as wide as the camera is far from what is in the middle, which puts a few of them in sight.
function backdropLevelFor(sight: CameraSight, coarsestLevel: number): number {
  const wanted = Math.ceil(Math.log2(Math.max(1, (sight.orbitDistance * BACKDROP_TILE_BLOCKS_PER_ORBIT_BLOCK) / TILE_COLUMNS)))
  let level = Math.min(coarsestLevel, Math.max(0, wanted))
  while (level < coarsestLevel && sight.tilesAt(level, BACKDROP_MARGIN).length > MOST_BACKDROP_TILES) {
    level++
  }
  return level
}

// The tile whose columns look widest is split into finer ones, again and again, until no column looks wider than a
// pixel or the view has as many tiles as it may. A camera that moves a little so changes a tile or two, not all of them.
function detailTiles(sight: CameraSight, backdrop: TerrainTile[], finestLevel: number): TerrainTile[] {
  const tilesThePixelsCanShow = Math.floor((sight.pixelsWithin(DETAIL_MARGIN) * MOST_COLUMNS_PER_PIXEL) / COLUMNS_PER_TILE)
  const mostTiles = Math.min(MOST_TILES_IN_SIGHT, Math.max(backdrop.length, tilesThePixelsCanShow))
  const detail = backdrop.map((tile) => ({ tile, columnPixels: sight.columnPixels(tile) }))
  for (;;) {
    const widest = detail
      .filter((entry) => entry.tile.level > finestLevel && entry.columnPixels > MOST_COLUMN_PIXELS)
      .reduce<(typeof detail)[number] | null>((most, entry) => (most === null || entry.columnPixels > most.columnPixels ? entry : most), null)
    if (widest === null) {
      break
    }
    const finer = childrenOf(widest.tile).filter((child) => sight.sees(child, DETAIL_MARGIN))
    if (detail.length - 1 + finer.length > mostTiles) {
      break
    }
    detail.splice(detail.indexOf(widest), 1, ...finer.map((tile) => ({ tile, columnPixels: sight.columnPixels(tile) })))
  }
  return detail.map((entry) => entry.tile)
}

// Everything that may be drawn for the view: the backdrop, the detail and the levels between that happen to be there,
// and, where a tile's true ground is missing, finer tiles left over from before. None is finer than a backdrop tile can
// be cut a hole for.
function candidatesOf(
  backdrop: TerrainTile[],
  detail: TerrainTile[],
  backdropLevel: number,
  isTrue: (tile: TerrainTile) => boolean,
  leftOverInside: (tile: TerrainTile) => TerrainTile[]
): TerrainTile[] {
  const candidates = new Map<string, TerrainTile>()
  const add = (tile: TerrainTile): void => void candidates.set(tileKey(tile), tile)
  const addLeftOverWhereMissing = (tile: TerrainTile): void => {
    if (!isTrue(tile)) {
      leftOverInside(tile).forEach(add)
    }
  }
  backdrop.forEach(add)
  backdrop.forEach(addLeftOverWhereMissing)
  for (const tile of detail) {
    add(tile)
    for (let level = tile.level + 1; level <= backdropLevel; level++) {
      add(ancestorAt(tile, level))
    }
    addLeftOverWhereMissing(tile)
  }
  return [...candidates.values()]
}

function finerLeftOver(tile: TerrainTile, finestLevel: number, stateOf: StateOf, levels: number): TerrainTile[] {
  if (levels === 0 || tile.level <= finestLevel) {
    return []
  }
  return childrenOf(tile).flatMap((child) => [...(stateOf(child) === 'none' ? [] : [child]), ...finerLeftOver(child, finestLevel, stateOf, levels - 1)])
}

function wantedInOrder(sight: CameraSight, backdrop: TerrainTile[], detail: TerrainTile[], backdropLevel: number, stateOf: StateOf): TerrainTile[] {
  const previews = detail
    .filter((tile) => tile.level + PREVIEW_LEVELS_ABOVE_DETAIL < backdropLevel)
    .map((tile) => ancestorAt(tile, tile.level + PREVIEW_LEVELS_ABOVE_DETAIL))
  const wanted = new Map<string, TerrainTile>()
  for (const group of [backdrop, previews, detail]) {
    group
      .filter((tile) => stateOf(tile) !== 'fresh')
      .sort(nearestFirst(sight))
      .forEach((tile) => wanted.set(tileKey(tile), wanted.get(tileKey(tile)) ?? tile))
  }
  return [...wanted.values()]
}

function nearestFirst(sight: CameraSight): (first: TerrainTile, second: TerrainTile) => number {
  return (first, second) => sight.distanceFromLookedAt(first) - sight.distanceFromLookedAt(second)
}
