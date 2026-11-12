import { describe, expect, it } from 'vitest'
import { decodeTile, gridIndex } from '../bridge/tileDecoding'
import type { MapLabel, MapTile, TileBlocks } from '../bridge/types'
import { countShares, heightBandShares } from './census'
import { bandIndexOf, HEIGHT_BANDS, heightColor } from './heightColors'
import { arrowCorners } from './playerArrow'
import { stylePixels, type TileStyle } from './tileStyle'
import { coarserTile, previewsFor, renderBlocksPerPixel, screenToWorldX, visibleTiles, worldToScreenX, type Viewport } from './viewport'

const PLAINS: MapLabel = { kind: 'BIOME', key: 'plains', name: 'Plains', color: 0x00ff00 }
const DESERT: MapLabel = { kind: 'BIOME', key: 'desert', name: 'Desert', color: 0xffff00 }
const WARM: MapLabel = { kind: 'REGION', key: 'warm', name: 'Warm', color: 0xff0000 }
const FLAT: TileStyle = { byBlock: false, shading: false, regionBorders: false, highlight: null, seaLevel: 63, pickDisplay: 'picks' }

/** A tile whose left half is plains and right half desert, all in one region, at height 70. */
function splitTile(size = 4): MapTile {
  const cells = (size + 2) ** 2
  const subjects = new Int16Array(cells)
  for (let cell = 0; cell < cells; cell++) {
    subjects[cell] = cell % (size + 2) <= size / 2 ? 0 : 1
  }
  return {
    size,
    blocksPerPixel: 1,
    subjectKind: 'BIOME',
    labels: [PLAINS, DESERT, WARM],
    subjects,
    regions: new Int16Array(cells).fill(2),
    heights: new Float32Array(cells).fill(70),
    pickNoise: null,
    blocks: null
  }
}

/** A tile as the bridge sends it: u32 header length, JSON header, aligned grids. */
function encode(size: number, withHeights: boolean, withPickNoise = false): ArrayBuffer {
  const header = new TextEncoder().encode(
    JSON.stringify({ size, blocksPerPixel: 2, subjectKind: 'BIOME', labels: [PLAINS], heights: withHeights, pickNoise: withPickNoise })
  )
  const cells = (size + 2) ** 2
  const align = (bytes: number): number => Math.ceil(bytes / 4) * 4
  const subjectsAt = align(4 + header.length)
  const regionsAt = align(subjectsAt + cells * 2)
  const heightsAt = align(regionsAt + cells * 2)
  const pickNoiseAt = heightsAt + (withHeights ? cells * 4 : 0)
  const buffer = new ArrayBuffer(pickNoiseAt + (withPickNoise ? cells * 4 : 0))
  new DataView(buffer).setUint32(0, header.length, true)
  new Uint8Array(buffer, 4).set(header)
  new Int16Array(buffer, subjectsAt, cells).fill(0)
  new Int16Array(buffer, regionsAt, cells).fill(-1)
  if (withHeights) {
    new Float32Array(buffer, heightsAt, cells).fill(81.5)
  }
  if (withPickNoise) {
    new Float32Array(buffer, pickNoiseAt, cells).fill(0.25)
  }
  return buffer
}

function nothingRaised(cells: number): Pick<TileBlocks, 'raisedStart' | 'raisedY' | 'raised'> {
  return { raisedStart: new Int32Array(cells + 1), raisedY: new Int16Array(0), raised: new Int16Array(0) }
}

/** A tile of one pixel with its blocks as the bridge sends them: ground, cover and the raised blocks' counts, heights and blocks. */
function encodeBlocks(raisedCounts: number[], raisedY: number[], raised: number[]): ArrayBuffer {
  const size = 1
  const cells = (size + 2) ** 2
  const header = new TextEncoder().encode(
    JSON.stringify({ size, blocksPerPixel: 1, subjectKind: 'BIOME', labels: [PLAINS], heights: false, pickNoise: false, blocks: ['stone', 'leaves', 'log'], raised: raised.length })
  )
  const align = (bytes: number): number => Math.ceil(bytes / 4) * 4
  const grid = align(cells * 2)
  const groundAt = align(4 + header.length) + grid * 2
  const raisedYAt = groundAt + grid * 3
  const raisedAt = align(raisedYAt + raised.length * 2)
  const buffer = new ArrayBuffer(raisedAt + align(raised.length * 2))
  new DataView(buffer).setUint32(0, header.length, true)
  new Uint8Array(buffer, 4).set(header)
  new Int16Array(buffer, groundAt + grid, cells).fill(-1)
  new Int16Array(buffer, groundAt + grid * 2, cells).set(raisedCounts)
  new Int16Array(buffer, raisedYAt, raised.length).set(raisedY)
  new Int16Array(buffer, raisedAt, raised.length).set(raised)
  return buffer
}

describe('tile decoding', () => {
  it('reads the raised blocks of each cell after the cover grid', () => {
    const blocks = decodeTile(encodeBlocks([0, 0, 0, 0, 2, 0, 0, 1, 0], [70, 69, 66], [1, 2, 1])).blocks!

    expect([...blocks.raisedStart]).toEqual([0, 0, 0, 0, 0, 2, 2, 2, 3, 3])
    expect([...blocks.raisedY]).toEqual([70, 69, 66])
    expect([...blocks.raised]).toEqual([1, 2, 1])
    expect(blocks.cover[4]).toBe(-1)
  })

  it('reads the header and views each grid where the bridge put it', () => {
    const tile = decodeTile(encode(3, true))

    expect(tile.size).toBe(3)
    expect(tile.blocksPerPixel).toBe(2)
    expect(tile.labels[0].key).toBe('plains')
    expect(tile.subjects.length).toBe(25)
    expect(tile.regions[0]).toBe(-1)
    expect(tile.heights?.[24]).toBe(81.5)
  })

  it('has no heights when the header says so', () => {
    expect(decodeTile(encode(3, false)).heights).toBeNull()
  })

  it('reads the pick noise after the heights, or in their place without them', () => {
    expect(decodeTile(encode(3, true, true)).pickNoise?.[24]).toBe(0.25)
    expect(decodeTile(encode(3, false, true)).pickNoise?.[0]).toBe(0.25)
    expect(decodeTile(encode(3, true)).pickNoise).toBeNull()
  })
})

describe('pick noise', () => {
  function noisyTile(noise: number): MapTile {
    const tile = splitTile()
    return { ...tile, pickNoise: new Float32Array(tile.subjects.length).fill(noise) }
  }

  it('draws the bare noise from dark to white', () => {
    const pixels = stylePixels(noisyTile(1), { ...FLAT, pickDisplay: 'noise' })

    expect([pixels[0], pixels[1], pixels[2]]).toEqual([255, 255, 255])
  })

  it('draws the picks darker where their noise is lower', () => {
    const pixels = stylePixels(noisyTile(0), { ...FLAT, pickDisplay: 'both' })

    expect(pixels[1]).toBe(Math.floor(255 * 0.3))
  })

  it('leaves a spot nothing picked in its own colour', () => {
    const pixels = stylePixels(noisyTile(Number.NaN), { ...FLAT, pickDisplay: 'noise' })

    expect([pixels[0], pixels[1], pixels[2]]).toEqual([0, 255, 0])
  })
})

describe('height colours', () => {
  it('runs from deep water to snow and stays at the ends beyond them', () => {
    expect(heightColor(63, 63)).toBe(0x7db8d8)
    expect(heightColor(-1000, 63)).toBe(0x15304f)
    expect(heightColor(1000, 63)).toBe(0xf4f4f4)
  })

  it('puts each height in the band the legend names', () => {
    expect(HEIGHT_BANDS[bandIndexOf(63, 63)].name).toBe('0–10 below sea')
    expect(HEIGHT_BANDS[bandIndexOf(64, 63)].name).toBe('0–8 above sea')
    expect(HEIGHT_BANDS[bandIndexOf(500, 63)].name).toBe('300+ above sea')
    expect(HEIGHT_BANDS[bandIndexOf(-500, 63)].name).toBe('40+ below sea')
  })
})

describe('tile style', () => {
  const pixel = (pixels: Uint8ClampedArray, size: number, x: number, z: number): number => {
    const at = (z * size + x) * 4
    return (pixels[at] << 16) | (pixels[at + 1] << 8) | pixels[at + 2]
  }

  it('paints each pixel in its label colour', () => {
    const pixels = stylePixels(splitTile(), FLAT)

    expect(pixel(pixels, 4, 0, 0)).toBe(PLAINS.color)
    expect(pixel(pixels, 4, 3, 3)).toBe(DESERT.color)
  })

  it('paints each pixel in the colour of what stands on its ground, or of the ground itself', () => {
    const tile = splitTile()
    const cells = tile.subjects.length
    const cover = new Int16Array(cells).fill(-1)
    cover[gridIndex(tile, 3, 3)] = 1
    const blocks = { ...nothingRaised(cells), names: ['stone', 'poppy'], ground: new Int16Array(cells).fill(0), cover, colors: [0x808080, 0xff0000], looks: [], covers: [] }
    const pixels = stylePixels({ ...tile, blocks }, { ...FLAT, byBlock: true })

    expect(pixel(pixels, 4, 0, 0)).toBe(0x808080)
    expect(pixel(pixels, 4, 3, 3)).toBe(0xff0000)
  })

  it('paints a column with blocks raised over its ground in the colour of the highest', () => {
    const tile = splitTile()
    const cells = tile.subjects.length
    const raisedStart = new Int32Array(cells + 1)
    raisedStart.fill(2, gridIndex(tile, 3, 3) + 1)
    const blocks = {
      names: ['stone', 'leaves', 'log'],
      ground: new Int16Array(cells).fill(0),
      cover: new Int16Array(cells).fill(-1),
      raisedStart,
      raisedY: Int16Array.of(70, 69),
      raised: Int16Array.of(1, 2),
      colors: [0x808080, 0x00ff00, 0x654321],
      looks: [],
      covers: []
    }
    const pixels = stylePixels({ ...tile, blocks }, { ...FLAT, byBlock: true })

    expect(pixel(pixels, 4, 0, 0)).toBe(0x808080)
    expect(pixel(pixels, 4, 3, 3)).toBe(0x00ff00)
  })

  it('keeps the label colours of a tile without blocks', () => {
    expect(pixel(stylePixels(splitTile(), { ...FLAT, byBlock: true }), 4, 0, 0)).toBe(PLAINS.color)
  })

  it('dims everything but the highlight, and tints the highlight edge', () => {
    const pixels = stylePixels(splitTile(), { ...FLAT, highlight: { kind: 'BIOME', key: 'plains', name: 'Plains' } })

    expect(pixel(pixels, 4, 3, 3)).not.toBe(DESERT.color)
    expect(pixel(pixels, 4, 0, 1)).toBe(PLAINS.color)
    expect(pixel(pixels, 4, 1, 1)).not.toBe(PLAINS.color)
  })

  it('leaves flat ground almost as it is when shading', () => {
    const pixels = stylePixels(splitTile(), { ...FLAT, shading: true })
    const green = (pixel(pixels, 4, 0, 0) >> 8) & 0xff

    expect(green).toBeGreaterThan(200)
  })
})

describe('viewport', () => {
  const view: Viewport = { centerX: 100, centerZ: -50, blocksPerPixel: 2, width: 800, height: 600 }

  it('maps screen and world both ways', () => {
    expect(screenToWorldX(view, 400)).toBe(100)
    expect(worldToScreenX(view, screenToWorldX(view, 123))).toBe(123)
  })

  it('draws tiles at the power of two nearest a device pixel, never finer than a block', () => {
    expect(renderBlocksPerPixel(4, 1)).toBe(4)
    expect(renderBlocksPerPixel(4, 2)).toBe(2)
    expect(renderBlocksPerPixel(0.25, 1)).toBe(1)
    expect(renderBlocksPerPixel(5.5, 1)).toBe(4)
  })

  it('covers the screen, starting from the middle', () => {
    const tiles = visibleTiles(view, 2, 128)

    // 1600 × 1200 blocks around (100, -50) in tiles of 256 blocks: x -3 to 3, z -3 to 2.
    expect(tiles.length).toBe(7 * 6)
    expect(tiles[0]).toEqual({ tileX: 0, tileZ: -1, blocksPerPixel: 2 })
  })

  it('finds the coarser tile that covers a tile', () => {
    expect(coarserTile({ tileX: -3, tileZ: 5, blocksPerPixel: 2 }, 2)).toEqual({ tileX: -1, tileZ: 1, blocksPerPixel: 8 })
  })

  it('previews four blank tiles with one tile a level out, never past the coarsest scale', () => {
    const blank = [
      { tileX: 0, tileZ: 0, blocksPerPixel: 2 },
      { tileX: 1, tileZ: 0, blocksPerPixel: 2 },
      { tileX: 0, tileZ: 1, blocksPerPixel: 2 },
      { tileX: -1, tileZ: 0, blocksPerPixel: 2 },
      { tileX: 0, tileZ: 0, blocksPerPixel: 4096 }
    ]

    expect(previewsFor(blank)).toEqual([
      { tileX: 0, tileZ: 0, blocksPerPixel: 4 },
      { tileX: -1, tileZ: 0, blocksPerPixel: 4 }
    ])
  })
})

describe('census', () => {
  it('shares the visible pixels between the labels', () => {
    const tile = splitTile(4)
    const shares = countShares([{ tile, worldX: 0, worldZ: 0 }], { minimumX: 0, minimumZ: 0, maximumX: 4, maximumZ: 4 })

    expect(shares.map((share) => share.subject.key).sort()).toEqual(['desert', 'plains'])
    expect(shares[0].fraction).toBeCloseTo(0.5)
    expect(gridIndex(tile, -1, -1)).toBe(0)
  })

  it('counts only the part of a tile inside the area, largest share first', () => {
    const shares = countShares([{ tile: splitTile(4), worldX: 0, worldZ: 0 }], { minimumX: 0, minimumZ: 0, maximumX: 3, maximumZ: 4 })

    expect(shares.map((share) => share.subject.key)).toEqual(['plains', 'desert'])
    expect(shares[0].fraction).toBeCloseTo(2 / 3)
  })

  it('shares spot heights between their bands, highest band first', () => {
    const shares = heightBandShares([64, 64, 64, 500, 30], 63)

    expect(shares.map((share) => share.subject.name)).toEqual(['300+ above sea', '0–8 above sea', '10–40 below sea'])
    expect(shares.map((share) => share.fraction)).toEqual([0.2, 0.6, 0.2])
    expect(shares[1].color).toBe(HEIGHT_BANDS[bandIndexOf(64, 63)].color)
  })

  it('has no shares for no heights', () => {
    expect(heightBandShares([], 63)).toEqual([])
  })
})

describe('player arrow', () => {
  const tipAt = (yaw: number) => arrowCorners(yaw, 10, 7, 3)[0]

  it('points the tip south, down the map, at yaw 0', () => {
    expect(tipAt(0).x).toBeCloseTo(0)
    expect(tipAt(0).z).toBeCloseTo(10)
  })

  it('points west at 90, north at 180 and east at 270', () => {
    expect(tipAt(90).x).toBeCloseTo(-10)
    expect(tipAt(90).z).toBeCloseTo(0)
    expect(tipAt(180).z).toBeCloseTo(-10)
    expect(tipAt(270).x).toBeCloseTo(10)
  })

  it('keeps its shape while turning', () => {
    const [tip, right, notch, left] = arrowCorners(37, 10, 7, 3)

    expect(Math.hypot(tip.x, tip.z)).toBeCloseTo(10)
    expect(Math.hypot(right.x - left.x, right.z - left.z)).toBeCloseTo(14)
    expect(Math.hypot(notch.x, notch.z)).toBeCloseTo(7)
  })
})
