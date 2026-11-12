import type { FaceLook } from './blockLooks'
import type { ColumnField } from './columnField'
import { shaded } from './columnFaces'
import { FACE_LIGHT } from './coverLooks'
import type { Picture, QuadBatch, Vec3 } from './quadBatch'
import type { RaisedBlock } from './terrainMesh'
import { sunk, tintAt } from './waterTint'

/** One face of a whole block: the step to the block it touches, and its quad on a block of {@code step} wide. */
interface BlockFace {
  columnStep: number
  yStep: number
  rowStep: number
  light: number
  /** Which of the block's pictures the face shows, and whether that picture stands upright. */
  picture: (look: NonNullable<RaisedBlock['look']>) => FaceLook
  standing: boolean
  /** The quad's corner, along and up from the block's lowest north west corner; it faces the way up crosses along. */
  quad: (step: number) => [Vec3, Vec3, Vec3]
}

// A block's bottom shows the picture of its top, as the end of a log does.
const BLOCK_FACES: readonly BlockFace[] = [
  { columnStep: 0, yStep: 1, rowStep: 0, light: FACE_LIGHT.up, picture: (look) => look.top, standing: false, quad: (step) => [[0, 1, 0], [step, 0, 0], [0, 0, step]] },
  { columnStep: 0, yStep: -1, rowStep: 0, light: FACE_LIGHT.down, picture: (look) => look.top, standing: false, quad: (step) => [[0, 0, 0], [0, 0, step], [step, 0, 0]] },
  { columnStep: 0, yStep: 0, rowStep: -1, light: FACE_LIGHT.north, picture: (look) => look.side, standing: true, quad: (step) => [[0, 0, 0], [step, 0, 0], [0, 1, 0]] },
  { columnStep: 0, yStep: 0, rowStep: 1, light: FACE_LIGHT.south, picture: (look) => look.side, standing: true, quad: (step) => [[step, 0, step], [-step, 0, 0], [0, 1, 0]] },
  { columnStep: -1, yStep: 0, rowStep: 0, light: FACE_LIGHT.west, picture: (look) => look.side, standing: true, quad: (step) => [[0, 0, step], [0, 0, -step], [0, 1, 0]] },
  { columnStep: 1, yStep: 0, rowStep: 0, light: FACE_LIGHT.east, picture: (look) => look.side, standing: true, quad: (step) => [[step, 0, 0], [0, 0, step], [0, 1, 0]] }
]

/**
 * The blocks raised above the ground, such as trees, as whole blocks: each face that touches neither the ground nor
 * another raised block. A face at the field's edge is drawn, as the field next to it is not known here. Blocks under
 * water sink toward its deep colour as the ground does.
 */
export function addRaisedFaces(field: ColumnField, batch: QuadBatch): void {
  const step = field.blocksPerColumn
  for (let row = 0; row < field.rows; row++) {
    for (let column = 0; column < field.columns; column++) {
      const index = field.indexOf(column, row)
      const water = field.waterTopAt(column, row)
      for (const block of field.raised[index]) {
        const corner: Vec3 = [field.originX + column * step, block.y, field.originZ + row * step]
        for (const face of BLOCK_FACES) {
          if (field.isSolidAt(column + face.columnStep, row + face.rowStep, block.y + face.yStep)) {
            continue
          }
          addFace(batch, face, corner, step, block, water)
        }
      }
    }
  }
}

function addFace(batch: QuadBatch, face: BlockFace, corner: Vec3, step: number, block: RaisedBlock, water: number | null): void {
  const [start, along, up] = face.quad(step)
  const origin: Vec3 = [corner[0] + start[0], corner[1] + start[1], corner[2] + start[2]]
  const look = block.look === null ? null : face.picture(block.look)
  const light = shaded(look === null ? block.color : look.tint, face.light)
  const color = water === null ? light : sunk(light, tintAt(water - (block.y + start[1])))
  const picture: Picture | null = look === null ? null : { cell: look.cell, standing: face.standing }
  batch.add(origin, along, up, color, color, picture)
}
