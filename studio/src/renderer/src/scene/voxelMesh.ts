import { BufferAttribute, BufferGeometry } from 'three'
import { VOXEL_SIDES, type TileVoxels } from '../bridge/types'
import type { Direction } from './blockModels'
import type { BlockLook, FaceLook } from './blockLooks'
import type { ColumnField } from './columnField'
import { shaded } from './columnFaces'
import { FACE_LIGHT, type CoverFace, type CoverLook } from './coverLooks'
import { brightnessOfCorner, brightnessOfLight, lightOf, opaqueAround } from './skyLight'
import type { UvCell } from './terrainAtlas'
import { sunk, tintAt } from './waterTint'

type Corners = CoverFace['corners']
type Uvs = CoverFace['uvs']
type Color = readonly [number, number, number]

/** The blocks of a tile seen from the open air, and how to draw each of the blocks they name. */
export interface SurfaceVoxels {
  voxels: TileVoxels
  /** Each named block's model with its pictures placed, or null to draw it as a cube of its look or colour. */
  models: readonly (CoverLook | null)[]
  looks: readonly (BlockLook | null)[]
  colors: readonly number[]
  /** Whether each named block is water, drawn as the water's sheet. */
  isWater: readonly boolean[]
}

/** The faces of a tile's blocks: opaque ones, ones cut out along their pictures, and the water's. Null where there are none. */
export interface VoxelGeometries {
  opaque: BufferGeometry | null
  cutOut: BufferGeometry | null
  water: BufferGeometry | null
}

const SIDE_BITS: Record<Direction, number> = Object.fromEntries(VOXEL_SIDES.map((side, bit) => [side, 1 << bit])) as Record<Direction, number>
const WHOLE_PICTURE: Uvs = [[0, 0], [0, 1], [1, 1], [1, 0]]
// A whole block's face on each side, top left, bottom left, bottom right and top right as seen from outside.
const CUBE_FACES: Record<Direction, Corners> = {
  down: [[0, 0, 1], [0, 0, 0], [1, 0, 0], [1, 0, 1]],
  up: [[0, 1, 0], [0, 1, 1], [1, 1, 1], [1, 1, 0]],
  north: [[1, 1, 0], [1, 0, 0], [0, 0, 0], [0, 1, 0]],
  south: [[0, 1, 1], [0, 0, 1], [1, 0, 1], [1, 1, 1]],
  west: [[0, 1, 0], [0, 0, 0], [0, 0, 1], [0, 1, 1]],
  east: [[1, 1, 1], [1, 0, 1], [1, 0, 0], [1, 1, 0]]
}
// The axis a side's faces look along: x, y or z.
const NORMAL_AXIS: Record<Direction, number> = { down: 1, up: 1, north: 2, south: 2, west: 0, east: 0 }

/**
 * Each block seen from the open air drawn as Minecraft draws it: the faces of its model, leaving out a face its model
 * lets a neighbour hide unless that side faces the open air. A block without a model is a cube of its look, or of its
 * colour; water shows the sides that face the air. What lies under water sinks toward its deep colour as the ground does.
 */
export function voxelGeometries(field: ColumnField, surface: SurfaceVoxels, plain: UvCell): VoxelGeometries {
  const opaque = new FaceSoup(true)
  const cutOut = new FaceSoup(true)
  const water = new FaceSoup(false)
  const { voxels } = surface
  let firstShade = 0
  for (let at = 0; at < voxels.x.length; at++) {
    const column = voxels.x[at]
    const row = voxels.z[at]
    const corner: Color = [field.originX + column, voxels.y[at], field.originZ + row]
    const block = voxels.blocks[at]
    const sides = voxels.faces[at]
    const shades = new FaceShades(voxels.shades, firstShade, sides, voxels.lights[at])
    firstShade += shades.count
    const waterTop = field.waterTopAt(column, row)
    if (surface.isWater[block]) {
      addCubeFaces(sides, (_side, corners) => water.add(corner, corners, null, null))
      continue
    }
    const model = surface.models[block]
    if (model !== null) {
      for (const face of model.faces) {
        if (face.cullFace !== null && (sides & SIDE_BITS[face.cullFace]) === 0) {
          continue
        }
        const color = underWater(face.color, corner[1] + middleY(face.corners), waterTop)
        const brightness = face.cullFace === null ? shades.inside() : shades.across(face.cullFace, face.corners)
        ;(face.cell.hasGaps ? cutOut : opaque).add(corner, face.corners, pictureUvs(face.cell, face.uvs), litCorners(color, brightness))
      }
      continue
    }
    const look = surface.looks[block]
    addCubeFaces(sides, (side, corners) => {
      const face: FaceLook | null = look === null ? null : side === 'up' || side === 'down' ? look.top : look.side
      const light = shaded(face === null ? surface.colors[block] : face.tint, FACE_LIGHT[side])
      const color = underWater(light, corner[1] + middleY(corners), waterTop)
      const cell = face === null ? plain : face.cell
      ;(cell.hasGaps ? cutOut : opaque).add(corner, corners, pictureUvs(cell, WHOLE_PICTURE), litCorners(color, shades.ofCube(side)))
    })
  }
  return { opaque: opaque.geometry(), cutOut: cutOut.geometry(), water: water.geometry() }
}

type CornerBrightness = readonly [number, number, number, number]

/** One block's face shades, as the server sent them for the sides it shows, read as each corner's brightness. */
class FaceShades {
  readonly count: number

  constructor(
    private readonly shades: Uint16Array,
    private readonly first: number,
    private readonly sides: number,
    private readonly ownLight: number
  ) {
    let count = 0
    for (let rest = sides; rest !== 0; rest &= rest - 1) {
      count++
    }
    this.count = count
  }

  /** A whole face on a side, its corners in {@link CUBE_FACES}' order. */
  ofCube(side: Direction): CornerBrightness {
    const shade = this.shadeOf(side)
    const light = brightnessOfLight(lightOf(shade))
    return [0, 1, 2, 3].map((corner) => light * brightnessOfCorner(opaqueAround(shade, corner))) as unknown as CornerBrightness
  }

  /** A model's face against a side, smaller than the block's, each corner as bright as that place of the whole face. */
  across(side: Direction, corners: Corners): CornerBrightness {
    const whole = this.ofCube(side)
    const cube = CUBE_FACES[side]
    return corners.map((place) => {
      let brightness = 0
      for (let corner = 0; corner < CORNERS; corner++) {
        brightness += whole[corner] * weightAt(place, cube[corner], side)
      }
      return brightness
    }) as unknown as CornerBrightness
  }

  /** A face that stands inside the block, such as a plant's, lit as the block's own cell, with no corner darkened. */
  inside(): CornerBrightness {
    const light = brightnessOfLight(this.ownLight)
    return [light, light, light, light]
  }

  private shadeOf(side: Direction): number {
    const bit = SIDE_BITS[side]
    let before = 0
    for (let rest = this.sides & (bit - 1); rest !== 0; rest &= rest - 1) {
      before++
    }
    return this.shades[this.first + before]
  }
}

// How much a corner of a whole face counts at a place on it: the product, along the face's two axes, of how near the
// place lies to the corner's edge.
function weightAt(place: Corners[number], corner: Corners[number], side: Direction): number {
  const across = NORMAL_AXIS[side]
  let weight = 1
  for (let axis = 0; axis < 3; axis++) {
    if (axis !== across) {
      weight *= corner[axis] === 1 ? place[axis] : 1 - place[axis]
    }
  }
  return weight
}

function litCorners(color: Color, brightness: CornerBrightness): Color[] {
  return brightness.map((amount) => [color[0] * amount, color[1] * amount, color[2] * amount] as const)
}

function addCubeFaces(sides: number, add: (side: Direction, corners: Corners) => void): void {
  for (const side of VOXEL_SIDES) {
    if ((sides & SIDE_BITS[side]) !== 0) {
      add(side, CUBE_FACES[side])
    }
  }
}

function middleY(corners: Corners): number {
  return (corners[0][1] + corners[2][1]) / 2
}

function underWater(color: Color, y: number, waterTop: number | null): Color {
  return waterTop === null ? color : sunk(color, tintAt(waterTop - y))
}

function pictureUvs(cell: UvCell, uvs: Uvs): Uvs {
  return uvs.map(([u, v]) => [cell.u + u * cell.width, cell.v + v * cell.height]) as unknown as Uvs
}

const CORNERS = 4
const FIRST_CAPACITY_FACES = 4096

/** Faces gathered for one geometry, each with its own corners, so every face keeps its own colours. */
class FaceSoup {
  private positions: Float32Array<ArrayBuffer> = new Float32Array(FIRST_CAPACITY_FACES * CORNERS * 3)
  private uvs: Float32Array<ArrayBuffer> | null
  private colors: Float32Array<ArrayBuffer> | null
  // Whether each face is split along its other diagonal, as Minecraft splits one whose darker corners lie on the first,
  // so the shading runs along the face and not across a crease.
  private flipped: Uint8Array<ArrayBuffer> = new Uint8Array(FIRST_CAPACITY_FACES)
  private faces = 0

  constructor(textured: boolean) {
    this.uvs = textured ? new Float32Array(FIRST_CAPACITY_FACES * CORNERS * 2) : null
    this.colors = textured ? new Float32Array(FIRST_CAPACITY_FACES * CORNERS * 3) : null
  }

  /** Adds a face, with a colour for each corner, or none. */
  add(origin: Color, corners: Corners, uvs: Uvs | null, colors: readonly Color[] | null): void {
    this.reserveOne()
    const first = this.faces * CORNERS
    for (let corner = 0; corner < CORNERS; corner++) {
      const vertex = first + corner
      this.positions[vertex * 3] = origin[0] + corners[corner][0]
      this.positions[vertex * 3 + 1] = origin[1] + corners[corner][1]
      this.positions[vertex * 3 + 2] = origin[2] + corners[corner][2]
      if (this.uvs !== null && uvs !== null) {
        this.uvs[vertex * 2] = uvs[corner][0]
        this.uvs[vertex * 2 + 1] = uvs[corner][1]
      }
      if (this.colors !== null && colors !== null) {
        this.colors.set(colors[corner], vertex * 3)
      }
    }
    this.flipped[this.faces] = colors !== null && sum(colors[0]) + sum(colors[2]) < sum(colors[1]) + sum(colors[3]) ? 1 : 0
    this.faces++
  }

  geometry(): BufferGeometry | null {
    if (this.faces === 0) {
      return null
    }
    const geometry = new BufferGeometry()
    geometry.setAttribute('position', new BufferAttribute(this.positions.slice(0, this.faces * CORNERS * 3), 3))
    if (this.uvs !== null) {
      geometry.setAttribute('uv', new BufferAttribute(this.uvs.slice(0, this.faces * CORNERS * 2), 2))
    }
    if (this.colors !== null) {
      geometry.setAttribute('color', new BufferAttribute(this.colors.slice(0, this.faces * CORNERS * 3), 3))
    }
    const indices = new Uint32Array(this.faces * 6)
    for (let face = 0; face < this.faces; face++) {
      const first = face * CORNERS
      const split =
        this.flipped[face] === 1 ? [first + 1, first + 2, first + 3, first + 1, first + 3, first] : [first, first + 1, first + 2, first, first + 2, first + 3]
      indices.set(split, face * 6)
    }
    geometry.setIndex(new BufferAttribute(indices, 1))
    return geometry
  }

  private reserveOne(): void {
    if ((this.faces + 1) * CORNERS * 3 <= this.positions.length) {
      return
    }
    this.positions = grown(this.positions)
    this.uvs = this.uvs === null ? null : grown(this.uvs)
    this.colors = this.colors === null ? null : grown(this.colors)
    const flipped = new Uint8Array(this.flipped.length * 2)
    flipped.set(this.flipped)
    this.flipped = flipped
  }
}

function sum(color: Color): number {
  return color[0] + color[1] + color[2]
}

function grown(values: Float32Array): Float32Array<ArrayBuffer> {
  const bigger = new Float32Array(values.length * 2)
  bigger.set(values)
  return bigger
}
