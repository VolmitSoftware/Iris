import type { LabelKind, MapLabel, MapTile } from './types'

const ALIGNMENT_BYTES = 4

interface TileHeader {
  size: number
  blocksPerPixel: number
  subjectKind: LabelKind
  labels: MapLabel[]
  heights: boolean
  pickNoise: boolean
  /** The blocks the ground and cover grids index; absent or null on a tile without them. */
  blocks?: string[] | null
}

function aligned(bytes: number): number {
  return Math.ceil(bytes / ALIGNMENT_BYTES) * ALIGNMENT_BYTES
}

/**
 * Reads a tile as the bridge writes it (MapTileEncoding): a u32 header length, the JSON header, then the subject,
 * region, height, pick noise, ground and cover grids, each starting on a multiple of 4 bytes. The grids are views into the buffer,
 * not copies.
 */
export function decodeTile(buffer: ArrayBuffer): MapTile {
  const view = new DataView(buffer)
  const headerLength = view.getUint32(0, true)
  const header = JSON.parse(new TextDecoder().decode(new Uint8Array(buffer, 4, headerLength))) as TileHeader
  const gridSize = header.size + 2
  const cells = gridSize * gridSize
  const subjectsAt = aligned(4 + headerLength)
  const regionsAt = aligned(subjectsAt + cells * Int16Array.BYTES_PER_ELEMENT)
  const heightsAt = aligned(regionsAt + cells * Int16Array.BYTES_PER_ELEMENT)
  const pickNoiseAt = heightsAt + (header.heights ? cells * Float32Array.BYTES_PER_ELEMENT : 0)
  const groundAt = pickNoiseAt + (header.pickNoise ? cells * Float32Array.BYTES_PER_ELEMENT : 0)
  const coverAt = aligned(groundAt + cells * Int16Array.BYTES_PER_ELEMENT)
  return {
    size: header.size,
    blocksPerPixel: header.blocksPerPixel,
    subjectKind: header.subjectKind,
    labels: header.labels,
    subjects: new Int16Array(buffer, subjectsAt, cells),
    regions: new Int16Array(buffer, regionsAt, cells),
    heights: header.heights ? new Float32Array(buffer, heightsAt, cells) : null,
    pickNoise: header.pickNoise ? new Float32Array(buffer, pickNoiseAt, cells) : null,
    blocks:
      header.blocks == null
        ? null
        : { names: header.blocks, ground: new Int16Array(buffer, groundAt, cells), cover: new Int16Array(buffer, coverAt, cells), colors: [], looks: [], covers: [] }
  }
}

/** The grid index of a pixel; pixel coordinates run from -1 to size, the ring being the neighbours'. */
export function gridIndex(tile: MapTile, pixelX: number, pixelZ: number): number {
  return (pixelZ + 1) * (tile.size + 2) + pixelX + 1
}
