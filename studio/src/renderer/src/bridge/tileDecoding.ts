import type { LabelKind, MapLabel, MapTile, TileBlocks, TileRuns, TileVoxels } from './types'

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
  /** How many blocks are raised over the ground of all cells together. */
  raised?: number
  /** How many blocks can be seen from the open air, or -1 on a tile without them. */
  voxels?: number
  /** How many runs of solid blocks the cells have together, or -1 on a tile without them. */
  runs?: number
}

const NONE = -1

function aligned(bytes: number): number {
  return Math.ceil(bytes / ALIGNMENT_BYTES) * ALIGNMENT_BYTES
}

/**
 * Reads a tile as the bridge writes it (MapTileEncoding): a u32 header length, the JSON header, then the subject,
 * region, height, pick noise, ground, cover and raised block grids, each starting on a multiple of 4 bytes. The grids
 * are views into the buffer, not copies.
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
  return {
    size: header.size,
    blocksPerPixel: header.blocksPerPixel,
    subjectKind: header.subjectKind,
    labels: header.labels,
    subjects: new Int16Array(buffer, subjectsAt, cells),
    regions: new Int16Array(buffer, regionsAt, cells),
    heights: header.heights ? new Float32Array(buffer, heightsAt, cells) : null,
    pickNoise: header.pickNoise ? new Float32Array(buffer, pickNoiseAt, cells) : null,
    blocks: header.blocks == null ? null : decodeBlocks(buffer, groundAt, cells, header.blocks, header.raised ?? 0, header.voxels ?? NONE, header.runs ?? NONE)
  }
}

function decodeBlocks(buffer: ArrayBuffer, groundAt: number, cells: number, names: string[], raisedCount: number, voxelCount: number, runCount: number): TileBlocks {
  const gridBytes = aligned(cells * Int16Array.BYTES_PER_ELEMENT)
  const coverAt = groundAt + gridBytes
  const raisedCountsAt = coverAt + gridBytes
  const raisedYAt = raisedCountsAt + gridBytes
  const raisedAt = aligned(raisedYAt + raisedCount * Int16Array.BYTES_PER_ELEMENT)
  const voxelsAt = aligned(raisedAt + raisedCount * Int16Array.BYTES_PER_ELEMENT)
  const runsAt = voxelsAt + (voxelCount === NONE ? 0 : voxelBytes(voxelCount))
  return {
    names,
    ground: new Int16Array(buffer, groundAt, cells),
    cover: new Int16Array(buffer, coverAt, cells),
    raisedStart: startsOf(new Int16Array(buffer, raisedCountsAt, cells)),
    raisedY: new Int16Array(buffer, raisedYAt, raisedCount),
    raised: new Int16Array(buffer, raisedAt, raisedCount),
    voxels: voxelCount === NONE ? null : decodeVoxels(buffer, voxelsAt, voxelCount),
    runs: runCount === NONE ? null : decodeRuns(buffer, runsAt, cells, runCount),
    colors: [],
    looks: [],
    covers: [],
    models: []
  }
}

function voxelBytes(count: number): number {
  return aligned(count) * 3 + aligned(count * Int16Array.BYTES_PER_ELEMENT) * 2
}

function decodeRuns(buffer: ArrayBuffer, countsAt: number, cells: number, count: number): TileRuns {
  const bottomsAt = countsAt + aligned(cells * Int16Array.BYTES_PER_ELEMENT)
  const topsAt = bottomsAt + aligned(count * Int16Array.BYTES_PER_ELEMENT)
  const blocksAt = topsAt + aligned(count * Int16Array.BYTES_PER_ELEMENT)
  const coversAt = blocksAt + aligned(count * Int16Array.BYTES_PER_ELEMENT)
  const coverSharesAt = coversAt + aligned(count * Int16Array.BYTES_PER_ELEMENT)
  return {
    starts: startsOf(new Int16Array(buffer, countsAt, cells)),
    bottoms: new Int16Array(buffer, bottomsAt, count),
    tops: new Int16Array(buffer, topsAt, count),
    blocks: new Int16Array(buffer, blocksAt, count),
    covers: new Int16Array(buffer, coversAt, count),
    coverShares: new Uint8Array(buffer, coverSharesAt, count)
  }
}

function decodeVoxels(buffer: ArrayBuffer, xAt: number, count: number): TileVoxels {
  const zAt = xAt + aligned(count)
  const yAt = zAt + aligned(count)
  const blocksAt = yAt + aligned(count * Int16Array.BYTES_PER_ELEMENT)
  const facesAt = blocksAt + aligned(count * Int16Array.BYTES_PER_ELEMENT)
  return {
    x: new Uint8Array(buffer, xAt, count),
    z: new Uint8Array(buffer, zAt, count),
    y: new Int16Array(buffer, yAt, count),
    blocks: new Int16Array(buffer, blocksAt, count),
    faces: new Uint8Array(buffer, facesAt, count)
  }
}

function startsOf(counts: Int16Array): Int32Array {
  const starts = new Int32Array(counts.length + 1)
  for (let cell = 0; cell < counts.length; cell++) {
    starts[cell + 1] = starts[cell] + counts[cell]
  }
  return starts
}

/** The grid index of a pixel; pixel coordinates run from -1 to size, the ring being the neighbours'. */
export function gridIndex(tile: MapTile, pixelX: number, pixelZ: number): number {
  return (pixelZ + 1) * (tile.size + 2) + pixelX + 1
}
