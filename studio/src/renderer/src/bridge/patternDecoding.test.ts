import { describe, expect, it } from 'vitest'
import { decodePattern } from './patternDecoding'
import { NO_PATTERN_BLOCK } from './types'

function encodedPattern(header: object, picks: number[]): ArrayBuffer {
  const headerBytes = new TextEncoder().encode(JSON.stringify(header))
  const picksAt = Math.ceil((4 + headerBytes.length) / 4) * 4
  const buffer = new ArrayBuffer(picksAt + picks.length * 2)
  const view = new DataView(buffer)
  view.setUint32(0, headerBytes.length, true)
  new Uint8Array(buffer, 4).set(headerBytes)
  picks.forEach((pick, index) => view.setInt16(picksAt + index * 2, pick, true))
  return buffer
}

describe('decodePattern', () => {
  it('reads the picks after the padded header, row by row, with no block as -1', () => {
    const header = { columns: 2, blocksPerColumn: 4, blocks: ['minecraft:grass_block[snowy=false]', 'minecraft:dirt'], noBlock: -1 }
    const pattern = decodePattern(encodedPattern(header, [0, -1, 1, 0]))
    expect(pattern.columns).toBe(2)
    expect(pattern.blocksPerColumn).toBe(4)
    expect(pattern.blocks).toEqual(header.blocks)
    expect([...pattern.picks]).toEqual([0, NO_PATTERN_BLOCK, 1, 0])
  })
})
