import { describe, expect, it } from 'vitest'
import { NO_PART } from '../bridge/types'
import { blocksInPart, blocksOutsidePart } from './partFocus'

const PARTS = Int32Array.of(2, NO_PART, 0, 2, 2)

describe('part focus', () => {
  it('marks every block outside the chosen part, the ones no part placed too', () => {
    expect(blocksOutsidePart(PARTS, 2)).toEqual(Uint8Array.of(0, 1, 1, 0, 0))
  })

  it('marks nothing when no part is chosen', () => {
    expect(blocksOutsidePart(PARTS, NO_PART)).toBeNull()
  })

  it('counts the blocks of a part, none for a part that placed nothing', () => {
    expect(blocksInPart(PARTS, 2)).toBe(3)
    expect(blocksInPart(PARTS, 7)).toBe(0)
  })
})
