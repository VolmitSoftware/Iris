/** A block scene as the bridge encodes it (Iris SceneEncoding). */
export interface BlockScene {
  /** The block states the scene uses, once each. */
  palette: string[]
  /** Four numbers per block: x, y, z from the scene's anchor, then the index of its state in the palette. */
  blocks: Int32Array
  /** The lowest corner from the anchor, inclusive; {@code max} the highest, exclusive. */
  min: [number, number, number]
  max: [number, number, number]
}

interface Header {
  palette: string[]
  blockCount: number
  min: [number, number, number]
  max: [number, number, number]
}

export const INTS_PER_BLOCK = 4
const ALIGNMENT_BYTES = 4

export function decodeScene(buffer: ArrayBuffer): BlockScene {
  const headerLength = new DataView(buffer).getUint32(0, true)
  const header = JSON.parse(new TextDecoder().decode(new Uint8Array(buffer, 4, headerLength))) as Header
  const blocksAt = Math.ceil((4 + headerLength) / ALIGNMENT_BYTES) * ALIGNMENT_BYTES
  return {
    palette: header.palette,
    blocks: new Int32Array(buffer, blocksAt, header.blockCount * INTS_PER_BLOCK),
    min: header.min,
    max: header.max
  }
}
