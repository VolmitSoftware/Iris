import { gridIndex } from '../bridge/tileDecoding'
import { NO_LABEL, type MapTile, type PickDisplay } from '../bridge/types'
import { bandIndexOf, blend, heightColor } from './heightColors'

/** What a highlight picks out: a biome or region by key, or a height band in the height view. */
export interface Highlight {
  kind: 'BIOME' | 'REGION' | 'HEIGHT'
  key: string
  name: string
}

export interface TileStyle {
  /** Colour by height instead of by label. */
  byHeight: boolean
  shading: boolean
  regionBorders: boolean
  highlight: Highlight | null
  seaLevel: number
  /** How tiles that carry their pick noise draw it. */
  pickDisplay: PickDisplay
}

export function styleKey(style: TileStyle): string {
  const highlight = style.highlight === null ? '' : `${style.highlight.kind}:${style.highlight.key}`
  return `${style.byHeight}|${style.shading}|${style.regionBorders}|${highlight}|${style.seaLevel}|${style.pickDisplay}`
}

const UNKNOWN_SUBJECT = 0x5a5c62
const SHADING_EXAGGERATION = 2
const LIGHT_X = -0.5
const LIGHT_Y = 0.7
const LIGHT_Z = -0.5
const LIGHT_LENGTH = Math.sqrt(LIGHT_X * LIGHT_X + LIGHT_Y * LIGHT_Y + LIGHT_Z * LIGHT_Z)
const AMBIENT_LIGHT = 0.35
const DIRECT_LIGHT = 0.75
const DARKEST_SHADE = 0.25
const BRIGHTEST_SHADE = 1.15
// Water lies flat over the seafloor, so only a hint of the relief beneath shows through.
const UNDERWATER_RELIEF = 0.15
const DIM_TARGET = 0x1e1f22
const DIM_AMOUNT = 0.72
const DIM_DESATURATION = 0.6
const HIGHLIGHT_EDGE = 0xffffff
// A solid outline turns biomes that are scattered pixel by pixel into white noise, so the edge only tints.
const HIGHLIGHT_EDGE_STRENGTH = 0.55
const REGION_BORDER = 0x101114
const REGION_BORDER_STRENGTH = 0.6
const NOISE_LOW = 0x050505
const NOISE_HIGH = 0xffffff
// Picks drawn by their noise keep some colour where the noise is low, so they still read as the pick.
const DARKEST_PICK_SHADE = 0.3

/** The tile's pixels as RGBA, as {@code style} draws them (v3e MapTile.styledPixels). */
export function stylePixels(tile: MapTile, style: TileStyle): Uint8ClampedArray<ArrayBuffer> {
  const { size, heights } = tile
  const pixels = new Uint8ClampedArray(size * size * 4)
  const colors = tile.labels.map((label) => label.color)
  const byHeight = style.byHeight && heights !== null
  const pickNoise = style.pickDisplay === 'picks' ? null : tile.pickNoise
  // The bare noise is read by its brightness, which relief shading would mix up.
  const shading = (style.shading || byHeight) && heights !== null && !(pickNoise !== null && style.pickDisplay === 'noise')
  const highlighted = highlightCells(tile, style)
  for (let pixelZ = 0; pixelZ < size; pixelZ++) {
    for (let pixelX = 0; pixelX < size; pixelX++) {
      const cell = gridIndex(tile, pixelX, pixelZ)
      let color = byHeight ? heightColor(heights[cell], style.seaLevel) : labelColor(colors, tile.subjects[cell])
      if (pickNoise !== null) {
        color = byNoise(color, pickNoise[cell], style.pickDisplay)
      }
      if (shading) {
        color = shade(tile, heights, cell, color, style.seaLevel)
      }
      if (highlighted !== null) {
        color = highlight(tile, highlighted, cell, color)
      }
      if (style.regionBorders && isRegionEdge(tile, cell)) {
        color = blend(color, REGION_BORDER, REGION_BORDER_STRENGTH)
      }
      const at = (pixelZ * size + pixelX) * 4
      pixels[at] = (color >> 16) & 0xff
      pixels[at + 1] = (color >> 8) & 0xff
      pixels[at + 2] = color & 0xff
      pixels[at + 3] = 0xff
    }
  }
  return pixels
}

function labelColor(colors: number[], index: number): number {
  return index === NO_LABEL ? UNKNOWN_SUBJECT : colors[index]
}

// Where nothing picked the spot, as on an image map, it keeps its colour.
function byNoise(color: number, noise: number, display: PickDisplay): number {
  if (Number.isNaN(noise)) {
    return color
  }
  const value = Math.min(1, Math.max(0, noise))
  return display === 'noise' ? blend(NOISE_LOW, NOISE_HIGH, value) : scale(color, DARKEST_PICK_SHADE + (1 - DARKEST_PICK_SHADE) * value)
}

// Lit from the north west like a relief map, so slopes read as hills and valleys at a glance.
function shade(tile: MapTile, heights: Float32Array, cell: number, color: number, seaLevel: number): number {
  const row = tile.size + 2
  const slopeX = (heights[cell + 1] - heights[cell - 1]) / (2 * tile.blocksPerPixel)
  const slopeZ = (heights[cell + row] - heights[cell - row]) / (2 * tile.blocksPerPixel)
  const normalX = -slopeX * SHADING_EXAGGERATION
  const normalZ = -slopeZ * SHADING_EXAGGERATION
  const normalLength = Math.sqrt(normalX * normalX + 1 + normalZ * normalZ)
  const light = (normalX * LIGHT_X + LIGHT_Y + normalZ * LIGHT_Z) / (normalLength * LIGHT_LENGTH)
  let factor = Math.min(BRIGHTEST_SHADE, Math.max(DARKEST_SHADE, AMBIENT_LIGHT + DIRECT_LIGHT * light))
  if (heights[cell] <= seaLevel) {
    factor = 1 - (1 - factor) * UNDERWATER_RELIEF
  }
  return scale(color, factor)
}

/** Which cells the highlight covers, as one flag per grid cell; null when nothing is highlighted. */
function highlightCells(tile: MapTile, style: TileStyle): Uint8Array | null {
  const target = style.highlight
  if (target === null) {
    return null
  }
  const cells = new Uint8Array(tile.subjects.length)
  if (target.kind === 'HEIGHT') {
    if (tile.heights !== null) {
      const band = Number(target.key)
      tile.heights.forEach((height, cell) => {
        cells[cell] = bandIndexOf(height, style.seaLevel) === band ? 1 : 0
      })
    }
    return cells
  }
  // Every view knows its regions, so a region can be highlighted in a biome view too.
  const grid = target.kind === tile.subjectKind ? tile.subjects : target.kind === 'REGION' ? tile.regions : null
  const index = tile.labels.findIndex((label) => label.kind === target.kind && label.key === target.key)
  if (grid !== null && index >= 0) {
    grid.forEach((label, cell) => {
      cells[cell] = label === index ? 1 : 0
    })
  }
  return cells
}

function highlight(tile: MapTile, highlighted: Uint8Array, cell: number, color: number): number {
  if (highlighted[cell] === 0) {
    return dim(color)
  }
  const row = tile.size + 2
  const edge = highlighted[cell + 1] === 0 || highlighted[cell - 1] === 0 || highlighted[cell + row] === 0 || highlighted[cell - row] === 0
  return edge ? blend(color, HIGHLIGHT_EDGE, HIGHLIGHT_EDGE_STRENGTH) : color
}

function isRegionEdge(tile: MapTile, cell: number): boolean {
  const region = tile.regions[cell]
  return tile.regions[cell + 1] !== region || tile.regions[cell + tile.size + 2] !== region
}

function dim(color: number): number {
  const gray = Math.floor((((color >> 16) & 0xff) + ((color >> 8) & 0xff) + (color & 0xff)) / 3)
  const desaturated = blend(color, (gray << 16) | (gray << 8) | gray, DIM_DESATURATION)
  return blend(desaturated, DIM_TARGET, DIM_AMOUNT)
}

function scale(color: number, factor: number): number {
  const red = Math.min(255, Math.floor(((color >> 16) & 0xff) * factor))
  const green = Math.min(255, Math.floor(((color >> 8) & 0xff) * factor))
  const blue = Math.min(255, Math.floor((color & 0xff) * factor))
  return (red << 16) | (green << 8) | blue
}
