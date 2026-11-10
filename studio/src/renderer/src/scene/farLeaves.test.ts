import { describe, expect, it } from 'vitest'
import { countFarLeaves, isFarLeaf } from './farLeaves'
import type { BlockScene } from './sceneDecoding'

function sceneOf(palette: string[], ...indexes: number[]): BlockScene {
  const blocks = new Int32Array(indexes.flatMap((index, position) => [position, 0, 0, index]))
  return { palette, blocks, min: [0, 0, 0], max: [indexes.length, 1, 1] }
}

describe('far leaves', () => {
  it('takes a leaf with the persistent property at distance 7', () => {
    expect(isFarLeaf('minecraft:oak_leaves[distance=7,persistent=true,waterlogged=false]')).toBe(true)
    expect(isFarLeaf('minecraft:oak_leaves[distance=7,persistent=false]')).toBe(true)
  })

  it('leaves out leaves near wood, leaves without the persistent property, and other blocks', () => {
    expect(isFarLeaf('minecraft:oak_leaves[distance=6,persistent=true]')).toBe(false)
    expect(isFarLeaf('minecraft:oak_leaves[distance=7]')).toBe(false)
    expect(isFarLeaf('minecraft:oak_log[axis=y]')).toBe(false)
    expect(isFarLeaf('minecraft:stone')).toBe(false)
  })

  it('counts the blocks of the scene that are far leaves, not the palette entries', () => {
    const scene = sceneOf(['minecraft:stone', 'minecraft:oak_leaves[distance=7,persistent=true]', 'minecraft:oak_leaves[distance=1,persistent=false]'], 1, 0, 1, 2, 1)

    expect(countFarLeaves(scene)).toBe(3)
  })

  it('counts none in a scene without leaves', () => {
    expect(countFarLeaves(sceneOf(['minecraft:stone'], 0, 0))).toBe(0)
  })
})
