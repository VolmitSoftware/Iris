import { BufferAttribute, BufferGeometry } from 'three'
import { cubeModel, parseState, type BlockModels, type BlockModel, type Direction, type Quad } from './blockModels'
import { blockTint } from './blockTints'
import { INTS_PER_BLOCK, type BlockScene } from './sceneDecoding'
import { TextureAtlas, type ImageSource } from './textureAtlas'

export interface SceneMesh {
  geometry: BufferGeometry
  atlas: TextureAtlas
  /** Block states the client has no blockstate file for, drawn as magenta and black cubes. */
  unknownStates: string[]
}

/** The light Minecraft gives each side of a block without shadows: the top brightest, the bottom darkest. */
const FACE_LIGHT: Record<Direction, number> = { up: 1, down: 0.5, north: 0.8, south: 0.8, west: 0.6, east: 0.6 }
const MISSING_TEXTURE = 'iris:missing'
const NEIGHBOUR_OFFSETS: Record<Direction, [number, number, number]> = {
  down: [0, -1, 0],
  up: [0, 1, 0],
  north: [0, 0, -1],
  south: [0, 0, 1],
  west: [-1, 0, 0],
  east: [1, 0, 0]
}
const CORNERS_PER_QUAD = 4
const INDICES_PER_QUAD = 6

interface PaletteEntry {
  model: BlockModel
  name: string
  properties: Record<string, string>
  /** Hides the faces of its neighbours that touch it. */
  solid: boolean
}

/** One geometry for the whole scene, its faces against solid neighbours left out, textured from one atlas. */
export async function buildSceneMesh(scene: BlockScene, models: BlockModels, images: ImageSource): Promise<SceneMesh> {
  const resolved = await Promise.all(scene.palette.map((state) => models.model(state)))
  const unknownStates = scene.palette.filter((_, index) => resolved[index] === null)
  const drawn = resolved.map((model) => drawnModel(model))
  const atlas = await TextureAtlas.of(
    drawn.flatMap((model) => model.quads.map((quad) => quad.texture)),
    images
  )
  const palette = drawn.map((model, index): PaletteEntry => {
    const state = parseState(scene.palette[index])
    const solid = model.fullCube && model.quads.every((quad) => atlas.cell(quad.texture).solid)
    return { model, name: state.name, properties: state.properties, solid }
  })
  return { geometry: geometryOf(scene, palette, atlas), atlas, unknownStates }
}

// The client draws chests, signs, water and the like without a block model; their break texture stands in for them.
function drawnModel(model: BlockModel | null): BlockModel {
  if (model === null) {
    return cubeModel(MISSING_TEXTURE)
  }
  if (model.quads.length === 0) {
    return cubeModel(model.particle ?? MISSING_TEXTURE)
  }
  return model
}

function geometryOf(scene: BlockScene, palette: PaletteEntry[], atlas: TextureAtlas): BufferGeometry {
  const solids = new SolidBlocks(scene, palette)
  const visible: { quad: Quad; entry: PaletteEntry; x: number; y: number; z: number }[] = []
  const blocks = scene.blocks
  for (let offset = 0; offset < blocks.length; offset += INTS_PER_BLOCK) {
    const [x, y, z, paletteIndex] = [blocks[offset], blocks[offset + 1], blocks[offset + 2], blocks[offset + 3]]
    const entry = palette[paletteIndex]
    for (const quad of entry.model.quads) {
      if (quad.cullFace !== null && solids.besides(x, y, z, quad.cullFace)) {
        continue
      }
      visible.push({ quad, entry, x, y, z })
    }
  }
  const positions = new Float32Array(visible.length * CORNERS_PER_QUAD * 3)
  const uvs = new Float32Array(visible.length * CORNERS_PER_QUAD * 2)
  const colors = new Float32Array(visible.length * CORNERS_PER_QUAD * 3)
  const indices = new Uint32Array(visible.length * INDICES_PER_QUAD)
  visible.forEach(({ quad, entry, x, y, z }, quadIndex) => {
    const cell = atlas.cell(quad.texture)
    const light = quad.shade ? FACE_LIGHT[quad.facing] : 1
    const tint = blockTint(entry.name, entry.properties, quad.tintIndex)
    const [red, green, blue] = [((tint >> 16) & 0xff) / 255, ((tint >> 8) & 0xff) / 255, (tint & 0xff) / 255]
    for (let corner = 0; corner < CORNERS_PER_QUAD; corner++) {
      const vertex = quadIndex * CORNERS_PER_QUAD + corner
      const [cornerX, cornerY, cornerZ] = quad.corners[corner]
      positions.set([x + cornerX, y + cornerY, z + cornerZ], vertex * 3)
      uvs.set([cell.u + quad.uvs[corner][0] * cell.width, cell.v + quad.uvs[corner][1] * cell.height], vertex * 2)
      colors.set([red * light, green * light, blue * light], vertex * 3)
    }
    const first = quadIndex * CORNERS_PER_QUAD
    indices.set([first, first + 1, first + 2, first, first + 2, first + 3], quadIndex * INDICES_PER_QUAD)
  })
  const geometry = new BufferGeometry()
  geometry.setAttribute('position', new BufferAttribute(positions, 3))
  geometry.setAttribute('uv', new BufferAttribute(uvs, 2))
  geometry.setAttribute('color', new BufferAttribute(colors, 3))
  geometry.setIndex(new BufferAttribute(indices, 1))
  geometry.computeBoundingSphere()
  return geometry
}

/** Which spots of the scene hold a solid block, in a grid over its bounds. */
class SolidBlocks {
  private readonly solid: Uint8Array
  private readonly sizeX: number
  private readonly sizeY: number
  private readonly sizeZ: number

  constructor(private readonly scene: BlockScene, palette: PaletteEntry[]) {
    ;[this.sizeX, this.sizeY, this.sizeZ] = [0, 1, 2].map((axis) => scene.max[axis] - scene.min[axis])
    this.solid = new Uint8Array(this.sizeX * this.sizeY * this.sizeZ)
    const blocks = scene.blocks
    for (let offset = 0; offset < blocks.length; offset += INTS_PER_BLOCK) {
      if (palette[blocks[offset + 3]].solid) {
        this.solid[this.indexOf(blocks[offset], blocks[offset + 1], blocks[offset + 2])] = 1
      }
    }
  }

  besides(x: number, y: number, z: number, side: Direction): boolean {
    const [dx, dy, dz] = NEIGHBOUR_OFFSETS[side]
    const index = this.indexOf(x + dx, y + dy, z + dz)
    return index >= 0 && this.solid[index] === 1
  }

  private indexOf(x: number, y: number, z: number): number {
    const [localX, localY, localZ] = [x - this.scene.min[0], y - this.scene.min[1], z - this.scene.min[2]]
    if (localX < 0 || localY < 0 || localZ < 0 || localX >= this.sizeX || localY >= this.sizeY || localZ >= this.sizeZ) {
      return -1
    }
    return (localX * this.sizeY + localY) * this.sizeZ + localZ
  }
}
