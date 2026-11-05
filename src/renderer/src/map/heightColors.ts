/** The height scale of the map, from deep water to snowy peaks, measured from sea level (v3e HeightColors). */

const DEEPEST_BELOW_SEA_BLOCKS = 90
const HIGHEST_ABOVE_SEA_BLOCKS = 300
const DEPTH_BLOCKS = [0, 10, 40, DEEPEST_BELOW_SEA_BLOCKS]
const DEPTH_COLORS = [0x7db8d8, 0x4c8dba, 0x2a5e8f, 0x15304f]
const ABOVE_SEA_BLOCKS = [0, 8, 30, 70, 130, 200, HIGHEST_ABOVE_SEA_BLOCKS]
const LAND_COLORS = [0x7fa35a, 0x5e8c44, 0x8fa65a, 0xc2ae78, 0x8e7663, 0xa9a6a2, 0xf4f4f4]
// Where each band ends, in blocks above sea; they follow the colour stops, merging the deepest two.
const BAND_TOPS_ABOVE_SEA = [-40, -10, 0, 8, 30, 70, 130, 200, 300, Number.POSITIVE_INFINITY]

/** A stretch of heights between two of the scale's stops, for telling how much ground lies at each height. */
export interface HeightBand {
  /** The band's place from the deepest, as text that sorts the same way. */
  key: string
  name: string
  color: number
}

export function heightColor(height: number, seaLevel: number): number {
  if (height <= seaLevel) {
    return along(DEPTH_BLOCKS, DEPTH_COLORS, seaLevel - height)
  }
  return along(ABOVE_SEA_BLOCKS, LAND_COLORS, height - seaLevel)
}

export const HEIGHT_BANDS: readonly HeightBand[] = createBands()

export function bandIndexOf(height: number, seaLevel: number): number {
  const aboveSea = height - seaLevel
  const index = BAND_TOPS_ABOVE_SEA.findIndex((top) => aboveSea <= top)
  return index < 0 ? BAND_TOPS_ABOVE_SEA.length - 1 : index
}

function createBands(): HeightBand[] {
  const bands: HeightBand[] = []
  let bottom = -DEEPEST_BELOW_SEA_BLOCKS
  BAND_TOPS_ABOVE_SEA.forEach((top, band) => {
    const colorHeight = Number.isFinite(top) ? (bottom + top) / 2 : bottom
    bands.push({ key: String(band).padStart(2, '0'), name: bandName(band, bottom, top), color: heightColor(colorHeight, 0) })
    bottom = top
  })
  return bands
}

function bandName(band: number, bottom: number, top: number): string {
  if (band === 0) {
    return `${Math.round(-top)}+ below sea`
  }
  if (!Number.isFinite(top)) {
    return `${Math.round(bottom)}+ above sea`
  }
  if (top <= 0) {
    return `${Math.round(-top)}–${Math.round(-bottom)} below sea`
  }
  return `${Math.round(bottom)}–${Math.round(top)} above sea`
}

function along(stops: number[], colors: number[], blocks: number): number {
  if (blocks <= stops[0]) {
    return colors[0]
  }
  for (let stop = 1; stop < stops.length; stop++) {
    if (blocks <= stops[stop]) {
      return blend(colors[stop - 1], colors[stop], (blocks - stops[stop - 1]) / (stops[stop] - stops[stop - 1]))
    }
  }
  return colors[colors.length - 1]
}

export function blend(first: number, second: number, amount: number): number {
  const red = Math.round(((first >> 16) & 0xff) * (1 - amount) + ((second >> 16) & 0xff) * amount)
  const green = Math.round(((first >> 8) & 0xff) * (1 - amount) + ((second >> 8) & 0xff) * amount)
  const blue = Math.round((first & 0xff) * (1 - amount) + (second & 0xff) * amount)
  return (red << 16) | (green << 8) | blue
}
