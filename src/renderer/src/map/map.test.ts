import { describe, expect, it } from 'vitest'
import { decodeTile, gridIndex } from '../bridge/tileDecoding'
import type { MapLabel, MapTile } from '../bridge/types'
import { countShares } from './census'
import { bandIndexOf, HEIGHT_BANDS, heightColor } from './heightColors'
import { stylePixels, type TileStyle } from './tileStyle'
import { coarserTile, previewsFor, renderBlocksPerPixel, screenToWorldX, visibleTiles, worldToScreenX, type Viewport } from './viewport'

const PLAINS: MapLabel = { kind: 'BIOME', key: 'plains', name: 'Plains', color: 0x00ff00 }
const DESERT: MapLabel = { kind: 'BIOME', key: 'desert', name: 'Desert', color: 0xffff00 }
const WARM: MapLabel = { kind: 'REGION', key: 'warm', name: 'Warm', color: 0xff0000 }
const FLAT: TileStyle = { byHeight: false, shading: false, regionBorders: false, highlight: null, seaLevel: 63 }

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
    heights: new Float32Array(cells).fill(70)
  }
}

/** A tile as the bridge sends it: u32 header length, JSON header, aligned grids. */
function encode(size: number, withHeights: boolean): ArrayBuffer {
  const header = new TextEncoder().encode(JSON.stringify({ size, blocksPerPixel: 2, subjectKind: 'BIOME', labels: [PLAINS], heights: withHeights }))
  const cells = (size + 2) ** 2
  const align = (bytes: number): number => Math.ceil(bytes / 4) * 4
  const subjectsAt = align(4 + header.length)
  const regionsAt = align(subjectsAt + cells * 2)
  const heightsAt = align(regionsAt + cells * 2)
  const buffer = new ArrayBuffer(heightsAt + (withHeights ? cells * 4 : 0))
  new DataView(buffer).setUint32(0, header.length, true)
  new Uint8Array(buffer, 4).set(header)
  new Int16Array(buffer, subjectsAt, cells).fill(0)
  new Int16Array(buffer, regionsAt, cells).fill(-1)
  if (withHeights) {
    new Float32Array(buffer, heightsAt, cells).fill(81.5)
  }
  return buffer
}

describe('tile decoding', () => {
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
    const shares = countShares([{ tile, worldX: 0, worldZ: 0 }], { minimumX: 0, minimumZ: 0, maximumX: 4, maximumZ: 4 }, false, 63)

    expect(shares.map((share) => share.subject.key).sort()).toEqual(['desert', 'plains'])
    expect(shares[0].fraction).toBeCloseTo(0.5)
    expect(gridIndex(tile, -1, -1)).toBe(0)
  })

  it('counts only the part of a tile inside the area, largest share first', () => {
    const shares = countShares([{ tile: splitTile(4), worldX: 0, worldZ: 0 }], { minimumX: 0, minimumZ: 0, maximumX: 3, maximumZ: 4 }, false, 63)

    expect(shares.map((share) => share.subject.key)).toEqual(['plains', 'desert'])
    expect(shares[0].fraction).toBeCloseTo(2 / 3)
  })

  it('counts height bands in the height view', () => {
    const shares = countShares([{ tile: splitTile(4), worldX: 0, worldZ: 0 }], { minimumX: 0, minimumZ: 0, maximumX: 4, maximumZ: 4 }, true, 63)

    expect(shares).toHaveLength(1)
    expect(shares[0].subject).toMatchObject({ kind: 'HEIGHT', name: '0–8 above sea' })
  })
})
