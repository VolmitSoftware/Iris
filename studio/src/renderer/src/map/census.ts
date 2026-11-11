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
 * The shares of what the tiles show inside {@code area}, largest first: their subjects, which are biomes or regions.
 */
export function countShares(tiles: PlacedTile[], area: WorldArea): Share[] {
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
        const subject = subjectAt(tile, gridIndex(tile, pixelX, pixelZ))
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

/** The shares of the height bands the {@code heights} fall in, highest band first. */
export function heightBandShares(heights: readonly number[], seaLevel: number): Share[] {
  const counts = new Map<number, number>()
  for (const height of heights) {
    const band = bandIndexOf(height, seaLevel)
    counts.set(band, (counts.get(band) ?? 0) + 1)
  }
  return [...counts]
    .sort(([firstBand], [secondBand]) => secondBand - firstBand)
    .map(([band, count]) => {
      const { key, name, color } = HEIGHT_BANDS[band]
      return { subject: { kind: 'HEIGHT', key, name }, color, fraction: count / heights.length }
    })
}

function subjectAt(tile: MapTile, cell: number): { subject: Highlight; color: number } | null {
  const index = tile.subjects[cell]
  if (index === NO_LABEL) {
    return null
  }
  const label = tile.labels[index]
  return { subject: { kind: label.kind, key: label.key, name: label.name }, color: label.color }
}
