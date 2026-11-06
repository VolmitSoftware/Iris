import { coarserTile, previewsFor, visibleTiles, worldToScreenX, worldToScreenZ, type TileAddress, type Viewport } from './viewport'

// While a tile is on its way, a coarser one already drawn stands in, up to this many zoom levels out.
const COARSER_FALLBACK_LEVELS = 6

export interface DrawnTiles {
  /** Every tile the view covers, nearest the centre first. */
  tiles: TileAddress[]
  ready: number
  /** What to fetch, in order: a coarser preview for each blank stretch first, then the tiles themselves. */
  wanted: TileAddress[]
}

/**
 * Draws the tiles covering the view at {@code renderScale}, from {@code pictureOf}; where one hasn't arrived, the part
 * of a coarser one that covers it.
 */
export function drawVisibleTiles(
  context: CanvasRenderingContext2D,
  view: Viewport,
  renderScale: number,
  tileSize: number,
  pictureOf: (address: TileAddress) => HTMLCanvasElement | null
): DrawnTiles {
  const tiles = visibleTiles(view, renderScale, tileSize)
  context.imageSmoothingEnabled = view.blocksPerPixel > renderScale
  let ready = 0
  const blank: TileAddress[] = []
  for (const address of tiles) {
    const picture = pictureOf(address)
    if (picture !== null) {
      drawTile(context, view, tileSize, picture, address, 0, 0, picture.width)
      ready++
    } else if (!drawFallback(context, view, tileSize, address, pictureOf)) {
      blank.push(address)
    }
  }
  return { tiles, ready, wanted: [...previewsFor(blank), ...tiles] }
}

/** Draws the part of a tile picture from (sourceX, sourceZ), {@code sourceSize} pixels wide, over {@code address}. */
function drawTile(
  context: CanvasRenderingContext2D,
  view: Viewport,
  tileSize: number,
  picture: HTMLCanvasElement,
  address: TileAddress,
  sourceX: number,
  sourceZ: number,
  sourceSize: number
): void {
  const span = tileSize * address.blocksPerPixel
  const left = Math.round(worldToScreenX(view, address.tileX * span))
  const top = Math.round(worldToScreenZ(view, address.tileZ * span))
  const right = Math.round(worldToScreenX(view, (address.tileX + 1) * span))
  const bottom = Math.round(worldToScreenZ(view, (address.tileZ + 1) * span))
  context.drawImage(picture, sourceX, sourceZ, sourceSize, sourceSize, left, top, right - left, bottom - top)
}

/** Draws the part of a coarser tile that covers {@code address}; false when no coarser tile has arrived either. */
function drawFallback(
  context: CanvasRenderingContext2D,
  view: Viewport,
  tileSize: number,
  address: TileAddress,
  pictureOf: (address: TileAddress) => HTMLCanvasElement | null
): boolean {
  for (let levels = 1; levels <= COARSER_FALLBACK_LEVELS; levels++) {
    const coarser = coarserTile(address, levels)
    const picture = pictureOf(coarser)
    if (picture === null) {
      continue
    }
    const factor = 2 ** levels
    const sourceSize = picture.width / factor
    const sourceX = (address.tileX - coarser.tileX * factor) * sourceSize
    const sourceZ = (address.tileZ - coarser.tileZ * factor) * sourceSize
    drawTile(context, view, tileSize, picture, address, sourceX, sourceZ, sourceSize)
    return true
  }
  return false
}
