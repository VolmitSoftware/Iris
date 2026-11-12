import { DoubleSide, FrontSide, Group, Mesh, MeshBasicMaterial, ShaderChunk, type BufferGeometry } from 'three'
import type { BlockLook } from './blockLooks'
import { groundFaces, waterFaces } from './columnFaces'
import { ColumnField } from './columnField'
import { coverGeometry } from './coverFaces'
import type { CoverLook } from './coverLooks'
import { QuadBatch } from './quadBatch'
import { addRaisedFaces } from './raisedFaces'
import type { CompactPlacement } from './compactGeometry'
import { runFaces, type SurfaceRuns } from './runFaces'
import { voxelGeometries, type SurfaceVoxels } from './voxelMesh'

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
  /**
   * Every block seen from the open air, to draw one by one in place of the columns, which then only answer where the
   * ground lies; null to draw the columns.
   */
  voxels?(): SurfaceVoxels | null
  /** The runs of solid blocks of each column's area, to draw in place of the columns' tops; null to draw the columns. */
  runs?(): SurfaceRuns | null
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
 * whole blocks; what is seen through, such as leaves, is cut out along its pictures.
 */
export function buildTerrainMesh(surface: TerrainSurface, base: number, holes: readonly ColumnArea[] = []): TerrainMesh {
  const geometry = buildTerrainGeometry(surface, base, holes)
  return meshOfGeometry(geometry, geometry.field.pictures)
}

/** How a part of a tile's geometry is drawn: in its colours alone, textured, cut out along its pictures, or as water. */
export type GeometryKind = 'coloured' | 'textured' | 'cutOut' | 'water'

/** A tile's faces, ready to draw in materials of their kinds, in the order they are drawn; water comes last. */
export interface TerrainGeometry {
  field: ColumnField
  /** Each part's faces; where a part has a placement, its corners are in the steps of a compact geometry. */
  parts: { kind: GeometryKind; geometry: BufferGeometry; placement?: CompactPlacement }[]
}

/** The faces {@link buildTerrainMesh} draws, without materials, so they can be built away from where they are drawn. */
export function buildTerrainGeometry(surface: TerrainSurface, base: number, holes: readonly ColumnArea[] = []): TerrainGeometry {
  const field = new ColumnField(surface, holes)
  if (!field.hasAnyGround()) {
    return { field, parts: [] }
  }
  const voxels = field.pictures === null ? null : (surface.voxels?.() ?? null)
  if (voxels !== null && field.pictures !== null) {
    const { opaque, cutOut, water } = voxelGeometries(field, voxels, field.pictures.plain)
    return { field, parts: present([part('textured', opaque), part('cutOut', cutOut), part('water', water)]) }
  }
  const runs = surface.runs?.() ?? null
  if (runs !== null) {
    return { field, parts: [{ kind: 'coloured', geometry: runFaces(field, runs, base).geometry() }, { kind: 'water', geometry: waterFaces(field).geometry() }] }
  }
  const groundBatch = groundFaces(field, base)
  const seeThroughBatch = new QuadBatch(true, field.pictures?.plain ?? null)
  addRaisedFaces(field, groundBatch, seeThroughBatch)
  const cutOut = field.pictures === null ? [] : present([part('cutOut', coverGeometry(field)), part('cutOut', seeThroughBatch.count === 0 ? null : seeThroughBatch.geometry())])
  return {
    field,
    parts: [{ kind: field.pictures === null ? 'coloured' : 'textured', geometry: groundBatch.geometry() }, ...cutOut, { kind: 'water', geometry: waterFaces(field).geometry() }]
  }
}

function part(kind: GeometryKind, geometry: BufferGeometry | null): { kind: GeometryKind; geometry: BufferGeometry } | null {
  return geometry === null ? null : { kind, geometry }
}

function present<T>(parts: (T | null)[]): T[] {
  return parts.filter((found): found is T => found !== null)
}

/** The geometry drawn in its materials; textured and cut-out parts need {@code pictures}. */
export function meshOfGeometry({ field, parts }: TerrainGeometry, pictures: TerrainPictures | null): TerrainMesh {
  const group = new Group()
  const materials = parts.map(({ kind }) => materialOf(kind, pictures))
  parts.forEach(({ geometry, placement }, index) => {
    const mesh = new Mesh(geometry, materials[index])
    if (placement !== undefined) {
      mesh.position.set(...placement.origin)
      mesh.scale.set(...placement.scale)
    }
    group.add(mesh)
  })
  return {
    object: group,
    field,
    dispose: () => {
      parts.forEach(({ geometry }) => geometry.dispose())
      materials.forEach((material) => material.dispose())
    }
  }
}

// Cut-out parts are drawn where their pictures are not open, so the grass between its blades and the leaves between their
// twigs show what lies behind.
function materialOf(kind: GeometryKind, pictures: TerrainPictures | null): MeshBasicMaterial {
  switch (kind) {
    case 'coloured':
      return new MeshBasicMaterial({ vertexColors: true, side: FrontSide })
    case 'textured':
      return withPicturesReadInsideFaces(new MeshBasicMaterial({ vertexColors: true, side: FrontSide, map: pictures?.texture ?? null }))
    case 'cutOut':
      return withPicturesReadInsideFaces(
        new MeshBasicMaterial({ vertexColors: true, map: pictures?.texture ?? null, alphaMap: pictures?.cutouts ?? null, alphaTest: CUTOUT_ALPHA, side: FrontSide })
      )
    case 'water':
      return new MeshBasicMaterial({ color: WATER_SURFACE_COLOR, transparent: true, opacity: WATER_SURFACE_OPACITY, depthWrite: false, side: DoubleSide })
  }
}

const PICTURE_COORDINATES = /varying vec2 (vMapUv|vAlphaMapUv);/g
const CENTROID_COORDINATES = 'centroid varying vec2 $1;'

// With antialiasing, a pixel only partly on a face is shaded at its centre, which can lie off the face, and the picture
// is read past the face's edge. Far off, where a block face is a pixel or less, that is the picture beside it in the atlas,
// and the ground and trees are speckled with other blocks' colours. Centroid coordinates are read where the face is.
function withPicturesReadInsideFaces(material: MeshBasicMaterial): MeshBasicMaterial {
  material.onBeforeCompile = (shader) => {
    shader.vertexShader = shader.vertexShader.replace('#include <uv_pars_vertex>', ShaderChunk.uv_pars_vertex.replace(PICTURE_COORDINATES, CENTROID_COORDINATES))
    shader.fragmentShader = shader.fragmentShader.replace('#include <uv_pars_fragment>', ShaderChunk.uv_pars_fragment.replace(PICTURE_COORDINATES, CENTROID_COORDINATES))
  }
  return material
}
