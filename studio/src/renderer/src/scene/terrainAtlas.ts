import { CanvasTexture, NearestFilter, type Texture } from 'three'
import { averageColor } from './blockColors'
import { texturePath, type ImageSource } from './textureAtlas'

/** Where one picture lies in the atlas, as a rectangle of it from 0 to 1, and the picture's average colour as 0xRRGGBB. */
export interface UvCell {
  u: number
  v: number
  width: number
  height: number
  average: number
}

/** The atlas as the terrain draws it. */
export interface TerrainPictures {
  readonly texture: Texture
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
export function cellAt(index: number, average: number): UvCell {
  return {
    u: (index % CELLS_PER_SIDE) / CELLS_PER_SIDE,
    v: Math.floor(index / CELLS_PER_SIDE) / CELLS_PER_SIDE,
    width: 1 / CELLS_PER_SIDE,
    height: 1 / CELLS_PER_SIDE,
    average
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
  readonly plain = cellAt(PLAIN_CELL, WHITE_COLOR)

  private readonly drawing: CanvasRenderingContext2D
  private readonly cells = new Map<string, Promise<UvCell | null>>()
  private used = PLAIN_CELL + 1

  constructor(private readonly images: ImageSource) {
    const canvas = document.createElement('canvas')
    canvas.width = ATLAS_PIXELS
    canvas.height = ATLAS_PIXELS
    this.drawing = canvas.getContext('2d', { willReadFrequently: true })!
    this.drawing.imageSmoothingEnabled = false
    this.drawing.fillStyle = WHITE
    this.drawing.fillRect(0, 0, CELL_PIXELS, CELL_PIXELS)
    this.texture = new CanvasTexture(canvas)
    this.texture.magFilter = NearestFilter
    this.texture.minFilter = NearestFilter
    this.texture.generateMipmaps = false
    this.texture.flipY = false
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
    this.drawing.putImageData(withGapsFilled(picture, average), left, top)
    this.texture.needsUpdate = true
    return { ...placed, average }
  }
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
