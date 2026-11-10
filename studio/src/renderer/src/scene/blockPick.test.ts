import { Vector3 } from 'three'
import { describe, expect, it } from 'vitest'
import { BlockIndex, blockAtHit, NO_BLOCK } from './blockPick'
import type { BlockScene } from './sceneDecoding'

// Three blocks in a row along x, from -1 to 1, and one above the middle one.
const SCENE: BlockScene = {
  palette: ['minecraft:stone'],
  blocks: new Int32Array([-1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0]),
  min: [-1, 0, 0],
  max: [2, 2, 1]
}

describe('BlockIndex', () => {
  it('finds each block by position, in the scene order', () => {
    const index = new BlockIndex(SCENE)

    expect([index.at(-1, 0, 0), index.at(0, 0, 0), index.at(1, 0, 0), index.at(0, 1, 0)]).toEqual([0, 1, 2, 3])
  })

  it('finds nothing where the scene has no block, inside its bounds or beyond', () => {
    const index = new BlockIndex(SCENE)

    expect(index.at(1, 1, 0)).toBe(NO_BLOCK)
    expect(index.at(5, 0, 0)).toBe(NO_BLOCK)
    expect(index.at(0, -1, 0)).toBe(NO_BLOCK)
  })
})

describe('blockAtHit', () => {
  const index = new BlockIndex(SCENE)

  it('takes the block a ray goes into, whichever way it faces', () => {
    expect(blockAtHit(index, new Vector3(2, 0.5, 0.5), new Vector3(-1, 0, 0))).toBe(2)
    expect(blockAtHit(index, new Vector3(-1, 0.5, 0.5), new Vector3(1, 0, 0))).toBe(0)
    expect(blockAtHit(index, new Vector3(0.5, 2, 0.5), new Vector3(0, -1, 0))).toBe(3)
    expect(blockAtHit(index, new Vector3(0.5, 0, 0.5), new Vector3(0, 1, 0))).toBe(1)
  })

  it('finds no block in the open', () => {
    expect(blockAtHit(index, new Vector3(5.5, 0.5, 0.5), new Vector3(-1, 0, 0))).toBe(NO_BLOCK)
  })
})
