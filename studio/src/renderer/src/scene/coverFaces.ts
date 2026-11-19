import { BufferAttribute, BufferGeometry } from 'three'
import type { ColumnField } from './columnField'

const CORNERS_PER_FACE = 4
const INDICES_PER_FACE = 6

/**
 * What stands on the ground, such as grass, flowers and layers of snow: each column's cover as its block's own model,
 * set on the column's top face. Null where no column has a cover.
 */
export function coverGeometry(field: ColumnField): BufferGeometry | null {
  const faces = field.covers.reduce((count, cover) => count + (cover?.faces.length ?? 0), 0)
  if (faces === 0) {
    return null
  }
  const positions = new Float32Array(faces * CORNERS_PER_FACE * 3)
  const uvs = new Float32Array(faces * CORNERS_PER_FACE * 2)
  const colors = new Float32Array(faces * CORNERS_PER_FACE * 3)
  const indices = new Uint32Array(faces * INDICES_PER_FACE)
  const step = field.blocksPerColumn
  let face = 0
  for (let row = 0; row < field.rows; row++) {
    for (let column = 0; column < field.columns; column++) {
      const index = field.indexOf(column, row)
      const [x, y, z] = [field.originX + column * step, field.groundTop[index], field.originZ + row * step]
      for (const { corners, uvs: faceUvs, cell, color } of field.covers[index]?.faces ?? []) {
        const first = face * CORNERS_PER_FACE
        for (let corner = 0; corner < CORNERS_PER_FACE; corner++) {
          const [cornerX, cornerY, cornerZ] = corners[corner]
          positions.set([x + cornerX * step, y + cornerY, z + cornerZ * step], (first + corner) * 3)
          uvs.set([cell.u + faceUvs[corner][0] * cell.width, cell.v + faceUvs[corner][1] * cell.height], (first + corner) * 2)
          colors.set(color, (first + corner) * 3)
        }
        indices.set([first, first + 1, first + 2, first, first + 2, first + 3], face * INDICES_PER_FACE)
        face++
      }
    }
  }
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(positions, 3))
  geometry.setAttribute('uv', new BufferAttribute(uvs, 2))
  geometry.setAttribute('color', new BufferAttribute(colors, 3))
  geometry.setIndex(new BufferAttribute(indices, 1))
  return geometry
}
