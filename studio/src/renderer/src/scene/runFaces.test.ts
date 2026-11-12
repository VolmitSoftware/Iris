import { Vector3 } from 'three'
import { describe, expect, it } from 'vitest'
import { ColumnField } from './columnField'
import { runFaces, uncovered, type SurfaceRuns } from './runFaces'
import type { TerrainSurface } from './terrainMesh'

const GROUND = 10
const BASE = 0
const CANOPY_BOTTOM = 14
const CANOPY_TOP = 15

// Two columns side by side, a block each: plain ground on the west, ground with a canopy floating over it on the east.
const surface: TerrainSurface = {
  columns: 2,
  rows: 1,
  originX: 0,
  originZ: 0,
  blocksPerColumn: 1,
  groundAt: () => GROUND,
  waterAt: () => null,
  colorAt: () => 0x808080
}

const runs: SurfaceRuns = {
  runs: {
    starts: Int32Array.of(0, 1, 3),
    bottoms: Int16Array.of(0, CANOPY_BOTTOM, 0),
    tops: Int16Array.of(GROUND, CANOPY_TOP, GROUND),
    blocks: Int16Array.of(0, 1, 0),
    covers: Int16Array.of(-1, -1, -1),
    coverShares: new Uint8Array(3)
  },
  cellAt: (column) => column,
  looks: [null, null],
  colors: [0x808080, 0x00ff00]
}

function faces(): { middle: Vector3; normal: Vector3 }[] {
  const geometry = runFaces(new ColumnField(surface), runs, BASE).geometry()
  const positions = geometry.getAttribute('position')
  const found: { middle: Vector3; normal: Vector3 }[] = []
  for (let first = 0; first < positions.count; first += 4) {
    const corner = (index: number): Vector3 => new Vector3().fromBufferAttribute(positions, first + index)
    const [origin, up, along, far] = [corner(0), corner(1), corner(2), corner(3)]
    found.push({ middle: origin.clone().add(far).multiplyScalar(0.5), normal: up.clone().sub(origin).cross(along.clone().sub(origin)).normalize() })
  }
  return found
}

describe('run faces', () => {
  it('finds the parts of a span its neighbours leave open', () => {
    expect(uncovered({ bottom: 0, top: 10 }, [{ bottom: 3, top: 5 }])).toEqual([{ bottom: 0, top: 2 }, { bottom: 6, top: 10 }])
    expect(uncovered({ bottom: -Infinity, top: 10 }, [{ bottom: -Infinity, top: 12 }])).toEqual([])
  })

  it('draws the tops of neighbouring columns at one height in one block as one face', () => {
    const flat: SurfaceRuns = { ...runs, runs: { starts: Int32Array.of(0, 1, 2), bottoms: Int16Array.of(0, 0), tops: Int16Array.of(GROUND, GROUND), blocks: Int16Array.of(0, 0), covers: Int16Array.of(-1, -1), coverShares: new Uint8Array(2) } }
    const geometry = runFaces(new ColumnField(surface), flat, BASE).geometry()
    const positions = geometry.getAttribute('position')
    const tops = []
    for (let first = 0; first < positions.count; first += 4) {
      if (positions.getY(first) === GROUND + 1 && positions.getY(first + 3) === GROUND + 1) {
        tops.push(Math.abs(positions.getX(first + 3) - positions.getX(first)))
      }
    }

    expect(tops).toEqual([2])
  })

  it('floats a canopy over the ground with its underside showing', () => {
    const drawn = faces()

    expect(drawn.some((face) => face.normal.y === -1 && face.middle.y === CANOPY_BOTTOM && face.middle.x === 1.5)).toBe(true)
    expect(drawn.some((face) => face.normal.y === 1 && face.middle.y === CANOPY_TOP + 1 && face.middle.x === 1.5)).toBe(true)
  })

  it('shows the side of a run toward a neighbour where the neighbour leaves it open, and walls the field edge down to the base', () => {
    const drawn = faces()
    const canopyWest = drawn.filter((face) => face.normal.x === -1 && face.middle.x === 1)
    const groundBetween = drawn.filter((face) => face.normal.x !== 0 && face.middle.x === 1 && face.middle.y <= GROUND + 1)

    expect(canopyWest.map((face) => face.middle.y).sort()).toEqual([CANOPY_BOTTOM + 0.5, CANOPY_TOP + 0.5])
    expect(groundBetween).toEqual([])
    // Under the top block's side, the wall at the field's edge reaches from the base to the ground's top block.
    expect(drawn.some((face) => face.normal.x === -1 && face.middle.x === 0 && face.middle.y === (BASE + GROUND) / 2)).toBe(true)
  })
})
