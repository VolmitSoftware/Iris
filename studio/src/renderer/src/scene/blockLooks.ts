import { parseState, type BlockModel, type Direction, type ParsedState } from './blockModels'
import { blockTint } from './blockTints'
import { coverLookOf, type CoverLook } from './coverLooks'
import type { PictureSource, UvCell } from './terrainAtlas'

const NO_TINT_INDEX = -1
const DIRT = 'minecraft:dirt'
const WHITE = 0xffffff

/** One face of a block as the terrain draws it: its picture, the colour that tints the picture, and the picture's tinted average. */
export interface FaceLook {
  cell: UvCell
  tint: number
  color: number
}

/** A block seen on a column: from above, on the side of its top block, and on the sides of the blocks under it. */
export interface BlockLook {
  top: FaceLook
  side: FaceLook
  below: FaceLook
}

/** The texture a face takes from a block's model, and which of the block's tints colours it. */
export interface FaceTexture {
  texture: string
  tintIndex: number
}

export interface BlockFaceTextures {
  top: FaceTexture
  side: FaceTexture
  below: FaceTexture
}

/** Where block models come from. */
export interface ModelSource {
  model(stateKey: string): Promise<BlockModel | null>
}

/**
 * The textures a column of the block shows. A side can be drawn in layers, like grass over dirt, and the untinted layer is
 * the one that reads on its own. A column is one block drawn as tall as its side shows, so a grassy edge would repeat all
 * the way down a cliff: blocks capping dirt show dirt under their top block instead. Null where the model has no texture.
 */
export function faceTexturesOf(model: BlockModel, dirtBottom: string | null): BlockFaceTextures | null {
  const stood = model.particle === null ? null : { texture: model.particle, tintIndex: NO_TINT_INDEX }
  const top = lastFacing(model, 'up') ?? stood
  if (top === null) {
    return null
  }
  const side = untintedFacing(model, 'north') ?? top
  const bottom = lastFacing(model, 'down')
  const below = bottom !== null && bottom.texture === dirtBottom ? bottom : side
  return { top, side, below }
}

function lastFacing(model: BlockModel, facing: Direction): FaceTexture | null {
  const quad = model.quads.filter((candidate) => candidate.facing === facing).at(-1)
  return quad === undefined ? null : { texture: quad.texture, tintIndex: quad.tintIndex }
}

function untintedFacing(model: BlockModel, facing: Direction): FaceTexture | null {
  const quads = model.quads.filter((candidate) => candidate.facing === facing)
  const quad = quads.find((candidate) => candidate.tintIndex === NO_TINT_INDEX) ?? quads[0]
  return quad === undefined ? null : { texture: quad.texture, tintIndex: quad.tintIndex }
}

/** The look of each block state the terrain draws, read once. */
export class BlockLooks {
  private readonly looks = new Map<string, Promise<BlockLook | null>>()
  private readonly covers = new Map<string, Promise<CoverLook | null>>()
  private dirtBottom: Promise<string | null> | null = null

  constructor(
    private readonly models: ModelSource,
    private readonly pictures: PictureSource
  ) {}

  /** The block's look; null when it has no model or texture, or one of its pictures cannot be placed. */
  lookOf(block: string): Promise<BlockLook | null> {
    let look = this.looks.get(block)
    if (look === undefined) {
      look = this.read(block).catch(() => null)
      this.looks.set(block, look)
    }
    return look
  }

  /** What each block looks like standing on the ground, by name; null for one that is not drawn there. */
  async coversOf(blocks: Iterable<string>): Promise<Map<string, CoverLook | null>> {
    const names = [...new Set(blocks)]
    const looks = await Promise.all(names.map((name) => this.coverOf(name)))
    return new Map(names.map((name, index) => [name, looks[index]]))
  }

  private coverOf(block: string): Promise<CoverLook | null> {
    let cover = this.covers.get(block)
    if (cover === undefined) {
      cover = this.models
        .model(block)
        .then((model) => coverLookOf(block, model, this.pictures))
        .catch(() => null)
      this.covers.set(block, cover)
    }
    return cover
  }

  /** Every block's look, by name. */
  async looksOf(blocks: Iterable<string>): Promise<Map<string, BlockLook | null>> {
    const names = [...new Set(blocks)]
    const looks = await Promise.all(names.map((name) => this.lookOf(name)))
    return new Map(names.map((name, index) => [name, looks[index]]))
  }

  private async read(block: string): Promise<BlockLook | null> {
    const model = await this.models.model(block)
    if (model === null) {
      return null
    }
    const textures = faceTexturesOf(model, await this.dirtBottomTexture())
    if (textures === null) {
      return null
    }
    const state = parseState(block)
    const [top, side, below] = await Promise.all([textures.top, textures.side, textures.below].map((face) => this.faceLook(face, state)))
    return top === null || side === null || below === null ? null : { top, side, below }
  }

  private async faceLook(face: FaceTexture, state: ParsedState): Promise<FaceLook | null> {
    const cell = await this.pictures.cellOf(face.texture)
    if (cell === null) {
      return null
    }
    const tint = blockTint(state.name, state.properties, face.tintIndex)
    return { cell, tint, color: multiplied(cell.average, tint) }
  }

  private dirtBottomTexture(): Promise<string | null> {
    this.dirtBottom ??= this.models.model(DIRT).then((model) => (model === null ? null : lastFacing(model, 'down')?.texture ?? null))
    return this.dirtBottom
  }
}

function multiplied(color: number, tint: number): number {
  if (tint === WHITE) {
    return color
  }
  const channel = (shift: number): number => Math.round((((color >> shift) & 0xff) * ((tint >> shift) & 0xff)) / 255)
  return (channel(16) << 16) | (channel(8) << 8) | channel(0)
}
