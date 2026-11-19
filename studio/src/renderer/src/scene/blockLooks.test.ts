import { describe, expect, it } from 'vitest'
import { BlockLooks, faceTexturesOf } from './blockLooks'
import { BlockModels, type AssetSource } from './blockModels'
import { cellAt, type PictureSource, type UvCell } from './terrainAtlas'

const SIDES = ['down', 'up', 'north', 'south', 'west', 'east']
const GRASS_TINT = 0x91bd59

// Grass as the client has it: a dirt side with a tinted overlay element over it, a tinted top, and the dirt's bottom.
const FILES: Record<string, unknown> = {
  'minecraft/blockstates/dirt.json': { variants: { '': { model: 'minecraft:block/dirt' } } },
  'minecraft/models/block/dirt.json': {
    textures: { particle: 'minecraft:block/dirt' },
    elements: [{ from: [0, 0, 0], to: [16, 16, 16], faces: Object.fromEntries(SIDES.map((side) => [side, { texture: 'minecraft:block/dirt', cullface: side }])) }]
  },
  'minecraft/blockstates/grass_block.json': { variants: { '': { model: 'minecraft:block/grass_block' } } },
  'minecraft/models/block/grass_block.json': {
    textures: { particle: 'minecraft:block/dirt' },
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: {
          down: { texture: 'minecraft:block/dirt', cullface: 'down' },
          up: { texture: 'minecraft:block/grass_block_top', cullface: 'up', tintindex: 0 },
          north: { texture: 'minecraft:block/grass_block_side', cullface: 'north' },
          south: { texture: 'minecraft:block/grass_block_side', cullface: 'south' },
          west: { texture: 'minecraft:block/grass_block_side', cullface: 'west' },
          east: { texture: 'minecraft:block/grass_block_side', cullface: 'east' }
        }
      },
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: { north: { texture: 'minecraft:block/grass_block_side_overlay', tintindex: 0 } }
      }
    ]
  },
  'minecraft/blockstates/log.json': { variants: { '': { model: 'minecraft:block/log' } } },
  'minecraft/models/block/log.json': {
    textures: { particle: 'minecraft:block/log' },
    elements: [
      {
        from: [0, 0, 0],
        to: [16, 16, 16],
        faces: {
          down: { texture: 'minecraft:block/log_top', cullface: 'down' },
          up: { texture: 'minecraft:block/log_top', cullface: 'up' },
          north: { texture: 'minecraft:block/log', cullface: 'north' }
        }
      }
    ]
  },
  'minecraft/blockstates/chest.json': { variants: { '': { model: 'minecraft:block/chest' } } },
  'minecraft/models/block/chest.json': { textures: { particle: 'minecraft:block/oak_planks' } },
  'minecraft/blockstates/glass.json': { variants: { '': { model: 'minecraft:block/glass' } } },
  'minecraft/models/block/glass.json': {}
}

const assets: AssetSource = { json: async (path) => FILES[path] ?? null }

// Every texture is placed in the next cell, with a grey as light as its number, except the ones named missing.
function pictures(missing: string[] = []): PictureSource {
  const cells = new Map<string, UvCell>()
  return {
    cellOf: async (textureId) => {
      if (missing.includes(textureId)) {
        return null
      }
      if (!cells.has(textureId)) {
        cells.set(textureId, cellAt(cells.size + 1, (cells.size + 1) * 0x101010))
      }
      return cells.get(textureId)!
    }
  }
}

async function modelOf(block: string): ReturnType<BlockModels['model']> {
  return new BlockModels(assets).model(block)
}

describe('block faces', () => {
  it('takes the top, the untinted side layer and the dirt under a block that caps dirt', async () => {
    const faces = faceTexturesOf((await modelOf('minecraft:grass_block'))!, 'minecraft:block/dirt')

    expect(faces).toEqual({
      top: { texture: 'minecraft:block/grass_block_top', tintIndex: 0 },
      side: { texture: 'minecraft:block/grass_block_side', tintIndex: -1 },
      below: { texture: 'minecraft:block/dirt', tintIndex: -1 }
    })
  })

  it('shows the side below the top block when the block does not cap dirt', async () => {
    const faces = faceTexturesOf((await modelOf('minecraft:log'))!, 'minecraft:block/dirt')

    expect(faces!.below).toEqual(faces!.side)
    expect(faces!.side.texture).toBe('minecraft:block/log')
  })

  it('stands the particle texture in for a block the client draws without a model', async () => {
    const faces = faceTexturesOf((await modelOf('minecraft:chest'))!, null)

    expect(faces).toEqual({
      top: { texture: 'minecraft:block/oak_planks', tintIndex: -1 },
      side: { texture: 'minecraft:block/oak_planks', tintIndex: -1 },
      below: { texture: 'minecraft:block/oak_planks', tintIndex: -1 }
    })
  })

  it('has none for a model without any texture', async () => {
    expect(faceTexturesOf((await modelOf('minecraft:glass'))!, null)).toBeNull()
  })
})

describe('block looks', () => {
  it('gives each face the cell of its texture and tints the faces that take a tint', async () => {
    const looks = new BlockLooks(new BlockModels(assets), pictures())

    const grass = (await looks.lookOf('minecraft:grass_block[snowy=false]'))!

    const dirt = (await looks.lookOf('minecraft:dirt'))!

    expect(grass.top.tint).toBe(GRASS_TINT)
    expect(grass.side.tint).toBe(0xffffff)
    expect(grass.below.cell).toBe(dirt.top.cell)
    expect(grass.side.cell).not.toBe(grass.top.cell)
  })

  it("colours a face with its picture's average under the tint", async () => {
    const looks = new BlockLooks(new BlockModels(assets), pictures())

    const grass = (await looks.lookOf('minecraft:grass_block'))!

    expect(grass.top.color).toBe(multiplied(grass.top.cell.average, GRASS_TINT))
    expect(grass.side.color).toBe(grass.side.cell.average)
  })

  it('has no look for a block without a model, or one whose picture cannot be placed', async () => {
    const looks = new BlockLooks(new BlockModels(assets), pictures(['minecraft:block/log_top']))

    const found = await looks.looksOf(['minecraft:unknown_block', 'minecraft:log', 'minecraft:dirt', 'minecraft:dirt'])

    expect([...found.keys()]).toEqual(['minecraft:unknown_block', 'minecraft:log', 'minecraft:dirt'])
    expect(found.get('minecraft:unknown_block')).toBeNull()
    expect(found.get('minecraft:log')).toBeNull()
    expect(found.get('minecraft:dirt')).not.toBeNull()
  })
})

function multiplied(color: number, tint: number): number {
  const channel = (shift: number): number => Math.round((((color >> shift) & 0xff) * ((tint >> shift) & 0xff)) / 255)
  return (channel(16) << 16) | (channel(8) << 8) | channel(0)
}
