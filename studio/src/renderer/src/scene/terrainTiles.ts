import type { TileAddress } from '../map/viewport'

/** Columns along one side of a terrain tile, at every level. */
export const TILE_COLUMNS = 128

/**
 * A square of terrain sampled in one go: {@link TILE_COLUMNS} by {@link TILE_COLUMNS} columns, each 2^level blocks wide.
 * Tiles of a level lie edge to edge and each covers exactly four of the level below, so finer tiles can stand in for
 * parts of a coarser one.
 */
export interface TerrainTile {
  level: number
  tileX: number
  tileZ: number
}

/** Past this many levels finer, a tile covers less than one column of a coarser one, which cannot be cut a hole. */
export const MOST_LEVELS_FINER = Math.log2(TILE_COLUMNS)

/** Columns of a tile, from the first up to but not including the last. */
export interface ColumnArea {
  fromColumn: number
  fromRow: number
  toColumn: number
  toRow: number
}

export function blocksPerColumn(level: number): number {
  return 2 ** level
}

export function tileBlocks(level: number): number {
  return TILE_COLUMNS * blocksPerColumn(level)
}

export function tileKey(tile: TerrainTile): string {
  return `${tile.level}/${tile.tileX}/${tile.tileZ}`
}

export function sameTile(first: TerrainTile, second: TerrainTile): boolean {
  return first.level === second.level && first.tileX === second.tileX && first.tileZ === second.tileZ
}

export function originXOf(tile: TerrainTile): number {
  return tile.tileX * tileBlocks(tile.level)
}

export function originZOf(tile: TerrainTile): number {
  return tile.tileZ * tileBlocks(tile.level)
}

/** The tile of {@code level} that holds the block. */
export function tileContaining(worldX: number, worldZ: number, level: number): TerrainTile {
  const blocks = tileBlocks(level)
  return { level, tileX: Math.floor(worldX / blocks), tileZ: Math.floor(worldZ / blocks) }
}

/** What to ask the bridge for: tile (x, z) of a layer sampled a column per pixel covers the same blocks. */
export function addressOf(tile: TerrainTile): TileAddress {
  return { tileX: tile.tileX, tileZ: tile.tileZ, blocksPerPixel: blocksPerColumn(tile.level) }
}

/** The four tiles of the level below that cover this one. */
export function childrenOf(tile: TerrainTile): TerrainTile[] {
  const level = tile.level - 1
  const tileX = tile.tileX * 2
  const tileZ = tile.tileZ * 2
  return [
    { level, tileX, tileZ },
    { level, tileX: tileX + 1, tileZ },
    { level, tileX, tileZ: tileZ + 1 },
    { level, tileX: tileX + 1, tileZ: tileZ + 1 }
  ]
}

/** The tile of a coarser level that this one lies inside. */
export function ancestorAt(tile: TerrainTile, level: number): TerrainTile {
  const levelsUp = level - tile.level
  return { level, tileX: tile.tileX >> levelsUp, tileZ: tile.tileZ >> levelsUp }
}

export function isInside(tile: TerrainTile, coarser: TerrainTile): boolean {
  return tile.level < coarser.level && sameTile(ancestorAt(tile, coarser.level), coarser)
}

/** The columns of {@code coarser} that the finer tile inside it covers. */
export function columnsUnder(coarser: TerrainTile, finer: TerrainTile): ColumnArea {
  const levelsDown = coarser.level - finer.level
  const across = TILE_COLUMNS >> levelsDown
  const fromColumn = (finer.tileX - coarser.tileX * 2 ** levelsDown) * across
  const fromRow = (finer.tileZ - coarser.tileZ * 2 ** levelsDown) * across
  return { fromColumn, fromRow, toColumn: fromColumn + across, toRow: fromRow + across }
}
