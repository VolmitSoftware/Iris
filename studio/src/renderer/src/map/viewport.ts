/** Where the map looks and how far it is zoomed; screen sizes are CSS pixels. */
export interface Viewport {
  centerX: number
  centerZ: number
  blocksPerPixel: number
  width: number
  height: number
}

export interface TileAddress {
  tileX: number
  tileZ: number
  blocksPerPixel: number
}

export const MINIMUM_VIEW_BLOCKS_PER_PIXEL = 1 / 8
export const MAXIMUM_BLOCKS_PER_PIXEL = 4096
export const DEFAULT_BLOCKS_PER_PIXEL = 4
// Iris has nothing finer than a block, so closer views magnify the block-sized tiles.
const MINIMUM_RENDER_BLOCKS_PER_PIXEL = 1

export function clampBlocksPerPixel(blocksPerPixel: number): number {
  return Math.min(MAXIMUM_BLOCKS_PER_PIXEL, Math.max(MINIMUM_VIEW_BLOCKS_PER_PIXEL, blocksPerPixel))
}

export function screenToWorldX(view: Viewport, screenX: number): number {
  return view.centerX + (screenX - view.width / 2) * view.blocksPerPixel
}

export function screenToWorldZ(view: Viewport, screenZ: number): number {
  return view.centerZ + (screenZ - view.height / 2) * view.blocksPerPixel
}

export function worldToScreenX(view: Viewport, worldX: number): number {
  return (worldX - view.centerX) / view.blocksPerPixel + view.width / 2
}

export function worldToScreenZ(view: Viewport, worldZ: number): number {
  return (worldZ - view.centerZ) / view.blocksPerPixel + view.height / 2
}

/**
 * The scale tiles are drawn at for a view: the nearest power of two to one tile pixel per device pixel, so tiles stay
 * sharp on dense screens and every zoom between two levels reuses the same tiles.
 */
export function renderBlocksPerPixel(
  viewBlocksPerPixel: number,
  devicePixelRatio: number,
  finestBlocksPerPixel = MINIMUM_RENDER_BLOCKS_PER_PIXEL
): number {
  const level = 2 ** Math.round(Math.log2(viewBlocksPerPixel / devicePixelRatio))
  return Math.min(MAXIMUM_BLOCKS_PER_PIXEL, Math.max(finestBlocksPerPixel, level))
}

/** The tiles covering the view, nearest the centre first so the middle of the screen fills in first. */
export function visibleTiles(view: Viewport, renderScale: number, tileSize: number): TileAddress[] {
  const span = tileSize * renderScale
  const firstX = Math.floor(screenToWorldX(view, 0) / span)
  const lastX = Math.floor(screenToWorldX(view, view.width) / span)
  const firstZ = Math.floor(screenToWorldZ(view, 0) / span)
  const lastZ = Math.floor(screenToWorldZ(view, view.height) / span)
  const middleX = view.centerX / span - 0.5
  const middleZ = view.centerZ / span - 0.5
  const tiles: TileAddress[] = []
  for (let tileZ = firstZ; tileZ <= lastZ; tileZ++) {
    for (let tileX = firstX; tileX <= lastX; tileX++) {
      tiles.push({ tileX, tileZ, blocksPerPixel: renderScale })
    }
  }
  const distance = (tile: TileAddress): number => (tile.tileX - middleX) ** 2 + (tile.tileZ - middleZ) ** 2
  return tiles.sort((first, second) => distance(first) - distance(second))
}

/**
 * One tile a level out for each group of blank tiles, in their order: a quarter of the pixels, so the screen fills
 * about four times sooner and sharpens as the tiles themselves arrive.
 */
export function previewsFor(blankTiles: TileAddress[]): TileAddress[] {
  const previews = new Map<string, TileAddress>()
  for (const tile of blankTiles) {
    const preview = coarserTile(tile, 1)
    if (preview.blocksPerPixel <= MAXIMUM_BLOCKS_PER_PIXEL) {
      previews.set(`${preview.tileX}/${preview.tileZ}/${preview.blocksPerPixel}`, preview)
    }
  }
  return [...previews.values()]
}

/** The coarser tile that covers {@code tile}, {@code levels} zoom levels out. */
export function coarserTile(tile: TileAddress, levels: number): TileAddress {
  const factor = 2 ** levels
  return {
    tileX: Math.floor(tile.tileX / factor),
    tileZ: Math.floor(tile.tileZ / factor),
    blocksPerPixel: tile.blocksPerPixel * factor
  }
}
