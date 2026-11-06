import type { NoiseSource } from '../bridge/types'
import { DEEPEST_BELOW_SEA_BLOCKS, heightColor, HIGHEST_ABOVE_SEA_BLOCKS } from '../map/heightColors'

export type NoisePaletteId = 'terrain' | 'signed' | 'grayscale' | 'height' | 'green'

interface NoisePalette {
  id: NoisePaletteId
  label: string
  minimum: number
  maximum: number
  /** Colours from the minimum to the maximum, evenly spaced; null for the map's height colours. */
  stops: number[] | null
}

/** v3e's noise palettes, each with the values it spans when the noise is not measured in blocks. */
export const NOISE_PALETTES: readonly NoisePalette[] = [
  { id: 'terrain', label: 'Terrain', minimum: 0, maximum: 1, stops: [0x071a2f, 0x155e75, 0x2a9d8f, 0xe9c46a, 0xf4f1de] },
  { id: 'signed', label: 'Signed', minimum: -1, maximum: 1, stops: [0x173b66, 0x4f86c6, 0xe7edf3, 0xe89555, 0x9d3b35] },
  { id: 'grayscale', label: 'Grayscale', minimum: 0, maximum: 1, stops: [0x050505, 0xffffff] },
  { id: 'height', label: 'Height', minimum: 0, maximum: 1, stops: null },
  { id: 'green', label: 'Green', minimum: 0, maximum: 1, stops: [0x2a3330, 0x5fd3a8] }
]

/** The colour of a value the noise couldn't give, so it stands out from every palette. */
export const INVALID_NOISE_COLOR = 0xff2daa
const LOOKUP_SIZE = 256

/** How values become colours: the span the palette covers, and the colour of a value. */
export interface NoiseScale {
  minimum: number
  maximum: number
  /** Over and under the span take the end colours. */
  colorOf(value: number): number
  /** The colour a fraction of the way along the span, for the legend. */
  colorAlong(fraction: number): number
}

/**
 * The scale for a palette and a source: noise of 0 to 1 spans the palette's own range; noise in blocks spans its block
 * range, and the height palette then colours it as the map colours heights above the fluid.
 */
export function noiseScale(paletteId: NoisePaletteId, source: NoiseSource | null): NoiseScale {
  const palette = NOISE_PALETTES.find((candidate) => candidate.id === paletteId)!
  const blocks = source?.blockRange ?? null
  const minimum = blocks?.lowest ?? palette.minimum
  const maximum = blocks?.highest ?? palette.maximum
  const colorAlong = paletteColors(palette, blocks === null ? null : [blocks.lowest, blocks.highest])
  const span = maximum - minimum
  return {
    minimum,
    maximum,
    colorAlong,
    colorOf: (value) => (Number.isFinite(value) ? colorAlong(span <= 0 ? 0.5 : (value - minimum) / span) : INVALID_NOISE_COLOR)
  }
}

function paletteColors(palette: NoisePalette, blockSpan: [number, number] | null): (fraction: number) => number {
  if (palette.stops === null) {
    const [lowest, highest] = blockSpan ?? [-DEEPEST_BELOW_SEA_BLOCKS, HIGHEST_ABOVE_SEA_BLOCKS]
    return (fraction) => heightColor(lowest + (highest - lowest) * clampUnit(fraction), 0)
  }
  const lookup = lookupOf(palette.stops)
  return (fraction) => lookup[Math.round(clampUnit(fraction) * (LOOKUP_SIZE - 1))]
}

function lookupOf(stops: number[]): number[] {
  return Array.from({ length: LOOKUP_SIZE }, (_, index) => {
    const position = (index / (LOOKUP_SIZE - 1)) * (stops.length - 1)
    const lower = Math.min(stops.length - 2, Math.floor(position))
    return mix(stops[lower], stops[lower + 1], position - lower)
  })
}

function mix(from: number, to: number, fraction: number): number {
  const channel = (shift: number): number => Math.round(((from >>> shift) & 0xff) + (((to >>> shift) & 0xff) - ((from >>> shift) & 0xff)) * fraction)
  return (channel(16) << 16) | (channel(8) << 8) | channel(0)
}

function clampUnit(fraction: number): number {
  return Math.min(1, Math.max(0, fraction))
}
