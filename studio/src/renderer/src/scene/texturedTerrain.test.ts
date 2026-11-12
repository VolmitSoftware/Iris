import { Texture, Vector3, type BufferGeometry, type Mesh, type MeshBasicMaterial } from 'three'
import { describe, expect, it } from 'vitest'
import type { BlockLook, FaceLook } from './blockLooks'
import type { CoverLook } from './coverLooks'
import { cellAt, type TerrainPictures } from './terrainAtlas'
import { buildTerrainMesh, type RaisedBlock, type TerrainSurface } from './terrainMesh'

const ORIGIN_X = 100
const ORIGIN_Z = 200
const PEAK_COLUMN = 1
const PEAK_GROUND = 12
const LOW_GROUND = 10
const HIGH_GROUND = 70
const FLOOR = 0
const MOST_TEXTURED_SIDE_BLOCKS = 16
const GRASS_TINT = 0x91bd59
const WHITE = 0xffffff

const plain = cellAt(0, WHITE)
const pictures: TerrainPictures = { texture: new Texture(), cutouts: new Texture(), plain }
const cellWidth = plain.width

function face(index: number, tint = WHITE): FaceLook {
  return { cell: cellAt(index, 0x808080), tint, color: 0x606060 }
}

function gappyFace(index: number): FaceLook {
  return { cell: cellAt(index, 0x808080, true), tint: WHITE, color: 0x606060 }
}

const grass: BlockLook = { top: face(1, GRASS_TINT), side: face(2), below: face(3) }

// Three by three columns of one-block ground, the middle one two blocks above the rest.
function surfaceOf(lookAt: TerrainSurface['lookAt'], ground = LOW_GROUND, withPictures = true): TerrainSurface {
  return {
    columns: 3,
    rows: 3,
    originX: ORIGIN_X,
    originZ: ORIGIN_Z,
    blocksPerColumn: 1,
    groundAt: (column, row) => (column === PEAK_COLUMN && row === PEAK_COLUMN ? ground + (PEAK_GROUND - LOW_GROUND) : ground),
    waterAt: () => null,
    colorAt: () => 0x808080,
    ...(withPictures ? { pictures, lookAt } : {})
  }
}

function groundOf(terrain: TerrainSurface): Mesh {
  return buildTerrainMesh(terrain, FLOOR).object.children[0] as Mesh
}

interface Placed {
  quad: number
  middle: Vector3
  normal: Vector3
}

function placed(geometry: BufferGeometry): Placed[] {
  const positions = geometry.getAttribute('position')
  const found: Placed[] = []
  for (let quad = 0; quad < positions.count / 4; quad++) {
    const corner = (index: number): Vector3 => new Vector3().fromBufferAttribute(positions, quad * 4 + index)
    const [origin, up, along, far] = [corner(0), corner(1), corner(2), corner(3)]
    found.push({
      quad,
      middle: origin.clone().add(far).multiplyScalar(0.5),
      normal: up.clone().sub(origin).cross(along.clone().sub(origin)).normalize()
    })
  }
  return found
}

function uvOf(geometry: BufferGeometry, quad: number, corner: number): [number, number] {
  const uvs = geometry.getAttribute('uv')
  return [uvs.getX(quad * 4 + corner), uvs.getY(quad * 4 + corner)]
}

function peakSides(ground: Mesh): Placed[] {
  return placed(ground.geometry)
    .filter((face) => face.normal.z === -1 && face.middle.x === ORIGIN_X + PEAK_COLUMN + 0.5 && face.middle.z === ORIGIN_Z + PEAK_COLUMN)
    .sort((first, second) => second.middle.y - first.middle.y)
}

describe('textured terrain', () => {
  it('draws a top for every column, as tall as it is wide, with the texture of its block', () => {
    const ground = groundOf(surfaceOf(() => grass))
    const tops = placed(ground.geometry).filter((face) => face.normal.y === 1)

    expect(tops).toHaveLength(9)
    expect((ground.material as MeshBasicMaterial).map).toBe(pictures.texture)
    const top = tops.find((candidate) => candidate.middle.x === ORIGIN_X + 0.5 && candidate.middle.z === ORIGIN_Z + 0.5)!
    expect(uvOf(ground.geometry, top.quad, 0)).toEqual([grass.top.cell.u, grass.top.cell.v])
    expect(uvOf(ground.geometry, top.quad, 2)).toEqual([grass.top.cell.u + cellWidth, grass.top.cell.v])
    expect(uvOf(ground.geometry, top.quad, 1)).toEqual([grass.top.cell.u, grass.top.cell.v + cellWidth])
  })

  it('multiplies the picture with the tint of its face, and with nothing on an untinted one', () => {
    const ground = groundOf(surfaceOf(() => grass))
    const colors = ground.geometry.getAttribute('color')
    const top = placed(ground.geometry).find((face) => face.normal.y === 1)!
    const side = peakSides(ground)[0]

    expect(colors.getX(top.quad * 4)).toBeCloseTo(0x91 / 255)
    expect(colors.getX(side.quad * 4)).toBeCloseTo(0.8)
  })

  it('shows the side of the top block over the blocks of the side below it, upright seen from outside', () => {
    const ground = groundOf(surfaceOf(() => grass))
    const [topBlock, blockBelow] = peakSides(ground)

    expect(topBlock.middle.y).toBe(PEAK_GROUND + 1 - 0.5)
    expect(blockBelow.middle.y).toBe(PEAK_GROUND + 1 - 1.5)
    expect(uvOf(ground.geometry, topBlock.quad, 3)).toEqual([grass.side.cell.u, grass.side.cell.v])
    expect(uvOf(ground.geometry, topBlock.quad, 0)).toEqual([grass.side.cell.u + cellWidth, grass.side.cell.v + cellWidth])
    expect(uvOf(ground.geometry, blockBelow.quad, 3)).toEqual([grass.below.cell.u, grass.below.cell.v])
  })

  it('draws deep sides as one flat quad in the average colour below the textured blocks', () => {
    const ground = groundOf(surfaceOf(() => grass, HIGH_GROUND))
    const northEdge = placed(ground.geometry)
      .filter((face) => face.normal.z === -1 && face.middle.x === ORIGIN_X + 1.5 && face.middle.z === ORIGIN_Z)
      .sort((first, second) => second.middle.y - first.middle.y)
    const deep = northEdge[northEdge.length - 1]
    const texturedFloor = HIGH_GROUND + 1 - MOST_TEXTURED_SIDE_BLOCKS

    expect(northEdge).toHaveLength(MOST_TEXTURED_SIDE_BLOCKS + 1)
    expect(deep.middle.y).toBe((FLOOR + texturedFloor) / 2)
    expect(uvOf(ground.geometry, deep.quad, 0)).toEqual([plain.u + cellWidth / 2, plain.v + cellWidth / 2])
    expect(ground.geometry.getAttribute('color').getX(deep.quad * 4)).toBeCloseTo((0x60 / 255) * 0.8)
  })

  it('shows columns without a look in their colour on the plain cell, among textured ones', () => {
    const ground = groundOf(surfaceOf((column, row) => (column === 0 && row === 0 ? null : grass)))
    const tops = placed(ground.geometry).filter((face) => face.normal.y === 1)
    const bare = tops.find((face) => face.middle.x === ORIGIN_X + 0.5 && face.middle.z === ORIGIN_Z + 0.5)!

    expect(tops).toHaveLength(9)
    expect(uvOf(ground.geometry, bare.quad, 0)).toEqual([plain.u + cellWidth / 2, plain.v + cellWidth / 2])
    expect(ground.geometry.getAttribute('color').getX(bare.quad * 4)).toBeCloseTo(0x80 / 255)
  })

  it('keeps merging columns that show no texture, and draws no uvs for them', () => {
    const ground = groundOf(surfaceOf(() => null))
    const tops = placed(ground.geometry).filter((face) => face.normal.y === 1)

    expect(tops).toHaveLength(5)
    expect(ground.geometry.getAttribute('uv')).toBeUndefined()
    expect((ground.material as MeshBasicMaterial).map).toBeNull()
  })

  it('stands a cover on the top face of its column, cut out along its picture', () => {
    const blade: CoverLook = {
      faces: [
        {
          corners: [[0, 1, 0], [0, 0, 0], [1, 0, 1], [1, 1, 1]],
          uvs: [[0, 0], [0, 1], [1, 1], [1, 0]],
          cell: cellAt(4, WHITE),
          color: [0.5, 0.75, 0.25]
        }
      ]
    }
    const terrain = { ...surfaceOf(() => grass), coverAt: (column: number, row: number) => (column === PEAK_COLUMN && row === PEAK_COLUMN ? blade : null) }
    const [, cover, water] = buildTerrainMesh(terrain, FLOOR).object.children as Mesh[]
    const positions = cover.geometry.getAttribute('position')
    const material = cover.material as MeshBasicMaterial

    expect(water).toBeDefined()
    expect(positions.count).toBe(4)
    expect([positions.getX(1), positions.getY(1), positions.getZ(1)]).toEqual([ORIGIN_X + PEAK_COLUMN, PEAK_GROUND + 1, ORIGIN_Z + PEAK_COLUMN])
    expect([positions.getX(3), positions.getY(3), positions.getZ(3)]).toEqual([ORIGIN_X + PEAK_COLUMN + 1, PEAK_GROUND + 2, ORIGIN_Z + PEAK_COLUMN + 1])
    expect(uvOf(cover.geometry, 0, 2)).toEqual([blade.faces[0].cell.u + cellWidth, blade.faces[0].cell.v + cellWidth])
    expect(cover.geometry.getAttribute('color').getY(0)).toBeCloseTo(0.75)
    expect(material.alphaMap).toBe(pictures.cutouts)
    expect(material.alphaTest).toBeGreaterThan(0)
  })

  it('cuts leaves out along their pictures and hides no face behind them, as with fancy graphics', () => {
    const leaves: BlockLook = { top: gappyFace(5), side: gappyFace(5), below: gappyFace(5) }
    const log: BlockLook = { top: face(6), side: face(7), below: face(7) }
    // Two leaves stacked on the peak, a log beside the lower one.
    const raisedAt = (column: number, row: number): RaisedBlock[] => {
      if (column === PEAK_COLUMN && row === PEAK_COLUMN) {
        return [{ y: PEAK_GROUND + 2, color: 0, look: leaves }, { y: PEAK_GROUND + 1, color: 0, look: leaves }]
      }
      return column === PEAK_COLUMN + 1 && row === PEAK_COLUMN ? [{ y: PEAK_GROUND + 1, color: 0, look: log }] : []
    }
    const [ground, seeThrough, water] = buildTerrainMesh({ ...surfaceOf(() => grass), raisedAt }, FLOOR).object.children as Mesh[]
    const leafFaces = placed(seeThrough.geometry)
    const logBesideLeaf = placed(ground.geometry).filter((face) => face.normal.x === -1 && face.middle.x === ORIGIN_X + PEAK_COLUMN + 1 && face.middle.y === PEAK_GROUND + 1.5)

    expect(water).toBeDefined()
    expect((seeThrough.material as MeshBasicMaterial).alphaMap).toBe(pictures.cutouts)
    // The lower leaf shows all but its bottom on the ground and its side on the log; the upper one shows every face.
    expect(leafFaces).toHaveLength(4 + 6)
    expect(leafFaces.filter((face) => face.middle.y === PEAK_GROUND + 2)).toHaveLength(2)
    expect(logBesideLeaf).toHaveLength(1)
  })

  it('draws no cover mesh where nothing stands on the ground', () => {
    expect(buildTerrainMesh(surfaceOf(() => grass), FLOOR).object.children).toHaveLength(2)
  })

  it('draws a surface without an atlas in its colour alone', () => {
    const ground = groundOf(surfaceOf(() => grass, LOW_GROUND, false))

    expect(ground.geometry.getAttribute('uv')).toBeUndefined()
  })
})
