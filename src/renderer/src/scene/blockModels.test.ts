import { describe, expect, it } from 'vitest'
import { BlockModels, parseState, type AssetSource } from './blockModels'

const CUBE_ALL = {
  textures: { particle: '#all' },
  elements: [
    {
      from: [0, 0, 0],
      to: [16, 16, 16],
      faces: Object.fromEntries(['down', 'up', 'north', 'south', 'west', 'east'].map((side) => [side, { texture: '#all', cullface: side }]))
    }
  ]
}

const FILES: Record<string, unknown> = {
  'minecraft/blockstates/stone.json': { variants: { '': { model: 'minecraft:block/stone' } } },
  'minecraft/models/block/stone.json': { parent: 'block/cube_all', textures: { all: 'minecraft:block/stone' } },
  'minecraft/models/block/cube_all.json': CUBE_ALL,
  'minecraft/blockstates/oak_log.json': {
    variants: { 'axis=y': { model: 'block/oak_log' }, 'axis=x': { model: 'block/oak_log', x: 90, y: 90 } }
  },
  'minecraft/models/block/oak_log.json': { parent: 'block/cube_all', textures: { all: 'block/oak_log' } },
  'minecraft/blockstates/fence.json': {
    multipart: [{ apply: { model: 'block/stone' } }, { when: { north: 'true' }, apply: { model: 'block/stone' } }, { when: { OR: [{ east: 'true' }, { west: 'true|x' }] }, apply: { model: 'block/stone' } }]
  },
  'minecraft/blockstates/chest.json': { variants: { '': { model: 'block/chest' } } },
  'minecraft/models/block/chest.json': { textures: { particle: 'block/oak_planks' } }
}

const assets: AssetSource = { json: async (path) => FILES[path] ?? null }

describe('block models', () => {
  it('reads a block state key', () => {
    expect(parseState('minecraft:oak_log[axis=y]')).toEqual({ namespace: 'minecraft', name: 'oak_log', properties: { axis: 'y' } })
    expect(parseState('stone')).toEqual({ namespace: 'minecraft', name: 'stone', properties: {} })
  })

  it('fills a model from its parents and keeps its six faces culled by their sides', async () => {
    const stone = (await new BlockModels(assets).model('minecraft:stone'))!

    expect(stone.fullCube).toBe(true)
    expect(stone.particle).toBe('minecraft:block/stone')
    expect(stone.quads.map((quad) => quad.texture)).toEqual(Array(6).fill('minecraft:block/stone'))
    expect(stone.quads.find((quad) => quad.facing === 'up')!.corners.every(([, y]) => y === 1)).toBe(true)
  })

  it('turns a variant: x first, then y, so a log lying along x shows its top to the east', async () => {
    const log = (await new BlockModels(assets).model('minecraft:oak_log[axis=x]'))!
    const top = log.quads.find((quad) => quad.cullFace === 'east')!

    expect(top.facing).toBe('east')
    expect(top.corners.every(([x]) => Math.abs(x - 1) < 1e-9)).toBe(true)
  })

  it('applies the multipart pieces whose conditions the state meets', async () => {
    const models = new BlockModels(assets)

    expect((await models.model('minecraft:fence[east=false,north=false,west=false]'))!.quads).toHaveLength(6)
    expect((await models.model('minecraft:fence[east=false,north=true,west=x]'))!.quads).toHaveLength(18)
  })

  it('knows a model without elements, and no model at all', async () => {
    const models = new BlockModels(assets)

    expect(await models.model('minecraft:chest')).toEqual({ quads: [], fullCube: false, particle: 'minecraft:block/oak_planks' })
    expect(await models.model('minecraft:nothing')).toBeNull()
  })
})
