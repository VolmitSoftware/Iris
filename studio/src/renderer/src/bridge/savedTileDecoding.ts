import type { MapLabel, SavedTile } from './types'

const ALIGNMENT_BYTES = 4

interface SavedTileHeader {
  size: number
  blocksPerPixel: number
  blocks: { name: string; drawnAs: string }[]
  biomes: MapLabel[]
  noGround: number
  noWater: number
}

function aligned(bytes: number): number {
  return Math.ceil(bytes / ALIGNMENT_BYTES) * ALIGNMENT_BYTES
}

/**
 * Reads a saved tile as the bridge writes it (SavedTileEncoding): a u32 header length, the JSON header, then the ground,
 * water, block and biome grids, each starting on a multiple of 4 bytes. The grids are views into the buffer.
 */
export function decodeSavedTile(buffer: ArrayBuffer): SavedTile {
  const headerLength = new DataView(buffer).getUint32(0, true)
  const header = JSON.parse(new TextDecoder().decode(new Uint8Array(buffer, 4, headerLength))) as SavedTileHeader
  const pixels = header.size * header.size
  const gridBytes = aligned(pixels * Int16Array.BYTES_PER_ELEMENT)
  const gridAt = (index: number): Int16Array => new Int16Array(buffer, aligned(4 + headerLength) + index * gridBytes, pixels)
  return {
    size: header.size,
    blocksPerPixel: header.blocksPerPixel,
    groundY: gridAt(0),
    waterSurfaceY: gridAt(1),
    blocks: gridAt(2),
    biomes: gridAt(3),
    blockNames: header.blocks.map((block) => block.name),
    blocksDrawnAs: header.blocks.map((block) => block.drawnAs),
    biomeLabels: header.biomes,
    noGround: header.noGround,
    noWater: header.noWater
  }
}
