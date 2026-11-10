import { PerspectiveCamera, Vector3 } from 'three'
import { describe, expect, it } from 'vitest'
import { ColumnField } from './columnField'
import { groundInView } from './groundInView'

const GROUND = 70
const CEILING = 128
const FIELD_BLOCKS = 4000
const BLOCKS_PER_COLUMN = 8
const VIEW_WIDTH = 1280
const VIEW_HEIGHT = 800

// A square of flat ground from the origin, with a ridge ten blocks higher along its eastern edge.
const RIDGE_FROM_X = 3000
const ground = new ColumnField({
  columns: FIELD_BLOCKS / BLOCKS_PER_COLUMN,
  rows: FIELD_BLOCKS / BLOCKS_PER_COLUMN,
  originX: 0,
  originZ: 0,
  blocksPerColumn: BLOCKS_PER_COLUMN,
  groundAt: (column) => (column * BLOCKS_PER_COLUMN >= RIDGE_FROM_X ? GROUND + 10 : GROUND),
  waterAt: () => null,
  colorAt: () => 0
})

function lookDownFrom(position: Vector3, lookedAt: Vector3): { camera: PerspectiveCamera; width: number; height: number; orbitDistance: number } {
  const camera = new PerspectiveCamera(45, VIEW_WIDTH / VIEW_HEIGHT, 1, 20_000)
  camera.position.copy(position)
  camera.lookAt(lookedAt)
  camera.updateMatrixWorld()
  return { camera, width: VIEW_WIDTH, height: VIEW_HEIGHT, orbitDistance: position.distanceTo(lookedAt) }
}

const drawn = {
  surfaceTopOver: (x: number, z: number) => ground.surfaceTopOver(x, z),
  groundTopOver: (x: number, z: number) => ground.groundTopOver(x, z)
}

describe('ground in view', () => {
  it('boxes the ground the camera looks down at and reads its height', () => {
    const seen = groundInView(lookDownFrom(new Vector3(1000, 400, 1400), new Vector3(1000, GROUND, 1000)), CEILING, drawn)!

    expect(seen.heights.length).toBeGreaterThan(100)
    expect(new Set(seen.heights)).toEqual(new Set([GROUND]))
    expect(seen.area.minX).toBeLessThan(1000)
    expect(seen.area.maxX).toBeGreaterThan(1000)
    expect(seen.area.minZ).toBeLessThan(1000)
    expect(seen.area.maxZ).toBeGreaterThan(1000)
    expect(seen.area.maxX).toBeLessThan(FIELD_BLOCKS)
  })

  it('reads the height of every kind of ground in view', () => {
    const seen = groundInView(lookDownFrom(new Vector3(3000, 400, 1400), new Vector3(3000, GROUND, 1000)), CEILING, drawn)!

    expect(new Set(seen.heights)).toEqual(new Set([GROUND, GROUND + 10]))
  })

  it('sees no ground over where nothing is drawn, or when looking at the sky', () => {
    expect(groundInView(lookDownFrom(new Vector3(9000, 400, 1400), new Vector3(9000, GROUND, 1000)), CEILING, drawn)).toBeNull()
    expect(groundInView(lookDownFrom(new Vector3(1000, 400, 1400), new Vector3(1000, 900, 1000)), CEILING, drawn)).toBeNull()
  })
})
