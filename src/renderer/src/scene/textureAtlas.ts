import { CanvasTexture, NearestFilter, SRGBColorSpace } from 'three'

/** Where block textures come from: the bridge's copy of the client's assets. */
export interface ImageSource {
  /** The PNG at {@code path} below the assets folder, or null when there is none. */
  image(path: string): Promise<ImageBitmap | null>
}

/** Where one texture lies in the atlas, as a rectangle of the atlas from 0 to 1. */
export interface AtlasCell {
  u: number
  v: number
  width: number
  height: number
  /** Whether every pixel is fully opaque, so a block made of it hides its neighbours' faces. */
  solid: boolean
}

const CELL_PIXELS = 16
const CELLS_PER_ROW = 32
const OPAQUE_ALPHA = 255
const MISSING_TEXTURE_COLORS = ['#f800f8', '#000000']

/**
 * The block textures of one scene in one image, each scaled to 16 pixels; an animated texture shows its first frame,
 * and a missing one Minecraft's magenta and black checks.
 */
export class TextureAtlas {
  readonly texture: CanvasTexture

  private constructor(
    private readonly cells: Map<string, AtlasCell>,
    canvas: HTMLCanvasElement
  ) {
    this.texture = new CanvasTexture(canvas)
    this.texture.magFilter = NearestFilter
    this.texture.minFilter = NearestFilter
    this.texture.generateMipmaps = false
    this.texture.flipY = false
    this.texture.colorSpace = SRGBColorSpace
  }

  static async of(textureIds: string[], images: ImageSource): Promise<TextureAtlas> {
    const unique = [...new Set(textureIds)]
    const bitmaps = await Promise.all(unique.map((id) => images.image(texturePath(id)).catch(() => null)))
    const rows = Math.max(1, Math.ceil(unique.length / CELLS_PER_ROW))
    const canvas = document.createElement('canvas')
    canvas.width = CELLS_PER_ROW * CELL_PIXELS
    canvas.height = rows * CELL_PIXELS
    const drawing = canvas.getContext('2d', { willReadFrequently: true })!
    drawing.imageSmoothingEnabled = false
    const cells = new Map<string, AtlasCell>()
    unique.forEach((id, index) => {
      const left = (index % CELLS_PER_ROW) * CELL_PIXELS
      const top = Math.floor(index / CELLS_PER_ROW) * CELL_PIXELS
      const bitmap = bitmaps[index]
      if (bitmap === null) {
        drawMissing(drawing, left, top)
      } else {
        drawing.drawImage(bitmap, 0, 0, bitmap.width, Math.min(bitmap.width, bitmap.height), left, top, CELL_PIXELS, CELL_PIXELS)
      }
      cells.set(id, {
        u: left / canvas.width,
        v: top / canvas.height,
        width: CELL_PIXELS / canvas.width,
        height: CELL_PIXELS / canvas.height,
        solid: isSolid(drawing.getImageData(left, top, CELL_PIXELS, CELL_PIXELS).data)
      })
    })
    return new TextureAtlas(cells, canvas)
  }

  cell(textureId: string): AtlasCell {
    return this.cells.get(textureId)!
  }

  dispose(): void {
    this.texture.dispose()
  }
}

/** {@code minecraft:block/stone} lies at {@code minecraft/textures/block/stone.png}. */
export function texturePath(textureId: string): string {
  const colon = textureId.indexOf(':')
  return `${textureId.slice(0, colon)}/textures/${textureId.slice(colon + 1)}.png`
}

function drawMissing(drawing: CanvasRenderingContext2D, left: number, top: number): void {
  const half = CELL_PIXELS / 2
  for (let quarter = 0; quarter < 4; quarter++) {
    const column = quarter % 2
    const row = Math.floor(quarter / 2)
    drawing.fillStyle = MISSING_TEXTURE_COLORS[(column + row) % 2]
    drawing.fillRect(left + column * half, top + row * half, half, half)
  }
}

function isSolid(pixels: Uint8ClampedArray): boolean {
  for (let alpha = 3; alpha < pixels.length; alpha += 4) {
    if (pixels[alpha] < OPAQUE_ALPHA) {
      return false
    }
  }
  return true
}
