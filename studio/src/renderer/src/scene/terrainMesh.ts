import { DoubleSide, FrontSide, Group, Mesh, MeshBasicMaterial } from 'three'
import type { BlockLook } from './blockLooks'
import { groundFaces, waterFaces } from './columnFaces'
import { ColumnField } from './columnField'
import { coverGeometry } from './coverFaces'
import type { CoverLook } from './coverLooks'
import { addRaisedFaces } from './raisedFaces'
import type { TerrainPictures } from './terrainAtlas'
import type { ColumnArea } from './terrainTiles'
import { WATER_SURFACE_COLOR, WATER_SURFACE_OPACITY } from './waterTint'

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
  /** The atlas that {@link lookAt}'s pictures lie in; a surface without one is drawn in colour alone. */
  readonly pictures?: TerrainPictures
  /** What the block of the column looks like, or null to show the column in its colour. */
  lookAt?(column: number, row: number): BlockLook | null
  /** What stands on the column's ground, such as grass or a flower, or null for nothing that is drawn. */
  coverAt?(column: number, row: number): CoverLook | null
  /** The solid blocks above the column's ground, such as a tree's wood and leaves, highest first. */
  raisedAt?(column: number, row: number): readonly RaisedBlock[]
}

/** A solid block above a column's ground, drawn as a whole block. */
export interface RaisedBlock {
  y: number
  color: number
  /** What the block looks like, or null to show its colour. */
  look: BlockLook | null
}

const CUTOUT_ALPHA = 0.5

export interface TerrainMesh {
  object: Group
  field: ColumnField
  dispose(): void
}

/**
 * The surface as columns with their faces merged where neighbours show the same one, flat-shaded as Minecraft shades
 * blocks, with the ground under water sunk toward a deep colour and a light sheet of water over it. Sides on the edge reach down to {@code base}, so tiles of ground next to
 * each other leave no cracks. The columns in {@code holes} are left out, for finer ground to fill. Columns with a look
 * show their block's textures, one picture to a block face. Blocks raised above the ground, such as trees, stand as
 * whole blocks.
 */
export function buildTerrainMesh(surface: TerrainSurface, base: number, holes: readonly ColumnArea[] = []): TerrainMesh {
  const field = new ColumnField(surface, holes)
  const group = new Group()
  if (!field.hasAnyGround()) {
    return { object: group, field, dispose: () => {} }
  }
  const groundBatch = groundFaces(field, base)
  addRaisedFaces(field, groundBatch)
  const ground = groundBatch.geometry()
  const water = waterFaces(field).geometry()
  const groundMaterial = new MeshBasicMaterial({ vertexColors: true, side: FrontSide, map: field.pictures?.texture ?? null })
  const waterMaterial = new MeshBasicMaterial({ color: WATER_SURFACE_COLOR, transparent: true, opacity: WATER_SURFACE_OPACITY, depthWrite: false, side: DoubleSide })
  const cover = field.pictures === null ? null : coverMesh(field, field.pictures)
  // The water is drawn last, over what stands under it.
  group.add(new Mesh(ground, groundMaterial), ...(cover === null ? [] : [cover.mesh]), new Mesh(water, waterMaterial))
  return {
    object: group,
    field,
    dispose: () => {
      ground.dispose()
      water.dispose()
      groundMaterial.dispose()
      waterMaterial.dispose()
      cover?.dispose()
    }
  }
}

function coverMesh(field: ColumnField, pictures: TerrainPictures): { mesh: Mesh; dispose(): void } | null {
  const geometry = coverGeometry(field)
  if (geometry === null) {
    return null
  }
  const material = new MeshBasicMaterial({ vertexColors: true, map: pictures.texture, alphaMap: pictures.cutouts, alphaTest: CUTOUT_ALPHA, side: FrontSide })
  return {
    mesh: new Mesh(geometry, material),
    dispose: () => {
      geometry.dispose()
      material.dispose()
    }
  }
}
