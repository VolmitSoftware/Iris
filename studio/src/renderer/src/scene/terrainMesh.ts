import { DoubleSide, FrontSide, Group, Mesh, MeshBasicMaterial } from 'three'
import type { ViewportContent } from '../studio/SceneViewport'
import { groundFaces, waterFaces } from './columnFaces'
import { ColumnField } from './columnField'

/**
 * Terrain to draw as columns, one block each, row by row from the north west: the ground, the water over it, and the
 * ground's colour. Heights are the Y of the top block, so a column of 64 has its top face at 65. Where nothing is
 * known, the ground is null and the column is left out.
 */
export interface TerrainSurface {
  readonly columns: number
  readonly rows: number
  /** The world block of the first column and of the first row. */
  readonly originX: number
  readonly originZ: number
  groundAt(column: number, row: number): number | null
  /** The Y of the water's top block over the column, or null where it is dry. */
  waterAt(column: number, row: number): number | null
  colorAt(column: number, row: number): number
}

export interface TerrainMesh {
  content: ViewportContent
  dispose(): void
}

const WATER_COLOR = 0x3f76e4
const WATER_OPACITY = 0.55
const BASE_DEPTH_BLOCKS = 8

/**
 * The surface as block columns with their faces merged where neighbours show the same one, flat-shaded as Minecraft
 * shades blocks, with see-through water over them. Sides on the rim reach down to below the lowest ground, so the
 * square reads as a solid block cut from the world.
 */
export function buildTerrainMesh(surface: TerrainSurface): TerrainMesh {
  const field = new ColumnField(surface)
  const base = field.lowestTop() - BASE_DEPTH_BLOCKS
  const ground = groundFaces(field, base).geometry()
  const water = waterFaces(field).geometry()
  const groundMaterial = new MeshBasicMaterial({ vertexColors: true, side: FrontSide })
  const waterMaterial = new MeshBasicMaterial({ color: WATER_COLOR, transparent: true, opacity: WATER_OPACITY, depthWrite: false, side: DoubleSide })
  const group = new Group()
  group.add(new Mesh(ground, groundMaterial), new Mesh(water, waterMaterial))
  const bounds = field.bounds(base)
  return {
    content: { object: group, bounds, surfaceAlong: (ray) => field.surfaceAlong(ray, bounds) },
    dispose: () => {
      ground.dispose()
      water.dispose()
      groundMaterial.dispose()
      waterMaterial.dispose()
    }
  }
}
