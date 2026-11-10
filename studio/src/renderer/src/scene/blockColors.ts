import { blockTint } from './blockTints'
import { parseState, type BlockModels } from './blockModels'
import { texturePath, type ImageSource } from './textureAtlas'

const UNKNOWN_BLOCK_COLOR = 0x7f7f7f
const OPAQUE_ENOUGH_ALPHA = 128
const NO_TINT = 0xffffff

/** Each block seen from above: its top face's texture tinted as the client tints it, and that texture's average colour, read once. */
export class BlockColors {
  private readonly textures = new Map<string, Promise<ImageData | null>>()
  private readonly colors = new Map<string, Promise<number>>()

  constructor(
    private readonly models: BlockModels,
    private readonly images: ImageSource
  ) {}

  /** The first frame of the block's top face texture, tinted; null when the block has no model or texture. */
  topTexture(block: string): Promise<ImageData | null> {
    let texture = this.textures.get(block)
    if (texture === undefined) {
      texture = this.readTopTexture(block).catch(() => null)
      this.textures.set(block, texture)
    }
    return texture
  }

  colorOf(block: string): Promise<number> {
    let color = this.colors.get(block)
    if (color === undefined) {
      color = this.topTexture(block).then((texture) => (texture === null ? UNKNOWN_BLOCK_COLOR : averageColor(texture)))
      this.colors.set(block, color)
    }
    return color
  }

  /** Every block's colour, by name. */
  async colorsOf(blocks: Iterable<string>): Promise<Map<string, number>> {
    const names = [...new Set(blocks)]
    const colors = await Promise.all(names.map((name) => this.colorOf(name)))
    return new Map(names.map((name, index) => [name, colors[index]]))
  }

  private async readTopTexture(block: string): Promise<ImageData | null> {
    const model = await this.models.model(block)
    const top = model?.quads.filter((quad) => quad.facing === 'up').at(-1)
    const texture = top?.texture ?? model?.particle
    if (texture === undefined || texture === null) {
      return null
    }
    const bitmap = await this.images.image(texturePath(texture))
    if (bitmap === null) {
      return null
    }
    const state = parseState(block)
    return tinted(firstFrame(bitmap), blockTint(state.name, state.properties, top?.tintIndex ?? -1))
  }
}

// An animated texture stacks its frames downwards.
function firstFrame(bitmap: ImageBitmap): ImageData {
  const side = bitmap.width
  const canvas = new OffscreenCanvas(side, side)
  const drawing = canvas.getContext('2d', { willReadFrequently: true })!
  drawing.drawImage(bitmap, 0, 0, side, side, 0, 0, side, side)
  return drawing.getImageData(0, 0, side, side)
}

function tinted(texture: ImageData, tint: number): ImageData {
  if (tint === NO_TINT) {
    return texture
  }
  const pixels = texture.data
  const [red, green, blue] = [(tint >> 16) & 0xff, (tint >> 8) & 0xff, tint & 0xff]
  for (let offset = 0; offset < pixels.length; offset += 4) {
    pixels[offset] = Math.round((pixels[offset] * red) / 255)
    pixels[offset + 1] = Math.round((pixels[offset + 1] * green) / 255)
    pixels[offset + 2] = Math.round((pixels[offset + 2] * blue) / 255)
  }
  return texture
}

/** The mean colour of the pixels that are opaque enough, as 0xRRGGBB. */
export function averageColor(texture: ImageData): number {
  const pixels = texture.data
  let [red, green, blue, counted] = [0, 0, 0, 0]
  for (let offset = 0; offset < pixels.length; offset += 4) {
    if (pixels[offset + 3] >= OPAQUE_ENOUGH_ALPHA) {
      red += pixels[offset]
      green += pixels[offset + 1]
      blue += pixels[offset + 2]
      counted++
    }
  }
  if (counted === 0) {
    return UNKNOWN_BLOCK_COLOR
  }
  return (Math.round(red / counted) << 16) | (Math.round(green / counted) << 8) | Math.round(blue / counted)
}
