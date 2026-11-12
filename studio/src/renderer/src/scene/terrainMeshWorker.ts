/// <reference lib="webworker" />
import type { MapTile } from '../bridge/types'
import { compacted, type CompactArrays } from './compactGeometry'
import type { FieldShape } from './columnField'
import { MapTerrain, type MapTerrainColoring } from './mapTerrain'
import type { TerrainPictures, UvCell } from './terrainAtlas'
import { buildTerrainGeometry, type GeometryKind } from './terrainMesh'
import type { ColumnArea, TerrainTile } from './terrainTiles'

/** One map tile to build the faces of, as the studio's 3D world draws it. */
export interface MeshRequest {
  id: number
  tile: MapTile
  place: TerrainTile
  coloring: MapTerrainColoring
  fluidHeight: number
  /** The atlas's plain cell, or null to draw the tile in colour alone. */
  plain: UvCell | null
  base: number
  holes: ColumnArea[]
}

/** A built geometry's arrays, packed small, as a worker sends them back. */
export interface GeometryArrays {
  kind: GeometryKind
  arrays: CompactArrays
}

export interface MeshResult {
  id: number
  field: FieldShape
  parts: GeometryArrays[]
}

// The faces are built from the atlas's cell layout alone; the textures themselves stay where they are drawn.
function picturesOf(plain: UvCell | null): TerrainPictures | undefined {
  return plain === null ? undefined : ({ plain, texture: null, cutouts: null } as unknown as TerrainPictures)
}

self.onmessage = (event: MessageEvent<MeshRequest>) => {
  const { id, tile, place, coloring, fluidHeight, plain, base, holes } = event.data
  const surface = new MapTerrain(tile, place, coloring, fluidHeight, picturesOf(plain))
  const { field, parts } = buildTerrainGeometry(surface, base, holes)
  const packed: GeometryArrays[] = parts.map(({ kind, geometry }) => ({ kind, arrays: compacted(geometry, field.blocksPerColumn) }))
  const shape = field.shape()
  const buffers = new Set<ArrayBuffer>([shape.groundTop.buffer as ArrayBuffer, shape.waterTop.buffer as ArrayBuffer])
  for (const { arrays } of packed) {
    for (const array of [arrays.position, arrays.color, arrays.uv, arrays.index]) {
      if (array !== null) {
        buffers.add(array.buffer as ArrayBuffer)
      }
    }
  }
  const result: MeshResult = { id, field: shape, parts: packed }
  self.postMessage(result, [...buffers])
}
