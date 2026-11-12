import { parseState, type BlockModel, type Direction, type Quad } from './blockModels'
import { blockTint } from './blockTints'
import type { PictureSource, UvCell } from './terrainAtlas'

/** The light Minecraft gives each side of a block without shadows: the top brightest, the bottom darkest. */
export const FACE_LIGHT: Record<Direction, number> = { up: 1, down: 0.5, north: 0.8, south: 0.8, west: 0.6, east: 0.6 }

/** One face of a block that stands on the ground, in block units from the block's lowest corner. */
export interface CoverFace {
  corners: Quad['corners']
  uvs: Quad['uvs']
  cell: UvCell
  /** The face's tint in its light, as red, green and blue from 0 to 1. */
  color: readonly [number, number, number]
}

/** A block standing on a column's ground, such as grass, a flower or a layer of snow, as the faces of its model. */
export interface CoverLook {
  faces: CoverFace[]
}

/**
 * The block's model with its pictures placed; null for a block the client draws without a model, such as a chest, or
 * when one of its pictures cannot be placed, so a cover is drawn whole or not at all.
 */
export async function coverLookOf(block: string, model: BlockModel | null, pictures: PictureSource): Promise<CoverLook | null> {
  if (model === null || model.quads.length === 0) {
    return null
  }
  const state = parseState(block)
  const faces = await Promise.all(
    model.quads.map(async (quad): Promise<CoverFace | null> => {
      const cell = await pictures.cellOf(quad.texture)
      if (cell === null) {
        return null
      }
      const tint = blockTint(state.name, state.properties, quad.tintIndex)
      const light = quad.shade ? FACE_LIGHT[quad.facing] : 1
      const color = [(((tint >> 16) & 0xff) / 255) * light, (((tint >> 8) & 0xff) / 255) * light, ((tint & 0xff) / 255) * light] as const
      return { corners: quad.corners, uvs: quad.uvs, cell, color }
    })
  )
  return faces.includes(null) ? null : { faces: faces as CoverFace[] }
}
