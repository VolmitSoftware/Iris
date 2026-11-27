import { BufferAttribute, BufferGeometry, Vector3 } from 'three'
import { describe, expect, it } from 'vitest'
import { compacted, geometryOfCompact } from './compactGeometry'

function geometryOf(positions: number[], colors: number[], uvs: number[]): BufferGeometry {
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(Float32Array.from(positions), 3))
  geometry.setAttribute('color', new BufferAttribute(Float32Array.from(colors), 3))
  geometry.setAttribute('uv', new BufferAttribute(Float32Array.from(uvs), 2))
  geometry.setIndex(new BufferAttribute(Uint32Array.of(0, 1, 2), 1))
  return geometry
}

// Where a corner of the packed geometry lands once placed.
function placedCorner(arrays: ReturnType<typeof compacted>, corner: number): Vector3 {
  const position = geometryOfCompact(arrays).getAttribute('position')
  const { origin, scale } = arrays.placement
  return new Vector3(origin[0] + position.getX(corner) * scale[0], origin[1] + position.getY(corner) * scale[1], origin[2] + position.getZ(corner) * scale[2])
}

describe('compact geometry', () => {
  it('keeps the corners of block models where they were, to a sixteenth of a block', () => {
    const arrays = compacted(geometryOf([100, 64, 200, 100.3125, 65, 200, 101, 64.5, 201], [0, 0.5, 1, 1, 1, 1, 0, 0, 0], [0, 0, 0.5, 0.25, 1, 1]), 1)

    expect(placedCorner(arrays, 1).toArray()).toEqual([100.3125, 65, 200])
    expect(placedCorner(arrays, 2).toArray()).toEqual([101, 64.5, 201])
    expect(geometryOfCompact(arrays).getAttribute('color').getY(0)).toBeCloseTo(0.5, 2)
    expect(geometryOfCompact(arrays).getAttribute('uv').getX(1)).toBeCloseTo(0.5, 4)
  })

  it('fits ground reaching far down by taking longer steps up and down', () => {
    const arrays = compacted(geometryOf([0, -2048, 0, 1, 2048, 0, 0, 0, 1], new Array(9).fill(0), new Array(6).fill(0)), 1)

    expect(placedCorner(arrays, 1).y).toBeCloseTo(2048, 0)
    expect(placedCorner(arrays, 0).y).toBe(-2048)
  })
})
