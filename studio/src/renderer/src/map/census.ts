import { gridIndex } from '../bridge/tileDecoding'
import { NO_LABEL, type MapTile } from '../bridge/types'
import { bandIndexOf, HEIGHT_BANDS } from './heightColors'
import type { Highlight } from './tileStyle'

/** How much of the screen one biome, region or height band covers. */
export interface Share {
  subject: Highlight
  color: number
  fraction: number
}

/** A loaded tile and where its top left pixel lies in the world. */
export interface PlacedTile {
  tile: MapTile
  worldX: number
  worldZ: number
}

export interface WorldArea {
  minimumX: number
  minimumZ: number
  maximumX: number
  maximumZ: number
}

// Enough samples that a share is right to a fraction of a percent, few enough to count every frame the map settles.
const MAXIMUM_COUNTED_PIXELS = 250_000

/**
 * The shares of what the tiles show inside {@code area}, largest first: their subjects (biomes or regions), or their
 * height bands when {@code byHeight} is set.
 */
export function countShares(tiles: PlacedTile[], area: WorldArea, byHeight: boolean, seaLevel: number): Share[] {
  const counts = new Map<string, { subject: Highlight; color: number; count: number }>()
  const totalPixels = tiles.reduce((sum, placed) => sum + placed.tile.size * placed.tile.size, 0)
  const stride = Math.max(1, Math.ceil(Math.sqrt(totalPixels / MAXIMUM_COUNTED_PIXELS)))
  let counted = 0
  for (const { tile, worldX, worldZ } of tiles) {
    const scale = tile.blocksPerPixel
    const firstX = Math.max(0, Math.ceil((area.minimumX - worldX) / scale))
    const endX = Math.min(tile.size, Math.ceil((area.maximumX - worldX) / scale))
    const firstZ = Math.max(0, Math.ceil((area.minimumZ - worldZ) / scale))
    const endZ = Math.min(tile.size, Math.ceil((area.maximumZ - worldZ) / scale))
    for (let pixelZ = firstZ; pixelZ < endZ; pixelZ += stride) {
      for (let pixelX = firstX; pixelX < endX; pixelX += stride) {
        const subject = subjectAt(tile, gridIndex(tile, pixelX, pixelZ), byHeight, seaLevel)
        if (subject === null) {
          continue
        }
        const id = `${subject.subject.kind}:${subject.subject.key}`
        const entry = counts.get(id) ?? { ...subject, count: 0 }
        entry.count++
        counts.set(id, entry)
        counted++
      }
    }
  }
  return [...counts.values()]
    .map((entry) => ({ subject: entry.subject, color: entry.color, fraction: entry.count / Math.max(1, counted) }))
    .sort((first, second) => second.fraction - first.fraction)
}

function subjectAt(tile: MapTile, cell: number, byHeight: boolean, seaLevel: number): { subject: Highlight; color: number } | null {
  if (byHeight) {
    if (tile.heights === null) {
      return null
    }
    const band = HEIGHT_BANDS[bandIndexOf(tile.heights[cell], seaLevel)]
    return { subject: { kind: 'HEIGHT', key: band.key, name: band.name }, color: band.color }
  }
  const index = tile.subjects[cell]
  if (index === NO_LABEL) {
    return null
  }
  const label = tile.labels[index]
  return { subject: { kind: label.kind, key: label.key, name: label.name }, color: label.color }
}
