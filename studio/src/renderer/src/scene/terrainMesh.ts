import { DoubleSide, FrontSide, Group, Mesh, MeshBasicMaterial } from 'three'
import { groundFaces, waterFaces } from './columnFaces'
import { ColumnField } from './columnField'
import type { ColumnArea } from './terrainTiles'

/**
 * Terrain to draw as columns, row by row from the north west: the ground, the water over it, and the ground's colour.
 * Heights are the Y of the top block, so a column of 64 has its top face at 65. Where nothing is known, the ground is
 * null and the column is left out.
 */
export interface TerrainSurface {
  readonly columns: number
  readonly rows: number
  /** The world block of the first column and of the first row. */
  readonly originX: number
  readonly originZ: number
  /** How many blocks wide and deep a column is. */
  readonly blocksPerColumn: number
  groundAt(column: number, row: number): number | null
  /** The Y of the water's top block over the column, or null where it is dry. */
  waterAt(column: number, row: number): number | null
  colorAt(column: number, row: number): number
}

export interface TerrainMesh {
  object: Group
  field: ColumnField
  dispose(): void
}

const WATER_COLOR = 0x3f76e4
const WATER_OPACITY = 0.55

/**
 * The surface as columns with their faces merged where neighbours show the same one, flat-shaded as Minecraft shades
 * blocks, with see-through water over them. Sides on the edge reach down to {@code base}, so tiles of ground next to
 * each other leave no cracks. The columns in {@code holes} are left out, for finer ground to fill.
 */
export function buildTerrainMesh(surface: TerrainSurface, base: number, holes: readonly ColumnArea[] = []): TerrainMesh {
  const field = new ColumnField(surface, holes)
  const group = new Group()
  if (!field.hasAnyGround()) {
    return { object: group, field, dispose: () => {} }
  }
  const ground = groundFaces(field, base).geometry()
  const water = waterFaces(field).geometry()
  const groundMaterial = new MeshBasicMaterial({ vertexColors: true, side: FrontSide })
  const waterMaterial = new MeshBasicMaterial({ color: WATER_COLOR, transparent: true, opacity: WATER_OPACITY, depthWrite: false, side: DoubleSide })
  group.add(new Mesh(ground, groundMaterial), new Mesh(water, waterMaterial))
  return {
    object: group,
    field,
    dispose: () => {
      ground.dispose()
      water.dispose()
      groundMaterial.dispose()
      waterMaterial.dispose()
    }
  }
}
