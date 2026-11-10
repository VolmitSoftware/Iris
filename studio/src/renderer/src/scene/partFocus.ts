import { NO_PART } from '../bridge/types'

/** Which blocks are not of {@code part}, one 1 per block in the scene's order; null when no part is chosen. */
export function blocksOutsidePart(parts: Int32Array, part: number): Uint8Array | null {
  if (part === NO_PART) {
    return null
  }
  return Uint8Array.from(parts, (blockPart) => (blockPart === part ? 0 : 1))
}

/** How many blocks {@code part} placed. */
export function blocksInPart(parts: Int32Array, part: number): number {
  return parts.reduce((count, blockPart) => count + (blockPart === part ? 1 : 0), 0)
}
