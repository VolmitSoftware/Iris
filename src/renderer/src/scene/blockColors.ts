import { blockTint } from './blockTints'
import { parseState, type BlockModels } from './blockModels'
import { texturePath, type ImageSource } from './textureAtlas'

const UNKNOWN_BLOCK_COLOR = 0x7f7f7f
const OPAQUE_ENOUGH_ALPHA = 128

/** Each block's colour seen from above: its top face's texture averaged and tinted as the client tints it, read once. */
export class BlockColors {
  private readonly known = new Map<string, Promise<number>>()

  constructor(
    private readonly models: BlockModels,
    private readonly images: ImageSource
  ) {}

  colorOf(block: string): Promise<number> {
    let color = this.known.get(block)
    if (color === undefined) {
      color = this.read(block).catch(() => UNKNOWN_BLOCK_COLOR)
      this.known.set(block, color)
    }
    return color
  }

  /** Every block's colour, by name. */
  async colorsOf(blocks: Iterable<string>): Promise<Map<string, number>> {
    const names = [...new Set(blocks)]
    const colors = await Promise.all(names.map((name) => this.colorOf(name)))
    return new Map(names.map((name, index) => [name, colors[index]]))
  }

  private async read(block: string): Promise<number> {
    const model = await this.models.model(block)
    const top = model?.quads.filter((quad) => quad.facing === 'up').at(-1)
    const texture = top?.texture ?? model?.particle
    if (texture === undefined || texture === null) {
      return UNKNOWN_BLOCK_COLOR
    }
    const bitmap = await this.images.image(texturePath(texture))
    if (bitmap === null) {
      return UNKNOWN_BLOCK_COLOR
    }
    const state = parseState(block)
    return multiplied(averageColor(bitmap), blockTint(state.name, state.properties, top?.tintIndex ?? -1))
  }
}

// The first frame only: an animated texture stacks its frames downwards.
function averageColor(bitmap: ImageBitmap): number {
  const side = bitmap.width
  const canvas = new OffscreenCanvas(side, side)
  const drawing = canvas.getContext('2d', { willReadFrequently: true })!
  drawing.drawImage(bitmap, 0, 0, side, side, 0, 0, side, side)
  const pixels = drawing.getImageData(0, 0, side, side).data
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

function multiplied(color: number, tint: number): number {
  const channel = (shift: number): number => Math.round((((color >> shift) & 0xff) * ((tint >> shift) & 0xff)) / 255)
  return (channel(16) << 16) | (channel(8) << 8) | channel(0)
}
