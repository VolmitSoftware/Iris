import type { PatternTile } from './types'

const ALIGNMENT_BYTES = 4

interface PatternHeader {
  columns: number
  blocksPerColumn: number
  blocks: string[]
}

/**
 * Reads a pattern as the bridge writes it (PatternEncoding): a u32 header length, the JSON header, then from the next
 * multiple of 4 bytes an i16 pick per column, row by row. The picks are a view into the buffer, not a copy.
 */
export function decodePattern(buffer: ArrayBuffer): PatternTile {
  const headerLength = new DataView(buffer).getUint32(0, true)
  const header = JSON.parse(new TextDecoder().decode(new Uint8Array(buffer, 4, headerLength))) as PatternHeader
  const picksAt = Math.ceil((4 + headerLength) / ALIGNMENT_BYTES) * ALIGNMENT_BYTES
  return {
    columns: header.columns,
    blocksPerColumn: header.blocksPerColumn,
    blocks: header.blocks,
    picks: new Int16Array(buffer, picksAt, header.columns * header.columns)
  }
}
