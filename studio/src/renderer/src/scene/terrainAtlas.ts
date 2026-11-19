import { CanvasTexture, NearestFilter, NearestMipmapLinearFilter, type Texture } from 'three'
import { averageColor } from './blockColors'
import { texturePath, type ImageSource } from './textureAtlas'

/** Where one picture lies in the atlas, as a rectangle of it from 0 to 1, and the picture's average colour as 0xRRGGBB. */
export interface UvCell {
  u: number
  v: number
  width: number
  height: number
  average: number
  /** Whether the picture is open in places, as leaves are between their twigs, so what lies behind shows through. */
  hasGaps: boolean
}

/** The atlas as the terrain draws it. */
export interface TerrainPictures {
  readonly texture: Texture
  /** Laid out as {@link texture}: white where a picture is drawn and black where it is open, for faces that are cut out. */
  readonly cutouts: Texture
  /** A cell of plain white, for faces that show only their colour. */
  readonly plain: UvCell
}

/** Where block pictures are placed. */
export interface PictureSource {
  /** The cell of the texture such as {@code minecraft:block/stone}, or null when it is missing or the atlas is full. */
  cellOf(textureId: string): Promise<UvCell | null>
}

const CELL_PIXELS = 16
const CELLS_PER_SIDE = 64
const ATLAS_PIXELS = CELLS_PER_SIDE * CELL_PIXELS
const PLAIN_CELL = 0
const WHITE = '#ffffff'
const WHITE_COLOR = 0xffffff
const OPAQUE_ALPHA = 255
// The same line blockColors draws between a pixel that counts toward a picture's colour and one that does not.
const OPAQUE_ENOUGH_ALPHA = 128
const GAP_SHADE = 0.6

/** The cell with this number, counted row by row from the top left. */
export function cellAt(index: number, average: number, hasGaps = false): UvCell {
  return {
    u: (index % CELLS_PER_SIDE) / CELLS_PER_SIDE,
    v: Math.floor(index / CELLS_PER_SIDE) / CELLS_PER_SIDE,
    width: 1 / CELLS_PER_SIDE,
    height: 1 / CELLS_PER_SIDE,
    average,
    hasGaps
  }
}

/** The most pictures one atlas holds, the plain cell among them. */
export const MOST_CELLS = CELLS_PER_SIDE * CELLS_PER_SIDE

/**
 * Block textures of the terrain in one image, each scaled to 16 pixels and placed the first time it is asked for, so
 * the atlas grows as tiles arrive; an animated texture shows its first frame.
 */
export class TerrainAtlas implements TerrainPictures, PictureSource {
  readonly texture: CanvasTexture
  readonly cutouts: CanvasTexture
  readonly plain = cellAt(PLAIN_CELL, WHITE_COLOR)

  private readonly drawing: CanvasRenderingContext2D
  private readonly cutoutDrawing: CanvasRenderingContext2D
  /** The texture halved again and again, down to a pixel a picture. */
  private readonly smallerDrawings: CanvasRenderingContext2D[]
  private readonly cells = new Map<string, Promise<UvCell | null>>()
  private used = PLAIN_CELL + 1

  constructor(private readonly images: ImageSource) {
    ;[this.drawing, this.texture] = sheet()
    ;[this.cutoutDrawing, this.cutouts] = sheet()
    this.smallerDrawings = withSmallerSheets(this.texture)
    for (const [level, drawing] of [this.drawing, ...this.smallerDrawings].entries()) {
      drawing.fillStyle = WHITE
      drawing.fillRect(0, 0, CELL_PIXELS >> level, CELL_PIXELS >> level)
    }
    this.cutoutDrawing.fillStyle = WHITE
    this.cutoutDrawing.fillRect(0, 0, CELL_PIXELS, CELL_PIXELS)
  }

  cellOf(textureId: string): Promise<UvCell | null> {
    let cell = this.cells.get(textureId)
    if (cell === undefined) {
      cell = this.place(textureId)
      this.cells.set(textureId, cell)
    }
    return cell
  }

  dispose(): void {
    this.texture.dispose()
    this.cutouts.dispose()
  }

  private async place(textureId: string): Promise<UvCell | null> {
    const bitmap = await this.images.image(texturePath(textureId)).catch(() => null)
    if (bitmap === null || this.used >= MOST_CELLS) {
      return null
    }
    const index = this.used++
    const placed = cellAt(index, WHITE_COLOR)
    const left = placed.u * ATLAS_PIXELS
    const top = placed.v * ATLAS_PIXELS
    this.drawing.drawImage(bitmap, 0, 0, bitmap.width, Math.min(bitmap.width, bitmap.height), left, top, CELL_PIXELS, CELL_PIXELS)
    const picture = this.drawing.getImageData(left, top, CELL_PIXELS, CELL_PIXELS)
    const average = averageColor(picture)
    const hasGaps = isOpenInPlaces(picture)
    this.cutoutDrawing.putImageData(cutoutOf(picture), left, top)
    let smaller = withGapsFilled(picture, average)
    this.drawing.putImageData(smaller, left, top)
    for (const [index, drawing] of this.smallerDrawings.entries()) {
      smaller = halved(smaller)
      drawing.putImageData(smaller, left >> (index + 1), top >> (index + 1))
    }
    this.texture.needsUpdate = true
    this.cutouts.needsUpdate = true
    return { ...placed, average, hasGaps }
  }
}

function sheet(): [CanvasRenderingContext2D, CanvasTexture] {
  const canvas = document.createElement('canvas')
  canvas.width = ATLAS_PIXELS
  canvas.height = ATLAS_PIXELS
  const drawing = canvas.getContext('2d', { willReadFrequently: true })!
  drawing.imageSmoothingEnabled = false
  const texture = new CanvasTexture(canvas)
  texture.magFilter = NearestFilter
  texture.minFilter = NearestFilter
  texture.generateMipmaps = false
  texture.flipY = false
  return [drawing, texture]
}

// Far off, a texture is drawn smaller, down to a pixel a picture: its average, as the coarser ground's flat colour is,
// so the ground looks the same where it changes from textured blocks to coloured columns. Halving no further keeps every
// level's pictures apart, and the nearest pixel of a level never reaches into the picture beside it.
function withSmallerSheets(texture: CanvasTexture): CanvasRenderingContext2D[] {
  const drawings: CanvasRenderingContext2D[] = []
  for (let level = 1; CELL_PIXELS >> level >= 1; level++) {
    const canvas = document.createElement('canvas')
    canvas.width = ATLAS_PIXELS >> level
    canvas.height = ATLAS_PIXELS >> level
    drawings.push(canvas.getContext('2d', { willReadFrequently: true })!)
  }
  texture.mipmaps = [texture.image, ...drawings.map((drawing) => drawing.canvas)]
  texture.minFilter = NearestMipmapLinearFilter
  return drawings
}

/** The picture at half its size, each pixel the average of the four it covers. */
export function halved(picture: ImageData): ImageData {
  const width = Math.max(1, picture.width >> 1)
  const height = Math.max(1, picture.height >> 1)
  const half = new ImageData(width, height)
  for (let y = 0; y < height; y++) {
    for (let x = 0; x < width; x++) {
      for (let channel = 0; channel < 4; channel++) {
        let sum = 0
        for (const [dx, dy] of [[0, 0], [1, 0], [0, 1], [1, 1]]) {
          sum += picture.data[((y * 2 + dy) * picture.width + x * 2 + dx) * 4 + channel]
        }
        half.data[(y * width + x) * 4 + channel] = Math.round(sum / 4)
      }
    }
  }
  return half
}

function isOpenInPlaces(picture: ImageData): boolean {
  for (let offset = 3; offset < picture.data.length; offset += 4) {
    if (picture.data[offset] < OPAQUE_ENOUGH_ALPHA) {
      return true
    }
  }
  return false
}

// A canvas keeps no colour under a transparent pixel, so the picture cannot carry both its filled gaps and where they were.
function cutoutOf(picture: ImageData): ImageData {
  const cutout = new ImageData(picture.width, picture.height)
  for (let offset = 0; offset < picture.data.length; offset += 4) {
    const shade = picture.data[offset + 3] < OPAQUE_ENOUGH_ALPHA ? 0 : 255
    cutout.data.set([shade, shade, shade, OPAQUE_ALPHA], offset)
  }
  return cutout
}

// The terrain draws whole faces, so what a picture leaves open, as leaves do between their twigs, would show as black:
// it takes the picture's own colour instead, darker, as the shade seen through the gaps.
function withGapsFilled(picture: ImageData, average: number): ImageData {
  const pixels = picture.data
  const gap = [(average >> 16) & 0xff, (average >> 8) & 0xff, average & 0xff].map((channel) => Math.round(channel * GAP_SHADE))
  for (let offset = 0; offset < pixels.length; offset += 4) {
    if (pixels[offset + 3] < OPAQUE_ENOUGH_ALPHA) {
      pixels[offset] = gap[0]
      pixels[offset + 1] = gap[1]
      pixels[offset + 2] = gap[2]
    }
    pixels[offset + 3] = OPAQUE_ALPHA
  }
  return picture
}
